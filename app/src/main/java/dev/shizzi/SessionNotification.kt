package dev.shizzi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class SessionNotification(private val context: Context) {

    fun build(state: SessionUiState, isStopping: Boolean): Notification {
        createChannel()

        val text = bodyFor(state, isStopping)

        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(titleFor(state, isStopping))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent())
            .setOngoing(state.status != UiStatus.ERROR)
            .addAction(stopAction())
            .build()
    }

    private fun titleFor(state: SessionUiState, isStopping: Boolean): String =
        when (state.status) {
            UiStatus.CONNECTED -> connectedTitle(state)
            UiStatus.LOADING -> if (isStopping) "جارٍ التنظيف…" else "جارٍ التحضير…"
            UiStatus.ERROR -> "انتهت الجلسة"
            UiStatus.READY -> "انتهت الجلسة"
        }

    private fun connectedTitle(state: SessionUiState): String = when {
        state.isWaitingForUsb -> "بانتظار USB"
        state.isVpnBypassed -> "متصل · تم تجاهل VPN"
        state.isVpnBound -> "متصل · VPN"
        else -> "متصل"
    }

    private fun bodyFor(state: SessionUiState, isStopping: Boolean): String = when {
        state.lastError.isNotEmpty() -> userFacingError(state.lastError)
        state.status == UiStatus.LOADING -> loadingBody(isStopping)
        state.isWaitingForUsb -> "وصّل كابل USB خلال $USB_GRACE_MINUTES دقيقة لتستمر المشاركة"
        state.status == UiStatus.CONNECTED -> connectedBody(state)
        else -> "لا توجد مشاركة"
    }

    private fun userFacingError(raw: String): String = when {
        raw.contains(EXCEPTION_MARKER) -> "حدث خطأ. افتح التطبيق لمعرفة التفاصيل."
        else -> raw
    }

    private fun loadingBody(isStopping: Boolean): String =
        if (isStopping) "جارٍ إيقاف النقطة الساخنة…" else "جارٍ تشغيل النقطة الساخنة…"

    private fun connectedBody(state: SessionUiState): String = when (state.clientCount) {
        0 -> "لا توجد أجهزة متصلة"
        else -> "${SessionTile.deviceCount(state.clientCount)} · " +
            "$DOWN_ARROW ${Traffic.format(state.traffic.down)} · " +
            "$UP_ARROW ${Traffic.format(state.traffic.up)}"
    }

    fun describeLoss(problem: String?): String = when (problem) {
        null -> "توقف Shizuku فانتهت الجلسة. تم إيقاف النقطة الساخنة. " +
            "ابدأ من جديد عندما تكون جاهزًا."

        else -> "انتهت الجلسة لكن النقطة الساخنة قد تكون ما زالت تعمل: $problem — " +
            "أوقفها من الإعدادات."
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopAction(): Notification.Action = Notification.Action.Builder(
        null,
        "إيقاف",
        PendingIntent.getService(
            context,
            1,
            Intent(context, SessionService::class.java).setAction(SessionService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "جلسة المشاركة",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "يظهر أثناء تشغيل جلسة المشاركة" }

        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private companion object {
        const val CHANNEL_ID = "tethering-session"

        const val EXCEPTION_MARKER = "Exception"

        const val DOWN_ARROW = "\u2193"
        const val UP_ARROW = "\u2191"

        const val USB_GRACE_MINUTES = UsbReconnect.GIVE_UP_AFTER_MS / 60_000
    }
}
