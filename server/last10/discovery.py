import json
import sqlite3
from collections import Counter
from dataclasses import dataclass

import numpy as np
from sklearn.cluster import DBSCAN

from .geo import EARTH_RADIUS_M, contains, haversine_m, padded_bbox, parse_cell
from .timeutil import BUCKETS_PER_DAY, bucket_label, weekday_class
from .zones import Zone

EPS_M = 60.0
MIN_CELL_EVENTS = 8
LOCAL_PEAK_RATIO = 0.1
LOCAL_RADIUS_M = 150.0
MIN_VEHICLES = 10
MIN_DAYS = 5
COVERED_RADIUS_M = 50.0
PEAK_WINDOW_BUCKETS = 4
PEAK_SHARE = 0.4


@dataclass
class Hotspot:
    center: tuple[float, float]
    polygon: list[list[float]]
    cells: list[str]
    distinct_vehicles: int
    distinct_days: int
    events: int
    peak: str
    profile: list[dict[str, object]]


def peak_label(profile: Counter[tuple[str, int]]) -> str:
    """Best 1-hour window per weekday class; a cluster spread thinly over the day is labelled 상시 (all day)."""
    total = sum(profile.values())
    best_share, best_cls, best_start = 0.0, "weekday", 0
    for cls in ("weekday", "weekend"):
        for start in range(BUCKETS_PER_DAY):
            share = sum(profile[(cls, (start + i) % BUCKETS_PER_DAY)] for i in range(PEAK_WINDOW_BUCKETS)) / total
            if share > best_share:
                best_share, best_cls, best_start = share, cls, start
    if best_share < PEAK_SHARE:
        return "상시"
    day = "평일" if best_cls == "weekday" else "주말"
    return f"{day} {bucket_label(best_start)}-{bucket_label(best_start + PEAK_WINDOW_BUCKETS)}"


def find_hotspots(
    rows: list[sqlite3.Row],
    guidance_zones: list[Zone],
    min_vehicles: int = MIN_VEHICLES,
    min_days: int = MIN_DAYS,
) -> list[Hotspot]:
    """
    Events arrive snapped to a grid, so many share one cell. Plain point-DBSCAN would make every sparse cell
    next to a busy one a core point and chain neighbouring hotspots together as data accumulates. Instead a
    cell is dense only if it has MIN_CELL_EVENTS and at least LOCAL_PEAK_RATIO of the busiest cell within
    LOCAL_RADIUS_M; DBSCAN (min_samples=1, eps = one cell) then joins adjacent dense cells into hotspots.
    """
    by_cell: dict[tuple[float, float], list[sqlite3.Row]] = {}
    for r in rows:
        p = parse_cell(r["zone_id"])
        if p is not None:
            by_cell.setdefault(p, []).append(r)
    candidates = [c for c, rs in by_cell.items() if len(rs) >= MIN_CELL_EVENTS]
    dense = [
        c for c in candidates
        if len(by_cell[c]) >= LOCAL_PEAK_RATIO * max(len(by_cell[o]) for o in candidates if haversine_m(c, o) <= LOCAL_RADIUS_M)
    ]
    if not dense:
        return []
    labels = DBSCAN(eps=EPS_M / EARTH_RADIUS_M, min_samples=1, metric="haversine", algorithm="ball_tree").fit(np.radians(np.array(dense))).labels_

    found: list[Hotspot] = []
    for label in sorted(set(labels)):
        members = [(cell, r) for i in np.flatnonzero(labels == label) for cell in [dense[i]] for r in by_cell[cell]]
        tokens = {r["daily_token"] for _, r in members}
        days = {r["day"] for _, r in members}
        if len(tokens) < min_vehicles or len(days) < min_days:
            continue
        center = (
            sum(p[0] for p, _ in members) / len(members),
            sum(p[1] for p, _ in members) / len(members),
        )
        if any(contains(z["polygon"], center) or haversine_m(center, tuple(z["center"])) < COVERED_RADIUS_M for z in guidance_zones):
            continue
        cells = sorted({r["zone_id"] for _, r in members})
        profile = Counter((weekday_class(r["weekday"]), r["time_bucket"]) for _, r in members)
        found.append(Hotspot(
            center=(round(center[0], 6), round(center[1], 6)),
            polygon=padded_bbox([p for p, _ in members], pad_m=30),
            cells=cells,
            distinct_vehicles=len(tokens),
            distinct_days=len(days),
            events=len(members),
            peak=peak_label(profile),
            profile=[{"weekdayClass": c, "timeBucket": b, "count": n} for (c, b), n in sorted(profile.items())],
        ))
    return sorted(found, key=lambda h: -h.events)


def discover(conn: sqlite3.Connection, guidance_zones: list[Zone], min_vehicles: int = MIN_VEHICLES, min_days: int = MIN_DAYS) -> list[Hotspot]:
    rows = conn.execute(
        "SELECT zone_id, daily_token, day, weekday, time_bucket FROM events WHERE zone_type = 'hotspot_candidate'"
    ).fetchall()
    found = find_hotspots(rows, guidance_zones, min_vehicles, min_days)
    previous = conn.execute("SELECT id, seq, lat, lon FROM hotspots").fetchall()
    next_seq = max((p["seq"] for p in previous), default=0) + 1
    claimed: set[str] = set()

    conn.execute("BEGIN")
    conn.execute("UPDATE hotspots SET active = 0")
    for h in found:
        match = min(
            (p for p in previous if p["id"] not in claimed and haversine_m(h.center, (p["lat"], p["lon"])) <= EPS_M),
            key=lambda p: haversine_m(h.center, (p["lat"], p["lon"])),
            default=None,
        )
        if match is not None:
            hid, seq = match["id"], match["seq"]
        else:
            hid, seq = f"hotspot-{next_seq}", next_seq
            next_seq += 1
        claimed.add(hid)
        conn.execute(
            "INSERT OR REPLACE INTO hotspots (id, seq, lat, lon, polygon, cells, distinct_vehicles, distinct_days, events, peak, profile, active) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
            (hid, seq, h.center[0], h.center[1], json.dumps(h.polygon), json.dumps(h.cells),
             h.distinct_vehicles, h.distinct_days, h.events, h.peak, json.dumps(h.profile)),
        )
    conn.execute("COMMIT")
    return found
