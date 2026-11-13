package app.last10min.engine

data class ParkingConfig(
    val enterMaxSpeedKph: Double = 20.0,
    val enterDwellMs: Long = 15_000,
    val stayInCarParkedMs: Long = 3 * 60_000,
)

/** PLAN 4.4: enter -> park complete / park fail, then P->D -> exit complete. */
class ParkingDetector(private val config: ParkingConfig = ParkingConfig()) {

    sealed interface State {
        data object Idle : State
        data class Entering(val zone: Zone, val firstInsideMs: Long) : State
        data class Inside(val zone: Zone, val enterMs: Long, val parkSinceMs: Long?, val driverDoorOpenedInPark: Boolean) : State
        data class Parked(val zone: Zone) : State
        data class Exiting(val zone: Zone, val startMs: Long) : State
    }

    var state: State = State.Idle
        private set

    fun update(s: VehicleState, zones: List<Zone>): List<DetectedEvent> {
        val out = mutableListOf<DetectedEvent>()
        state = step(state, s, zones, out)
        return out
    }

    private fun step(current: State, s: VehicleState, zones: List<Zone>, out: MutableList<DetectedEvent>): State {
        val loc = s.location
        return when (current) {
            State.Idle -> {
                val zone = loc?.let { p -> zones.firstOrNull { it.type == ZoneType.PARKING && it.contains(p) } }
                    ?: return State.Idle
                when {
                    s.gear == Gear.PARK -> State.Parked(zone)
                    isSlow(s) -> step(State.Entering(zone, s.timeMs), s, zones, out)
                    else -> State.Idle
                }
            }

            is State.Entering -> {
                if (loc != null && !current.zone.contains(loc)) return State.Idle
                val inside = State.Inside(current.zone, current.firstInsideMs, null, false)
                when {
                    s.timeMs - current.firstInsideMs >= config.enterDwellMs || s.gear == Gear.PARK -> {
                        out += event(EventKind.PARK_ENTER, current.zone, current.firstInsideMs, s.timeMs)
                        step(inside, s, zones, out)
                    }
                    else -> current
                }
            }

            is State.Inside -> {
                if (loc != null && !current.zone.contains(loc) && s.gear != Gear.PARK) {
                    out += event(EventKind.PARK_FAIL, current.zone, current.enterMs, s.timeMs)
                    return State.Idle
                }
                if (s.gear != Gear.PARK) return current.copy(parkSinceMs = null, driverDoorOpenedInPark = false)

                val parkSince = current.parkSinceMs ?: s.timeMs
                val doorOpened = current.driverDoorOpenedInPark || s.doorOpen[SeatPos.DRIVER] == true
                val driverSeat = s.seatOccupied[SeatPos.DRIVER]
                val driverLeft = doorOpened && driverSeat != true
                val stayedParked = s.timeMs - parkSince >= config.stayInCarParkedMs
                if (driverLeft || stayedParked) {
                    out += event(EventKind.PARK_COMPLETE, current.zone, current.enterMs, if (driverLeft) s.timeMs else parkSince)
                    State.Parked(current.zone)
                } else {
                    current.copy(parkSinceMs = parkSince, driverDoorOpenedInPark = doorOpened)
                }
            }

            is State.Parked ->
                if (s.gear == Gear.DRIVE || s.gear == Gear.REVERSE) step(State.Exiting(current.zone, s.timeMs), s, zones, out)
                else current

            is State.Exiting -> when {
                loc != null && !current.zone.contains(loc) -> {
                    out += event(EventKind.EXIT_COMPLETE, current.zone, current.startMs, s.timeMs)
                    State.Idle
                }
                else -> current
            }
        }
    }

    private fun isSlow(s: VehicleState): Boolean = (s.speedKph ?: 0.0) <= config.enterMaxSpeedKph

    private fun event(kind: EventKind, zone: Zone, startMs: Long, endMs: Long) =
        DetectedEvent(kind, zone.id, zone.type.wire, startMs, endMs)
}
