package app.last10min.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.last10min.AppGraph

enum class Mode(val label: String) { PARKING("주차"), PICKUP("픽업") }

/** True when the app window is wide (full or 2:1 landscape); false for 1:1 split or portrait windows. */
val LocalWide = compositionLocalOf { true }

@Composable
fun Last10MinScreen(graph: AppGraph) {
    var mode by rememberSaveable { mutableStateOf(Mode.PARKING) }
    var signals by rememberSaveable { mutableStateOf(false) }
    PleosTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Pleos.colors.backgroundPrimary)) {
            val wide = maxWidth >= 1100.dp
            CompositionLocalProvider(LocalWide provides wide) {
                Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 48.dp else 24.dp, vertical = 24.dp)) {
                    if (signals) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 96.dp), verticalAlignment = Alignment.CenterVertically) {
                            PButton("\u2190 \ub3cc\uc544\uac00\uae30", ButtonKind.BASIC) { signals = false }
                            Spacer(Modifier.width(24.dp))
                            PText("\ucc28\ub7c9 \uc2e0\ud638", PleosType.titleMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { SignalsScreen(graph) }
                    } else {
                        PPageNavigation("\ub9c8\uc9c0\ub9c9 10\ubd84") {
                            if (wide) {
                                PTabs(Mode.entries, mode, { it.label }, Modifier.width(360.dp)) { mode = it }
                                Spacer(Modifier.width(16.dp))
                            }
                            PButton("\ucc28\ub7c9 \uc2e0\ud638", ButtonKind.BASIC) { signals = true }
                        }
                        if (!wide) {
                            PTabs(Mode.entries, mode, { it.label }, Modifier.fillMaxWidth()) { mode = it }
                        }
                        Spacer(Modifier.height(16.dp))
                        when (mode) {
                            Mode.PARKING -> ParkingScreen(graph)
                            Mode.PICKUP -> PickupScreen(graph)
                        }
                    }
                }
            }
        }
    }
}
