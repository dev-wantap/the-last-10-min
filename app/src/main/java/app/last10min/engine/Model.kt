package app.last10min.engine

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lon: Double) {
    fun distanceMeters(other: LatLng): Double {
        val r = 6_371_000.0
        val dLat = (other.lat - lat) * PI / 180
        val dLon = (other.lon - lon) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat * PI / 180) * cos(other.lat * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    /** Snaps to a 0.0005 degree grid (about 55 m x 44 m in Seoul): the only coordinate ever uploaded. */
    fun toCell(): LatLng = LatLng(snap(lat), snap(lon))

    private fun snap(v: Double): Double = (v / CELL_DEG).roundToLong() * CELL_DEG

    companion object {
        const val CELL_DEG = 0.0005
    }
}

enum class ZoneType(val wire: String) {
    PARKING("parking"),
    PICKUP_OFFICIAL("pickup_official"),
    PICKUP_HOTSPOT("pickup_hotspot");

    val isPickup: Boolean get() = this != PARKING

    companion object {
        fun fromWire(value: String): ZoneType? = entries.firstOrNull { it.wire == value }
    }
}

data class Zone(
    val id: String,
    val type: ZoneType,
    val name: String,
    val center: LatLng,
    val polygon: List<LatLng>,
    /** False for discovered congestion hotspots: they are never offered as a place to stop. */
    val guidance: Boolean,
    val capacity: Int? = null,
    val available: Int? = null,
    val fee: ParkingFee? = null,
) {
    /** Ray casting on lat/lon; zones are a few dozen meters wide, so planar math is exact enough. */
    fun contains(p: LatLng): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.lat > p.lat) != (b.lat > p.lat) &&
                p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon
            ) inside = !inside
            j = i
        }
        return inside
    }
}

data class ParkingFee(val baseMin: Int, val baseFee: Int, val unitMin: Int, val unitFee: Int, val dailyMax: Int?) {
    fun cost(minutes: Int): Int {
        if (minutes <= 0) return 0
        val extraUnits = ((minutes - baseMin).coerceAtLeast(0) + unitMin - 1) / unitMin
        val total = baseFee + extraUnits * unitFee
        return dailyMax?.let { minOf(total, it) } ?: total
    }
}

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** Seat positions for a left-hand-drive car. Doors are indexed by the seat they serve. */
enum class SeatPos { DRIVER, PASSENGER, REAR_LEFT, REAR_RIGHT }

/**
 * One consistent snapshot of every signal the engine uses. A null value means the signal is
 * unsupported on this vehicle or not received yet; the detectors fall back as PLAN 4.4/4.5 describe.
 */
data class VehicleState(
    val timeMs: Long,
    val location: LatLng? = null,
    val speedKph: Double? = null,
    val gear: Gear = Gear.UNKNOWN,
    val seatOccupied: Map<SeatPos, Boolean?> = emptyMap(),
    val doorOpen: Map<SeatPos, Boolean?> = emptyMap(),
    val beltBuckled: Map<SeatPos, Boolean?> = emptyMap(),
)

enum class EventKind(val wire: String) {
    PARK_ENTER("park_enter"),
    PARK_COMPLETE("park_complete"),
    PARK_FAIL("park_fail"),
    EXIT_COMPLETE("exit_complete"),
    PICKUP_COMPLETE("pickup_complete"),
    DROPOFF_COMPLETE("dropoff_complete"),
    BOARDING_UNKNOWN("boarding_unknown"),
}

data class DetectedEvent(
    val kind: EventKind,
    val zoneId: String,
    val zoneType: String,
    val startMs: Long,
    val endMs: Long,
    val cell: LatLng? = null,
) {
    val durationSec: Long get() = ((endMs - startMs).coerceAtLeast(0)) / 1000
}

const val HOTSPOT_CANDIDATE = "hotspot_candidate"
