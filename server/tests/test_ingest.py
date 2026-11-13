from tests.conftest import FIXED_NOW


def event(**over):
    base = {
        "zoneId": "lot-gangnam-a", "zoneType": "parking", "event": "park_complete", "durationSec": 420,
        "weekday": 4, "timeBucket": 85, "day": "2026-11-12", "dailyToken": "a1b2c3d4e5f60718293a4b5c6d7e8f90",
    }
    return base | over


def test_valid_events_are_stored_and_counted(client):
    before = client.get("/v1/stats").json()["total"]
    res = client.post("/v1/events", json={"events": [event(), event(zoneId="cell:37.49950,127.06250", zoneType="hotspot_candidate", event="dropoff_complete")]})

    assert res.json() == {"accepted": 2, "rejected": []}
    stats = client.get("/v1/stats").json()
    assert stats["total"] == before + 2
    assert stats["lastHour"][0]["received_at"] == FIXED_NOW


def test_each_invalid_event_is_rejected_with_its_index_and_reason(client):
    res = client.post("/v1/events", json={"events": [
        event(),
        event(dailyToken="NOT-HEX"),
        event(zoneId="lot-unknown"),
        event(event="pickup_complete"),
        event(zoneType="pickup_official"),
        event(zoneId="37.4,127.0", zoneType="hotspot_candidate", event="pickup_complete"),
        event(latitude=37.5),
        event(durationSec="420"),
    ]}).json()

    assert res["accepted"] == 1
    assert [r["index"] for r in res["rejected"]] == [1, 2, 3, 4, 5, 6, 7]
    reasons = {r["index"]: r["reason"] for r in res["rejected"]}
    assert reasons[2].startswith("zoneId: unknown zone")
    assert "not valid for parking" in reasons[3]
    assert "latitude" in reasons[6]


def test_body_without_events_list_is_rejected(client):
    assert client.post("/v1/events", json={"foo": 1}).json()["accepted"] == 0
