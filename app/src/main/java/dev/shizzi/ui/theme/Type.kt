package dev.shizzi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.shizzi.ui.twon5a.AppFont

private val Mono = AppFont
private val Display = AppFont
private val Sans = AppFont
private val GoogleSans = AppFont

@Immutable
data class ShizziTypography(
    val display: TextStyle,
    val heading: TextStyle,
    val subheading: TextStyle,
    val title: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val log: TextStyle,
    val body: TextStyle,
)

val Typography = ShizziTypography(

    display = TextStyle(
        fontFamily = Display,
        fontSize = 28.sp,
        fontWeight = FontWeight.W700,
    ),

    heading = TextStyle(
        fontFamily = Display,
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
    ),

    subheading = TextStyle(
        fontFamily = Display,
        fontSize = 17.sp,
        fontWeight = FontWeight.W500,
    ),

    title = TextStyle(fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.W700),

    label = TextStyle(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.W500),

    caption = TextStyle(
        fontFamily = Mono,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
    ),

    log = TextStyle(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.W400),
    body = TextStyle(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.W400),
)

val ExpressiveTypography = ShizziTypography(

    display = TextStyle(
        fontFamily = GoogleSans,
        fontSize = 32.sp,
        fontWeight = FontWeight.W700,
    ),

    heading = TextStyle(
        fontFamily = GoogleSans,
        fontSize = 22.sp,
        fontWeight = FontWeight.W700,
    ),

    subheading = TextStyle(
        fontFamily = GoogleSans,
        fontSize = 17.sp,
        fontWeight = FontWeight.W500,
    ),

    title = TextStyle(
        fontFamily = GoogleSans,
        fontSize = 16.sp,
        fontWeight = FontWeight.W500,
    ),

    label = TextStyle(
        fontFamily = GoogleSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.W500,
    ),

    caption = TextStyle(
        fontFamily = GoogleSans,
        fontSize = 12.sp,
        fontWeight = FontWeight.W500,
    ),

    log = TextStyle(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.W400),
    body = TextStyle(
        fontFamily = GoogleSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.W400,
    ),
)
