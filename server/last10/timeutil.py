from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

SEOUL = ZoneInfo("Asia/Seoul")
BUCKETS_PER_DAY = 96


def slot(moment: datetime) -> tuple[int, int, str]:
    """(ISO weekday 1-7, 15-minute bucket 0-95, local date) in Asia/Seoul."""
    local = moment.astimezone(SEOUL)
    return local.isoweekday(), (local.hour * 60 + local.minute) // 15, local.date().isoformat()


def weekday_class(weekday: int) -> str:
    return "weekend" if weekday >= 6 else "weekday"


def bucket_label(bucket: int) -> str:
    minutes = (bucket % BUCKETS_PER_DAY) * 15
    return f"{minutes // 60:02d}:{minutes % 60:02d}"


def shift(moment: datetime, minutes: int) -> datetime:
    return moment + timedelta(minutes=minutes)
