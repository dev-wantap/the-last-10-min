package app.last10min.data

import app.last10min.engine.Zone
import app.last10min.engine.ZoneType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

enum class PredictionKind(val wire: String) { PARK("park"), EXIT("exit"), PICKUP("pickup") }

data class Prediction(
    val zoneId: String,
    val kind: PredictionKind,
    val offsetMin: Int,
    val minutes: Double,
    val samples: Int,
    val stage: String,
    val lowConfidence: Boolean,
)

/** Same stage-1 prior as the server (PLAN 7), so the app still answers when it is offline. */
object Prior {
    fun minutes(zone: Zone, kind: PredictionKind): Double {
        val occupancy = occupancy(zone)
        return when (kind) {
            PredictionKind.PARK -> (1.0 + 14.0 * occupancy * occupancy * occupancy).coerceIn(1.0, 20.0)
            PredictionKind.EXIT -> (3.0 + 7.0 * occupancy).coerceIn(1.0, 20.0)
            PredictionKind.PICKUP -> 4.0
        }
    }

    private fun occupancy(zone: Zone): Double {
        val capacity = zone.capacity ?: return 0.5
        val available = zone.available ?: return 0.5
        if (capacity <= 0) return 0.5
        return (1.0 - available.toDouble() / capacity).coerceIn(0.0, 1.0)
    }
}

class PredictionRepository(private val http: Http, private val snapshot: DemoSnapshot, private val clock: () -> Long) {
    private val _predictions = MutableStateFlow<Map<Triple<String, PredictionKind, Int>, Prediction>>(emptyMap())
    val predictions: StateFlow<Map<Triple<String, PredictionKind, Int>, Prediction>> = _predictions

    fun refresh(zones: List<Zone>, offsets: List<Int>) {
        val fetched = try {
            fetch(zones.map { it.id }, offsets)
        } catch (_: Exception) {
            emptyList()
        }
        val now = clock()
        val byKey = fetched.associateBy { Triple(it.zoneId, it.kind, it.offsetMin) }.toMutableMap()
        for (zone in zones) for (kind in kindsFor(zone)) for (offset in offsets) {
            byKey.getOrPut(Triple(zone.id, kind, offset)) {
                snapshot.lookup(zone.id, kind, offset, now + offset * 60_000L)
                    ?: Prediction(zone.id, kind, offset, Prior.minutes(zone, kind), 0, "offline-prior", true)
            }
        }
        _predictions.value = byKey
    }

    fun get(zoneId: String, kind: PredictionKind, offset: Int = 0): Prediction? = _predictions.value[Triple(zoneId, kind, offset)]

    private fun fetch(zoneIds: List<String>, offsets: List<Int>): List<Prediction> {
        if (zoneIds.isEmpty()) return emptyList()
        val body = http.get("/v1/predictions?zoneIds=${zoneIds.joinToString(",")}&offsets=${offsets.joinToString(",")}")
        val arr = JSONObject(body).getJSONArray("predictions")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val kind = PredictionKind.entries.firstOrNull { it.wire == o.getString("kind") } ?: return@mapNotNull null
            Prediction(
                zoneId = o.getString("zoneId"),
                kind = kind,
                offsetMin = o.getInt("offsetMin"),
                minutes = o.getDouble("minutes"),
                samples = o.getInt("samples"),
                stage = o.getString("stage"),
                lowConfidence = o.getString("confidence") != "ok" || o.optBoolean("suppressed", false),
            )
        }
    }

    private fun kindsFor(zone: Zone) = when (zone.type) {
        ZoneType.PARKING -> listOf(PredictionKind.PARK, PredictionKind.EXIT)
        ZoneType.PICKUP_OFFICIAL, ZoneType.PICKUP_HOTSPOT -> listOf(PredictionKind.PICKUP)
    }
}
