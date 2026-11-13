package app.last10min.engine

fun rectZone(id: String, type: ZoneType, center: LatLng, dLat: Double = 0.0003, dLon: Double = 0.0004) = Zone(
    id = id,
    type = type,
    name = id,
    center = center,
    polygon = listOf(
        LatLng(center.lat - dLat, center.lon - dLon),
        LatLng(center.lat - dLat, center.lon + dLon),
        LatLng(center.lat + dLat, center.lon + dLon),
        LatLng(center.lat + dLat, center.lon - dLon),
    ),
    guidance = type != ZoneType.PICKUP_HOTSPOT,
)

val LOT_A = rectZone("lot-a", ZoneType.PARKING, LatLng(37.49790, 127.02760))
val PICKUP_OFFICIAL = rectZone("pickup-official", ZoneType.PICKUP_OFFICIAL, LatLng(37.49870, 127.05900), 0.0001, 0.0003)

val INSIDE_A = LatLng(37.49790, 127.02760)
val OUTSIDE_A = LatLng(37.49900, 127.02760)
val INSIDE_PICKUP = LatLng(37.49870, 127.05900)
val ROADSIDE = LatLng(37.49961, 127.06262)

fun allClosed() = SeatPos.entries.associateWith { false as Boolean? }

fun seats(vararg occupied: SeatPos): Map<SeatPos, Boolean?> = SeatPos.entries.associateWith { it in occupied }
