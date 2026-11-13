from pathlib import Path

from fastapi.testclient import TestClient

from last10.main import create_app


def client(tmp_path: Path, demo: bool) -> TestClient:
    return TestClient(create_app(tmp_path / "t.db", discover_every_sec=0, demo=demo))


def test_demo_endpoints_exist_only_when_demo_is_enabled(tmp_path: Path):
    assert client(tmp_path, demo=False).get("/demo/telemetry").status_code == 404
    assert client(tmp_path, demo=True).get("/demo/telemetry").json() == {"telemetry": None, "ageSec": None}


def test_telemetry_mirror_returns_the_latest_vehicle_state(tmp_path: Path):
    c = client(tmp_path, demo=True)
    c.post("/demo/telemetry", json={"gear": "D", "speedKph": 12.0})
    c.post("/demo/telemetry", json={"gear": "P", "speedKph": 0.0})

    body = c.get("/demo/telemetry").json()

    assert body["telemetry"] == {"gear": "P", "speedKph": 0.0}
    assert body["ageSec"] >= 0


def test_unknown_scenario_is_rejected_without_starting_a_process(tmp_path: Path):
    c = client(tmp_path, demo=True)

    assert c.post("/demo/scenario/rm-rf").status_code == 404
    assert c.get("/demo/scenario").json()["running"] is False


def test_profile_covers_24_hours_for_a_known_zone_and_is_empty_for_an_unknown_one(tmp_path: Path):
    c = client(tmp_path, demo=False)

    known = c.get("/v1/profile", params={"zoneId": "lot-gangnam-a", "kind": "park"}).json()
    unknown = c.get("/v1/profile", params={"zoneId": "nope", "kind": "park"}).json()

    assert [h["hour"] for h in known["hours"]] == list(range(24))
    assert unknown["hours"] == []
