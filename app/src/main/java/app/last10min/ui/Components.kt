package app.last10min.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PText(
    text: String,
    style: TextStyle = PleosType.bodyMedium,
    color: Color = Pleos.colors.textPrimary,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) = Text(text, style = style, color = color, modifier = modifier, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun <T> PDropdown(label: String, items: List<T>, selected: T, text: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val c = Pleos.colors
    var open by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.onSizeChanged { widthPx = it.width }) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(6.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
                .background(c.surfaceBasic, shape)
                .clickable { open = true }
                .heightIn(min = 88.dp)
                .padding(horizontal = 28.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                PText(label, PleosType.labelMedium, c.textTertiary)
                PText(text(selected), PleosType.bodyLargeStrong, maxLines = 1)
            }
            Spacer(Modifier.width(16.dp))
            PText("▾", PleosType.titleSmall, c.textTertiary)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(with(LocalDensity.current) { widthPx.toDp() }),
            shape = RoundedCornerShape(20.dp),
            containerColor = c.surfaceBasic,
        ) {
            items.forEach { item ->
                val isSelected = item == selected
                DropdownMenuItem(
                    text = { PText(text(item), if (isSelected) PleosType.bodyMediumStrong else PleosType.bodyMedium, if (isSelected) c.informativePositive else c.textPrimary) },
                    onClick = { open = false; onSelect(item) },
                    modifier = Modifier.heightIn(min = 76.dp),
                )
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color) = PText(
    text,
    PleosType.labelMedium,
    color,
    Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
)

data class Segment(val label: String, val minutes: Double, val color: Color)

@Composable
fun TimeBar(segments: List<Segment>) {
    val shown = segments.filter { it.minutes >= 0.05 }
    val total = shown.sumOf { it.minutes }.coerceAtLeast(0.1)
    Row(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp)), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { s -> Box(Modifier.weight((s.minutes / total).toFloat().coerceAtLeast(0.05f)).fillMaxHeight().background(s.color)) }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        shown.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(s.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                PText("${s.label} ${minutes(s.minutes)}분", PleosType.labelMedium, Pleos.colors.textSecondary)
            }
        }
    }
}

/** Surface card on background_primary with Pleos shadow level 1 (y 6, blur 24, 8%). */
@Composable
fun PCard(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Pleos.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .background(c.surfaceBasic, shape)
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        if (accent != null) {
            Box(Modifier.width(48.dp).height(6.dp).background(accent, RoundedCornerShape(3.dp)))
            Spacer(Modifier.height(16.dp))
        }
        content()
    }
}

enum class ButtonKind { BASIC, FILLED }

/** Pleos basic/filled button; touch feedback is the guide's shrink effect: scale 95% + pressed background. */
@Composable
fun PButton(text: String, kind: ButtonKind = ButtonKind.FILLED, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Pleos.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = when (kind) {
        ButtonKind.BASIC -> if (pressed) c.buttonBasicPressed else c.buttonBasicEnabled
        ButtonKind.FILLED -> if (pressed) c.buttonFilledPressed else c.buttonFilledEnabled
    }
    Box(
        modifier
            .scale(if (pressed) 0.95f else 1f)
            .background(bg, RoundedCornerShape(20.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 36.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        PText(text, PleosType.bodyMediumStrong, if (kind == ButtonKind.FILLED) c.onButtonFilled else c.textPrimary)
    }
}

/** Tabs, "Box" type, fitted: selected tab is a raised surface inside a surface_high container. */
@Composable
fun <T> PTabs(items: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val c = Pleos.colors
    Row(
        modifier.background(c.surfaceHigh, RoundedCornerShape(24.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .weight(1f)
                    .let { if (isSelected) it.shadow(6.dp, shape, ambientColor = c.shadow, spotColor = c.shadow).background(c.surfaceBasic, shape) else it }
                    .clickable { onSelect(item) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                PText(label(item), if (isSelected) PleosType.bodyMediumStrong else PleosType.bodyMedium, if (isSelected) c.textPrimary else c.textTertiary)
            }
        }
    }
}

/** Page Navigation (Main type): app icon + title, with suffix content on the right. */
@Composable
fun PPageNavigation(title: String, suffix: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon()
        Spacer(Modifier.width(20.dp))
        PText(title, PleosType.titleMedium, modifier = Modifier.weight(1f))
        suffix()
    }
}

@Composable
private fun AppIcon() {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .size(64.dp)
            .shadow(8.dp, shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF3D8BFF), Color(0xFF0064FF))), shape),
        contentAlignment = Alignment.Center,
    ) { PText("10", PleosType.titleSmall, Color.White) }
}

enum class Tone { NEUTRAL, ACTIVE, POSITIVE, NEGATIVE }

@Composable
fun toneColor(tone: Tone): Color = when (tone) {
    Tone.NEUTRAL -> Pleos.colors.textTertiary
    Tone.ACTIVE -> Pleos.colors.informativeActive
    Tone.POSITIVE -> Pleos.colors.informativePositive
    Tone.NEGATIVE -> Pleos.colors.informativeNegative
}

@Composable
fun StatusPill(text: String, tone: Tone = Tone.NEUTRAL) {
    val c = Pleos.colors
    Row(
        Modifier.background(c.surfaceBasic, RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(toneColor(tone), CircleShape))
        Spacer(Modifier.width(10.dp))
        PText(text, PleosType.labelMedium, c.textSecondary)
    }
}

@Composable
fun SectionTitle(text: String) = PText(text, PleosType.titleSmall, modifier = Modifier.padding(top = 8.dp))

fun minutes(v: Double): String = "%.0f".format(v.coerceAtLeast(0.0))

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun meters(m: Double): String = if (m < 1000) "${m.toInt()}m" else "%.1fkm".format(m / 1000)

fun won(amount: Int): String = "%,d원".format(amount)

fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(ms))

fun durationLabel(min: Int): String = when {
    min < 60 -> "${min}분"
    min % 60 == 0 -> "${min / 60}시간"
    else -> "${min / 60}시간 ${min % 60}분"
}
