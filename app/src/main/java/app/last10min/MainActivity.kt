package app.last10min

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.last10min.ui.Last10MinScreen

class MainActivity : ComponentActivity() {
    private val graph get() = (application as Last10MinApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST) else startSensing()
        setContent { Last10MinScreen(graph) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST) return
        permissions.zip(grantResults.toList()).filter { it.second != PackageManager.PERMISSION_GRANTED }
            .forEach { graph.record("권한 거부: ${it.first.substringAfterLast('.')} → 대체 경로 사용") }
        startSensing()
        graph.signals.permissionsChanged()
    }

    private fun startSensing() {
        startForegroundService(Intent(this, SensingService::class.java))
        graph.start()
    }

    private companion object {
        const val REQUEST = 7
        val RUNTIME_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            "android.car.permission.CAR_SPEED",
            "pleos.car.permission.CAR_DRIVE",
            "pleos.car.permission.CAR_SEATS",
            "pleos.car.permission.CAR_DOORS",
            "pleos.car.permission.NAVI_ROUTE",
            "pleos.car.permission.NAVI_ROUTE_SEARCH",
        )
    }
}
