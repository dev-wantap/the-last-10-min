package app.last10min.engine

data class PickupConfig(
    val stopMaxSpeedKph: Double = 3.0,
)

/**
 * PLAN 4.5: while stopped with the driver still seated, a non-driver door open->close cycle whose seat
 * went empty->occupied is a pickup, occupied->empty a drop-off. Inside an official pickup zone the event is
 * timed from zone entry; elsewhere, including inside an already discovered hotspot, it stays an anonymous
 * hotspot candidate at a snapped grid cell so the server's discovery keeps receiving fresh evidence.
 */
class PickupDetector(private val config: PickupConfig = PickupConfig()) {

    private data class Pending(val seatAtOpen: Boolean?)

    private var zoneEntry: Pair<Zone, Long>? = null
    private var stopStartMs: Long? = null
    private val pending = mutableMapOf<SeatPos, Pending>()
    private var lastDoor: Map<SeatPos, Boolean?> = emptyMap()
    private var lastSeat: Map<SeatPos, Boolean?> = emptyMap()

    val isStopped: Boolean get() = stopStartMs != null
    val currentZone: Zone? get() = zoneEntry?.first

    fun update(s: VehicleState, zones: List<Zone>): List<DetectedEvent> {
        val out = mutableListOf<DetectedEvent>()
        val loc = s.location
        trackZoneEntry(s, zones)

        val inParkingLot = loc != null && zones.any { it.type == ZoneType.PARKING && it.contains(loc) }
        val stopped = (s.speedKph ?: 0.0) <= config.stopMaxSpeedKph
        val driverStays = s.seatOccupied[SeatPos.DRIVER] != false

        if (!stopped || !driverStays || inParkingLot) {
            stopStartMs = null
            pending.clear()
        } else {
            if (stopStartMs == null) stopStartMs = s.timeMs
            for (pos in PASSENGER_SEATS) {
                val wasOpen = lastDoor[pos] == true
                val isOpen = s.doorOpen[pos] == true
                if (!wasOpen && isOpen) pending[pos] = Pending(lastSeat[pos] ?: s.seatOccupied[pos])
                if (wasOpen && !isOpen) {
                    val p = pending.remove(pos) ?: continue
                    classify(p.seatAtOpen, s.seatOccupied[pos], s.beltBuckled[pos])?.let { kind ->
                        attribute(kind, s, zones)?.let(out::add)
                    }
                }
            }
        }
        lastDoor = s.doorOpen
        lastSeat = s.seatOccupied
        return out
    }

    private fun classify(before: Boolean?, after: Boolean?, belt: Boolean?): EventKind? = when {
        before == false && after == true -> EventKind.PICKUP_COMPLETE
        before == true && after == false -> EventKind.DROPOFF_COMPLETE
        before != null && after != null -> null
        belt == true -> EventKind.PICKUP_COMPLETE
        else -> EventKind.BOARDING_UNKNOWN
    }

    private fun attribute(kind: EventKind, s: VehicleState, zones: List<Zone>): DetectedEvent? {
        val loc = s.location ?: return null
        val stopStart = stopStartMs ?: s.timeMs
        val zone = zones.firstOrNull { it.type == ZoneType.PICKUP_OFFICIAL && it.contains(loc) }
        if (zone != null) {
            val start = zoneEntry?.takeIf { it.first.id == zone.id }?.second ?: stopStart
            return DetectedEvent(kind, zone.id, zone.type.wire, start, s.timeMs)
        }
        val cell = loc.toCell()
        return DetectedEvent(kind, cellId(cell), HOTSPOT_CANDIDATE, stopStart, s.timeMs, cell)
    }

    private fun trackZoneEntry(s: VehicleState, zones: List<Zone>) {
        val loc = s.location ?: return
        val zone = zones.firstOrNull { it.type == ZoneType.PICKUP_OFFICIAL && it.contains(loc) }
        zoneEntry = when {
            zone == null -> null
            zoneEntry?.first?.id == zone.id -> zoneEntry
            else -> zone to s.timeMs
        }
    }

    companion object {
        val PASSENGER_SEATS = listOf(SeatPos.PASSENGER, SeatPos.REAR_LEFT, SeatPos.REAR_RIGHT)

        fun cellId(cell: LatLng): String = "cell:%.5f,%.5f".format(java.util.Locale.ROOT, cell.lat, cell.lon)
    }
}
