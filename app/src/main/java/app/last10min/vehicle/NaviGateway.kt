package app.last10min.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import app.last10min.engine.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Fix(val position: LatLng, val receivedAtMs: Long)

data class NaviDestination(val name: String, val position: LatLng, val remainSec: Long)

class NaviGateway(private val context: Context, private val clock: () -> Long) {
    private var helper: NaviHelper? = null
    @Volatile private var connected = false

    private val _status = MutableStateFlow("연결 전")
    val status: StateFlow<String> = _status
    private val _location = MutableStateFlow<Fix?>(null)
    val location: StateFlow<Fix?> = _location
    private val _destination = MutableStateFlow<NaviDestination?>(null)
    val destination: StateFlow<NaviDestination?> = _destination

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val p = LatLng(currentLocationInfo.latitude, currentLocationInfo.longitude)
            if (p.lat in -90.0..90.0 && p.lon in -180.0..180.0 && (p.lat != 0.0 || p.lon != 0.0)) {
                _location.value = Fix(p, clock())
            }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            _destination.value = NaviDestination(
                destinationInfo.name,
                LatLng(destinationInfo.latitude, destinationInfo.longitude),
                destinationInfo.remainTime.toLong(),
            ).takeIf { destinationInfo.name.isNotBlank() }
        }

        override fun onDestinationChanged() {
            _destination.value = null
            poll()
        }

        override fun onRoutingCompleted() = poll()

        override fun onRouteCancelled() {
            _destination.value = null
        }

        override fun onError(errorCode: NaviErrorCode) {
            _status.value = "오류"
        }
    }

    fun connect() {
        if (helper != null) return
        _status.value = "연결 중"
        try {
            val instance = NaviHelper(context.applicationContext)
            helper = instance
            instance.initialize(
                onConnected = {
                    connected = true
                    instance.addListener(listener)
                    _status.value = "연결됨"
                    poll()
                },
                onFailed = {
                    connected = false
                    helper = null
                    _status.value = "연결 실패"
                },
            )
        } catch (_: Exception) {
            helper = null
            _status.value = "초기화 실패"
        }
    }

    fun poll() {
        val instance = helper ?: return
        if (!connected) return
        try {
            instance.getCurrentLocationInfo()
            instance.getDestinationInfo()
        } catch (_: Exception) {
            _status.value = "조회 실패"
        }
    }

    /** Returns null when the route request was handed to the navigation app, otherwise the reason it was not. */
    fun requestRoute(position: LatLng, name: String): String? {
        val instance = helper ?: return "내비게이션 미연결"
        if (!connected) return "내비게이션 미연결"
        return try {
            instance.requestRoute(
                RouteInfo(latitude = position.lat, longitude = position.lon, poiName = name, poiId = "", address = "", poiSubId = "0"),
            )
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    fun release() {
        try {
            helper?.removeListener(listener)
            helper?.release()
        } catch (_: Exception) {
        }
        helper = null
        connected = false
        _status.value = "연결 해제"
    }
}
