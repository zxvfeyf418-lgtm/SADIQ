package dev.shizzi.ui.twon5a

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val IntroRed = Color(0xFFE0382B)

private const val GREETING = "اهلاً بيك بتطبيق"
private const val NAME = "أبـو الأكبر"
private const val BRAND = "2n5a"

private const val GREETING_STEP_MILLIS = 70L
private const val NAME_STEP_MILLIS = 110L

/**
 * The first thing shown when the app opens: the greeting types itself out, the
 * name lands with a spring and a red glow, then the Instagram mark and handle
 * rise in. Tapping anywhere skips it.
 */
@Composable
fun WelcomeIntro(onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)

    var greetingChars by remember { mutableIntStateOf(0) }
    var nameChars by remember { mutableIntStateOf(0) }
    val nameScale = remember { Animatable(0.7f) }
    val glow = remember { Animatable(0f) }
    val brandAlpha = remember { Animatable(0f) }
    val brandRise = remember { Animatable(28f) }

    LaunchedEffect(Unit) {
        delay(350)

        for (count in 1..GREETING.length) {
            greetingChars = count
            delay(GREETING_STEP_MILLIS)
        }
        delay(220)

        coroutineScope {
            launch {
                nameScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                )
            }
            launch { glow.animateTo(1f, tween(900)) }

            for (count in 1..NAME.length) {
                nameChars = count
                delay(NAME_STEP_MILLIS)
            }
        }
        delay(180)

        coroutineScope {
            launch { brandAlpha.animateTo(1f, tween(500)) }
            launch { brandRise.animateTo(0f, tween(500)) }
        }

        delay(1400)
        finish()
    }

    TwoN5aRtl {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF1A0705), Color.Black)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TypedText(
                    text = GREETING,
                    visibleChars = greetingChars,
                    color = Color.White,
                    style = TextStyle(fontFamily = AppFontHeavy, fontWeight = FontWeight(800), fontSize = 32.sp),
                )

                Spacer(Modifier.height(10.dp))

                TypedText(
                    text = NAME,
                    visibleChars = nameChars,
                    color = IntroRed,
                    style = TextStyle(
                        fontFamily = AppFontHeavy,
                        fontWeight = FontWeight(900),
                        fontSize = 60.sp,
                        shadow = Shadow(color = IntroRed.copy(alpha = 0.7f), offset = Offset.Zero, blurRadius = 36f * glow.value),
                    ),
                    modifier = Modifier.graphicsLayer {
                        scaleX = nameScale.value
                        scaleY = nameScale.value
                    },
                )

                Spacer(Modifier.height(28.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .alpha(brandAlpha.value)
                        .graphicsLayer { translationY = brandRise.value },
                ) {
                    InstagramMark(Modifier.size(48.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(
                        BRAND,
                        style = TextStyle(fontFamily = AppFontHeavy, fontWeight = FontWeight(900), fontSize = 52.sp),
                        color = IntroRed,
                    )
                }
            }
        }
    }
}

/**
 * Reveals [text] one character at a time. The unrevealed rest stays in the layout,
 * transparent, so nothing shifts while typing and each letter already has its final
 * joined Arabic shape.
 */
@Composable
private fun TypedText(
    text: String,
    visibleChars: Int,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val shown = buildAnnotatedString {
        append(text.take(visibleChars))
        withStyle(SpanStyle(color = Color.Transparent, shadow = Shadow(Color.Transparent))) {
            append(text.drop(visibleChars))
        }
    }

    Text(text = shown, style = style, color = color, textAlign = TextAlign.Center, modifier = modifier)
}

/** The Instagram camera mark: a rounded square, a lens ring and a flash dot. */
@Composable
private fun InstagramMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        val side = size.minDimension

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(side - stroke, side - stroke),
            cornerRadius = CornerRadius(side * 0.3f),
            style = Stroke(width = stroke),
        )
        drawCircle(color = Color.White, radius = side * 0.22f, style = Stroke(width = stroke))
        drawCircle(color = Color.White, radius = stroke * 0.7f, center = Offset(side * 0.76f, side * 0.24f))
    }
}
