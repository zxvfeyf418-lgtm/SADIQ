package dev.shizzi.ui.twon5a

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shizzi.SessionUiState
import dev.shizzi.UiStatus
import dev.shizzi.vpn.ProxyConfig
import dev.shizzi.vpn.VpnState
import dev.shizzi.vpn.isActive
import kotlinx.coroutines.delay

data class TwoN5aHomeActions(
    val onToggleVpn: () -> Unit,
    val onToggleSharing: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenEasterEgg: () -> Unit,
)

@Composable
fun TwoN5aHome(
    session: SessionUiState,
    vpn: VpnState,
    selected: ProxyConfig?,
    actions: TwoN5aHomeActions,
) {
    TwoN5aRtl {
        val isSharing = session.status == UiStatus.CONNECTED
        val isSharingBusy = session.status == UiStatus.LOADING
        val isVpnOn = vpn.isActive

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TwoN5aColors.Background)
                .systemBarsPadding(),
        ) {
            TopBar(selected, vpn, actions.onOpenSettings)

            SharingToast(isSharing = isSharing, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))

            SignalRing(
                isLive = isSharing || vpn is VpnState.Connected,
                modifier = Modifier.align(Alignment.Center).size(260.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (vpn is VpnState.Failed) {
                    Text(
                        text = vpn.message,
                        style = TwoN5aType.caption,
                        color = TwoN5aColors.Bad,
                        textAlign = TextAlign.Center,
                    )
                }

                PrimaryButton(
                    text = when {
                        vpn is VpnState.Connecting -> "جارٍ الاتصال…"
                        isVpnOn -> "إيقاف VPN"
                        else -> "تشغيل VPN"
                    },
                    fill = TwoN5aColors.MagentaStrong,
                    content = Color.Black,
                    enabled = isVpnOn || selected != null,
                    onClick = actions.onToggleVpn,
                )

                SecondaryButton(
                    text = when {
                        isSharingBusy -> "جارٍ التجهيز…"
                        isSharing -> "إيقاف مشاركة الشبكة"
                        else -> "بدء مشاركة الشبكة"
                    },
                    enabled = isSharing || session.canStart,
                    onClick = actions.onToggleSharing,
                )

                Footer(session = session, vpn = vpn, onVersionClick = actions.onOpenEasterEgg)
            }
        }
    }
}

@Composable
private fun TopBar(selected: ProxyConfig?, vpn: VpnState, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // In RTL the first child sits on the right: server chip right, gear left.
        if (selected != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(TwoN5aColors.Card)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (vpn is VpnState.Connected) TwoN5aColors.Good else TwoN5aColors.TextMuted),
                )
                Spacer(Modifier.size(8.dp))
                Text(selected.name, style = TwoN5aType.caption, color = TwoN5aColors.TextMuted, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "الإعدادات",
            tint = Color(0xFFB9A7D6),
            modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onOpenSettings).padding(4.dp),
        )
    }
}

/** The pill that announces hotspot sharing, then gets out of the way. */
@Composable
private fun SharingToast(isSharing: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(isSharing) {
        if (isSharing) {
            visible = true
            delay(3500)
        }
        visible = false
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF17151D))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("تم تفعيل مشاركة نقطة الاتصال عبر 2n5a", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
            Spacer(Modifier.size(10.dp))
            Box(Modifier.size(width = 6.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)).background(TwoN5aColors.Magenta))
        }
    }
}

/** Two broken arcs and a dot, the broadcast mark from the reference design. */
@Composable
private fun SignalRing(isLive: Boolean, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "ring")
    val breathing by pulse.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "breathing",
    )
    val alpha = if (isLive) breathing else 0.45f

    Canvas(modifier.alpha(alpha)) {
        val center = Offset(size.width / 2, size.height / 2)
        val stroke = size.minDimension * 0.095f
        val gapStart = 112f   // degrees: the opening points straight down
        val sweep = 316f

        fun arc(diameterFraction: Float, color: Color, width: Float) {
            val d = size.minDimension * diameterFraction
            drawArc(
                color = color,
                startAngle = gapStart,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - d / 2, center.y - d / 2),
                size = Size(d, d),
                style = Stroke(width = width, cap = StrokeCap.Butt),
            )
        }

        arc(0.98f - 0.095f, TwoN5aColors.RingOuter, stroke)
        arc(0.66f, TwoN5aColors.RingInner, stroke)
        drawCircle(TwoN5aColors.RingInner, radius = size.minDimension * 0.115f, center = center)
    }
}

@Composable
private fun PrimaryButton(text: String, fill: Color, content: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(26.dp))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TwoN5aType.button, color = content)
    }
}

@Composable
private fun SecondaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(24.dp))
            .background(TwoN5aColors.VioletButton)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TwoN5aType.button.copy(fontSize = 18.sp), color = Color.White)
    }
}

@Composable
private fun Footer(session: SessionUiState, vpn: VpnState, onVersionClick: () -> Unit) {
    val status = when {
        session.status == UiStatus.CONNECTED -> "SHARING ACTIVE"
        vpn is VpnState.Connected -> "VPN ACTIVE"
        vpn is VpnState.Connecting || session.status == UiStatus.LOADING -> "CONNECTING"
        else -> "IDLE"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(
            "V1.0.0",
            style = TwoN5aType.caption.copy(letterSpacing = 2.sp),
            color = TwoN5aColors.TextMuted.copy(alpha = 0.6f),
            modifier = Modifier.clickable(onClick = onVersionClick),
        )
        Text("   |   ", style = TwoN5aType.caption, color = TwoN5aColors.TextMuted.copy(alpha = 0.4f))
        Text(
            "STATUS $status",
            style = TwoN5aType.caption.copy(letterSpacing = 2.sp),
            color = TwoN5aColors.TextMuted.copy(alpha = 0.6f),
        )
    }
}
