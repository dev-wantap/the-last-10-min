#!/usr/bin/env python3
"""
Drives the Pleos Connect emulator through demo scenarios for the "마지막 10분" app.

Vehicle signals go through the Virtual Console HTTP API that runs inside the emulator (port 8088,
reached with `adb forward`); location goes through `adb emu geo fix`. The Virtual Console only accepts
writes while a browser session heartbeat is alive, so a background thread keeps one open.

    python3 demo/scenario.py parking      # 주차장 진입 → 자리 찾기 → 주차 → 출차
    python3 demo/scenario.py pickup       # 공식 승하차구역에서 아이 태우기
    python3 demo/scenario.py hotspot      # 승하차구역 밖 도로변 하차 (혼잡 지점 후보)
    python3 demo/scenario.py all
"""
import argparse
import json
import os
import subprocess
import sys
import threading
import time
import urllib.request

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
CONSOLE_PORT = 18088
SERVER = os.environ.get("SERVER", "http://127.0.0.1:8080")

LOT_A_SOUTH_ROAD = (37.49700, 127.02760)
LOT_A_GATE = (37.49760, 127.02760)
LOT_A_AISLE = [(37.49775, 127.02740), (37.49790, 127.02740), (37.49800, 127.02760), (37.49800, 127.02790), (37.49785, 127.02790)]
LOT_A_SPOT = (37.49780, 127.02775)
DAECHI_APPROACH = (37.49870, 127.05800)
DAECHI_PICKUP = (37.49870, 127.05900)
ROADSIDE = (37.49950, 127.06250)
ROADSIDE_LEAVE = (37.49950, 127.06400)


def log(msg: str) -> None:
    print(f"{time.strftime('%H:%M:%S')}  {msg}", flush=True)


def adb(*args: str) -> str:
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True).stdout.strip()


def put(path: str) -> None:
    req = urllib.request.Request(f"http://127.0.0.1:{CONSOLE_PORT}{path}", method="PUT")
    with urllib.request.urlopen(req, timeout=5) as res:
        if res.status != 200:
            raise RuntimeError(f"{path} -> {res.status}")


def heartbeat(stop: threading.Event) -> None:
    while not stop.is_set():
        try:
            urllib.request.urlopen(urllib.request.Request(
                f"http://127.0.0.1:{CONSOLE_PORT}/browser/session?client=last10-demo", method="POST"), timeout=3)
        except OSError as err:
            log(f"heartbeat failed: {err}")
        stop.wait(2)


def at(p: tuple[float, float]) -> None:
    adb("emu", "geo", "fix", f"{p[1]:.6f}", f"{p[0]:.6f}")


def gear(g: str) -> None:
    put(f"/vehicle/gear?gear={g}")


def speed(kph: float) -> None:
    put(f"/vehicle/controls?speed_kph={kph:g}")


def door(area: str, open_: bool) -> None:
    put(f"/vehicle/body?door_{area}={'true' if open_ else 'false'}")


def seat(area: str, occupied: bool) -> None:
    put(f"/vehicle/seat?area={area}&kind=occupied&value={'true' if occupied else 'false'}")


def drive_to(points: list[tuple[float, float]], kph: float, step_sec: float = 1.0) -> None:
    speed(kph)
    for p in points:
        at(p)
        time.sleep(step_sec)


def start_driving(start: tuple[float, float]) -> None:
    log("출발 준비: 운전석 착석, 모든 문 닫힘, D, 30km/h")
    for area in ("fl", "fr", "rl", "rr"):
        door(area, False)
    seat("driver", True)
    for area in ("passenger", "rear_left", "rear_right"):
        seat(area, False)
    gear("Drive")
    speed(30)
    at(start)
    time.sleep(4)


def parking() -> None:
    start_driving(LOT_A_SOUTH_ROAD)
    log("강남역 A주차장 입구로 접근")
    drive_to([LOT_A_SOUTH_ROAD, LOT_A_GATE], 15)
    log("주차장 경계 진입 → 저속으로 자리 찾기 (약 40초)")
    for _ in range(3):
        drive_to(LOT_A_AISLE, 6, step_sec=2.5)
    log("자리 발견: 정차 후 P")
    at(LOT_A_SPOT)
    speed(0)
    time.sleep(2)
    gear("Park")
    time.sleep(2)
    log("운전석 문 열림 → 운전석 비움 → 문 닫힘 (주차 완료 판정)")
    door("fl", True)
    time.sleep(1.5)
    seat("driver", False)
    time.sleep(1.5)
    door("fl", False)
    time.sleep(6)

    log("볼일 보고 복귀: 운전석 착석")
    door("fl", True)
    time.sleep(1)
    seat("driver", True)
    time.sleep(1)
    door("fl", False)
    time.sleep(2)
    log("P → D, 출구로 이동 (출차 판정)")
    gear("Drive")
    drive_to([LOT_A_SPOT, LOT_A_AISLE[0], LOT_A_GATE], 6, step_sec=3)
    drive_to([LOT_A_SOUTH_ROAD], 20, step_sec=4)


def pickup() -> None:
    start_driving(DAECHI_APPROACH)
    log("대치 학원가 어린이 승하차구역 진입 후 정차")
    drive_to([DAECHI_APPROACH, DAECHI_PICKUP], 10, step_sec=2)
    speed(0)
    time.sleep(8)
    log("뒷좌석 왼쪽 문 열림 → 아이 착석 → 문 닫힘 (픽업 완료 판정)")
    door("rl", True)
    time.sleep(2)
    seat("rear_left", True)
    time.sleep(2)
    door("rl", False)
    time.sleep(3)
    log("출발")
    drive_to([(DAECHI_PICKUP[0], DAECHI_PICKUP[1] + 0.001)], 20, step_sec=3)


def hotspot() -> None:
    start_driving((ROADSIDE[0], ROADSIDE[1] - 0.0015))
    log("승하차구역이 없는 도로변에 정차 (학원가)")
    drive_to([ROADSIDE], 10, step_sec=2)
    speed(0)
    time.sleep(4)
    seat("rear_left", True)
    time.sleep(1)
    log("뒷좌석 왼쪽 문 열림 → 아이 하차 → 문 닫힘 (혼잡 지점 후보 이벤트)")
    door("rl", True)
    time.sleep(2)
    seat("rear_left", False)
    time.sleep(2)
    door("rl", False)
    time.sleep(3)
    drive_to([ROADSIDE_LEAVE], 25, step_sec=3)


def server_events_since(since: float) -> list[dict[str, object]]:
    try:
        with urllib.request.urlopen(f"{SERVER}/v1/stats", timeout=5) as res:
            stats = json.load(res)
    except OSError as err:
        log(f"서버 확인 실패 ({SERVER}): {err}")
        return []
    return [e for e in stats["lastHour"] if e["received_at"] >= since]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("scenario", choices=["parking", "pickup", "hotspot", "all"])
    args = parser.parse_args()

    adb("forward", f"tcp:{CONSOLE_PORT}", "tcp:8088")
    stop = threading.Event()
    threading.Thread(target=heartbeat, args=(stop,), daemon=True).start()
    time.sleep(1.5)
    started = time.time()
    try:
        for name in (["parking", "pickup", "hotspot"] if args.scenario == "all" else [args.scenario]):
            log(f"=== 시나리오: {name} ===")
            {"parking": parking, "pickup": pickup, "hotspot": hotspot}[name]()
        time.sleep(5)
    finally:
        stop.set()

    received = server_events_since(started - 5)
    log(f"서버가 받은 이벤트 {len(received)}건")
    for e in reversed(received):
        log(f"  {e['zone_id']}  {e['event']}  {e['duration_sec']}초")
    return 0 if received else 1


if __name__ == "__main__":
    sys.exit(main())
