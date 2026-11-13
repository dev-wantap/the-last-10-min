package app.last10min.plan

import app.last10min.data.Prediction
import app.last10min.data.PredictionKind
import app.last10min.data.Prior
import app.last10min.engine.LatLng
import app.last10min.engine.Zone
import app.last10min.engine.ZoneType
import kotlin.math.abs

typealias Predict = (zoneId: String, kind: PredictionKind, offsetMin: Int) -> Prediction?

data class Place(val id: String, val name: String, val position: LatLng)

object DemoPlaces {
    val parkingDestinations = listOf(
        Place("gangnam-exit11", "강남역 11번 출구", LatLng(37.49850, 127.02810)),
        Place("sinnonhyeon", "신논현역 교보문고", LatLng(37.50430, 127.02480)),
        Place("daechi-academy", "대치동 학원가", LatLng(37.49950, 127.06150)),
    )
    val parkingDurations = listOf(30, 60, 120, 180, 240)
}

data class ParkingOption(
    val zone: Zone,
    val driveMin: Double?,
    val parkMin: Double,
    val walkMin: Double,
    val fee: Int?,
    val prediction: Prediction?,
    val distanceToDestinationM: Double,
) {
    val totalMin: Double get() = (driveMin ?: 0.0) + parkMin + walkMin
}

data class PickupPlan(
    val zone: Zone,
    val arriveAtMs: Long,
    val driveMin: Double?,
    val queueMin: Double,
    val departAtMs: Long,
    val prediction: Prediction?,
)

object Planner {
    private const val CITY_SPEED_M_PER_MIN = 25_000.0 / 60
    private const val ROAD_FACTOR = 1.3
    private const val WALK_M_PER_MIN = 75.0
    private const val WALK_FACTOR = 1.25

    fun travelMin(from: LatLng, to: LatLng): Double = from.distanceMeters(to) * ROAD_FACTOR / CITY_SPEED_M_PER_MIN

    fun walkMin(from: LatLng, to: LatLng): Double = from.distanceMeters(to) * WALK_FACTOR / WALK_M_PER_MIN

    fun parkingOptions(
        zones: List<Zone>,
        destination: LatLng,
        current: LatLng?,
        durationMin: Int,
        predict: Predict,
        radiusM: Double = 1_200.0,
    ): List<ParkingOption> = zones
        .filter { it.type == ZoneType.PARKING && it.guidance && it.center.distanceMeters(destination) <= radiusM }
        .map { lot ->
            val prediction = predict(lot.id, PredictionKind.PARK, 0)
            ParkingOption(
                zone = lot,
                driveMin = current?.let { travelMin(it, lot.center) },
                parkMin = prediction?.minutes ?: Prior.minutes(lot, PredictionKind.PARK),
                walkMin = walkMin(lot.center, destination),
                fee = lot.fee?.cost(durationMin),
                prediction = prediction,
                distanceToDestinationM = lot.center.distanceMeters(destination),
            )
        }
        .sortedBy { it.totalMin }

    fun arrivalChoices(nowMs: Long, count: Int = 12, stepMin: Int = 10, leadMin: Int = 15): List<Long> {
        val step = stepMin * 60_000L
        val first = ((nowMs + leadMin * 60_000L + step - 1) / step) * step
        return (0 until count).map { first + it * step }
    }

    fun pickupPlan(zone: Zone, arriveAtMs: Long, nowMs: Long, current: LatLng?, offsets: List<Int>, predict: Predict): PickupPlan {
        val aheadMin = (arriveAtMs - nowMs) / 60_000.0
        val offset = offsets.minByOrNull { abs(it - aheadMin) } ?: 0
        val prediction = predict(zone.id, PredictionKind.PICKUP, offset)
        val queue = prediction?.minutes ?: Prior.minutes(zone, PredictionKind.PICKUP)
        val drive = current?.let { travelMin(it, zone.center) }
        val departAt = arriveAtMs - (((drive ?: 0.0) + queue) * 60_000).toLong()
        return PickupPlan(zone, arriveAtMs, drive, queue, departAt, prediction)
    }

    /** PLAN 5.3: only official pickup zones and parking lots are ever suggested as places to stop. */
    fun legalStops(zones: List<Zone>, target: LatLng, radiusM: Double = 600.0): List<Pair<Zone, Double>> = zones
        .filter { it.guidance && it.type != ZoneType.PICKUP_HOTSPOT }
        .map { it to it.center.distanceMeters(target) }
        .filter { it.second <= radiusM }
        .sortedBy { it.second }

    fun hotspotsNear(zones: List<Zone>, target: LatLng, radiusM: Double = 800.0): List<Pair<Zone, Double>> = zones
        .filter { it.type == ZoneType.PICKUP_HOTSPOT }
        .map { it to it.center.distanceMeters(target) }
        .filter { it.second <= radiusM }
        .sortedBy { it.second }
}
