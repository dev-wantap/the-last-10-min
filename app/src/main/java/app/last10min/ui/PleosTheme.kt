package app.last10min.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.last10min.R

/**
 * Pleos Connect design tokens (document.pleos.ai design guide, Foundations > Colors / Typography).
 * Names mirror the official semantic tokens so the mapping stays reviewable against the guide.
 */
@Immutable
data class PleosColors(
    val backgroundPrimary: Color,
    val surfaceBasic: Color,
    val surfaceLow: Color,
    val surfaceHigh: Color,
    val surfaceAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val buttonBasicEnabled: Color,
    val buttonBasicPressed: Color,
    val buttonFilledEnabled: Color,
    val buttonFilledPressed: Color,
    val onButtonFilled: Color,
    val informativeActive: Color,
    val informativePositive: Color,
    val informativeNegative: Color,
    val line: Color,
    val shadow: Color,
)

private val BASIC_DARK_TEXT = Color(0xFF131417)

val LightColors = PleosColors(
    backgroundPrimary = Color(0xFFF7F8FA),
    surfaceBasic = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFF7F8FA),
    surfaceHigh = Color(0xFFEDEEF2),
    surfaceAccent = Color(0xFFE0E1E6),
    textPrimary = BASIC_DARK_TEXT,
    textSecondary = BASIC_DARK_TEXT.copy(alpha = 0.84f),
    textTertiary = BASIC_DARK_TEXT.copy(alpha = 0.64f),
    textQuaternary = BASIC_DARK_TEXT.copy(alpha = 0.32f),
    buttonBasicEnabled = BASIC_DARK_TEXT.copy(alpha = 0.05f),
    buttonBasicPressed = BASIC_DARK_TEXT.copy(alpha = 0.10f),
    buttonFilledEnabled = Color(0xFF44464E),
    buttonFilledPressed = Color(0xFF313236),
    onButtonFilled = Color(0xFFFFFFFF),
    informativeActive = Color(0xFF02C265),
    informativePositive = Color(0xFF0064FF),
    informativeNegative = Color(0xFFFE3D16),
    line = Color(0xFFEDEEF2),
    shadow = Color(0x14000000),
)

val DarkColors = PleosColors(
    backgroundPrimary = Color(0xFF1D1E21),
    surfaceBasic = Color(0xFF1D1E21),
    surfaceLow = Color(0xFF131417),
    surfaceHigh = Color(0xFF313236),
    surfaceAccent = Color(0xFF44464E),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.84f),
    textTertiary = Color.White.copy(alpha = 0.64f),
    textQuaternary = Color.White.copy(alpha = 0.32f),
    buttonBasicEnabled = Color.White.copy(alpha = 0.08f),
    buttonBasicPressed = Color.White.copy(alpha = 0.14f),
    buttonFilledEnabled = Color(0xFFE0E1E6),
    buttonFilledPressed = Color(0xFFC7C9CE),
    onButtonFilled = BASIC_DARK_TEXT,
    informativeActive = Color(0xFF00E074),
    informativePositive = Color(0xFF0082FF),
    informativeNegative = Color(0xFFFF4C28),
    line = Color(0xFF313236),
    shadow = Color(0x29000000),
)

@OptIn(ExperimentalTextApi::class)
private fun astaSans(weight: FontWeight) =
    Font(R.font.asta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val AstaSans = FontFamily(
    astaSans(FontWeight.Normal),
    astaSans(FontWeight.SemiBold),
    astaSans(FontWeight.Bold),
    astaSans(FontWeight.ExtraBold),
)

/** Pleos type scale: size / line height in dp-equivalent sp at the 160 dpi reference density. */
object PleosType {
    private fun style(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontFamily = AstaSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    val headlineSmall = style(48, 64, FontWeight.ExtraBold)
    val titleMedium = style(36, 44, FontWeight.Bold)
    val titleSmall = style(32, 40, FontWeight.Bold)
    val bodyLargeStrong = style(30, 38, FontWeight.SemiBold)
    val bodyMedium = style(28, 36, FontWeight.Normal)
    val bodyMediumStrong = style(28, 36, FontWeight.SemiBold)
    val bodySmall = style(26, 34, FontWeight.Normal)
    val labelMedium = style(24, 28, FontWeight.Normal)
    val labelSmall = style(20, 24, FontWeight.Normal)
}

val LocalPleosColors = staticCompositionLocalOf { LightColors }

object Pleos {
    val colors: PleosColors @Composable get() = LocalPleosColors.current
}

@Composable
fun PleosTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPleosColors provides if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
