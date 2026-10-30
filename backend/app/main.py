import hashlib
import os
from contextlib import asynccontextmanager
from typing import Annotated
from uuid import UUID, uuid4
from xml.etree import ElementTree as ET

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Response

from .models import Place, PlanRequest, Route, SaveRequest, SketchRequest
from .providers import ExternalServices
from .attractions import AttractionsProvider, PoiRanker
from .sketch import SketchPlanner
from .storage import Storage


def client_owner(x_client_token: Annotated[str, Header(min_length=64, max_length=64,
                                                       pattern=r"^[0-9a-f]{64}$")]) -> str:
    # A random 256-bit bearer secret, stored only in the Android app's private storage.
    return hashlib.sha256(x_client_token.encode()).hexdigest()


Owner = Annotated[str, Depends(client_owner)]


def create_app(db_path: str | None = None, transport: httpx.AsyncBaseTransport | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.storage = Storage(db_path or os.getenv("DATABASE_PATH", "data/trail.db"))
        async with httpx.AsyncClient(
            timeout=25, transport=transport, follow_redirects=False,
            headers={"User-Agent": os.getenv("MAP_USER_AGENT", "TrailPocket-Hackathon/1.0"),
                     "Accept": "application/json"},
        ) as client:
            app.state.services = ExternalServices(
                client, app.state.storage,
                os.getenv("OSRM_FOOT_URL", "https://routing.openstreetmap.de/routed-foot"),
                os.getenv("OSRM_BIKE_URL", "https://routing.openstreetmap.de/routed-bike"),
                os.getenv("PHOTON_URL", "https://photon.komoot.io"),
            )
            app.state.ranker = PoiRanker(client, app.state.storage)
            app.state.sketch = SketchPlanner(app.state.services, AttractionsProvider(client, app.state.storage, app.state.services), app.state.ranker)
            yield

    app = FastAPI(title="Trail Pocket API", version="1.2.0", lifespan=lifespan,
                  description="Planowanie i zapis tras. Wymagany nagłówek X-Client-Token: 64 znaki hex.")

    @app.get("/health")
    def health():
        return {"status": "ok", "version": "1.2.0", "features": {"sketch": True, "gpt": bool(app.state.ranker.api_key)}}

    @app.get("/api/v1/places", response_model=list[Place])
    async def search_places(owner: Owner, q: Annotated[str, Query(min_length=2, max_length=160)]):
        if len(q.strip()) < 2:
            raise HTTPException(422, "Wpisz co najmniej dwa znaki.")
        return await app.state.services.search(q.strip())

    @app.post("/api/v1/plans", response_model=Route)
    async def plan(request: PlanRequest, owner: Owner):
        route = await app.state.services.plan(request)
        app.state.storage.store_plan(owner, route)
        return route

    @app.post("/api/v1/sketch-plans", response_model=Route)
    async def sketch_plan(request: SketchRequest, owner: Owner):
        route = await app.state.sketch.plan(request)
        app.state.storage.store_plan(owner, route)
        return route

    @app.post("/api/v1/routes", response_model=Route, status_code=201)
    def save_route(request: SaveRequest, owner: Owner):
        plan = app.state.storage.get_plan(owner, request.plan_id)
        if plan is None:
            raise HTTPException(404, "Plan wygasł. Wyznacz trasę ponownie.")
        saved = plan.model_copy(update={"id": str(uuid4()), "name": request.name})
        return app.state.storage.save_route(owner, request.plan_id, saved)

    @app.get("/api/v1/routes", response_model=list[Route])
    def list_routes(owner: Owner):
        return app.state.storage.list_routes(owner)

    @app.get("/api/v1/routes/{route_id}", response_model=Route)
    def get_route(route_id: UUID, owner: Owner):
        route = app.state.storage.get_route(owner, str(route_id))
        if route is None:
            raise HTTPException(404, "Nie znaleziono trasy.")
        return route

    @app.delete("/api/v1/routes/{route_id}", status_code=204)
    def delete_route(route_id: UUID, owner: Owner):
        if not app.state.storage.delete_route(owner, str(route_id)):
            raise HTTPException(404, "Nie znaleziono trasy.")
        return Response(status_code=204)

    @app.get("/api/v1/routes/{route_id}/gpx")
    def export_gpx(route_id: UUID, owner: Owner):
        route = get_route(route_id, owner)
        ET.register_namespace("", "http://www.topografix.com/GPX/1/1")
        ns = "{http://www.topografix.com/GPX/1/1}"
        gpx = ET.Element(ns + "gpx", version="1.1", creator="Trail Pocket")
        for i, stop in enumerate(route.discovery.stops if route.discovery else []):
            waypoint = ET.SubElement(gpx, ns + "wpt", lat=str(stop.lat), lon=str(stop.lon))
            ET.SubElement(waypoint, ns + "name").text = f"Postój {i + 1}"
        trk = ET.SubElement(gpx, ns + "trk")
        ET.SubElement(trk, ns + "name").text = route.name
        segment = ET.SubElement(trk, ns + "trkseg")
        for lon, lat in route.geometry.coordinates:
            ET.SubElement(segment, ns + "trkpt", lat=str(lat), lon=str(lon))
        return Response(ET.tostring(gpx, encoding="utf-8", xml_declaration=True),
                        media_type="application/gpx+xml",
                        headers={"Content-Disposition": f'attachment; filename="trail-{route.id}.gpx"'})

    return app


app = create_app()
