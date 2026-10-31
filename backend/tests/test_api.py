import json
from xml.etree import ElementTree as ET

import httpx
import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.storage import Storage


OWNER = {"X-Client-Token": "a" * 64}
OTHER = {"X-Client-Token": "b" * 64}
PLAN = {"profile": "hike", "points": [
    {"lat": 52.2297, "lon": 21.0122, "name": "Start"},
    {"lat": 52.24, "lon": 21.025, "name": "Cel"},
]}
GEOMETRY = {"type": "LineString", "coordinates": [[21.0122, 52.2297], [21.02, 52.235], [21.025, 52.24]]}


@pytest.fixture
def api(tmp_path):
    calls = []

    def upstream(request):
        calls.append(request)
        if "/api/" in request.url.path:
            return httpx.Response(200, json={"features": [{"geometry": {"coordinates": [21.0122, 52.2297]},
                "properties": {"name": "Warszawa", "city": "Warszawa", "country": "Polska"}}] * 2})
        return httpx.Response(200, json={"code": "Ok", "routes": [
            {"geometry": GEOMETRY, "distance": 1800.5, "duration": 1500.0}]})

    app = create_app(str(tmp_path / "test.db"), httpx.MockTransport(upstream))
    with TestClient(app) as client:
        yield client, calls


def save(client, headers=OWNER, name="Park & las <weekend>"):
    plan = client.post("/api/v1/plans", json=PLAN, headers=headers)
    assert plan.status_code == 200
    saved = client.post("/api/v1/routes", json={"plan_id": plan.json()["id"], "name": name}, headers=headers)
    assert saved.status_code == 201
    return plan.json(), saved.json()


def test_plan_save_list_export_delete(api):
    client, calls = api
    plan, saved = save(client)
    assert saved["geometry"] == GEOMETRY
    assert saved["distance_m"] == 1800.5
    assert saved["id"] != plan["id"]
    assert "/routed-foot/route/v1/driving/21.012200,52.229700;21.025000,52.240000" in str(calls[0].url)
    assert client.get("/api/v1/routes", headers=OWNER).json() == [saved]
    assert client.get(f'/api/v1/routes/{saved["id"]}', headers=OWNER).json() == saved
    gpx = client.get(f'/api/v1/routes/{saved["id"]}/gpx', headers=OWNER)
    assert gpx.status_code == 200
    root = ET.fromstring(gpx.content)
    ns = {"g": "http://www.topografix.com/GPX/1/1"}
    assert root.find("g:trk/g:name", ns).text == "Park & las <weekend>"
    points = root.findall("g:trk/g:trkseg/g:trkpt", ns)
    assert len(points) == 3
    assert float(points[0].attrib["lat"]) == 52.2297
    assert float(points[0].attrib["lon"]) == 21.0122
    assert client.delete(f'/api/v1/routes/{saved["id"]}', headers=OWNER).status_code == 204
    assert client.get("/api/v1/routes", headers=OWNER).json() == []
    assert client.get(f'/api/v1/routes/{saved["id"]}', headers=OWNER).status_code == 404


def test_ownership_for_every_private_operation(api):
    client, _ = api
    plan, saved = save(client)
    assert client.get("/api/v1/routes", headers=OTHER).json() == []
    for suffix in ("", "/gpx"):
        assert client.get(f'/api/v1/routes/{saved["id"]}{suffix}', headers=OTHER).status_code == 404
    assert client.delete(f'/api/v1/routes/{saved["id"]}', headers=OTHER).status_code == 404
    assert client.post("/api/v1/routes", json={"plan_id": plan["id"], "name": "Cudza"}, headers=OTHER).status_code == 404
    assert client.get("/api/v1/routes").status_code == 422
    assert client.get("/api/v1/routes", headers={"X-Client-Token": "wrong"}).status_code == 422


def test_repeated_save_is_idempotent_and_upstream_is_cached(api):
    client, calls = api
    plan, saved = save(client)
    duplicate = client.post("/api/v1/routes", json={"plan_id": plan["id"], "name": "Inna"}, headers=OWNER)
    assert duplicate.json()["id"] == saved["id"]
    assert len(client.get("/api/v1/routes", headers=OWNER).json()) == 1
    assert client.post("/api/v1/plans", json=PLAN, headers=OWNER).status_code == 200
    assert len(calls) == 1


def test_correct_bicycle_endpoint(api):
    client, calls = api
    result = client.post("/api/v1/plans", json={**PLAN, "profile": "bike"}, headers=OWNER)
    assert result.status_code == 200
    assert result.json()["profile"] == "bike"
    assert calls[0].url.path.startswith("/routed-bike/")


@pytest.mark.parametrize("payload", [
    {**PLAN, "profile": "car"},
    {**PLAN, "points": []},
    {**PLAN, "points": [PLAN["points"][0]] * 2},
    {**PLAN, "points": [{"lat": 999, "lon": 21}, PLAN["points"][1]]},
    {**PLAN, "unexpected": True},
])
def test_invalid_plans_do_not_call_upstream(api, payload):
    client, calls = api
    assert client.post("/api/v1/plans", json=payload, headers=OWNER).status_code == 422
    assert not calls


def test_save_validates_name_and_does_not_accept_client_geometry(api):
    client, _ = api
    plan = client.post("/api/v1/plans", json=PLAN, headers=OWNER).json()
    for body in ({"plan_id": plan["id"], "name": "   "},
                 {"plan_id": plan["id"], "name": "x", "geometry": GEOMETRY}):
        assert client.post("/api/v1/routes", json=body, headers=OWNER).status_code == 422


def test_search_normalizes_and_caches(api):
    client, calls = api
    results = client.get("/api/v1/places", params={"q": "Warszawa"}, headers=OWNER)
    assert results.status_code == 200
    assert results.json() == [{"lat": 52.2297, "lon": 21.0122, "name": "Warszawa, Polska"}]
    client.get("/api/v1/places", params={"q": "Warszawa"}, headers=OWNER)
    assert len(calls) == 1
    assert client.get("/api/v1/places", params={"q": "  "}, headers=OWNER).status_code == 422


@pytest.mark.parametrize("status,body,expected", [
    (200, {"code": "NoRoute", "routes": []}, 422),
    (200, {"code": "Ok", "routes": [{"geometry": GEOMETRY}]}, 502),
    (200, {"code": "Ok", "routes": [{"geometry": {"type": "Point", "coordinates": []}, "distance": 1, "duration": 1}]}, 502),
    (429, {}, 503),
    (400, {"code": "NoSegment"}, 422),
    (503, {}, 502),
])
def test_upstream_errors_never_create_fake_routes(tmp_path, status, body, expected):
    app = create_app(str(tmp_path / "errors.db"), httpx.MockTransport(lambda _: httpx.Response(status, json=body)))
    with TestClient(app) as client:
        result = client.post("/api/v1/plans", json=PLAN, headers=OWNER)
        assert result.status_code == expected
        assert client.get("/api/v1/routes", headers=OWNER).json() == []


def test_timeout_is_actionable(tmp_path):
    def upstream(request):
        raise httpx.ReadTimeout("timeout", request=request)
    app = create_app(str(tmp_path / "timeout.db"), httpx.MockTransport(upstream))
    with TestClient(app) as client:
        result = client.post("/api/v1/plans", json=PLAN, headers=OWNER)
        assert result.status_code == 502
        assert "unavailable" in result.json()["detail"]


def test_routes_persist_across_restart(api, tmp_path):
    client, _ = api
    _, saved = save(client)
    app = create_app(str(tmp_path / "test.db"))
    with TestClient(app) as restarted:
        assert restarted.get("/api/v1/routes", headers=OWNER).json() == [saved]


def test_expired_plan_cannot_be_saved(api):
    client, _ = api
    plan = client.post("/api/v1/plans", json=PLAN, headers=OWNER).json()
    with client.app.state.storage.connect() as db:
        db.execute("UPDATE plans SET expires=0")
    assert client.post("/api/v1/routes", json={"plan_id": plan["id"], "name": "Stara"}, headers=OWNER).status_code == 404


def test_storage_releases_windows_file_handles(tmp_path):
    path = tmp_path / "handles.db"
    storage = Storage(str(path))
    storage.cache_put("key", {"ok": True}, 60)
    assert storage.cache_get("key") == {"ok": True}
    path.unlink()  # Windows refuses this when a connection is still open.
    assert not path.exists()
