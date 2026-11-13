package app.last10min.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.last10min.data.Mercator
import app.last10min.data.RoadNetwork
import app.last10min.engine.LatLng
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class MapStyle { DESTINATION, BEST, OPTION, OFFICIAL, HOTSPOT, VEHICLE }

data class MapArea(val polygon: List<LatLng>, val style: MapStyle)

data class MapPin(val position: LatLng, val label: String, val style: MapStyle)

data class MapSpec(val areas: List<MapArea>, val pins: List<MapPin>, val focus: List<LatLng>, val focusKey: String)

private data class MapColors(
    val background: Color,
    val road: Color,
    val casing: Color,
    val label: Color,
    val ink: Color,
    val best: Color,
    val option: Color,
    val official: Color,
    val hotspot: Color,
)

private const val MIN_SPAN = 1.6e-5
private val ROAD_WIDTH_DP = floatArrayOf(14f, 12f, 10f, 8f, 5.5f, 3f)
private val FALLBACK_CENTER = LatLng(37.4985, 127.0281)

@Composable
fun MapPanel(spec: MapSpec, modifier: Modifier = Modifier) {
    val c = Pleos.colors
    val context = LocalContext.current
    val network = remember { RoadNetwork.get(context) }
    val measurer = rememberTextMeasurer()
    var zoom by remember(spec.focusKey) { mutableFloatStateOf(1f) }
    var pan by remember(spec.focusKey) { mutableStateOf(Offset.Zero) }
    val colors = MapColors(
        background = c.surfaceHigh,
        road = c.surfaceBasic,
        casing = c.surfaceAccent,
        label = c.textTertiary,
        ink = Color(0xFF131417),
        best = c.informativeActive,
        option = Color(0xFF686A72),
        official = c.informativePositive,
        hotspot = c.informativeNegative,
    )
    val shape = RoundedCornerShape(24.dp)
    Canvas(
        modifier
            .shadow(12.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(colors.background)
            .pointerInput(spec.focusKey) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(0.5f, 5f)
                    pan += panChange
                }
            },
    ) {
        val view = Viewport.fit(spec.focus, size, zoom, pan)
        drawRoads(network, view, colors, measurer, sqrt(zoom))
        spec.areas.forEach { drawArea(it, view, colors) }
        spec.pins.sortedBy { it.style == MapStyle.BEST }.forEach { drawPin(it, view, colors, measurer) }
        drawText(
            measurer,
            network.attribution,
            topLeft = Offset(16.dp.toPx(), size.height - 30.dp.toPx()),
            style = TextStyle(fontFamily = AstaSans, fontSize = 14.sp, color = colors.label),
        )
    }
}

private class Viewport(val cx: Double, val cy: Double, val scale: Double, val size: Size, val pan: Offset) {
    fun x(mx: Double): Float = ((mx - cx) * scale + size.width / 2 + pan.x).toFloat()
    fun y(my: Double): Float = ((my - cy) * scale + size.height / 2 + pan.y).toFloat()
    fun of(p: LatLng): Offset = Offset(x(Mercator.x(p.lon)), y(Mercator.y(p.lat)))

    companion object {
        fun fit(points: List<LatLng>, size: Size, zoom: Float, pan: Offset): Viewport {
            val focus = points.ifEmpty { listOf(FALLBACK_CENTER) }
            val xs = focus.map { Mercator.x(it.lon) }
            val ys = focus.map { Mercator.y(it.lat) }
            val spanX = max(xs.max() - xs.min(), MIN_SPAN)
            val spanY = max(ys.max() - ys.min(), MIN_SPAN)
            val base = min(size.width / (spanX * 1.6), size.height / (spanY * 1.9))
            val centerY = (ys.max() + ys.min()) / 2 - spanY * 0.08
            return Viewport((xs.max() + xs.min()) / 2, centerY, base * zoom, size, pan)
        }
    }
}

private fun DrawScope.drawRoads(network: RoadNetwork, view: Viewport, colors: MapColors, measurer: TextMeasurer, widthScale: Float) {
    val margin = 80f
    val bounds = Rect(-margin, -margin, size.width + margin, size.height + margin)
    val visible = network.roads.mapNotNull { road ->
        val path = Path()
        var inside = false
        for (i in road.x.indices) {
            val px = view.x(road.x[i])
            val py = view.y(road.y[i])
            if (!inside && bounds.contains(Offset(px, py))) inside = true
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        if (inside) Triple(road, path, ROAD_WIDTH_DP[road.rank.coerceIn(0, ROAD_WIDTH_DP.lastIndex)].dp.toPx() * widthScale) else null
    }.sortedByDescending { it.first.rank }
    visible.filter { it.first.rank <= 4 }.forEach { (_, path, width) ->
        drawPath(path, colors.casing, style = Stroke(width + 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    visible.forEach { (_, path, width) ->
        drawPath(path, colors.road, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val placed = mutableListOf<Rect>()
    val labelStyle = TextStyle(fontFamily = AstaSans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.label)
    visible.filter { it.first.rank <= 1 && it.first.name.isNotBlank() }
        .distinctBy { it.first.name }
        .forEach { (road, _, _) ->
            val mid = road.x.size / 2
            val at = Offset(view.x(road.x[mid]), view.y(road.y[mid]))
            val layout = measurer.measure(road.name, labelStyle)
            val rect = Rect(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f, at.x + layout.size.width / 2f, at.y + layout.size.height / 2f)
            if (rect.left < 0 || rect.right > size.width || rect.top < 0 || rect.bottom > size.height) return@forEach
            if (placed.any { it.overlaps(rect) }) return@forEach
            placed += rect
            drawRoundRect(colors.road.copy(alpha = 0.85f), rect.topLeft - Offset(6f, 2f), Size(rect.width + 12f, rect.height + 4f), CornerRadius(8f))
            drawText(layout, topLeft = rect.topLeft)
        }
}

private fun DrawScope.drawArea(area: MapArea, view: Viewport, colors: MapColors) {
    if (area.polygon.size < 3) return
    val color = styleColor(area.style, colors)
    val path = Path().apply {
        area.polygon.forEachIndexed { i, p -> view.of(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        close()
    }
    drawPath(path, color.copy(alpha = 0.28f))
    drawPath(path, color, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
}

private fun DrawScope.drawPin(pin: MapPin, view: Viewport, colors: MapColors, measurer: TextMeasurer) {
    val at = view.of(pin.position)
    if (pin.style == MapStyle.VEHICLE) {
        drawCircle(Color.White, 13.dp.toPx(), at)
        drawCircle(colors.official, 9.dp.toPx(), at)
        return
    }
    val background = when (pin.style) {
        MapStyle.OPTION -> Color.White
        else -> styleColor(pin.style, colors)
    }
    val foreground = if (pin.style == MapStyle.OPTION) colors.ink else Color.White
    val layout = measurer.measure(pin.label, TextStyle(fontFamily = AstaSans, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = foreground))
    val padH = 14.dp.toPx()
    val padV = 8.dp.toPx()
    val tail = 9.dp.toPx()
    val width = layout.size.width + padH * 2
    val height = layout.size.height + padV * 2
    val topLeft = Offset(at.x - width / 2, at.y - tail - height)
    val bubble = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(topLeft, Size(width, height)), CornerRadius(min(height / 2, 18.dp.toPx()))))
        moveTo(at.x - tail, topLeft.y + height - 1)
        lineTo(at.x, at.y)
        lineTo(at.x + tail, topLeft.y + height - 1)
        close()
    }
    drawPath(bubble, Color.Black.copy(alpha = 0.12f), style = Stroke(6.dp.toPx()))
    drawPath(bubble, background)
    if (pin.style == MapStyle.OPTION) drawPath(bubble, colors.option, style = Stroke(2.dp.toPx()))
    drawText(layout, topLeft = topLeft + Offset(padH, padV))
}

private fun styleColor(style: MapStyle, colors: MapColors): Color = when (style) {
    MapStyle.DESTINATION, MapStyle.VEHICLE -> colors.ink
    MapStyle.BEST -> colors.best
    MapStyle.OPTION -> colors.option
    MapStyle.OFFICIAL -> colors.official
    MapStyle.HOTSPOT -> colors.hotspot
}
