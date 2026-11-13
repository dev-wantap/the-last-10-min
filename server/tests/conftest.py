from datetime import date, datetime

import pytest
from fastapi.testclient import TestClient

from last10.db import connect, insert_events
from last10.main import DATA_DIR, create_app
from last10.seed import generate
from last10.timeutil import SEOUL

THURSDAY_2120 = datetime(2026, 11, 12, 21, 20, tzinfo=SEOUL)
FIXED_NOW = THURSDAY_2120.timestamp()


@pytest.fixture
def seeded_db(tmp_path):
    path = tmp_path / "test.db"
    insert_events(connect(path), generate(date(2026, 11, 2), days=7, seed=7))
    return path


@pytest.fixture
def client(seeded_db):
    app = create_app(seeded_db, DATA_DIR / "zones.json", clock=lambda: FIXED_NOW, discover_every_sec=0)
    with TestClient(app) as c:
        yield c
