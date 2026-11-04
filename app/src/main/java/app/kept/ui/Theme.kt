package app.kept.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kept.R

/** Kept: near-black ink, one warm amber accent, one coral reserved for "overdue". */
object C {
    val Bg = Color(0xFF0A0E14)
    val Surface = Color(0xFF111823)
    val Raised = Color(0xFF182233)
    val Line = Color(0xFF243044)
    val Text = Color(0xFFE8EEF6)
    val Muted = Color(0xFF8FA0B8)   // ~7:1 on Bg
    val Accent = Color(0xFF5EC8F2)  // ice cyan: reminders and actions
    val Violet = Color(0xFFB8A2FF)  // soft violet: birthdays only
    val Danger = Color(0xFFFF7D8E)  // cool coral: overdue/delete only
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun grotesk(w: Int) = Font(
    R.font.space_grotesk,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Grotesk = FontFamily(grotesk(300), grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** Type scale. Big steps on purpose: display > title > heading > body > label. */
object T {
    val Display = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp)
    val Numeral = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Light, fontSize = 34.sp, lineHeight = 36.sp)
    val Title = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.3).sp)
    val Heading = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp)
    val Body = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val Label = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp)
    val Caps = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp)
}

private val scheme = darkColorScheme(
    primary = C.Accent, onPrimary = C.Bg,
    secondary = C.Accent, onSecondary = C.Bg,
    background = C.Bg, onBackground = C.Text,
    surface = C.Surface, onSurface = C.Text,
    surfaceVariant = C.Raised, onSurfaceVariant = C.Muted,
    surfaceContainer = C.Raised, surfaceContainerHigh = C.Raised, surfaceContainerHighest = C.Line,
    surfaceContainerLow = C.Surface, surfaceContainerLowest = C.Bg,
    outline = C.Muted, outlineVariant = C.Line,
    error = C.Danger, onError = C.Bg,
)

private val typography = Typography().let { d ->
    fun TextStyle.g() = copy(fontFamily = Grotesk)
    d.copy(
        displayLarge = d.displayLarge.g(), displayMedium = d.displayMedium.g(), displaySmall = d.displaySmall.g(),
        headlineLarge = d.headlineLarge.g(), headlineMedium = d.headlineMedium.g(), headlineSmall = d.headlineSmall.g(),
        titleLarge = d.titleLarge.g(), titleMedium = d.titleMedium.g(), titleSmall = d.titleSmall.g(),
        bodyLarge = d.bodyLarge.g(), bodyMedium = d.bodyMedium.g(), bodySmall = d.bodySmall.g(),
        labelLarge = d.labelLarge.g(), labelMedium = d.labelMedium.g(), labelSmall = d.labelSmall.g(),
    )
}

@Composable
fun KeptTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
