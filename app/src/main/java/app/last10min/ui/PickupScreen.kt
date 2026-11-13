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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.last10min.AppGraph
import app.last10min.engine.ZoneType
import app.last10min.plan.Planner

private val DRIVE_COLOR = Color(0xFFA4A8AE)
private const val MIN_LEAD_MS = 5 * 60_000L

@Composable
fun PickupScreen(graph: AppGraph) {
    val c = Pleos.colors
    val zones by graph.zones.zones.collectAsState()
    val vehicle by graph.vehicle.collectAsState()
    val pickup by graph.pickup.collectAsState()
    val predict = rememberPredict(graph)
    var zoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenArrival by rememberSaveable { mutableStateOf<Long?>(null) }
    var reminderAt by remember { mutableStateOf<Long?>(null) }

    val official = zones.filter { it.type == ZoneType.PICKUP_OFFICIAL }
    val target = official.firstOrNull { it.id == zoneId } ?: official.firstOrNull()
    if (target == null) {
        PText("승하차구역 정보가 없어요", PleosType.bodyMedium, c.textTertiary)
        return
    }
    val now = vehicle.timeMs
    val choices = remember(now / 60_000) { Planner.arrivalChoices(now) }
    val planFor = { at: Long -> Planner.pickupPlan(target, at, now, vehicle.location, AppGraph.OFFSETS, predict) }
    val arrival = chosenArrival?.takeIf { it > now }
        ?: choices.firstOrNull { planFor(it).departAtMs > now + MIN_LEAD_MS }
        ?: choices.last()
    val plan = planFor(arrival)
    val late = plan.departAtMs <= now
    val stops = Planner.legalStops(zones, target.center).take(3)
    val hotspots = Planner.hotspotsNear(zones, target.center)

    val map = MapSpec(
        areas = listOf(MapArea(target.polygon, MapStyle.OFFICIAL)) +
            stops.filter { it.first.type == ZoneType.PARKING }.map { MapArea(it.first.polygon, MapStyle.OPTION) } +
            hotspots.map { MapArea(it.first.polygon, MapStyle.HOTSPOT) },
        pins = hotspots.map { MapPin(it.first.center, "혼잡 지점", MapStyle.HOTSPOT) } +
            listOf(MapPin(target.center, "승하차구역 · 5분 정차", MapStyle.OFFICIAL)) +
            listOfNotNull(vehicle.location?.let { MapPin(it, "내 차", MapStyle.VEHICLE) }),
        focus = listOf(target.center) + stops.map { it.first.center } + hotspots.map { it.first.center },
        focusKey = target.id,
    )

    MapLayout(
        controls = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PDropdown("목적지", official, target, { it.name }, Modifier.weight(1f)) { zoneId = it.id; chosenArrival = null }
                Spacer(Modifier.width(24.dp))
                val items = if (arrival in choices) choices else listOf(arrival) + choices
                PDropdown("도착 시간", items, arrival, ::hhmm, Modifier.width(300.dp)) { chosenArrival = it }
            }
        },
        map = map,
    ) {
        if (pickup.stopped) {
            PCard(accent = c.informativePositive) {
                PText("승하차 중", PleosType.titleSmall)
                PText(pickup.zone?.name ?: "등록된 구역 밖", PleosType.bodyMedium, c.textSecondary)
            }
        }
        PCard(accent = if (late) c.informativeNegative else c.informativeActive) {
            PText(if (late) "지금 바로 출발하세요" else "${hhmm(plan.departAtMs)}에 출발하세요", PleosType.titleMedium)
            Spacer(Modifier.height(6.dp))
            PText("${hhmm(arrival)}에 ${target.name}에서 태우려면", PleosType.bodyMedium, c.textSecondary)
            if (late) PText("지금 출발해도 약 ${minutes((now - plan.departAtMs) / 60_000.0)}분 늦어요", PleosType.bodyMediumStrong, c.informativeNegative)
            Spacer(Modifier.height(24.dp))
            TimeBar(
                listOfNotNull(
                    plan.driveMin?.let { Segment("주행", it, DRIVE_COLOR) },
                    Segment("승하차구역 진입·대기", plan.queueMin, c.informativePositive),
                ),
            )
            if (plan.driveMin == null) PText("현재 위치를 받으면 주행 시간도 계산해요", PleosType.labelMedium, c.textTertiary)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val remind = reminderAt
                when {
                    remind != null && now >= remind -> PButton("출발할 시간이에요 · 안내 시작") { reminderAt = null; graph.navigateTo(target) }
                    remind != null -> {
                        PText("${hhmm(remind)} 출발 알림 · ${clock(remind - now)} 남음", PleosType.bodyMediumStrong, c.textSecondary)
                        PButton("취소", ButtonKind.BASIC) { reminderAt = null }
                    }
                    late -> PButton("지금 안내 시작") { graph.navigateTo(target) }
                    else -> {
                        PButton("${hhmm(plan.departAtMs)} 출발 알림") {
                            reminderAt = plan.departAtMs
                            graph.record("출발 알림 예약: ${hhmm(plan.departAtMs)} · ${target.name}")
                        }
                        PButton("지금 안내", ButtonKind.BASIC) { graph.navigateTo(target) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PText("진입·대기: ${confidence(plan.prediction)}", PleosType.labelMedium, c.textTertiary)
        }

        SectionTitle("정차할 수 있는 곳")
        stops.forEach { (zone, distance) ->
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        PText(zone.name, PleosType.bodyLargeStrong, maxLines = 1)
                        val kind = if (zone.type == ZoneType.PARKING) "주차장" else "공식 승하차구역 · 5분 이내 정차"
                        PText("$kind · ${meters(distance)}", PleosType.bodySmall, c.textSecondary)
                    }
                    PButton("안내", ButtonKind.BASIC) { graph.navigateTo(zone) }
                }
            }
        }
        if (hotspots.isNotEmpty()) {
            PCard(accent = c.informativeNegative) {
                PText("근처 승하차 혼잡 지점 ${hotspots.size}곳", PleosType.bodyLargeStrong)
                PText("승하차구역이 없어 도로변 정차가 몰리는 곳이에요. 정차 위치로 안내하지 않아요.", PleosType.bodySmall, c.textSecondary)
            }
        }
        PText("정차 위치는 공식 승하차구역과 주차장만 안내해요. 혼잡 지점은 지자체의 승하차구역 설치 근거로 쓰여요.", PleosType.labelMedium, c.textTertiary)
    }
}
