import math

EARTH_RADIUS_M = 6_371_000.0


def haversine_m(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(h))


def contains(polygon: list[list[float]], p: tuple[float, float]) -> bool:
    inside = False
    j = len(polygon) - 1
    for i in range(len(polygon)):
        a, b = polygon[i], polygon[j]
        if (a[0] > p[0]) != (b[0] > p[0]) and p[1] < (b[1] - a[1]) * (p[0] - a[0]) / (b[0] - a[0]) + a[1]:
            inside = not inside
        j = i
    return inside


def padded_bbox(points: list[tuple[float, float]], pad_m: float) -> list[list[float]]:
    lat_pad = pad_m / 111_320
    mid_lat = sum(p[0] for p in points) / len(points)
    lon_pad = pad_m / (111_320 * math.cos(math.radians(mid_lat)))
    lo_lat, hi_lat = min(p[0] for p in points) - lat_pad, max(p[0] for p in points) + lat_pad
    lo_lon, hi_lon = min(p[1] for p in points) - lon_pad, max(p[1] for p in points) + lon_pad
    r = lambda v: round(v, 6)  # noqa: E731
    return [[r(lo_lat), r(lo_lon)], [r(lo_lat), r(hi_lon)], [r(hi_lat), r(hi_lon)], [r(hi_lat), r(lo_lon)]]


def parse_cell(zone_id: str) -> tuple[float, float] | None:
    if not zone_id.startswith("cell:"):
        return None
    try:
        lat_s, lon_s = zone_id[5:].split(",")
        lat, lon = float(lat_s), float(lon_s)
    except ValueError:
        return None
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        return None
    return lat, lon
