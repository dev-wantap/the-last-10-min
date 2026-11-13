import asyncio
import json
import os
import threading
import time
from collections import Counter
from collections.abc import Callable
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path
from typing import Any

from fastapi import Body, FastAPI, Query
from fastapi.responses import HTMLResponse

from .dashboard import DASHBOARD_HTML
from .db import connect, insert_events
from .demo import demo_router
from .discovery import discover
from .ingest import check
from .predict import as_json, kinds_for, predict
from .timeutil import SEOUL, shift
from .zones import active_hotspots, all_zones, load_seed_zones

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
MAX_BATCH = 500


def create_app(
    db_path: str | Path,
    zones_path: str | Path = DATA_DIR / "zones.json",
    clock: Callable[[], float] = time.time,
    discover_every_sec: float = 60.0,
    demo: bool = False,
) -> FastAPI:
    conn = connect(db_path)
    seed_zones = load_seed_zones(zones_path)
    guidance_zones = [z for z in seed_zones if z["guidance"]]
    lock = threading.Lock()

    def run_discovery() -> int:
        with lock:
            return len(discover(conn, guidance_zones))

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        run_discovery()
        task = None
        if discover_every_sec > 0:
            async def loop() -> None:
                while True:
                    await asyncio.sleep(discover_every_sec)
                    await asyncio.to_thread(run_discovery)
            task = asyncio.create_task(loop())
        yield
        if task:
            task.cancel()

    app = FastAPI(
        title="마지막 10분 서버",
        description="차량이 올린 익명 이벤트를 구역 × 요일 × 시간대로 집계해 소요 시간을 예측하고, 승하차 혼잡 지점을 찾아냅니다.",
        lifespan=lifespan,
    )

    if demo:
        app.include_router(demo_router(clock))

    @app.get("/health")
    def health() -> dict[str, bool]:
        return {"ok": True}

    @app.post("/v1/events")
    def ingest(body: dict[str, Any] = Body(...)) -> dict[str, Any]:
        raw = body.get("events")
        if not isinstance(raw, list):
            return {"accepted": 0, "rejected": [{"index": -1, "reason": "body must be {\"events\": [...]}"}]}
        with lock:
            zones_by_id = {z["id"]: z for z in all_zones(seed_zones, conn)}
            accepted, rejected = [], []
            for i, item in enumerate(raw[:MAX_BATCH]):
                event, reason = check(item, zones_by_id)
                if event is None:
                    rejected.append({"index": i, "reason": reason})
                    continue
                accepted.append((event.zoneId, event.zoneType, event.event, event.durationSec, event.weekday,
                                 event.timeBucket, event.day, event.dailyToken, clock()))
            rejected += [{"index": i, "reason": f"batch limit {MAX_BATCH}"} for i in range(MAX_BATCH, len(raw))]
            if accepted:
                insert_events(conn, accepted)
        return {"accepted": len(accepted), "rejected": rejected}

    @app.get("/v1/zones")
    def zones() -> dict[str, Any]:
        with lock:
            return {"zones": all_zones(seed_zones, conn)}

    @app.get("/v1/predictions")
    def predictions(
        zoneIds: str = Query(..., description="쉼표로 구분한 구역 ID"),
        offsets: str = Query("0", description="지금부터 몇 분 뒤 (쉼표 구분)"),
        now: str | None = Query(None, description="ISO 시각, 테스트·시연용"),
    ) -> dict[str, Any]:
        moment = datetime.fromisoformat(now) if now else datetime.fromtimestamp(clock(), SEOUL)
        offset_list = [int(o) for o in offsets.split(",") if o.strip().lstrip("-").isdigit()][:13]
        with lock:
            by_id = {z["id"]: z for z in all_zones(seed_zones, conn)}
            cells = {r["id"]: json.loads(r["cells"]) for r in active_hotspots(conn)}
            out = []
            for zid in [z for z in zoneIds.split(",") if z in by_id][:30]:
                zone = by_id[zid]
                for offset in offset_list:
                    for kind in kinds_for(zone):
                        p = predict(conn, zone, kind, shift(moment, offset), offset, clock(), cells.get(zid))
                        out.append(as_json(p))
        return {"predictions": out}

    @app.get("/v1/hotspots")
    def hotspots() -> dict[str, Any]:
        with lock:
            rows = active_hotspots(conn)
        return {"hotspots": [
            {"id": r["id"], "center": [r["lat"], r["lon"]], "polygon": json.loads(r["polygon"]),
             "distinctVehicles": r["distinct_vehicles"], "distinctDays": r["distinct_days"], "events": r["events"],
             "peak": r["peak"], "profile": json.loads(r["profile"])}
            for r in rows
        ]}

    @app.post("/v1/admin/discover")
    def discover_now() -> dict[str, int]:
        return {"hotspots": run_discovery()}

    @app.get("/v1/stats")
    def stats() -> dict[str, Any]:
        now_ts = clock()
        with lock:
            total = conn.execute("SELECT COUNT(*) FROM events").fetchone()[0]
            recent = conn.execute(
                "SELECT id, zone_id, zone_type, event, duration_sec, weekday, time_bucket, substr(daily_token, 1, 6) AS token, received_at "
                "FROM events WHERE received_at >= ? ORDER BY received_at DESC, id DESC LIMIT 20",
                (now_ts - 3600,),
            ).fetchall()
            by_type = Counter(r[0] for r in conn.execute("SELECT zone_type FROM events"))
        return {"total": total, "byType": by_type, "lastHour": [dict(r) for r in recent]}

    @app.get("/v1/profile")
    def profile(zoneId: str = Query(...), kind: str = Query("park")) -> dict[str, Any]:
        now = datetime.fromtimestamp(clock(), SEOUL)
        with lock:
            zone = {z["id"]: z for z in all_zones(seed_zones, conn)}.get(zoneId)
            if zone is None or kind not in kinds_for(zone):
                return {"zoneId": zoneId, "kind": kind, "hours": [], "currentHour": now.hour}
            cells = {r["id"]: json.loads(r["cells"]) for r in active_hotspots(conn)}.get(zoneId)
            hours = []
            for hour in range(24):
                p = predict(conn, zone, kind, now.replace(hour=hour), 0 if hour == now.hour else 60, clock(), cells)
                hours.append({"hour": hour, "minutes": p.minutes, "samples": p.samples, "stage": p.stage})
        return {"zoneId": zoneId, "kind": kind, "hours": hours, "currentHour": now.hour}

    @app.get("/", response_class=HTMLResponse)
    def dashboard() -> str:
        return DASHBOARD_HTML

    return app


app = create_app(os.environ.get("DB_PATH", str(DATA_DIR / "last10.db")), demo=os.environ.get("L10_DEMO") == "1")
