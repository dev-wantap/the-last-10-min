package app.last10min.data

import android.content.Context
import app.last10min.engine.LatLng
import app.last10min.engine.ParkingFee
import app.last10min.engine.Zone
import app.last10min.engine.ZoneType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class ZoneRepository(context: Context, private val http: Http, snapshot: DemoSnapshot) {
    private val bundled: List<Zone> =
        parseZones(context.assets.open("zones.json").bufferedReader().use { it.readText() }) + parseZones(snapshot.json)

    private val _zones = MutableStateFlow(bundled)
    val zones: StateFlow<List<Zone>> = _zones

    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online

    fun refresh() {
        if (!http.enabled) return
        try {
            val fresh = parseZones(http.get("/v1/zones"))
            if (fresh.isNotEmpty()) _zones.value = fresh
            _online.value = true
        } catch (_: Exception) {
            _online.value = false
        }
    }

    companion object {
        fun parseZones(json: String): List<Zone> {
            val arr = JSONObject(json).getJSONArray("zones")
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val type = ZoneType.fromWire(o.getString("type")) ?: return@mapNotNull null
                Zone(
                    id = o.getString("id"),
                    type = type,
                    name = o.getString("name"),
                    center = o.getJSONArray("center").toLatLng(),
                    polygon = o.getJSONArray("polygon").let { p -> (0 until p.length()).map { p.getJSONArray(it).toLatLng() } },
                    guidance = o.optBoolean("guidance", type != ZoneType.PICKUP_HOTSPOT),
                    capacity = o.optInt("capacity", -1).takeIf { it >= 0 },
                    available = o.optInt("available", -1).takeIf { it >= 0 },
                    fee = o.optJSONObject("fee")?.let { f ->
                        ParkingFee(
                            baseMin = f.getInt("baseMin"),
                            baseFee = f.getInt("baseFee"),
                            unitMin = f.getInt("unitMin"),
                            unitFee = f.getInt("unitFee"),
                            dailyMax = f.optInt("dailyMax", -1).takeIf { it >= 0 },
                        )
                    },
                )
            }
        }

        private fun JSONArray.toLatLng() = LatLng(getDouble(0), getDouble(1))
    }
}
