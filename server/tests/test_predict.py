from datetime import datetime

from last10.db import connect, insert_events
from last10.predict import K_PARKING, predict, prior_minutes
from last10.timeutil import SEOUL, slot
from tests.conftest import FIXED_NOW, THURSDAY_2120

LOT = {"id": "lot-x", "type": "parking", "capacity": 100, "available": 50}


def rows_for(zone_id, durations, tokens, moment=THURSDAY_2120, received_at=0.0):
    weekday, bucket, day = slot(moment)
    return [(zone_id, "parking", "park_complete", d, weekday, bucket, day, t, received_at) for d, t in zip(durations, tokens)]


def test_seeded_history_makes_lot_a_slower_than_lot_b_on_a_weekday_evening(client):
    res = client.get("/v1/predictions", params={"zoneIds": "lot-gangnam-a,lot-gangnam-b", "now": "2026-11-12T19:00:00+09:00"}).json()
    park = {p["zoneId"]: p for p in res["predictions"] if p["kind"] == "park"}

    assert park["lot-gangnam-a"]["stage"] == "history"
    assert park["lot-gangnam-a"]["minutes"] > park["lot-gangnam-b"]["minutes"] + 4
    assert {p["kind"] for p in res["predictions"] if p["zoneId"] == "lot-gangnam-a"} == {"park", "exit"}


def test_offsets_shift_the_time_bucket(client):
    res = client.get("/v1/predictions", params={"zoneIds": "pickup-daechi-official", "offsets": "0,20", "now": "2026-11-12T20:50:00+09:00"}).json()

    assert [p["timeBucket"] for p in res["predictions"]] == [83, 84]


def test_too_few_distinct_vehicles_fall_back_to_the_prior_and_are_suppressed(tmp_path):
    conn = connect(tmp_path / "p.db")
    too_few_vehicles = [f"{i % (K_PARKING - 1):02x}" * 8 for i in range(10)]
    insert_events(conn, rows_for("lot-x", [600] * 10, too_few_vehicles))

    p = predict(conn, LOT, "park", THURSDAY_2120, 0, FIXED_NOW)

    assert (p.stage, p.suppressed, p.samples) == ("prior", True, 0)
    assert p.minutes == round(prior_minutes(LOT, "park"), 1)


def test_history_median_is_used_once_enough_distinct_vehicles_exist(tmp_path):
    conn = connect(tmp_path / "p.db")
    insert_events(conn, rows_for("lot-x", [300, 360, 420, 480, 540], [f"{i:02x}" * 8 for i in range(5)]))

    p = predict(conn, LOT, "park", THURSDAY_2120, 0, FIXED_NOW)

    assert (p.stage, p.minutes, p.samples, p.confidence) == ("history", 7.0, 5, "low")


def test_recent_events_blend_into_a_realtime_estimate(tmp_path):
    conn = connect(tmp_path / "p.db")
    insert_events(conn, rows_for("lot-x", [300] * 5, [f"{i:02x}" * 8 for i in range(5)]))
    other_day = datetime(2026, 11, 12, 9, 0, tzinfo=SEOUL)
    insert_events(conn, rows_for("lot-x", [900] * 3, [f"{i:02x}" * 8 for i in range(10, 13)], other_day, received_at=FIXED_NOW - 600))

    p = predict(conn, LOT, "park", THURSDAY_2120, 0, FIXED_NOW)
    later = predict(conn, LOT, "park", THURSDAY_2120, 10, FIXED_NOW)

    assert (p.stage, p.minutes) == ("realtime", 10.0)
    assert later.stage == "history"
