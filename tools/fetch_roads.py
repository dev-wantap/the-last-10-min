import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

USER_AGENT = "last10min-contest-demo/0.1 (one-time road extract for an offline demo map)"
OVERPASS_MIRRORS = (
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
)
AREAS = {
    "gangnam": (37.4915, 127.0190, 37.5075, 127.0350),
    "daechi": (37.4925, 127.0510, 37.5060, 127.0700),
}
RANKS = {
    "motorway": 0, "motorway_link": 0, "trunk": 0, "trunk_link": 0,
    "primary": 1, "primary_link": 1,
    "secondary": 2, "secondary_link": 2,
    "tertiary": 3, "tertiary_link": 3,
    "unclassified": 4, "residential": 4, "living_street": 4,
    "service": 5,
}


def query(south: float, west: float, north: float, east: float) -> list[dict[str, Any]]:
    pattern = "|".join(RANKS)
    q = f'[out:json][timeout:90];way["highway"~"^({pattern})$"]({south},{west},{north},{east});out geom;'
    body = urllib.parse.urlencode({"data": q}).encode()
    errors: list[str] = []
    for attempt in range(6):
        mirror = OVERPASS_MIRRORS[attempt % len(OVERPASS_MIRRORS)]
        request = urllib.request.Request(mirror, data=body, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                return json.load(response)["elements"]
        except (urllib.error.URLError, TimeoutError) as e:
            errors.append(f"{mirror}: {e}")
            time.sleep(5 * (attempt + 1))
    raise RuntimeError("all Overpass attempts failed:\n" + "\n".join(errors))


def main() -> None:
    out = Path(sys.argv[1])
    roads: dict[int, dict[str, Any]] = {}
    for bbox in AREAS.values():
        for way in query(*bbox):
            points = [v for g in way["geometry"] for v in (round(g["lat"], 5), round(g["lon"], 5))]
            roads[way["id"]] = {"r": RANKS[way["tags"]["highway"]], "n": way["tags"].get("name", ""), "p": points}
    payload = {"attribution": "© OpenStreetMap contributors (ODbL)", "roads": list(roads.values())}
    out.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{out}: {len(roads)} roads, {out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
