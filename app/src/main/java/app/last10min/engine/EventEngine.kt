package app.last10min.engine

import java.time.Instant
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class EventEngine(
    zones: List<Zone>,
    private val parking: ParkingDetector = ParkingDetector(),
    private val pickup: PickupDetector = PickupDetector(),
) {
    @Volatile
    var zones: List<Zone> = zones

    val parkingState: ParkingDetector.State get() = parking.state
    val pickupStopped: Boolean get() = pickup.isStopped
    val pickupZone: Zone? get() = pickup.currentZone

    fun update(s: VehicleState): List<DetectedEvent> {
        val snapshot = zones
        return parking.update(s, snapshot) + pickup.update(s, snapshot)
    }
}

data class WireEvent(
    val zoneId: String,
    val zoneType: String,
    val event: String,
    val durationSec: Long,
    val weekday: Int,
    val timeBucket: Int,
    val day: String,
    val dailyToken: String,
)

/**
 * PLAN 6.3 data minimisation: exact times become a 15-minute bucket and the vehicle becomes an
 * HMAC(install secret, local date) token, so the server can count distinct cars per day but cannot
 * link the same car across days.
 */
class EventEncoder(private val installSecret: ByteArray, private val timeZone: ZoneId = SEOUL) {

    fun encode(e: DetectedEvent): WireEvent {
        val t = Instant.ofEpochMilli(e.endMs).atZone(timeZone)
        val day = t.toLocalDate().toString()
        return WireEvent(
            zoneId = e.zoneId,
            zoneType = e.zoneType,
            event = e.kind.wire,
            durationSec = e.durationSec,
            weekday = t.dayOfWeek.value,
            timeBucket = (t.hour * 60 + t.minute) / 15,
            day = day,
            dailyToken = dailyToken(day),
        )
    }

    fun dailyToken(day: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(installSecret, "HmacSHA256"))
        return mac.doFinal(day.toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    }

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
