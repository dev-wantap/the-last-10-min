import json
import sqlite3
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

from .db import connect
from .predict import kinds_for, predict
from .timeutil import BUCKETS_PER_DAY, SEOUL
from .zones import Zone, active_hotspots, hotspot_zone, load_seed_zones

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
REFERENCE_DAYS = {
    "weekday": datetime(2026, 11, 11, tzinfo=SEOUL),
    "weekend": datetime(2026, 11, 14, tzinfo=SEOUL),
}


def build_snapshot(conn: sqlite3.Connection, seed: list[Zone]) -> dict[str, Any]:
    hotspot_rows = active_hotspots(conn)
    cells = {r["id"]: json.loads(r["cells"]) for r in hotspot_rows}
    hotspots = [hotspot_zone(r) for r in hotspot_rows]
    rows: list[list[Any]] = []
    for zone in seed + hotspots:
        for kind in kinds_for(zone):
            for day_class, day in REFERENCE_DAYS.items():
                for bucket in range(BUCKETS_PER_DAY):
                    moment = day + timedelta(minutes=15 * bucket + 7)
                    p = predict(conn, zone, kind, moment, 1, time.time(), cells.get(zone["id"]))
                    if p.stage == "history":
                        rows.append([zone["id"], kind, day_class, bucket, p.minutes, p.samples])
    return {"generatedAt": datetime.now(SEOUL).isoformat(timespec="seconds"), "zones": hotspots, "predictions": rows}


def main() -> None:
    out = Path(sys.argv[1])
    conn = connect(DATA_DIR / "last10.db")
    snapshot = build_snapshot(conn, load_seed_zones(DATA_DIR / "zones.json"))
    out.write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{out}: {len(snapshot['zones'])} hotspots, {len(snapshot['predictions'])} prediction rows")


if __name__ == "__main__":
    main()
