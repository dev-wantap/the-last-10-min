import json
import sqlite3
from pathlib import Path
from typing import Any

Zone = dict[str, Any]

PICKUP_TYPES = ("pickup_official", "pickup_hotspot")


def load_seed_zones(path: str | Path) -> list[Zone]:
    return json.loads(Path(path).read_text(encoding="utf-8"))["zones"]


def hotspot_name(seq: int) -> str:
    letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    return f"승하차 혼잡 지점 {letters[(seq - 1) % 26]}"


def active_hotspots(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    return conn.execute("SELECT * FROM hotspots WHERE active = 1 ORDER BY seq").fetchall()


def hotspot_zone(row: sqlite3.Row) -> Zone:
    return {
        "id": row["id"],
        "type": "pickup_hotspot",
        "name": hotspot_name(row["seq"]),
        "center": [row["lat"], row["lon"]],
        "polygon": json.loads(row["polygon"]),
        "guidance": False,
    }


def all_zones(seed: list[Zone], conn: sqlite3.Connection) -> list[Zone]:
    return seed + [hotspot_zone(r) for r in active_hotspots(conn)]
