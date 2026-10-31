import asyncio
from datetime import datetime, timezone

import httpx

from app.geo import Line
from app.models import Geometry, PlanRequest, Point, Profile, Route
from app.providers import ExternalServices
from app.route_quality import clean_spurs
from app.storage import Storage


def p(x, y=0):
    return Point(lon=21 + x * .001, lat=52 + y * .001)


def route(points):
    return Route(id="demo", profile="hike", points=[points[0], points[-1]],
                 geometry=Geometry(coordinates=[(v.lon, v.lat) for v in points]),
                 distance_m=Line(points).length, duration_s=1000, created_at=datetime.now(timezone.utc))


def test_spur_is_removed_without_creating_a_new_road():
    points = [p(0), p(2), p(2, 1), p(2, 2), p(2, 1), p(2), p(4)]
    original = route(points)
    clean = clean_spurs(original, [p(0), p(4)], [])
    assert clean.geometry.coordinates == [(v.lon, v.lat) for v in [p(0), p(2), p(4)]]
    assert clean.distance_m < original.distance_m
    assert clean.duration_s < original.duration_s


def test_stop_on_branch_preserves_required_visit():
    original = route([p(0), p(2), p(2, 1), p(2), p(4)])
    assert clean_spurs(original, [p(0), p(4)], [p(2, 1)]) == original


def test_real_loop_is_not_a_spur():
    points = [p(0), p(2), p(2, 2), p(4, 2), p(4), p(2), p(6)]
    original = route(points)
    assert clean_spurs(original, [p(0), p(6)], []) == original


def test_intentional_retraced_sketch_is_preserved():
    points = [p(0), p(2), p(2, 2), p(2), p(4)]
    original = route(points)
    assert clean_spurs(original, points, []) == original


def test_recreation_routes_use_real_profile_and_drop_elevation_dimension(tmp_path):
    seen = []
    def upstream(request):
        seen.append(request)
        return httpx.Response(200, json={"features": [{"geometry": {"type": "LineString", "coordinates": [
            [21, 52, 90], [21.002, 52, 92]]}, "properties": {"track-length": "140", "total-time": "100"}}]})
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(upstream)) as client:
            service = ExternalServices(client, Storage(str(tmp_path / "routing.db")), "https://foot", "https://bike", "https://search")
            for profile in (Profile.hike, Profile.bike):
                result = await service.plan(PlanRequest(profile=profile, points=[p(0), p(2)], recreation=1))
                assert result.routing_engine == "brouter"
                assert result.recreation == 1
                assert result.geometry.coordinates == [(21, 52), (21.002, 52)]
            assert seen[0].url.params["profile"] == "hiking-beta"
            assert seen[0].url.params["profile:SAC_scale_limit"] == "1"
            assert seen[1].url.params["profile"] == "trekking"
            assert seen[1].url.params["profile:consider_river"] == "1"
    asyncio.run(run())


def test_unavailable_recreation_service_falls_back_with_explicit_warning(tmp_path):
    def upstream(request):
        if "brouter" in request.url.host:
            return httpx.Response(503)
        return httpx.Response(200, json={"code": "Ok", "routes": [{"geometry": {
            "type": "LineString", "coordinates": [[21, 52], [21.002, 52]]}, "distance": 140, "duration": 100}]})
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(upstream)) as client:
            service = ExternalServices(client, Storage(str(tmp_path / "fallback.db")), "https://foot", "https://bike", "https://search")
            result = await service.plan(PlanRequest(profile="hike", points=[p(0), p(2)], recreation=1))
            assert result.routing_engine == "osrm"
            assert result.routing_warning
    asyncio.run(run())
