import subprocess
import sys
import threading
from collections import deque
from collections.abc import Callable
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Body, HTTPException
from fastapi.responses import FileResponse

REPO = Path(__file__).resolve().parents[2]
SCENARIOS = ("parking", "pickup", "hotspot")


class DemoState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.telemetry: dict[str, Any] | None = None
        self.telemetry_at = 0.0
        self.process: subprocess.Popen[str] | None = None
        self.scenario = ""
        self.log: deque[str] = deque(maxlen=14)

    def running(self) -> bool:
        return self.process is not None and self.process.poll() is None


def demo_router(clock: Callable[[], float]) -> APIRouter:
    router = APIRouter(prefix="/demo", tags=["demo"])
    state = DemoState()

    def pump(process: subprocess.Popen[str]) -> None:
        assert process.stdout is not None
        for line in process.stdout:
            with state.lock:
                state.log.append(line.rstrip())

    @router.post("/telemetry")
    def put_telemetry(body: dict[str, Any] = Body(...)) -> dict[str, bool]:
        with state.lock:
            state.telemetry = body
            state.telemetry_at = clock()
        return {"ok": True}

    @router.get("/telemetry")
    def get_telemetry() -> dict[str, Any]:
        with state.lock:
            age = clock() - state.telemetry_at if state.telemetry else None
            return {"telemetry": state.telemetry, "ageSec": age}

    @router.post("/scenario/{name}")
    def start_scenario(name: str) -> dict[str, Any]:
        if name not in SCENARIOS:
            raise HTTPException(404, f"unknown scenario: {name}")
        with state.lock:
            if state.running():
                raise HTTPException(409, f"scenario already running: {state.scenario}")
            state.log.clear()
            state.scenario = name
            state.process = subprocess.Popen(
                [sys.executable, "-u", str(REPO / "demo" / "scenario.py"), name],
                cwd=REPO, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
            )
            threading.Thread(target=pump, args=(state.process,), daemon=True).start()
        return {"started": name}

    @router.get("/scenario")
    def scenario_status() -> dict[str, Any]:
        with state.lock:
            code = state.process.poll() if state.process else None
            return {"running": state.running(), "name": state.scenario, "exitCode": code, "log": list(state.log)}

    @router.get("/roads")
    def roads() -> FileResponse:
        return FileResponse(REPO / "app" / "src" / "main" / "assets" / "roads.json", media_type="application/json")

    return router
