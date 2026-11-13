package app.last10min.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.last10min.AppGraph
import app.last10min.parkingLabel
import app.last10min.vehicle.LocationMode
import app.last10min.vehicle.SignalHealth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignalsScreen(graph: AppGraph) {
    val c = Pleos.colors
    val statuses by graph.signals.statuses.collectAsState()
    val mode by graph.signals.locationMode.collectAsState()
    val parking by graph.parking.collectAsState()
    val pickup by graph.pickup.collectAsState()
    val upload by graph.uploader.status.collectAsState()
    val log by graph.log.collectAsState()
    val online by graph.zones.online.collectAsState()
    val navi by graph.navi.status.collectAsState()
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.KOREA) }
    val wide = LocalWide.current

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (online) "서버 연결" else "오프라인 · 내장 데이터", if (online) Tone.ACTIVE else Tone.NEUTRAL)
            StatusPill(if (navi == "연결됨") "내비 연결" else "내비 $navi", if (navi == "연결됨") Tone.ACTIVE else Tone.NEUTRAL)
            StatusPill("전송 대기 ${upload.pending}건")
        }
        PCard {
            statuses.forEachIndexed { i, s ->
                if (i > 0) Spacer(Modifier.height(18.dp))
                val tone = when (s.health) { SignalHealth.OK -> Tone.ACTIVE; SignalHealth.WAITING -> Tone.NEUTRAL; SignalHealth.UNSUPPORTED -> Tone.NEGATIVE }
                if (wide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PText(s.label, PleosType.bodyMedium, c.textTertiary, Modifier.width(240.dp))
                        PText(s.value, PleosType.bodyMediumStrong, modifier = Modifier.weight(1f))
                        StatusPill(s.source, tone)
                    }
                } else {
                    PText(s.label, PleosType.labelMedium, c.textTertiary)
                    PText(s.value, PleosType.bodyMediumStrong)
                    StatusPill(s.source, tone)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PText("위치 소스", PleosType.bodyMediumStrong, c.textSecondary)
            PTabs(LocationMode.entries, mode, { it.label }) { graph.signals.locationMode.value = it }
        }
        PCard {
            PText("판정 엔진", PleosType.labelMedium, c.textTertiary)
            PText("주차 · ${parkingLabel(parking)}", PleosType.bodyMediumStrong)
            PText("승하차 · ${if (pickup.stopped) "정차 중 (${pickup.zone?.name ?: "구역 밖"})" else "주행/대기"}", PleosType.bodyMediumStrong)
            Spacer(Modifier.height(12.dp))
            PText("업로드 · 대기 ${upload.pending}건 · ${upload.lastResult}", PleosType.bodyMedium, c.textSecondary)
            Spacer(Modifier.height(20.dp))
            PButton("서버와 지금 동기화", ButtonKind.BASIC) { graph.refreshNow() }
        }
        SectionTitle("최근 기록")
        PCard {
            if (log.isEmpty()) PText("아직 기록이 없어요", PleosType.bodyMedium, c.textTertiary)
            log.take(25).forEach { PText("${time.format(Date(it.timeMs))}  ${it.text}", PleosType.bodySmall, c.textSecondary) }
        }
    }
}
