package app.last10min.data

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

class DemoSnapshot(val json: String) {
    private data class Key(val zoneId: String, val kind: PredictionKind, val weekend: Boolean, val bucket: Int)

    private val table: Map<Key, Pair<Double, Int>> = JSONObject(json).getJSONArray("predictions").let { rows ->
        (0 until rows.length()).mapNotNull { i ->
            val r = rows.getJSONArray(i)
            val kind = PredictionKind.entries.firstOrNull { it.wire == r.getString(1) } ?: return@mapNotNull null
            Key(r.getString(0), kind, r.getString(2) == "weekend", r.getInt(3)) to (r.getDouble(4) to r.getInt(5))
        }.toMap()
    }

    fun lookup(zoneId: String, kind: PredictionKind, offsetMin: Int, atMs: Long): Prediction? {
        val local = Instant.ofEpochMilli(atMs).atZone(SEOUL)
        val key = Key(zoneId, kind, local.dayOfWeek.value >= 6, (local.hour * 60 + local.minute) / 15)
        val (minutes, samples) = table[key] ?: return null
        return Prediction(zoneId, kind, offsetMin, minutes, samples, STAGE, samples < CONFIDENT_SAMPLES)
    }

    companion object {
        const val STAGE = "demo-snapshot"
        private const val CONFIDENT_SAMPLES = 20
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        fun load(context: Context) = DemoSnapshot(context.assets.open("demo_snapshot.json").bufferedReader().use { it.readText() })
    }
}
