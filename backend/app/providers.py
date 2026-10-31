import asyncio
import hashlib
import json
import time
import os
from datetime import datetime, timezone
from uuid import uuid4

import httpx
from fastapi import HTTPException
from pydantic import ValidationError

from .models import Geometry, Place, PlanRequest, Profile, Route
from .storage import Storage


class ExternalServices:
    """Replace this adapter to use your own routing engine or intelligence service."""

    def __init__(self, client: httpx.AsyncClient, storage: Storage, foot_url: str,
                 bike_url: str, photon_url: str):
        self.client = client
        self.storage = storage
        self.urls = {Profile.hike: foot_url.rstrip("/"), Profile.bike: bike_url.rstrip("/")}
        self.photon_url = photon_url.rstrip("/")
        self.locks = {"routing": asyncio.Lock(), "search": asyncio.Lock()}
        self.last_request = {"routing": 0.0, "search": 0.0}

    async def _get(self, url: str, params: dict, group: str, ttl: int, timeout: float = 25):
        key = hashlib.sha256((url + json.dumps(params, sort_keys=True)).encode()).hexdigest()
        cached = self.storage.cache_get(key)
        if cached is not None:
            return cached
        # Single uvicorn worker: all phones and both route profiles share this limit.
        async with self.locks[group]:
            cached = self.storage.cache_get(key)
            if cached is not None:
                return cached
            await asyncio.sleep(max(0.0, 1.1 - (time.monotonic() - self.last_request[group])))
            self.last_request[group] = time.monotonic()
            try:
                response = await self.client.get(url, params=params, timeout=timeout)
                if response.status_code == 429:
                    raise HTTPException(503, "The map service is busy. Try again shortly.")
                if response.status_code == 400 and group == "routing":
                    error = response.json()
                    if isinstance(error, dict) and error.get("code") in ("NoSegment", "NoRoute"):
                        raise HTTPException(422, "No road found near this point. Move it closer to a path.")
                response.raise_for_status()
                data = response.json()
            except (httpx.HTTPError, ValueError) as exc:
                raise HTTPException(502, "The map service is unavailable. Please try again.") from exc
            self.storage.cache_put(key, data, ttl)
            return data

    async def plan(self, request: PlanRequest, snap_radius: int | None = None, timeout: float = 25) -> Route:
        warning = None
        if request.recreation > 0:
            try:
                return await self._recreation_plan(request, timeout)
            except HTTPException:
                warning = "The leisure profile is unavailable. Used standard routing instead."
        coordinates = ";".join(f"{p.lon:.6f},{p.lat:.6f}" for p in request.points)
        url = f"{self.urls[request.profile]}/route/v1/driving/{coordinates}"
        params = {"overview": "full", "geometries": "geojson", "steps": "false"}
        if snap_radius is not None:
            params["radiuses"] = ";".join([str(snap_radius)] * len(request.points))
        data = await self._get(url, params, "routing", 3600, timeout=timeout)
        if not isinstance(data, dict) or data.get("code") != "Ok" or not data.get("routes"):
            raise HTTPException(422, "No route found. Choose points closer to paths or roads.")
        try:
            result = data["routes"][0]
            return Route(id=str(uuid4()), profile=request.profile, points=request.points,
                         geometry=Geometry.model_validate(result["geometry"]),
                         distance_m=result["distance"], duration_s=result["duration"],
                         created_at=datetime.now(timezone.utc), recreation=request.recreation, routing_warning=warning)
        except (KeyError, TypeError, IndexError, ValidationError) as exc:
            raise HTTPException(502, "The routing service returned invalid data.") from exc

    async def _recreation_plan(self, request: PlanRequest, timeout: float) -> Route:
        profile = "hiking-beta" if request.profile == Profile.hike else "trekking"
        params = {"lonlats": "|".join(f"{p.lon:.6f},{p.lat:.6f}" for p in request.points),
                  "profile": profile, "alternativeidx": 0, "format": "geojson"}
        if request.profile == Profile.hike:
            params.update({"profile:SAC_scale_limit": 1, "profile:consider_elevation": 0,
                           "profile:shortest_way": 1 if request.recreation < .3 else 0,
                           "profile:prefer_hiking_routes": 1 if request.recreation >= .6 else 0})
        else:
            params.update({"profile:consider_noise": 1 if request.recreation >= .3 else 0,
                           "profile:consider_river": 1 if request.recreation >= .6 else 0,
                           "profile:consider_forest": 1 if request.recreation >= .6 else 0})
        data = await self._get(os.getenv("BROUTER_URL", "https://brouter.de/brouter"), params, "routing", 3600, timeout=timeout)
        try:
            feature = next(f for f in data["features"] if f["geometry"]["type"] == "LineString")
            props = feature["properties"]
            geometry = Geometry(coordinates=[tuple(p[:2]) for p in feature["geometry"]["coordinates"]])
            from .geo import Line
            from .models import Point
            line = Line([Point(lon=p[0], lat=p[1]) for p in geometry.coordinates])
            if any(line.locate(p)[0] > 450 for p in request.points):
                raise ValueError("Routing moved waypoints too far")
            return Route(id=str(uuid4()), profile=request.profile, recreation=request.recreation,
                         points=request.points, geometry=geometry, distance_m=float(props["track-length"]),
                         duration_s=float(props["total-time"]), created_at=datetime.now(timezone.utc), routing_engine="brouter")
        except (KeyError, TypeError, ValueError, StopIteration, ValidationError) as exc:
            raise HTTPException(502, "Could not read the leisure route.") from exc

    async def search(self, query: str) -> list[Place]:
        data = await self._get(f"{self.photon_url}/api/", {"q": query, "limit": 5, "lang": "en"}, "search", 86400)
        try:
            places = []
            seen = set()
            for feature in data["features"][:5]:
                props = feature["properties"]
                lon, lat = feature["geometry"]["coordinates"]
                parts = list(dict.fromkeys(str(props[k]) for k in ("name", "street", "city", "country")
                                           if props.get(k)))
                place = Place(lat=lat, lon=lon, name=", ".join(parts)[:160] or query)
                key = (round(place.lat, 5), round(place.lon, 5), place.name)
                if key not in seen:
                    seen.add(key)
                    places.append(place)
            return places
        except (KeyError, TypeError, ValueError, ValidationError) as exc:
            raise HTTPException(502, "The search service returned invalid data.") from exc
