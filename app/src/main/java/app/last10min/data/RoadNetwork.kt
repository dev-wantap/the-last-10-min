package app.last10min.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

object Mercator {
    fun x(lon: Double): Double = (lon + 180.0) / 360.0

    fun y(lat: Double): Double {
        val s = sin(lat * PI / 180.0)
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }
}

class RoadNetwork(val attribution: String, val roads: List<Road>) {
    class Road(val rank: Int, val name: String, val x: DoubleArray, val y: DoubleArray)

    companion object {
        @Volatile private var cached: RoadNetwork? = null

        fun get(context: Context): RoadNetwork = cached ?: synchronized(this) {
            cached ?: parse(context.assets.open("roads.json").bufferedReader().use { it.readText() }).also { cached = it }
        }

        fun parse(json: String): RoadNetwork {
            val root = JSONObject(json)
            val arr = root.getJSONArray("roads")
            val roads = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val p = o.getJSONArray("p")
                val n = p.length() / 2
                Road(
                    rank = o.getInt("r"),
                    name = o.optString("n"),
                    x = DoubleArray(n) { Mercator.x(p.getDouble(it * 2 + 1)) },
                    y = DoubleArray(n) { Mercator.y(p.getDouble(it * 2)) },
                )
            }
            return RoadNetwork(root.getString("attribution"), roads)
        }
    }
}
