package app.last10min.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.last10min.AppGraph
import app.last10min.plan.DemoPlaces
import app.last10min.plan.ParkingOption
import app.last10min.plan.Planner

private val DRIVE_COLOR = Color(0xFFA4A8AE)

@Composable
fun ParkingScreen(graph: AppGraph) {
    val c = Pleos.colors
    val zones by graph.zones.zones.collectAsState()
    val vehicle by graph.vehicle.collectAsState()
    val parking by graph.parking.collectAsState()
    val predict = rememberPredict(graph)
    var destinationId by rememberSaveable { mutableStateOf(DemoPlaces.parkingDestinations.first().id) }
    var duration by rememberSaveable { mutableIntStateOf(120) }

    val destination = DemoPlaces.parkingDestinations.first { it.id == destinationId }
    val options = Planner.parkingOptions(zones, destination.position, vehicle.location, duration, predict)
    val best = options.firstOrNull()
    val nearest = options.minByOrNull { it.distanceToDestinationM }
    val cheapest = options.filter { it.fee != null }.takeIf { it.size > 1 }?.minByOrNull { it.fee!! }

    val map = MapSpec(
        areas = options.map { MapArea(it.zone.polygon, if (it == best) MapStyle.BEST else MapStyle.OPTION) },
        pins = listOf(MapPin(destination.position, destination.name, MapStyle.DESTINATION)) +
            options.map { MapPin(it.zone.center, "${it.zone.name}\n${arrivalLabel(it, vehicle.timeMs)} · ${it.fee?.let(::won) ?: "요금 정보 없음"}", if (it == best) MapStyle.BEST else MapStyle.OPTION) } +
            listOfNotNull(vehicle.location?.let { MapPin(it, "내 차", MapStyle.VEHICLE) }),
        focus = listOf(destination.position) + options.map { it.zone.center },
        focusKey = destination.id,
    )

    MapLayout(
        controls = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PDropdown("목적지", DemoPlaces.parkingDestinations, destination, { it.name }, Modifier.weight(1f)) { destinationId = it.id }
                Spacer(Modifier.width(24.dp))
                PDropdown("주차 시간", DemoPlaces.parkingDurations, duration, ::durationLabel, Modifier.width(300.dp)) { duration = it }
            }
        },
        map = map,
    ) {
        LiveParking(parking, vehicle.timeMs, predict)
        if (best == null) {
            PText("${destination.name} 주변 1.2km 안에 주차장 정보가 없어요", PleosType.bodyMedium, c.textTertiary)
        } else {
            BestParkingCard(best, nearest, best == cheapest || cheapest == null && options.size == 1, duration, vehicle.timeMs) { graph.navigateTo(best.zone) }
            if (options.size > 1) {
                SectionTitle("다른 주차장")
                options.drop(1).forEach { option ->
                    OptionRow(option, option == cheapest, vehicle.timeMs) { graph.navigateTo(option.zone) }
                }
            }
            PText("요금은 데모용 예시 데이터예요", PleosType.labelMedium, c.textTertiary)
        }
    }
}

private fun arrivalLabel(option: ParkingOption, now: Long): String =
    if (option.driveMin != null) "${hhmm(now + (option.totalMin * 60_000).toLong())} 도착" else "+${minutes(option.parkMin + option.walkMin)}분"

@Composable
private fun BestParkingCard(best: ParkingOption, nearest: ParkingOption?, cheapest: Boolean, duration: Int, now: Long, onGo: () -> Unit) {
    val c = Pleos.colors
    PCard(accent = c.informativeActive) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PText(best.zone.name, PleosType.titleSmall, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Badge("가장 빨리 도착", c.informativeActive)
            if (cheapest) Badge("가장 저렴", c.informativePositive)
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                if (best.driveMin != null) {
                    PText("목적지 도착", PleosType.labelMedium, c.textTertiary)
                    PText(hhmm(now + (best.totalMin * 60_000).toLong()), PleosType.headlineSmall)
                } else {
                    PText("주차장 도착 후 목적지까지", PleosType.labelMedium, c.textTertiary)
                    PText("${minutes(best.parkMin + best.walkMin)}분", PleosType.headlineSmall)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                PText("${durationLabel(duration)} 주차", PleosType.labelMedium, c.textTertiary)
                PText(best.fee?.let(::won) ?: "요금 정보 없음", PleosType.titleMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
        TimeBar(
            listOfNotNull(
                best.driveMin?.let { Segment("주행", it, DRIVE_COLOR) },
                Segment("자리 찾기", best.parkMin, c.informativePositive),
                Segment("도보", best.walkMin, c.surfaceAccent),
            ),
        )
        if (nearest != null && nearest != best) {
            Spacer(Modifier.height(20.dp))
            val faster = nearest.totalMin - best.totalMin
            val feeDiff = if (best.fee != null && nearest.fee != null) nearest.fee - best.fee else null
            val feeText = when {
                feeDiff == null || feeDiff == 0 -> ""
                feeDiff > 0 -> " · ${won(feeDiff)} 저렴해요"
                else -> " · ${won(-feeDiff)} 더 들어요"
            }
            PText("가장 가까운 ${nearest.zone.name}보다 ${minutes(faster)}분 빨라요$feeText", PleosType.bodyMediumStrong, c.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
        PButton("여기로 안내", modifier = Modifier.fillMaxWidth(), onClick = onGo)
        Spacer(Modifier.height(12.dp))
        PText("자리 찾기: ${confidence(best.prediction)}", PleosType.labelMedium, c.textTertiary)
    }
}

@Composable
private fun OptionRow(option: ParkingOption, cheapest: Boolean, now: Long, onGo: () -> Unit) {
    val c = Pleos.colors
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PText(option.zone.name, PleosType.bodyLargeStrong, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (cheapest) Badge("가장 저렴", c.informativePositive)
                }
                val arrival = option.driveMin?.let { "도착 ${hhmm(now + (option.totalMin * 60_000).toLong())} · " } ?: ""
                PText("${arrival}자리 찾기 ${minutes(option.parkMin)}분 · 도보 ${minutes(option.walkMin)}분", PleosType.bodySmall, c.textSecondary)
            }
            Spacer(Modifier.width(20.dp))
            PText(option.fee?.let(::won) ?: "-", PleosType.bodyLargeStrong)
            Spacer(Modifier.width(24.dp))
            PButton("안내", ButtonKind.BASIC, onClick = onGo)
        }
    }
}
