from fastapi import HTTPException

from .attractions import AttractionsProvider, PoiRanker, attraction_weight
from .geo import Line, anchors, distance, route_line, routing_points
from .models import Attraction, Discovery, PlanRequest, Point, Route, SketchRequest
from .providers import ExternalServices


class SketchPlanner:
    def __init__(self, routing: ExternalServices, places: AttractionsProvider, ranker: PoiRanker):
        self.routing, self.places, self.ranker = routing, places, ranker

    async def plan(self, request: SketchRequest) -> Route:
        line = Line(request.path)
        if line.length < 80:
            raise HTTPException(422, "Narysuj trasę dłuższą niż 80 metrów. Przybliż mapę, aby rysować dokładniej.")
        if line.length > 120_000:
            raise HTTPException(422, "Szkic jest za długi. Narysuj trasę do 120 km.")
        indices = anchors(line, request.stop_indices)
        required = {0, len(request.path) - 1, *request.stop_indices}
        stops = [request.path[i].model_copy(update={"name": f"Postój {n + 1}"})
                 for n, i in enumerate(request.stop_indices)]
        points = routing_points([request.path[i] for i in indices])
        if len(points) < 2:
            raise HTTPException(422, "Nie można odczytać przebiegu szkicu. Narysuj wyraźniejszą trasę.")
        baseline = await self.routing.plan(PlanRequest(profile=request.profile, points=points), snap_radius=450, timeout=15)
        self._check_stops(baseline, stops)
        base_line = route_line(baseline.geometry.coordinates)
        warnings = []
        base_sample = base_line.points[::max(1, len(base_line.points) // 60)]
        base_max_gap = max(line.locate(p)[0] for p in base_sample)
        if base_max_gap > 1000:
            warnings.append("Dostępne drogi miejscami odbiegają od szkicu o ponad kilometr. Sprawdź przebieg przed wyruszeniem.")
        try:
            pois = await self.places.along(line)
        except HTTPException:
            pois = []
            warnings.append("Nie udało się pobrać atrakcji. Dopasowano szkic do ścieżek, bez oceny ciekawych miejsc.")
        candidates = sorted(pois, key=lambda p: attraction_weight(p, request.interest) /
                            (1 + base_line.locate(p)[0] / 500), reverse=True)
        candidates, ai_used, ai_warning = await self.ranker.rank(candidates, request.interest)
        if ai_warning:
            warnings.append(ai_warning)
        # A bounded search of baseline + up to three real routed detours, not a global optimum claim.
        variants = [(baseline, None)]
        attempted_locations = []
        for poi in candidates:
            if len(attempted_locations) >= 3 or request.max_extra_ratio == 0:
                break
            gap, _ = base_line.locate(poi)
            if gap <= 120:
                continue
            if any(distance(poi, previous) < 250 for previous in attempted_locations):
                continue
            attempted_locations.append(poi)
            progress = line.locate(poi)[1]
            retained = list(indices)
            nearby = [i for i in indices if i not in required and abs(line.progress[i] - progress) < 700]
            if nearby:
                retained.remove(min(nearby, key=lambda i: distance(request.path[i], poi)))
            # A POI projected at the last meter is still a via point, never a new destination.
            knots = [(line.progress[i], request.path[i]) for i in retained if i not in (0, len(request.path) - 1)]
            knots.append((progress, Point(lat=poi.lat, lon=poi.lon, name=poi.name)))
            routed = routing_points([request.path[0]] + [p for _, p in sorted(knots, key=lambda k: k[0])] + [request.path[-1]])
            try:
                variant = await self.routing.plan(PlanRequest(profile=request.profile, points=routed), snap_radius=450, timeout=10)
                self._check_stops(variant, stops)
                if variant.distance_m > baseline.distance_m * (1 + request.max_extra_ratio) + 5:
                    continue
                variant_line = route_line(variant.geometry.coordinates)
                if variant_line.locate(poi)[0] > 200:
                    continue
                sample = variant_line.points[::max(1, len(variant_line.points) // 60)]
                if max(line.locate(p)[0] for p in sample) > max(1500, base_max_gap + 500):
                    continue
                variants.append((variant, poi.id))
            except HTTPException:
                # An inaccessible attraction must never remove a mandatory stop or discard the valid baseline.
                continue

        def assess(entry):
            route, detour_id = entry
            actual = route_line(route.geometry.coordinates)
            nearby = [(p, actual.locate(p)[0]) for p in pois]
            nearby = [(p, gap) for p, gap in nearby if gap <= 200]
            nearby.sort(key=lambda item: attraction_weight(item[0], request.interest), reverse=True)
            attraction_score = sum(attraction_weight(p, request.interest) for p, _ in nearby[:20])
            # GPT affects attraction priority, never geometric validity or the detour limit.
            if ai_used:
                priorities = {p.id: (len(candidates) - i) / max(1, len(candidates)) for i, p in enumerate(candidates)}
                attraction_score += sum(priorities.get(p.id, 0) for p, _ in nearby[:20])
            sample = actual.points[::max(1, len(actual.points) // 60)]
            deviation = sum(line.locate(p)[0] for p in sample) / len(sample)
            extra = max(0, route.distance_m - baseline.distance_m) / max(1, baseline.distance_m)
            score = attraction_score - 10 * extra - deviation / 200
            return score, nearby[:20]

        assessed = [(assess(v), v) for v in variants]
        (score, nearby), (best, detour_id) = max(assessed, key=lambda x: x[0][0])
        if not pois and not warnings:
            warnings.append("W danych OpenStreetMap nie znaleziono oznaczonych atrakcji w pobliżu szkicu.")
        attractions = [p.model_copy(update={"distance_to_route_m": round(gap, 1),
                                           "selected_for_detour": p.id == detour_id}) for p, gap in nearby]
        extra = max(0.0, best.distance_m - baseline.distance_m)
        explanation = (f"Porównano {len(variants)} poprawnych wariantów. Wybrano trasę z {len(attractions)} "
                       f"oznaczonymi miejscami w pobliżu, zachowując {len(stops)} postojów i ogólny przebieg szkicu.")
        if extra >= 50:
            explanation += f" Dodatkowa droga: około {round(extra)} m."
        if len(variants) == 1:
            explanation = (f"Dopasowano szkic do dróg i ścieżek. W pobliżu trasy: {len(attractions)} miejsc; "
                           f"zaplanowane postoje: {len(stops)}.")
        return best.model_copy(update={"discovery": Discovery(
            sketch=request.path, stops=stops, stop_indices=request.stop_indices, attractions=attractions,
            interest=request.interest, considered_variants=len(variants), baseline_distance_m=baseline.distance_m,
            extra_distance_m=round(extra, 1), ai_used=ai_used, explanation=explanation, warnings=warnings)})

    @staticmethod
    def _check_stops(route: Route, stops: list[Point]):
        if not stops:
            return
        full = Line([Point(lon=lon, lat=lat) for lon, lat in route.geometry.coordinates])
        if any(full.locate(stop)[0] > 180 for stop in stops):
            raise HTTPException(422, "Nie można poprowadzić trasy blisko zaznaczonego postoju. Przesuń go bliżej dostępnej ścieżki.")
