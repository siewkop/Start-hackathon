"""Remove exact out-and-back spurs, never bridges across a real loop."""
from .geo import Line, distance
from .models import Geometry, Point, Route


def prune_retraced(points: list[Point], protected: list[Point]):
    stack = []
    removed = 0.0
    for p in points:
        if stack and distance(stack[-1], p) < .1:
            continue
        if len(stack) >= 2 and distance(stack[-2], p) < 2:
            leg = Line([stack[-2], stack[-1]])
            # A requested stop on the branch makes that branch intentional.
            if not any(leg.locate(stop)[0] < 40 for stop in protected):
                removed += 2 * distance(stack[-2], stack[-1])
                stack.pop()
                continue
        stack.append(p)
    return stack, removed


def clean_spurs(route: Route, sketch: list[Point], stops: list[Point]) -> Route:
    # Respect a deliberately retraced sketch (e.g. walking to a viewpoint and back).
    if prune_retraced(sketch, [])[1] > 20:
        return route
    original = [Point(lon=lon, lat=lat) for lon, lat in route.geometry.coordinates]
    points, removed = prune_retraced(original, stops + [original[0], original[-1]])
    if removed < 5 or len(points) < 2:
        return route
    old_length, new_length = Line(original).length, Line(points).length
    ratio = new_length / max(1, old_length)
    return route.model_copy(update={"geometry": Geometry(coordinates=[(p.lon, p.lat) for p in points]),
                                    "distance_m": route.distance_m * ratio,
                                    "duration_s": route.duration_s * ratio})
