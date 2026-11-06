package dev.shizzi.ui.twon5a

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.shizzi.BuildConfig
import dev.shizzi.warp.ConnectionUiState
import dev.shizzi.warp.SavedConfig
import dev.shizzi.warp.TunnelStatus
import dev.shizzi.warp.WarpDefaults
import dev.shizzi.warp.formatBytes
import dev.shizzi.warp.WarpProfile
import dev.shizzi.warp.WarpStep
import dev.shizzi.warp.WarpUiState
import java.io.File

data class WarpActions(
    val onBack: () -> Unit,
    val onGenerate: () -> Unit,
    val onSetObfuscated: (Boolean) -> Unit,
    val onSelectEndpoint: (Int) -> Unit,
    val onSave: () -> Unit,
    val onMessage: (String) -> Unit,
    val onSelectConfig: (String) -> Unit,
    val onDeleteConfig: (String) -> Unit,
    val onImport: () -> Unit,
    val onConnect: (String) -> Unit,
    val onDisconnect: () -> Unit,
    val onRunInApp: () -> Unit,
    val onDismissConnectionMessage: () -> Unit,
)

@Composable
fun WarpScreen(state: WarpUiState, connection: ConnectionUiState, actions: WarpActions) {
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
            WarpHeader(actions.onBack)
            ConnectionSection(connection, actions)

            WarpCard {
                Text("إنشاء ملف WARP", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
                Text(
                    "ننشئ لك ملف اتصال Cloudflare WARP جاهز. المفاتيح تتولّد على جهازك والمفتاح الخاص ما يُرسل لأي جهة. " +
                        "بعد الإنشاء اضغط «تشغيل داخل التطبيق» وبعدها شارك الإنترنت من الشاشة الرئيسية.",
                    style = TwoN5aType.body,
                    color = TwoN5aColors.TextMuted,
                )
            }

            WarpButton(
                text = when (state) {
                    WarpUiState.Idle -> "إنشاء ملفات"
                    is WarpUiState.Working -> "جارٍ الإنشاء…"
                    is WarpUiState.Done -> "إنشاء ملف جديد"
                    is WarpUiState.Failed -> "إعادة المحاولة"
                },
                fill = TwoN5aColors.MagentaStrong,
                content = Color.Black,
                enabled = state !is WarpUiState.Working,
                onClick = actions.onGenerate,
            )

            when (state) {
                WarpUiState.Idle -> Unit
                is WarpUiState.Working -> WorkingCard(state.step)
                is WarpUiState.Failed -> Text(
                    state.reason,
                    style = TwoN5aType.body,
                    color = TwoN5aColors.Bad,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                is WarpUiState.Done -> ResultSection(state, actions)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WarpHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "رجوع",
            tint = TwoN5aColors.TextPrimary,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack).padding(8.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text("الاتصال والملفات", style = TwoN5aType.title, color = TwoN5aColors.TextPrimary)
    }
}

@Composable
private fun WarpCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(TwoN5aColors.Card)
            .border(1.dp, TwoN5aColors.CardBorder, RoundedCornerShape(26.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun WarpButton(text: String, fill: Color, content: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TwoN5aType.button, color = content)
    }
}

@Composable
private fun WorkingCard(step: WarpStep) {
    val (title, detail) = when (step) {
        WarpStep.GENERATING_KEYS -> "نفحص WARP" to "دا نولّد المفاتيح على جهازك…"
        WarpStep.REGISTERING -> "نفحص WARP" to "دا نسجّل جهاز WARP عند Cloudflare…"
        WarpStep.CHECKING_CLOUDFLARE -> "نفحص WARP" to "المصافحة نجحت؛ دا نتأكد من مدينة خروج Cloudflare…"
    }

    WarpCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color = TwoN5aColors.Magenta,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
                Text(detail, style = TwoN5aType.caption, color = TwoN5aColors.TextMuted)
            }
        }
    }
}

@Composable
private fun ResultSection(state: WarpUiState.Done, actions: WarpActions) {
    val context = LocalContext.current
    val profile = state.profile
    val fileName = profile.fileName(state.isObfuscated)
    val config = profile.toConfig(state.isObfuscated)

    WarpCard {
        Text("تم إنشاء الملف", style = TwoN5aType.bodyStrong, color = TwoN5aColors.Good)
        InfoRow("اسم الملف", fileName)
        InfoRow("مدينة خروج Cloudflare", profile.city ?: "غير معروفة")
        InfoRow("عنوان الجهاز", profile.addressV4)
        InfoRow("نقطة الاتصال", profile.endpoint)
        InfoRow("حجم الحزمة (MTU)", WarpDefaults.MTU.toString())
    }

    WarpCard {
        Text("نوع الملف", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
        ChoiceRow("AmneziaWG مع تمويه (يتجاوز الحجب)", state.isObfuscated) { actions.onSetObfuscated(true) }
        ChoiceRow("WireGuard عادي (بدون تمويه)", !state.isObfuscated) { actions.onSetObfuscated(false) }
    }

    WarpCard {
        Text("نقطة الاتصال", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
        Text(
            "إذا ما اشتغل الاتصال بنقطة، غيّرها وانسخ الملف من جديد.",
            style = TwoN5aType.caption,
            color = TwoN5aColors.TextMuted,
        )
        WarpDefaults.ENDPOINTS.forEachIndexed { index, endpoint ->
            ChoiceRow(endpoint, index == state.endpointIndex, isCode = true) { actions.onSelectEndpoint(index) }
        }
    }

    WarpButton("تشغيل داخل التطبيق", TwoN5aColors.MagentaStrong, Color.Black, true, actions.onRunInApp)

    WarpButton("حفظ في التنزيلات", TwoN5aColors.VioletButton, Color.White, true, actions.onSave)

    WarpButton("مشاركة الملف", TwoN5aColors.Card, TwoN5aColors.TextPrimary, true) {
        val shared = context.shareConfig(fileName, config)
        if (!shared) actions.onMessage("ما لقينا تطبيق يستلم الملف. جرّب الحفظ أو النسخ.")
    }

    WarpButton("نسخ الإعداد", TwoN5aColors.Card, TwoN5aColors.TextPrimary, true) {
        context.copyText(fileName, config)
        actions.onMessage("تم نسخ الإعداد. الصقه داخل تطبيق الـ VPN.")
    }

    state.message?.let {
        Text(
            it,
            style = TwoN5aType.bodyStrong,
            color = TwoN5aColors.Good,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConnectionSection(connection: ConnectionUiState, actions: WarpActions) {
    val status = connection.status
    val isRunning = status is TunnelStatus.Up || status is TunnelStatus.Connecting
    val isConnecting = status is TunnelStatus.Connecting
    val selected = connection.selected

    WarpCard {
        Text("الاتصال", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)
        StatusLine(status)
        Text(
            "اختر ملف أو أنشئ واحد، وشغّل الاتصال من هنا. بعد ما يشتغل، مشاركة الإنترنت من 2n5a تمر عبره تلقائياً.",
            style = TwoN5aType.caption,
            color = TwoN5aColors.TextMuted,
        )
    }

    WarpButton(
        text = when {
            isConnecting -> "جارٍ الاتصال…"
            isRunning -> "إيقاف الاتصال"
            else -> "تشغيل الاتصال"
        },
        fill = if (isRunning) TwoN5aColors.Card else TwoN5aColors.MagentaStrong,
        content = if (isRunning) TwoN5aColors.TextPrimary else Color.Black,
        enabled = !isConnecting && (isRunning || selected != null),
        onClick = { if (isRunning) actions.onDisconnect() else selected?.let(actions.onConnect) },
    )

    WarpCard {
        Text("الملفات المحفوظة", style = TwoN5aType.bodyStrong, color = TwoN5aColors.TextPrimary)

        if (connection.configs.isEmpty()) {
            Text(
                "ما عندك ملفات بعد. أنشئ ملف WARP من تحت أو استورد ملف .conf من جهازك.",
                style = TwoN5aType.caption,
                color = TwoN5aColors.TextMuted,
            )
        }

        connection.configs.forEach { config ->
            ConfigRow(
                config = config,
                isSelected = config.name == selected,
                onSelect = { actions.onSelectConfig(config.name) },
                onDelete = { actions.onDeleteConfig(config.name) },
            )
        }
    }

    WarpButton("استيراد ملف", TwoN5aColors.VioletButton, Color.White, true, actions.onImport)

    connection.message?.let {
        Text(
            it,
            style = TwoN5aType.bodyStrong,
            color = TwoN5aColors.Good,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = actions.onDismissConnectionMessage),
        )
    }
}

@Composable
private fun StatusLine(status: TunnelStatus) {
    val (text, color) = when (status) {
        TunnelStatus.Down -> "متوقف" to TwoN5aColors.TextMuted
        is TunnelStatus.Connecting -> "جارٍ الاتصال بـ ${status.name}…" to TwoN5aColors.Magenta
        is TunnelStatus.Up ->
            (if (status.hasHandshake) "متصل: ${status.name}" else "تم التشغيل: ${status.name}، بانتظار رد السيرفر…") to
                (if (status.hasHandshake) TwoN5aColors.Good else TwoN5aColors.Magenta)
        is TunnelStatus.Failed -> status.message to TwoN5aColors.Bad
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(10.dp))
        Text(text, style = TwoN5aType.bodyStrong, color = color, modifier = Modifier.weight(1f))
    }

    if (status is TunnelStatus.Up && status.hasHandshake) {
        Text(
            "تنزيل ${formatBytes(status.rxBytes)}  ·  رفع ${formatBytes(status.txBytes)}",
            style = TwoN5aType.caption,
            color = TwoN5aColors.TextMuted,
        )
    }
}

@Composable
private fun ConfigRow(config: SavedConfig, isSelected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) TwoN5aColors.SelectedFill else Color.Transparent)
            .border(1.dp, if (isSelected) TwoN5aColors.Violet else TwoN5aColors.CardBorder, shape)
            .clickable(onClick = onSelect)
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelected) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(TwoN5aColors.Magenta))
            Spacer(Modifier.size(10.dp))
        }
        Text(
            config.name,
            style = TwoN5aType.mono.copy(color = TwoN5aColors.TextPrimary),
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.Delete,
            contentDescription = "حذف الملف",
            tint = TwoN5aColors.TextMuted,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDelete).padding(9.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = TwoN5aType.caption, color = TwoN5aColors.TextMuted)
        Text(value, style = TwoN5aType.mono.copy(color = TwoN5aColors.TextPrimary), textAlign = TextAlign.Start)
    }
}

@Composable
private fun ChoiceRow(text: String, isSelected: Boolean, isCode: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) TwoN5aColors.SelectedFill else Color.Transparent)
            .border(1.dp, if (isSelected) TwoN5aColors.Violet else TwoN5aColors.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = if (isCode) TwoN5aType.mono.copy(color = TwoN5aColors.TextPrimary) else TwoN5aType.bodyStrong,
            color = TwoN5aColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(TwoN5aColors.Magenta))
        }
    }
}

private const val EXPORT_DIR = "exports"

/** Stages the config under the FileProvider's exports folder and opens the share sheet. */
private fun Context.shareConfig(fileName: String, config: String): Boolean = runCatching {
    val directory = File(filesDir, EXPORT_DIR).apply { mkdirs() }
    val file = File(directory, fileName).apply { writeText(config) }
    val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.exports", file)

    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, fileName)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    startActivity(Intent.createChooser(intent, "مشاركة الملف").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

private fun Context.copyText(label: String, text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
