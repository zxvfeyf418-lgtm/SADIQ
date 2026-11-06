package dev.shizzi

import android.service.quicksettings.Tile

enum class TileAction { START, STOP, OPEN_APP, NONE }

data class TileRender(
    val state: Int,
    val subtitle: String,
    val action: TileAction,
)

object SessionTile {

    const val LABEL = "2n5a"

    fun render(session: SessionUiState, shizuku: ShizukuState, isStopping: Boolean): TileRender {
        if (shizuku !is ShizukuState.Ready) return unavailable(shizuku)

        return when (session.status) {
            UiStatus.LOADING -> TileRender(
                state = Tile.STATE_UNAVAILABLE,
                subtitle = if (isStopping) "جارٍ الإيقاف…" else "جارٍ التشغيل…",
                action = TileAction.NONE,
            )

            UiStatus.CONNECTED -> TileRender(
                state = Tile.STATE_ACTIVE,
                subtitle = describeConnected(session),
                action = TileAction.STOP,
            )

            UiStatus.ERROR -> TileRender(
                state = Tile.STATE_INACTIVE,
                subtitle = "اضغط لإعادة المحاولة",
                action = TileAction.START,
            )

            UiStatus.READY -> TileRender(
                state = Tile.STATE_INACTIVE,
                subtitle = "اضغط للمشاركة",
                action = TileAction.START,
            )
        }
    }

    private fun unavailable(shizuku: ShizukuState) = TileRender(
        state = Tile.STATE_UNAVAILABLE,
        subtitle = describe(shizuku),
        action = TileAction.OPEN_APP,
    )

    private fun describe(shizuku: ShizukuState): String = when (shizuku) {
        ShizukuState.NotInstalled -> "Shizuku غير مثبّت"
        ShizukuState.NotRunning -> "Shizuku لا يعمل"
        ShizukuState.PermissionRequired -> "الإذن مطلوب"
        is ShizukuState.Ready -> ""
    }

    private fun describeConnected(session: SessionUiState): String = when (session.clientCount) {
        0 -> "لا توجد أجهزة"
        else -> "${deviceCount(session.clientCount)} · ${Traffic.format(session.traffic.down)}"
    }

    /** Arabic count phrase with the correct number agreement. */
    fun deviceCount(clients: Int): String = when (clients) {
        1 -> "جهاز واحد"
        2 -> "جهازان"
        in 3..10 -> "$clients أجهزة"
        else -> "$clients جهازًا"
    }
}
