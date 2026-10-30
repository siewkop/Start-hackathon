"""Geometry in meters; no routing or invented straight-line routes here."""
import bisect
import math

from .models import Point

EARTH = 6_371_008.8


def distance(a: Point, b: Point) -> float:
    lat1, lat2 = math.radians(a.lat), math.radians(b.lat)
    dlat = lat2 - lat1
    dlon = math.radians(b.lon - a.lon)
    value = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH * math.asin(min(1.0, math.sqrt(value)))


class Line:
    def __init__(self, points: list[Point]):
        self.points = points
        self.origin = points[0]
        self.scale = math.cos(math.radians(self.origin.lat))
        self.xy = [self.project(p) for p in points]
        self.progress = [0.0]
        for a, b in zip(points, points[1:]):
            self.progress.append(self.progress[-1] + distance(a, b))
        self.length = self.progress[-1]

    def project(self, p: Point):
        lon_delta = (p.lon - self.origin.lon + 180) % 360 - 180
        return EARTH * math.radians(lon_delta) * self.scale, EARTH * math.radians(p.lat - self.origin.lat)

    @staticmethod
    def segment(xy, a, b):
        dx, dy = b[0] - a[0], b[1] - a[1]
        square = dx * dx + dy * dy
        t = max(0.0, min(1.0, ((xy[0] - a[0]) * dx + (xy[1] - a[1]) * dy) / square)) if square else 0.0
        return math.hypot(xy[0] - a[0] - t * dx, xy[1] - a[1] - t * dy), t

    def locate(self, point: Point):
        xy = self.project(point)
        best, progress = float("inf"), 0.0
        for i, (a, b) in enumerate(zip(self.xy, self.xy[1:])):
            gap, t = self.segment(xy, a, b)
            if gap < best:
                best, progress = gap, self.progress[i] + t * (self.progress[i + 1] - self.progress[i])
        return best, progress


def anchors(line: Line, required: list[int], limit: int = 20) -> list[int]:
    """Budgeted Douglas–Peucker, with hard stop indices and extra anchors on long segments."""
    selected = {0, len(line.points) - 1, *required}
    tolerance = max(20.0, min(100.0, line.length / 150))
    while len(selected) < limit:
        ordered = sorted(selected)
        worst, chosen = tolerance, None
        for a, b in zip(ordered, ordered[1:]):
            for i in range(a + 1, b):
                gap, _ = Line.segment(line.xy[i], line.xy[a], line.xy[b])
                if gap > worst:
                    worst, chosen = gap, i
        if chosen is None:
            break
        selected.add(chosen)
    spacing = max(1200.0, line.length / 8)
    while len(selected) < limit:
        ordered = sorted(selected)
        intervals = [(line.progress[b] - line.progress[a], a, b) for a, b in zip(ordered, ordered[1:]) if b - a > 1]
        if not intervals:
            break
        gap, a, b = max(intervals)
        if gap <= spacing:
            break
        mid = bisect.bisect_left(line.progress, (line.progress[a] + line.progress[b]) / 2, a + 1, b)
        selected.add(min(b - 1, mid))
    return sorted(selected)


def route_line(coordinates: list[tuple[float, float]]) -> Line:
    # Bound scoring cost on very long geometries; mandatory stops are checked against the full path.
    points = [Point(lon=lon, lat=lat) for lon, lat in coordinates]
    if len(points) <= 800:
        return Line(points)
    # Scoring uses a sampled polyline; final mandatory stop checks use the full geometry.
    step = (len(points) - 1) / 799
    return Line([points[round(i * step)] for i in range(800)])


def routing_points(points: list[Point]) -> list[Point]:
    result = [points[0]]
    for p in points[1:]:
        if distance(result[-1], p) >= 3:
            result.append(p)
    return result
