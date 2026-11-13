package app.last10min.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.Manifest
import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import app.last10min.engine.Gear
import app.last10min.engine.LatLng
import app.last10min.engine.SeatPos
import app.last10min.engine.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

enum class SignalHealth { OK, WAITING, UNSUPPORTED }

data class SignalStatus(val label: String, val value: String, val source: String, val health: SignalHealth)

enum class LocationMode(val label: String) { AUTO("자동"), NAVI("NaviHelper"), GPS("GPS") }

/**
 * Collects every vehicle signal into one [VehicleState]. Primary sources follow PLAN 6.1; when a source
 * is missing the documented fallback is used and shown in the signal panel, never silently faked:
 * gear: Pleos Vehicle SDK -> AAOS GEAR_SELECTION; speed: AAOS PERF_VEHICLE_SPEED -> derived from fixes;
 * location: NaviHelper -> Android GPS provider.
 */
class SignalHub(private val context: Context, private val navi: NaviGateway, private val clock: () -> Long) {
    private var vehicle: Vehicle? = null
    private var car: Car? = null

    @Volatile private var pleosGear: Gear? = null
    @Volatile private var aaosGear: Gear? = null
    @Volatile private var aaosSpeedKph: Double? = null
    @Volatile private var gpsFix: Fix? = null
    @Volatile private var gpsMovedAtMs = Long.MIN_VALUE
    private var naviMovedAtMs = Long.MIN_VALUE
    private var lastNaviPosition: LatLng? = null
    @Volatile private var derivedSpeedKph: Double? = null
    private var previousFix: Fix? = null

    private val seats = ConcurrentHashMap<SeatPos, Boolean>()
    private val doors = ConcurrentHashMap<SeatPos, Boolean>()
    private val belts = ConcurrentHashMap<SeatPos, Boolean>()
    private val unsupported = ConcurrentHashMap.newKeySet<String>()

    /**
     * The emulator's Vehicle SDK reports SEAT_OCCUPANCY VACANT(1) as true and OCCUPIED(2) as false. Rather
     * than hard-coding a flip that may not hold on a real car, polarity is learned once: a car moving in D
     * must have its driver seat occupied. Null until that moment; raw SDK values are used meanwhile.
     */
    @Volatile var seatPolarityInverted: Boolean? = null
        private set
    var onSeatPolarityLearned: ((Boolean) -> Unit)? = null

    val locationMode = MutableStateFlow(LocationMode.AUTO)

    private val _statuses = MutableStateFlow<List<SignalStatus>>(emptyList())
    val statuses: StateFlow<List<SignalStatus>> = _statuses

    fun start() {
        startPleosVehicle()
        startCarProperties()
        startGps()
    }

    fun stop() {
        try { vehicle?.release() } catch (_: Exception) {}
        try { car?.disconnect() } catch (_: Exception) {}
        vehicle = null
        car = null
    }

    fun snapshot(): VehicleState {
        val now = clock()
        val fix = currentFix(now)
        if (fix != null && fix != previousFix) {
            previousFix?.let { prev ->
                val dt = (fix.receivedAtMs - prev.receivedAtMs) / 1000.0
                if (dt in 0.5..10.0) derivedSpeedKph = prev.position.distanceMeters(fix.position) / dt * 3.6
            }
            previousFix = fix
        }
        val speed = aaosSpeedKph ?: derivedSpeedKph
        val gear = pleosGear ?: aaosGear ?: Gear.UNKNOWN
        learnSeatPolarity(gear, speed)
        val inverted = seatPolarityInverted == true
        val state = VehicleState(
            timeMs = now,
            location = fix?.position,
            speedKph = speed,
            gear = gear,
            seatOccupied = SeatPos.entries.associateWith { pos -> seats[pos]?.let { if (inverted) !it else it } },
            doorOpen = SeatPos.entries.associateWith { doors[it] },
            beltBuckled = SeatPos.entries.associateWith { belts[it] },
        )
        _statuses.value = describe(state, fix)
        return state
    }

    private fun learnSeatPolarity(gear: Gear, speedKph: Double?) {
        if (seatPolarityInverted != null || gear != Gear.DRIVE || (speedKph ?: 0.0) < CALIBRATION_SPEED_KPH) return
        val driverRaw = seats[SeatPos.DRIVER] ?: return
        val inverted = !driverRaw
        seatPolarityInverted = inverted
        onSeatPolarityLearned?.invoke(inverted)
    }

    private fun currentFix(now: Long): Fix? {
        val naviFix = navi.location.value?.takeIf { now - it.receivedAtMs < STALE_MS }
        val gps = gpsFix?.takeIf { now - it.receivedAtMs < STALE_MS }
        if (naviFix != null) {
            val prev = lastNaviPosition
            if (prev != null && prev.distanceMeters(naviFix.position) > MOVE_EPSILON_M) naviMovedAtMs = now
            lastNaviPosition = naviFix.position
        }
        return when (locationMode.value) {
            LocationMode.AUTO -> if (gps != null && gpsMovedAtMs > naviMovedAtMs) gps else naviFix ?: gps
            LocationMode.NAVI -> naviFix
            LocationMode.GPS -> gps
        }
    }

    private fun startPleosVehicle() {
        val v = try {
            Vehicle(context.applicationContext).also { it.initialize() }
        } catch (e: Exception) {
            unsupported += listOf("gear", "seat", "door", "belt")
            return
        }
        vehicle = v
        guard("gear") {
            v.getGear().registerGearSelection(object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) { gear?.let { pleosGear = it.toGear() } }
                override fun onFailed(e: Exception) { unsupported += "gear" }
            })
            v.getGear().getCurrentGearSelectionState({ g -> g?.let { pleosGear = it.toGear() } }, { unsupported += "gear" })
        }
        for ((pos, value) in SEAT_AREAS) {
            val area = SeatArea(value)
            guard("seat") {
                v.getSeat().registerSeatOccupancy(area, object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(occupied: Boolean?) { occupied?.let { seats[pos] = it } }
                    override fun onFailed(e: Exception) { unsupported += "seat:$pos" }
                })
                v.getSeat().getCurrentSeatOccupancy(area, { o -> o?.let { seats[pos] = it } }, { unsupported += "seat:$pos" })
            }
            guard("belt") {
                v.getSeat().registerSeatBeltBuckledState(area, object : SeatBeltBuckledStateListener {
                    override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) { buckled?.let { belts[pos] = it } }
                    override fun onFailed(e: Exception) { unsupported += "belt:$pos" }
                })
            }
        }
        for ((pos, value) in DOOR_AREAS) {
            val area = DoorArea(value)
            guard("door") {
                v.getDoor().registerDoorPosition(area, object : DoorPositionListener {
                    override fun onPositionUpdated(position: Int?) { position?.let { doors[pos] = it > 0 } }
                    override fun onFailed(e: Exception) { unsupported += "door:$pos" }
                })
                v.getDoor().getCurrentDoorPosition(area, { p -> p?.let { doors[pos] = it > 0 } }, { unsupported += "door:$pos" })
            }
        }
    }

    private fun startCarProperties() {
        val c = try { Car.createCar(context) } catch (_: Exception) { null } ?: run {
            unsupported += listOf("speed", "aaosGear")
            return
        }
        car = c
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                when (value.propertyId) {
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { aaosSpeedKph = it * 3.6 }
                    VehiclePropertyIds.GEAR_SELECTION -> (value.value as? Int)?.let { aaosGear = aaosGearOf(it) }
                }
            }
            override fun onErrorEvent(propertyId: Int, areaId: Int) {}
        }
        guard("speed") { pm.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) }
        guard("aaosGear") { pm.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            unsupported += "gps"
            return
        }
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val listener = LocationListener { loc: Location ->
            val p = LatLng(loc.latitude, loc.longitude)
            val now = clock()
            val prev = gpsFix?.position
            if (prev != null && prev.distanceMeters(p) > MOVE_EPSILON_M) gpsMovedAtMs = now
            gpsFix = Fix(p, now)
        }
        guard("gps") {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            unsupported -= "gps"
        }
    }

    fun permissionsChanged() {
        if ("gps" in unsupported) startGps()
    }

    private inline fun guard(signal: String, block: () -> Unit) {
        try { block() } catch (_: Exception) { unsupported += signal }
    }

    private fun describe(s: VehicleState, fix: Fix?): List<SignalStatus> {
        fun health(key: String, has: Boolean) = when {
            has -> SignalHealth.OK
            unsupported.any { it == key || it.startsWith("$key:") } -> SignalHealth.UNSUPPORTED
            else -> SignalHealth.WAITING
        }
        val gearSource = when {
            pleosGear != null -> "Pleos Vehicle SDK"
            aaosGear != null -> "AAOS GEAR_SELECTION (대체)"
            else -> "Pleos Vehicle SDK"
        }
        val speedSource = if (aaosSpeedKph != null) "AAOS PERF_VEHICLE_SPEED" else "위치 변화로 계산 (대체)"
        val locSource = when {
            fix == null -> locationMode.value.label
            fix === navi.location.value -> "NaviHelper"
            else -> "Android GPS (대체)"
        }
        fun seatText(map: Map<SeatPos, Boolean?>, yes: String, no: String) =
            SeatPos.entries.joinToString("  ") { "${KOREAN[it]} ${map[it]?.let { v -> if (v) yes else no } ?: "?"}" }
        return listOf(
            SignalStatus("기어", s.gear.label(), gearSource, health("gear", s.gear != Gear.UNKNOWN)),
            SignalStatus("차속", s.speedKph?.let { "%.0f km/h".format(it) } ?: "-", speedSource, health("speed", s.speedKph != null)),
            SignalStatus("위치", fix?.position?.let { "%.5f, %.5f".format(it.lat, it.lon) } ?: "-", locSource, health("gps", fix != null)),
            SignalStatus(
                "좌석 착석", seatText(s.seatOccupied, "착석", "비움"),
                when (seatPolarityInverted) { null -> "Pleos Vehicle SDK · 극성 보정 전"; true -> "Pleos Vehicle SDK · 극성 반전 보정"; false -> "Pleos Vehicle SDK" },
                health("seat", seats.isNotEmpty()),
            ),
            SignalStatus("도어", seatText(s.doorOpen, "열림", "닫힘"), "Pleos Vehicle SDK", health("door", doors.isNotEmpty())),
            SignalStatus("안전벨트 (보조)", seatText(s.beltBuckled, "체결", "해제"), "Pleos Vehicle SDK", health("belt", belts.isNotEmpty())),
        )
    }

    companion object {
        private const val STALE_MS = 5_000L
        private const val CALIBRATION_SPEED_KPH = 5.0
        private const val MOVE_EPSILON_M = 3.0

        private val SEAT_AREAS = mapOf(
            SeatPos.DRIVER to SeatValue.ROW_1_LEFT,
            SeatPos.PASSENGER to SeatValue.ROW_1_RIGHT,
            SeatPos.REAR_LEFT to SeatValue.ROW_2_LEFT,
            SeatPos.REAR_RIGHT to SeatValue.ROW_2_RIGHT,
        )
        private val DOOR_AREAS = mapOf(
            SeatPos.DRIVER to DoorValue.ROW_1_LEFT,
            SeatPos.PASSENGER to DoorValue.ROW_1_RIGHT,
            SeatPos.REAR_LEFT to DoorValue.ROW_2_LEFT,
            SeatPos.REAR_RIGHT to DoorValue.ROW_2_RIGHT,
        )
        val KOREAN = mapOf(SeatPos.DRIVER to "운전석", SeatPos.PASSENGER to "동승석", SeatPos.REAR_LEFT to "뒤 좌", SeatPos.REAR_RIGHT to "뒤 우")

        private fun VehicleGear.toGear() = when (this) {
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.DRIVE
        }

        private fun aaosGearOf(v: Int) = when (v) {
            0x0004 -> Gear.PARK
            0x0002 -> Gear.REVERSE
            0x0001 -> Gear.NEUTRAL
            0 -> Gear.UNKNOWN
            else -> Gear.DRIVE
        }

        fun Gear.label() = when (this) {
            Gear.PARK -> "P"
            Gear.REVERSE -> "R"
            Gear.NEUTRAL -> "N"
            Gear.DRIVE -> "D"
            Gear.UNKNOWN -> "-"
        }
    }
}
