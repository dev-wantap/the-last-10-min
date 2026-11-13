from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from .geo import parse_cell
from .zones import Zone

PARKING_EVENTS = {"park_enter", "park_complete", "park_fail", "exit_complete"}
PICKUP_EVENTS = {"pickup_complete", "dropoff_complete", "boarding_unknown"}


class EventIn(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    zoneId: str = Field(min_length=1, max_length=64)
    zoneType: Literal["parking", "pickup_official", "pickup_hotspot", "hotspot_candidate"]
    event: Literal["park_enter", "park_complete", "park_fail", "exit_complete", "pickup_complete", "dropoff_complete", "boarding_unknown"]
    durationSec: int = Field(ge=0, le=6 * 3600)
    weekday: int = Field(ge=1, le=7)
    timeBucket: int = Field(ge=0, le=95)
    day: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    dailyToken: str = Field(pattern=r"^[0-9a-f]{16,64}$")


def check(raw: Any, zones_by_id: dict[str, Zone]) -> tuple[EventIn | None, str | None]:
    try:
        e = EventIn.model_validate(raw)
    except ValidationError as err:
        first = err.errors()[0]
        return None, f"{'.'.join(str(p) for p in first['loc']) or 'event'}: {first['msg']}"

    if e.zoneType == "hotspot_candidate":
        if parse_cell(e.zoneId) is None:
            return None, "zoneId: hotspot candidates need a 'cell:<lat>,<lon>' id"
    else:
        zone = zones_by_id.get(e.zoneId)
        if zone is None:
            return None, f"zoneId: unknown zone {e.zoneId}"
        if zone["type"] != e.zoneType:
            return None, f"zoneType: {e.zoneId} is {zone['type']}"

    allowed = PARKING_EVENTS if e.zoneType == "parking" else PICKUP_EVENTS
    if e.event not in allowed:
        return None, f"event: {e.event} is not valid for {e.zoneType}"
    return e, None
