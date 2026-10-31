import asyncio
import json

import httpx

from app.attractions import AttractionsProvider, PoiRanker, curated, deduplicate, sample_corridor
from app.geo import Line, distance
from app.models import Attraction, Interest, Point
from app.storage import Storage


def place(i, category="culture", lon=21.0, **kwargs):
    return Attraction(id=f"node/{i}", lat=52, lon=lon, name=f"Miejsce {i}", category=category, **kwargs)


def test_sparse_sketch_samples_segment_interiors_evenly():
    line = Line([Point(lat=52, lon=21), Point(lat=52, lon=21.12)])
    points = sample_corridor(line, 6)
    gaps = [distance(a, b) for a, b in zip(points, points[1:])]
    assert len(points) == 6
    assert max(gaps) - min(gaps) < 1
    assert points[0] == line.points[0] and points[-1] == line.points[-1]


def test_duplicate_osm_building_and_node_keep_richer_description():
    a = place(1).model_copy(update={"name": "Zamek"})
    b = place(2, lon=21.0001, description="Opis obiektu").model_copy(update={"name": "ZAMEK"})
    distant = place(3, lon=21.1).model_copy(update={"name": "Zamek"})
    result = deduplicate([a, b, distant])
    assert {p.id for p in result} == {b.id, distant.id}


def test_curated_shortlist_resists_cluster_of_similar_monuments():
    line = Line([Point(lat=52, lon=21), Point(lat=52, lon=21.1)])
    places = [place(i, lon=21 + i * .00001) for i in range(40)]
    places += [place(100, "nature", 21.03), place(101, "museum", 21.05), place(102, "view", 21.07)]
    result = curated(places, line, Interest.balanced)
    assert len(result) == 8
    assert {"nature", "museum", "view"}.issubset({p.category for p in result})


def test_ai_priority_can_change_selection_without_inventing_places():
    line = Line([Point(lat=52, lon=21), Point(lat=52, lon=21.1)])
    places = [place(i, lon=21 + i * .001) for i in range(12)]
    result = curated(places, line, Interest.balanced, limit=1, priorities={"node/11": 1})
    assert result[0].id == "node/11"


def test_provider_keeps_source_description_and_new_categories(tmp_path):
    async def exercise():
        data = {"elements": [{"id": 1, "type": "node", "lat": 52, "lon": 21.01,
            "tags": {"name": "Muzeum", "tourism": "museum", "description:pl": "<b>Historia</b> miasta.",
                     "wikipedia": "pl:Muzeum"}},
            {"id": 2, "type": "node", "lat": 52, "lon": 21.02,
             "tags": {"name": "Prywatne", "tourism": "museum", "access": "private"}}]}
        async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(200, json=data))) as client:
            provider = AttractionsProvider(client, Storage(str(tmp_path / "source.db")))
            places = await provider.along(Line([Point(lat=52, lon=21), Point(lat=52, lon=21.1)]))
            assert len(places) == 1
            assert places[0].category == "museum"
            assert places[0].description == "Historia miasta."
            assert places[0].wikipedia == "pl:Muzeum"
    asyncio.run(exercise())


def test_model_receives_context_and_keeps_unranked_tail(tmp_path, monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-only")
    places = [place(i, description="Opis ze zrodla", kind="castle") for i in range(40)]
    def upstream(request):
        body = json.loads(request.content)
        payload = json.loads(body["input"])
        assert len(payload["places"]) == 32
        assert payload["places"][0]["description"] == "Opis ze zrodla"
        ids = [p["id"] for p in payload["places"]][::-1]
        return httpx.Response(200, json={"status": "completed", "output": [{"type": "message", "content": [
            {"type": "output_text", "text": json.dumps({"ranked_ids": ids})}]}]})
    async def exercise():
        async with httpx.AsyncClient(transport=httpx.MockTransport(upstream)) as client:
            result, used, warning = await PoiRanker(client, Storage(str(tmp_path / "ai.db"))).rank(places, Interest.balanced)
            assert used and not warning
            assert len(result) == 40
            assert result[0].id == "node/31" and result[-1].id == "node/39"
    asyncio.run(exercise())
