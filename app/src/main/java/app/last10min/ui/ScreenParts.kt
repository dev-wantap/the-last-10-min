package app.last10min.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.last10min.AppGraph
import app.last10min.data.DemoSnapshot
import app.last10min.data.Prediction
import app.last10min.data.PredictionKind
import app.last10min.data.Prior
import app.last10min.engine.ParkingDetector
import app.last10min.engine.Zone
import app.last10min.plan.Predict

@Composable
fun rememberPredict(graph: AppGraph): Predict {
    val preds by graph.predictions.predictions.collectAsState()
    return { id, kind, offset -> preds[Triple(id, kind, offset)] }
}

fun confidence(p: Prediction?): String = when {
    p == null || p.stage == "offline-prior" || p.samples == 0 -> "아직 기록이 없어 기본값으로 계산했어요"
    p.stage == DemoSnapshot.STAGE -> "같은 시간대 Pleos 차량 기록 ${p.samples}건 (내장 데이터)"
    p.lowConfidence -> "같은 시간대 기록 ${p.samples}건 · 참고용"
    p.stage == "realtime" -> "최근 30분 실시간 보정 · 기록 ${p.samples}건"
    p.stage == "history" -> "같은 시간대 Pleos 차량 기록 ${p.samples}건"
    else -> "공공데이터 기본값으로 계산했어요"
}

fun predictedMin(zone: Zone, kind: PredictionKind, predict: Predict): Double =
    predict(zone.id, kind, 0)?.minutes ?: Prior.minutes(zone, kind)

@Composable
fun MapLayout(controls: @Composable () -> Unit, map: MapSpec, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        controls()
        Spacer(Modifier.height(24.dp))
        if (LocalWide.current) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    content = content,
                )
                MapPanel(map, Modifier.weight(1f).fillMaxHeight().padding(bottom = 24.dp))
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                MapPanel(map, Modifier.fillMaxWidth().height(420.dp))
                content()
            }
        }
    }
}

@Composable
fun LiveParking(state: ParkingDetector.State, now: Long, predict: Predict) {
    val c = Pleos.colors
    val (title, detail, tone) = when (state) {
        ParkingDetector.State.Idle -> return
        is ParkingDetector.State.Entering -> Triple("${state.zone.name}에 들어왔어요", "진입 확인 중 · ${clock(now - state.firstInsideMs)}", Tone.POSITIVE)
        is ParkingDetector.State.Inside ->
            Triple("${state.zone.name} · 자리 찾는 중", "${clock(now - state.enterMs)} 지남 · 이 시간대엔 보통 ${minutes(predictedMin(state.zone, PredictionKind.PARK, predict))}분", Tone.POSITIVE)
        is ParkingDetector.State.Parked ->
            Triple("${state.zone.name}에 주차했어요", "나갈 때 출차 약 ${minutes(predictedMin(state.zone, PredictionKind.EXIT, predict))}분", Tone.ACTIVE)
        is ParkingDetector.State.Exiting ->
            Triple("출차 중", "${clock(now - state.startMs)} 지남 · 보통 ${minutes(predictedMin(state.zone, PredictionKind.EXIT, predict))}분", Tone.POSITIVE)
    }
    PCard(accent = toneColor(tone)) {
        PText(title, PleosType.titleSmall)
        PText(detail, PleosType.bodyMedium, c.textSecondary)
    }
}
