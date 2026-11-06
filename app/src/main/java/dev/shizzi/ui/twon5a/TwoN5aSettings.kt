package dev.shizzi.ui.twon5a

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.shizzi.ShizukuState
import dev.shizzi.VpnMode

data class TwoN5aSettingsActions(
    val onBack: () -> Unit,
    val onSetVpnMode: (VpnMode) -> Unit,
    val onShizukuAction: () -> Unit,
    val onRunChecks: () -> Unit,
    val onOpenAdvanced: () -> Unit,
)

@Composable
fun TwoN5aSettings(
    shizuku: ShizukuState,
    vpnMode: VpnMode,
    actions: TwoN5aSettingsActions,
) {
    TwoN5aRtl {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TwoN5aColors.Background)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(onBack = actions.onBack)
            ShizziCard(shizuku, vpnMode, actions)
            AppearanceCard(actions.onOpenAdvanced)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Back arrow mirrors in RTL, so the stock icon points the right way.
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "رجوع",
            tint = TwoN5aColors.TextPrimary,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack).padding(8.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text("الإعدادات", style = TwoN5aType.title, color = TwoN5aColors.TextPrimary)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(TwoN5aColors.Card)
            .border(1.dp, TwoN5aColors.CardBorder, RoundedCornerShape(26.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

// ---- Shizzi -------------------------------------------------------------------

private fun modeTitle(mode: VpnMode) = when (mode) {
    VpnMode.AUTO -> "تلقائي: اتبع أي VPN شغّال"
    VpnMode.ALWAYS -> "إلزامي: ارفض المشاركة بدون VPN"
    VpnMode.NEVER -> "تجاهل VPN (اتصال مباشر)"
}

@Composable
private fun ShizziCard(shizuku: ShizukuState, vpnMode: VpnMode, actions: TwoN5aSettingsActions) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Text(
            "مشاركة الإنترنت (عبر Shizuku)",
            style = TwoN5aType.bodyStrong,
            color = TwoN5aColors.TextPrimary,
        )

        InnerBox(onClick = { expanded = !expanded }) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("وضع التعامل مع الـ VPN", style = TwoN5aType.caption, color = TwoN5aColors.TextMuted)
                Text(modeTitle(vpnMode), style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
            }
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = TwoN5aColors.TextMuted)
        }
        if (expanded) {
            VpnMode.entries.filter { it != vpnMode }.forEach { mode ->
                InnerBox(onClick = { actions.onSetVpnMode(mode); expanded = false }) {
                    Text(modeTitle(mode), style = TwoN5aType.body, color = TwoN5aColors.TextPrimary)
                }
            }
        }

        val (statusText, statusColor) = when (shizuku) {
            is ShizukuState.Ready -> "متصل ومفعّل (UID ${shizuku.uid})" to TwoN5aColors.Good
            ShizukuState.PermissionRequired -> "يحتاج صلاحية: اضغط فحص" to TwoN5aColors.Magenta
            ShizukuState.NotRunning -> "Shizuku غير مشغّل" to TwoN5aColors.Bad
            ShizukuState.NotInstalled -> "Shizuku غير مثبّت" to TwoN5aColors.Bad
        }
        InnerBox(onClick = null) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("صلاحية Shizuku", style = TwoN5aType.caption, color = TwoN5aColors.TextMuted)
                Text(statusText, style = TwoN5aType.bodyStrong, color = statusColor)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF26242C))
                    .clickable { if (shizuku is ShizukuState.Ready) actions.onRunChecks() else actions.onShizukuAction() }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) { Text("فحص", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary) }
        }
    }
}

@Composable
private fun InnerBox(onClick: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TwoN5aColors.Field)
            .border(1.dp, TwoN5aColors.CardBorder, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ---- appearance ----------------------------------------------------------------

@Composable
private fun AppearanceCard(onOpenAdvanced: () -> Unit) {
    Card {
        Text("المظهر والخط", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
        InnerBox(onClick = null) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(TwoN5aColors.Magenta))
            Spacer(Modifier.size(14.dp))
            Text("الوضع: داكن (وردي)", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
        }
        InnerBox(onClick = onOpenAdvanced) {
            Text(
                "إعدادات متقدمة: الصلاحيات، USB، الأتمتة، السجل",
                style = TwoN5aType.body,
                color = TwoN5aColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
