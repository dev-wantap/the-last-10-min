package app.last10min

import android.app.Application
import android.content.Context
import app.last10min.data.DemoSnapshot
import app.last10min.data.EventStore
import app.last10min.data.EventUploader
import app.last10min.data.Http
import app.last10min.data.PredictionRepository
import app.last10min.data.ZoneRepository
import app.last10min.engine.DetectedEvent
import app.last10min.engine.EventEncoder
import app.last10min.engine.EventEngine
import app.last10min.engine.Gear
import app.last10min.engine.ParkingDetector
import app.last10min.engine.SeatPos
import app.last10min.engine.VehicleState
import app.last10min.engine.Zone
import app.last10min.vehicle.NaviGateway
import app.last10min.vehicle.SignalHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.SecureRandom

data class LogEntry(val timeMs: Long, val text: String)

data class PickupView(val stopped: Boolean, val zone: Zone?)

fun parkingLabel(s: ParkingDetector.State): String = when (s) {
    ParkingDetector.State.Idle -> "주차장 밖"
    is ParkingDetector.State.Entering -> "진입 확인 중 (${s.zone.name})"
    is ParkingDetector.State.Inside -> "주차장 안 (${s.zone.name})"
    is ParkingDetector.State.Parked -> "주차됨 (${s.zone.name})"
    is ParkingDetector.State.Exiting -> "출차 중 (${s.zone.name})"
}

private class ParkingPhase(val name: String, val zone: Zone?, val sinceMs: Long?)

private fun parkingPhase(s: ParkingDetector.State): ParkingPhase = when (s) {
    ParkingDetector.State.Idle -> ParkingPhase("idle", null, null)
    is ParkingDetector.State.Entering -> ParkingPhase("entering", s.zone, s.firstInsideMs)
    is ParkingDetector.State.Inside -> ParkingPhase("inside", s.zone, s.enterMs)
    is ParkingDetector.State.Parked -> ParkingPhase("parked", s.zone, null)
    is ParkingDetector.State.Exiting -> ParkingPhase("exiting", s.zone, s.startMs)
}

class AppGraph(app: Application) {
    val clock: () -> Long = System::currentTimeMillis
    private val http = Http(BuildConfig.SERVER_URL)

    private val snapshot = DemoSnapshot.load(app)
    val zones = ZoneRepository(app, http, snapshot)
    val navi = NaviGateway(app, clock)
    val signals = SignalHub(app, navi, clock)
    val predictions = PredictionRepository(http, snapshot, clock)
    private val store = EventStore(File(app.filesDir, "pending-events.jsonl"))
    val uploader = EventUploader(http, store)
    private val engine = EventEngine(zones.zones.value)
    private val encoder = EventEncoder(installSecret(app))

    private val _parking = MutableStateFlow<ParkingDetector.State>(ParkingDetector.State.Idle)
    val parking: StateFlow<ParkingDetector.State> = _parking
    private val _pickup = MutableStateFlow(PickupView(false, null))
    val pickup: StateFlow<PickupView> = _pickup
    private val _vehicle = MutableStateFlow(VehicleState(clock()))
    val vehicle: StateFlow<VehicleState> = _vehicle
    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        navi.connect()
        signals.onSeatPolarityLearned = { inverted ->
            record(if (inverted) "좌석 착석 극성 보정: SDK 값 반전 (주행 중 운전석 기준)" else "좌석 착석 극성 확인: SDK 값 그대로")
        }
        signals.start()
        scope.launch { while (isActive) { tick(); delay(1_000) } }
        scope.launch(Dispatchers.Main) { while (isActive) { navi.poll(); delay(2_000) } }
        scope.launch(Dispatchers.IO) { while (isActive) { refreshServerData(); delay(20_000) } }
        scope.launch(Dispatchers.IO) { while (isActive) { uploader.flush(); delay(10_000) } }
        if (BuildConfig.DEBUG && http.enabled) scope.launch(Dispatchers.IO) { mirrorToDashboard() }
        record("앱 시작 · 서버 ${BuildConfig.SERVER_URL.ifBlank { "없음 (내장 데이터)" }}")
    }

    fun refreshNow() = scope.launch(Dispatchers.IO) { refreshServerData() }

    fun navigateTo(zone: Zone) {
        val failure = navi.requestRoute(zone.center, zone.name)
        record(if (failure == null) "목적지 변경 요청: ${zone.name}" else "목적지 변경 실패: $failure")
    }

    fun record(text: String) {
        _log.value = (listOf(LogEntry(clock(), text)) + _log.value).take(60)
    }

    private fun refreshServerData() {
        zones.refresh()
        engine.zones = zones.zones.value
        predictions.refresh(zones.zones.value, OFFSETS)
    }

    private fun tick() {
        val state = signals.snapshot()
        val events = engine.update(state)
        events.forEach { e ->
            store.append(encoder.encode(e))
            record(describe(e))
        }
        if (events.isNotEmpty()) scope.launch(Dispatchers.IO) { uploader.flush(); refreshServerData() }
        _parking.value = engine.parkingState
        _pickup.value = PickupView(engine.pickupStopped, engine.pickupZone)
        _vehicle.value = state
    }

    private suspend fun mirrorToDashboard() {
        var healthy: Boolean? = null
        while (scope.isActive) {
            val ok = try {
                http.post("/demo/telemetry", mirrorJson())
                true
            } catch (_: IOException) {
                false
            }
            if (ok != healthy) {
                record(if (ok) "시연 미러 연결: 차량 상태를 대시보드로 보냄 (디버그 빌드 전용)" else "시연 미러 꺼짐: 서버의 시연 기능에 연결하지 못함")
                healthy = ok
            }
            delay(if (ok) 1_000 else 10_000)
        }
    }

    private fun mirrorJson(): String {
        val v = _vehicle.value
        val pickup = _pickup.value
        val phase = parkingPhase(_parking.value)
        val gear = when (v.gear) {
            Gear.PARK -> "P"
            Gear.REVERSE -> "R"
            Gear.NEUTRAL -> "N"
            Gear.DRIVE -> "D"
            Gear.UNKNOWN -> "-"
        }
        return JSONObject()
            .put("gear", gear)
            .put("speedKph", v.speedKph ?: JSONObject.NULL)
            .put("lat", v.location?.lat ?: JSONObject.NULL)
            .put("lon", v.location?.lon ?: JSONObject.NULL)
            .put("seats", JSONObject(SeatPos.entries.associate { it.name to (v.seatOccupied[it] ?: JSONObject.NULL) }))
            .put("doors", JSONObject(SeatPos.entries.associate { it.name to (v.doorOpen[it] ?: JSONObject.NULL) }))
            .put("parking", parkingLabel(_parking.value))
            .put("parkingPhase", phase.name)
            .put("parkingZoneId", phase.zone?.id ?: JSONObject.NULL)
            .put("parkingElapsedSec", phase.sinceMs?.let { (v.timeMs - it) / 1000 } ?: JSONObject.NULL)
            .put("pickupStopped", pickup.stopped)
            .put("pickup", if (pickup.stopped) "정차 중 (${pickup.zone?.name ?: "구역 밖"})" else "주행/대기")
            .toString()
    }

    private fun describe(e: DetectedEvent): String {
        val name = zones.zones.value.firstOrNull { it.id == e.zoneId }?.name ?: e.zoneId
        val m = e.durationSec / 60
        val s = e.durationSec % 60
        return "이벤트 ${e.kind.wire} · $name · ${m}분 ${s}초"
    }

    companion object {
        val OFFSETS = (0..120 step 10).toList()

        private fun installSecret(context: Context): ByteArray {
            val prefs = context.getSharedPreferences("install", Context.MODE_PRIVATE)
            prefs.getString("secret", null)?.let { return it.chunked(2).map { b -> b.toInt(16).toByte() }.toByteArray() }
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            prefs.edit().putString("secret", bytes.joinToString("") { "%02x".format(it) }).apply()
            return bytes
        }
    }
}
