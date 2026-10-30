from datetime import datetime
from enum import Enum
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator


class Profile(str, Enum):
    hike = "hike"
    bike = "bike"


class Interest(str, Enum):
    balanced = "balanced"
    nature = "nature"
    culture = "culture"


class Point(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    lat: float = Field(ge=-85.0, le=85.0)
    lon: float = Field(ge=-180.0, le=180.0)
    name: str = Field(default="Punkt na mapie", max_length=160)


class PlanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    profile: Profile
    points: list[Point] = Field(min_length=2, max_length=25)

    @model_validator(mode="after")
    def distinct_points(self):
        if any(a.lat == b.lat and a.lon == b.lon for a, b in zip(self.points, self.points[1:])):
            raise ValueError("Kolejne punkty muszą być różne.")
        return self


Coordinate = tuple[Annotated[float, Field(ge=-180, le=180, allow_inf_nan=False)],
                   Annotated[float, Field(ge=-90, le=90, allow_inf_nan=False)]]


class Geometry(BaseModel):
    type: Literal["LineString"] = "LineString"
    coordinates: list[Coordinate] = Field(min_length=2, max_length=100_000)


class SketchRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    profile: Profile
    path: list[Point] = Field(min_length=2, max_length=2000)
    stop_indices: list[int] = Field(default_factory=list, max_length=8)
    interest: Interest = Interest.balanced
    max_extra_ratio: float = Field(default=0.25, ge=0, le=0.4)

    @model_validator(mode="after")
    def valid_stops(self):
        if len(set(self.stop_indices)) != len(self.stop_indices):
            raise ValueError("Postoje nie mogą się powtarzać.")
        if any(i < 0 or i >= len(self.path) for i in self.stop_indices):
            raise ValueError("Postój musi wskazywać punkt szkicu.")
        self.stop_indices.sort()
        return self


class Attraction(Point):
    id: str = Field(pattern=r"^(node|way|relation)/[0-9]+$")
    category: Literal["nature", "culture", "view"]
    distance_to_route_m: float = Field(default=0, ge=0, allow_inf_nan=False)
    selected_for_detour: bool = False


class Discovery(BaseModel):
    sketch: list[Point]
    stops: list[Point]
    stop_indices: list[int]
    attractions: list[Attraction]
    interest: Interest
    considered_variants: int = Field(ge=1)
    baseline_distance_m: float
    extra_distance_m: float
    ai_used: bool = False
    explanation: str
    warnings: list[str] = Field(default_factory=list)


class Route(BaseModel):
    id: str
    name: str | None = None
    profile: Profile
    points: list[Point]
    geometry: Geometry
    distance_m: float = Field(ge=0, allow_inf_nan=False)
    duration_s: float = Field(ge=0, allow_inf_nan=False)
    created_at: datetime
    discovery: Discovery | None = None


class SaveRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    plan_id: str = Field(pattern=r"^[0-9a-f-]{36}$")
    name: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=80)]


class Place(Point):
    pass
