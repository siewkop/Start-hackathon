import asyncio
import hashlib
import json
import time
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
                    raise HTTPException(503, "Dostawca map jest zajęty. Spróbuj za chwilę.")
                if response.status_code == 400 and group == "routing":
                    error = response.json()
                    if isinstance(error, dict) and error.get("code") in ("NoSegment", "NoRoute"):
                        raise HTTPException(422, "Nie znaleziono drogi przy wybranym punkcie. Przesuń go bliżej ścieżki.")
                response.raise_for_status()
                data = response.json()
            except (httpx.HTTPError, ValueError) as exc:
                raise HTTPException(502, "Usługa map jest niedostępna. Spróbuj ponownie.") from exc
            self.storage.cache_put(key, data, ttl)
            return data

    async def plan(self, request: PlanRequest, snap_radius: int | None = None, timeout: float = 25) -> Route:
        coordinates = ";".join(f"{p.lon:.6f},{p.lat:.6f}" for p in request.points)
        url = f"{self.urls[request.profile]}/route/v1/driving/{coordinates}"
        params = {"overview": "full", "geometries": "geojson", "steps": "false"}
        if snap_radius is not None:
            params["radiuses"] = ";".join([str(snap_radius)] * len(request.points))
        data = await self._get(url, params, "routing", 3600, timeout=timeout)
        if not isinstance(data, dict) or data.get("code") != "Ok" or not data.get("routes"):
            raise HTTPException(422, "Nie znaleziono trasy. Wybierz punkty bliżej ścieżek lub dróg.")
        try:
            result = data["routes"][0]
            return Route(id=str(uuid4()), profile=request.profile, points=request.points,
                         geometry=Geometry.model_validate(result["geometry"]),
                         distance_m=result["distance"], duration_s=result["duration"],
                         created_at=datetime.now(timezone.utc))
        except (KeyError, TypeError, IndexError, ValidationError) as exc:
            raise HTTPException(502, "Usługa tras zwróciła nieprawidłowe dane.") from exc

    async def search(self, query: str) -> list[Place]:
        data = await self._get(f"{self.photon_url}/api/", {"q": query, "limit": 5}, "search", 86400)
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
            raise HTTPException(502, "Wyszukiwarka zwróciła nieprawidłowe dane.") from exc
