import sqlite3
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY,
    zone_id TEXT NOT NULL,
    zone_type TEXT NOT NULL,
    event TEXT NOT NULL,
    duration_sec INTEGER NOT NULL,
    weekday INTEGER NOT NULL,
    time_bucket INTEGER NOT NULL,
    day TEXT NOT NULL,
    daily_token TEXT NOT NULL,
    received_at REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS events_zone ON events (zone_id, event, time_bucket);
CREATE INDEX IF NOT EXISTS events_type ON events (zone_type);
CREATE INDEX IF NOT EXISTS events_received ON events (received_at);
CREATE TABLE IF NOT EXISTS hotspots (
    id TEXT PRIMARY KEY,
    seq INTEGER NOT NULL,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    polygon TEXT NOT NULL,
    cells TEXT NOT NULL,
    distinct_vehicles INTEGER NOT NULL,
    distinct_days INTEGER NOT NULL,
    events INTEGER NOT NULL,
    peak TEXT NOT NULL,
    profile TEXT NOT NULL,
    active INTEGER NOT NULL
);
"""

COLUMNS = ("zone_id", "zone_type", "event", "duration_sec", "weekday", "time_bucket", "day", "daily_token", "received_at")


def connect(path: str | Path) -> sqlite3.Connection:
    conn = sqlite3.connect(str(path), check_same_thread=False, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.executescript(SCHEMA)
    return conn


def insert_events(conn: sqlite3.Connection, rows: list[tuple[object, ...]]) -> None:
    conn.execute("BEGIN")
    conn.executemany(f"INSERT INTO events ({', '.join(COLUMNS)}) VALUES ({', '.join('?' * len(COLUMNS))})", rows)
    conn.execute("COMMIT")
