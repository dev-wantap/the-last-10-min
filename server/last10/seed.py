import hashlib
import math
import os
import random
import sys
from datetime import date, datetime, timedelta

from .db import connect, insert_events
from .discovery import discover
from .timeutil import SEOUL
from .zones import load_seed_zones

TRUE_HOTSPOTS = {
    "academy": (37.49950, 127.06250),
    "school": (37.50050, 127.06100),
    "station": (37.49700, 127.02800),
}
AREA = ((37.4930, 37.5030), (127.0240, 127.0660))
CELL_DEG = 0.0005
VEHICLES_PER_DAY = 400

LOTS = {
    "lot-gangnam-a": {"rate": 6.0, "park": lambda h: 10.0 if h in (11, 12, 18, 19, 20) else 4.5, "exit": 5.0},
    "lot-gangnam-b": {"rate": 4.0, "park": lambda h: 2.5, "exit": 2.5},
    "lot-gangnam-c": {"rate": 5.0, "park": lambda h: 13.0 if 10 <= h <= 20 else 6.0, "exit": 8.0},
    "lot-daechi-public": {"rate": 3.0, "park": lambda h: 5.0 if h in (20, 21) else 3.0, "exit": 3.0},
}


class Generator:
    def __init__(self, seed: int) -> None:
        self.rng = random.Random(seed)
        self.rows: list[tuple[object, ...]] = []

    def poisson(self, lam: float) -> int:
        threshold, k, p = math.exp(-lam), 0, 1.0
        while True:
            p *= self.rng.random()
            if p <= threshold:
                return k
            k += 1

    def token(self, day: date) -> str:
        vehicle = self.rng.randrange(VEHICLES_PER_DAY)
        return hashlib.sha256(f"{vehicle}:{day.isoformat()}".encode()).hexdigest()[:32]

    def minutes_around(self, mean: float) -> int:
        return max(30, int(self.rng.gauss(mean * 60, mean * 60 * 0.3)))

    def add(self, zone_id: str, zone_type: str, event: str, duration: int, day: date, minute_of_day: int) -> None:
        moment = datetime(day.year, day.month, day.day, tzinfo=SEOUL) + timedelta(minutes=minute_of_day)
        self.rows.append((zone_id, zone_type, event, duration, moment.isoweekday(), minute_of_day // 15,
                          day.isoformat(), self.token(day), moment.timestamp()))

    def cell(self, lat: float, lon: float, jitter_m: float) -> str:
        lat += self.rng.gauss(0, jitter_m) / 111_320
        lon += self.rng.gauss(0, jitter_m) / (111_320 * math.cos(math.radians(lat)))
        return f"cell:{round(lat / CELL_DEG) * CELL_DEG:.5f},{round(lon / CELL_DEG) * CELL_DEG:.5f}"

    def day(self, d: date) -> None:
        weekday = d.isoweekday() <= 5
        for lot, spec in LOTS.items():
            for hour in range(7, 23):
                for _ in range(self.poisson(spec["rate"])):
                    m = hour * 60 + self.rng.randrange(60)
                    self.add(lot, "parking", "park_complete", self.minutes_around(spec["park"](hour)), d, m)
                    self.add(lot, "parking", "exit_complete", self.minutes_around(spec["exit"]), d, min(m + 90, 1439))

        for _ in range(self.poisson(12 if weekday else 3)):
            self.add("pickup-daechi-official", "pickup_official", "pickup_complete", self.minutes_around(6.0), d, 21 * 60 + self.rng.randrange(60))
        for hour in range(8, 23):
            for _ in range(self.poisson(1.0)):
                self.add("pickup-gangnam-official", "pickup_official", "pickup_complete", self.minutes_around(3.0), d, hour * 60 + self.rng.randrange(60))

        def candidates(where: str, count: int, start: int, span: int) -> None:
            for _ in range(count):
                kind = self.rng.choice(("pickup_complete", "dropoff_complete"))
                self.add(self.cell(*TRUE_HOTSPOTS[where], jitter_m=12), "hotspot_candidate", kind,
                         self.rng.randrange(60, 300), d, start + self.rng.randrange(span))

        if weekday:
            candidates("academy", self.poisson(14), 21 * 60, 60)
            candidates("school", self.poisson(12), 7 * 60 + 30, 70)
        candidates("station", self.poisson(10), 8 * 60, 14 * 60)
        for _ in range(self.poisson(8)):
            lat = self.rng.uniform(*AREA[0])
            lon = self.rng.uniform(*AREA[1])
            self.add(self.cell(lat, lon, 0), "hotspot_candidate", "dropoff_complete", self.rng.randrange(60, 300), d, self.rng.randrange(6 * 60, 23 * 60))


def generate(first_day: date, days: int, seed: int = 42) -> list[tuple[object, ...]]:
    g = Generator(seed)
    for i in range(days):
        g.day(first_day + timedelta(days=i))
    return g.rows


def main() -> None:
    db_path = os.environ.get("DB_PATH", os.path.join(os.path.dirname(__file__), "..", "data", "last10.db"))
    zones_path = os.path.join(os.path.dirname(__file__), "..", "data", "zones.json")
    days = int(sys.argv[1]) if len(sys.argv) > 1 else 28
    today = datetime.now(SEOUL).date()
    conn = connect(db_path)
    conn.execute("DELETE FROM events")
    conn.execute("DELETE FROM hotspots")
    rows = generate(today - timedelta(days=days), days)
    insert_events(conn, rows)
    found = discover(conn, [z for z in load_seed_zones(zones_path) if z["guidance"]])
    print(f"seeded {len(rows)} events over {days} days into {db_path}; discovered {len(found)} hotspots")
    for h in found:
        print(f"  {h.center} vehicles={h.distinct_vehicles} days={h.distinct_days} events={h.events} peak={h.peak}")


if __name__ == "__main__":
    main()
