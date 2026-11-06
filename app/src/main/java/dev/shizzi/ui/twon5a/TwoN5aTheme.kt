package dev.shizzi.ui.twon5a

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import dev.shizzi.R

/** Palette from the 2n5a reference design: dark premium, magenta + violet. */
object TwoN5aColors {
    val Background = Color(0xFF0D0E12)
    val Card = Color(0xFF15131C)
    val CardBorder = Color(0xFF2B2833)
    val Field = Color(0xFF0A0A0D)
    val Magenta = Color(0xFFE040FB)
    val MagentaStrong = Color(0xFFE62EC4)
    val Violet = Color(0xFF7C4DFF)
    val VioletButton = Color(0xFF9B51EC)
    val RingOuter = Color(0xFF8250C9)
    val RingInner = Color(0xFFC427A5)
    val TextPrimary = Color(0xFFF2F0F7)
    val TextMuted = Color(0xFF8E8A9A)
    val Good = Color(0xFF5FD39A)
    val Bad = Color(0xFFFF6B7A)
    val SelectedFill = Color(0xFF1D1428)
}

@OptIn(ExperimentalTextApi::class)
private fun cairo(weight: Int) = Font(
    R.font.cairo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/**
 * Cairo (SIL OFL) ships with the app. To switch to another Arabic family such as
 * Thmanyah, drop the .ttf files into res/font and point this one declaration at
 * them: nothing else references a font directly.
 */
val AppFont = FontFamily(cairo(400), cairo(600), cairo(700))

/** Heavy weights for the welcome screen. */
val AppFontHeavy = FontFamily(cairo(800), cairo(900))

object TwoN5aType {
    val title = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val body = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val bodyStrong = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val caption = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val button = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
}

/** Arabic UI: lay out right-to-left regardless of the device locale. */
@Composable
fun TwoN5aRtl(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
}
