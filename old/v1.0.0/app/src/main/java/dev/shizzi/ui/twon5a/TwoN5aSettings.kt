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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.shizzi.ShizukuState
import dev.shizzi.VpnMode
import dev.shizzi.vpn.ProxyConfig
import dev.shizzi.vpn.SaveFeedback
import dev.shizzi.vpn.VpnUiState

data class TwoN5aSettingsActions(
    val onBack: () -> Unit,
    val onSave: (String) -> Unit,
    val onSelect: (ProxyConfig) -> Unit,
    val onRemove: (ProxyConfig) -> Unit,
    val onTestAll: () -> Unit,
    val onClearFeedback: () -> Unit,
    val onSetVpnMode: (VpnMode) -> Unit,
    val onShizukuAction: () -> Unit,
    val onRunChecks: () -> Unit,
    val onOpenAdvanced: () -> Unit,
)

/** Shown by "إدراج كود تجريبي". A placeholder host on purpose: it never points at a real server. */
private const val SAMPLE_LINK =
    "vless://00000000-0000-4000-8000-000000000000@example.com:443" +
        "?encryption=none&security=tls&sni=example.com&type=ws&path=%2F#Sample-Server"

@Composable
fun TwoN5aSettings(
    vpn: VpnUiState,
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
            ConnectionCard(vpn, actions)
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

// ---- connection code ------------------------------------------------------

@Composable
private fun ConnectionCard(vpn: VpnUiState, actions: TwoN5aSettingsActions) {
    var text by remember { mutableStateOf("") }

    Card {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "كود اتصال V2Ray (VLESS / VMess / Trojan / SS)",
                style = TwoN5aType.bodyStrong,
                color = TwoN5aColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "إدراج كود تجريبي",
                style = TwoN5aType.bodyStrong,
                color = TwoN5aColors.Magenta,
                modifier = Modifier.clickable { text = SAMPLE_LINK; actions.onClearFeedback() },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CodeField(text, onChange = { text = it; actions.onClearFeedback() }, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(TwoN5aColors.Magenta)
                    .clickable { actions.onSave(text) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("حفظ الكود", style = TwoN5aType.bodyStrong, color = Color.Black) }
        }

        vpn.feedback?.let { FeedbackLine(it) }

        vpn.servers.selected?.let { CurrentServer(it) }

        if (vpn.servers.servers.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "سيرفرات سريعة محفوظة:",
                    style = TwoN5aType.caption,
                    color = TwoN5aColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "اختبار الكل",
                    style = TwoN5aType.caption,
                    color = TwoN5aColors.Violet,
                    modifier = Modifier.clickable(onClick = actions.onTestAll),
                )
            }
            vpn.servers.servers.forEach { server ->
                ServerRow(
                    server = server,
                    isSelected = server.uri == vpn.servers.selected?.uri,
                    hasLatency = server.uri in vpn.latency,
                    latency = vpn.latency[server.uri],
                    onSelect = { actions.onSelect(server) },
                    onRemove = { actions.onRemove(server) },
                )
            }
        }
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    // Links are Latin text: keep them left-to-right inside the RTL screen.
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .height(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(TwoN5aColors.Field)
                .border(1.dp, TwoN5aColors.CardBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text("vless:// vmess:// trojan:// ss://", style = TwoN5aType.mono, color = TwoN5aColors.TextMuted.copy(alpha = 0.6f), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TwoN5aType.mono.copy(color = TwoN5aColors.TextPrimary),
                cursorBrush = SolidColor(TwoN5aColors.Magenta),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FeedbackLine(feedback: SaveFeedback) {
    val (message, color) = when (feedback) {
        is SaveFeedback.Saved -> buildString {
            if (feedback.added > 0) append("تم حفظ ${feedback.added} سيرفر") else append("السيرفر محفوظ مسبقاً")
            if (feedback.duplicates > 0 && feedback.added > 0) append(" (${feedback.duplicates} مكرر)")
            if (feedback.failed > 0) append(" · ${feedback.failed} رابط غير صالح")
        } to TwoN5aColors.Good
        is SaveFeedback.Invalid -> "رابط غير صالح: ${feedback.reason}" to TwoN5aColors.Bad
    }
    Text(message, style = TwoN5aType.caption, color = color)
}

@Composable
private fun CurrentServer(server: ProxyConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                server.protocol.label.uppercase(),
                style = TwoN5aType.mono.copy(color = Color(0xFFD7B8FF)),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A1B3C))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(server.name, style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            server.endpoint,
            style = TwoN5aType.mono.copy(color = TwoN5aColors.TextMuted),
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(TwoN5aColors.CardBorder))
    }
}

@Composable
private fun ServerRow(
    server: ProxyConfig,
    isSelected: Boolean,
    hasLatency: Boolean,
    latency: Int?,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) TwoN5aColors.SelectedFill else Color.Transparent)
            .border(1.dp, if (isSelected) TwoN5aColors.Violet else TwoN5aColors.CardBorder, shape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${server.name} (${server.protocol.label})",
            style = TwoN5aType.bodyStrong,
            color = TwoN5aColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(10.dp))
        when {
            !hasLatency -> Text("—", style = TwoN5aType.mono, color = TwoN5aColors.TextMuted)
            latency == null -> Text("خارج الخدمة", style = TwoN5aType.caption, color = TwoN5aColors.Bad)
            else -> Text("$latency ms", style = TwoN5aType.mono.copy(color = TwoN5aColors.Good))
        }
        Spacer(Modifier.size(8.dp))
        Icon(
            Icons.Filled.Close,
            contentDescription = "حذف",
            tint = TwoN5aColors.TextMuted,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onRemove).padding(5.dp),
        )
    }
}

// ---- Shizzi -------------------------------------------------------------------

private fun modeTitle(mode: VpnMode) = when (mode) {
    VpnMode.AUTO -> "ربط تلقائي مع V2Ray (Follow VPN)"
    VpnMode.ALWAYS -> "إلزامي: ارفض المشاركة بدون VPN"
    VpnMode.NEVER -> "تجاهل VPN (اتصال مباشر)"
}

@Composable
private fun ShizziCard(shizuku: ShizukuState, vpnMode: VpnMode, actions: TwoN5aSettingsActions) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Text(
            "إعدادات Shizzi (بث نقطة الاتصال عبر Shizuku)",
            style = TwoN5aType.bodyStrong,
            color = TwoN5aColors.TextPrimary,
        )

        InnerBox(onClick = { expanded = !expanded }) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("وضع توجيه VPN في Shizzi", style = TwoN5aType.caption, color = TwoN5aColors.TextMuted)
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
            Text("الوضع: Dark Mode (الوردي الداكن)", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
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
