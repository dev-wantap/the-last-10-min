import sqlite3
from dataclasses import asdict, dataclass
from datetime import datetime
from statistics import median

from .timeutil import BUCKETS_PER_DAY, slot, weekday_class
from .zones import Zone

K_PARKING = 5
K_PICKUP = 10
CONFIDENT_SAMPLES = 20
REALTIME_WINDOW_SEC = 30 * 60
REALTIME_MIN_EVENTS = 3

KIND_EVENTS = {
    "park": ("park_complete",),
    "exit": ("exit_complete",),
    "pickup": ("pickup_complete", "boarding_unknown"),
}


@dataclass
class Prediction:
    zoneId: str
    offsetMin: int
    weekday: int
    timeBucket: int
    kind: str
    minutes: float
    samples: int
    stage: str
    confidence: str
    suppressed: bool


def kinds_for(zone: Zone) -> tuple[str, ...]:
    return ("park", "exit") if zone["type"] == "parking" else ("pickup",)


def prior_minutes(zone: Zone, kind: str) -> float:
    """Stage 1 prior (PLAN 7). Kept identical to the app's offline Prior so both sides agree."""
    capacity, available = zone.get("capacity"), zone.get("available")
    occupancy = 0.5 if not capacity or available is None else min(max(1 - available / capacity, 0.0), 1.0)
    if kind == "park":
        return min(max(1 + 14 * occupancy**3, 1.0), 20.0)
    if kind == "exit":
        return min(max(3 + 7 * occupancy, 1.0), 20.0)
    return 4.0


def predict(
    conn: sqlite3.Connection,
    zone: Zone,
    kind: str,
    moment: datetime,
    offset_min: int,
    now_ts: float,
    extra_zone_ids: list[str] | None = None,
) -> Prediction:
    weekday, bucket, _ = slot(moment)
    weekdays = (6, 7) if weekday_class(weekday) == "weekend" else (1, 2, 3, 4, 5)
    buckets = tuple(sorted({(bucket + d) % BUCKETS_PER_DAY for d in (-1, 0, 1)}))
    zone_ids = (zone["id"], *(extra_zone_ids or []))
    events = KIND_EVENTS[kind]
    k = K_PARKING if zone["type"] == "parking" else K_PICKUP

    q = lambda xs: ",".join("?" * len(xs))  # noqa: E731
    rows = conn.execute(
        f"SELECT duration_sec, daily_token FROM events WHERE zone_id IN ({q(zone_ids)}) AND event IN ({q(events)}) "
        f"AND time_bucket IN ({q(buckets)}) AND weekday IN ({q(weekdays)})",
        (*zone_ids, *events, *buckets, *weekdays),
    ).fetchall()

    minutes, samples, stage, suppressed = prior_minutes(zone, kind), 0, "prior", False
    if rows:
        if len({r["daily_token"] for r in rows}) >= k:
            minutes, samples, stage = median(r["duration_sec"] for r in rows) / 60, len(rows), "history"
        else:
            suppressed = True

    if offset_min == 0:
        recent = conn.execute(
            f"SELECT duration_sec, daily_token FROM events WHERE zone_id IN ({q(zone_ids)}) AND event IN ({q(events)}) AND received_at >= ?",
            (*zone_ids, *events, now_ts - REALTIME_WINDOW_SEC),
        ).fetchall()
        if len(recent) >= REALTIME_MIN_EVENTS and len({r["daily_token"] for r in recent}) >= REALTIME_MIN_EVENTS:
            minutes = 0.5 * minutes + 0.5 * median(r["duration_sec"] for r in recent) / 60
            stage, samples = "realtime", samples + len(recent)

    confidence = "ok" if stage != "prior" and samples >= CONFIDENT_SAMPLES else "low"
    return Prediction(zone["id"], offset_min, weekday, bucket, kind, round(minutes, 1), samples, stage, confidence, suppressed)


def as_json(p: Prediction) -> dict[str, object]:
    return asdict(p)
