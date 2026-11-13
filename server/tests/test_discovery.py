from datetime import date

from last10.db import connect, insert_events
from last10.discovery import discover
from last10.geo import haversine_m
from last10.seed import TRUE_HOTSPOTS, generate
from last10.zones import load_seed_zones
from last10.main import DATA_DIR

GUIDANCE = [z for z in load_seed_zones(DATA_DIR / "zones.json") if z["guidance"]]


def test_finds_exactly_the_three_true_hotspots_and_no_noise(client):
    hotspots = client.get("/v1/hotspots").json()["hotspots"]

    assert len(hotspots) == 3
    for name, center in TRUE_HOTSPOTS.items():
        assert any(haversine_m(center, tuple(h["center"])) < 40 for h in hotspots), name
    peaks = sorted(h["peak"] for h in hotspots)
    assert peaks == sorted(["평일 21:00-22:00", "상시", next(p for p in peaks if p.startswith("평일 07:") or p.startswith("평일 08:"))])


def test_nearby_hotspots_stay_separate_as_months_of_data_accumulate(tmp_path):
    conn = connect(tmp_path / "long.db")
    insert_events(conn, generate(date(2026, 8, 1), days=90, seed=3))

    found = discover(conn, GUIDANCE)

    assert len(found) == 3
    for name, center in TRUE_HOTSPOTS.items():
        assert any(haversine_m(center, h.center) < 40 for h in found), name


def test_discovered_hotspots_appear_as_non_guidance_zones(client):
    zones = client.get("/v1/zones").json()["zones"]
    hotspot_zones = [z for z in zones if z["type"] == "pickup_hotspot"]

    assert len(hotspot_zones) == 3
    assert all(z["guidance"] is False for z in hotspot_zones)
    assert hotspot_zones[0]["name"] == "승하차 혼잡 지점 A"


def test_hotspot_ids_stay_stable_across_reruns(seeded_db):
    conn = connect(seeded_db)
    discover(conn, GUIDANCE)
    first = {r["id"]: (r["lat"], r["lon"]) for r in conn.execute("SELECT * FROM hotspots WHERE active = 1")}
    insert_events(conn, generate(date(2026, 11, 9), days=1, seed=99))
    discover(conn, GUIDANCE)
    second = {r["id"]: (r["lat"], r["lon"]) for r in conn.execute("SELECT * FROM hotspots WHERE active = 1")}

    assert set(first) == set(second)


def test_repeats_on_too_few_days_are_not_reported(tmp_path):
    conn = connect(tmp_path / "d.db")
    insert_events(conn, generate(date(2026, 11, 2), days=3, seed=7))

    assert discover(conn, GUIDANCE) == []
