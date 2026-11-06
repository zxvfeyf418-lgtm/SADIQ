package dev.shizzi

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTileTest {

    private val ready = ShizukuState.Ready(uid = 2000, isRoot = false)

    @Test
    fun `is unavailable and opens the app while shizuku is missing`() {
        val render = SessionTile.render(SessionUiState(), ShizukuState.NotInstalled, false)

        assertEquals(Tile.STATE_UNAVAILABLE, render.state)
        assertEquals("Shizuku غير مثبّت", render.subtitle)
        assertEquals(TileAction.OPEN_APP, render.action)
    }

    @Test
    fun `names the reason shizuku cannot be used`() {
        val stopped = SessionTile.render(SessionUiState(), ShizukuState.NotRunning, false)
        val ungranted =
            SessionTile.render(SessionUiState(), ShizukuState.PermissionRequired, false)

        assertEquals("Shizuku لا يعمل", stopped.subtitle)
        assertEquals("الإذن مطلوب", ungranted.subtitle)
    }

    @Test
    fun `offers a start while idle`() {
        val render = SessionTile.render(SessionUiState(status = UiStatus.READY), ready, false)

        assertEquals(Tile.STATE_INACTIVE, render.state)
        assertEquals("اضغط للمشاركة", render.subtitle)
        assertEquals(TileAction.START, render.action)
    }

    @Test
    fun `refuses a tap while a session is starting`() {
        val render = SessionTile.render(SessionUiState(status = UiStatus.LOADING), ready, false)

        assertEquals(Tile.STATE_UNAVAILABLE, render.state)
        assertEquals("جارٍ التشغيل…", render.subtitle)
        assertEquals(TileAction.NONE, render.action)
    }

    @Test
    fun `says it is stopping while tearing a session down`() {
        val render = SessionTile.render(SessionUiState(status = UiStatus.LOADING), ready, true)

        assertEquals("جارٍ الإيقاف…", render.subtitle)
        assertEquals(TileAction.NONE, render.action)
    }

    @Test
    fun `offers a stop while connected`() {
        val state = SessionUiState(status = UiStatus.CONNECTED)

        val render = SessionTile.render(state, ready, false)

        assertEquals(Tile.STATE_ACTIVE, render.state)
        assertEquals(TileAction.STOP, render.action)
    }

    @Test
    fun `counts connected devices in the subtitle`() {
        val none = SessionUiState(status = UiStatus.CONNECTED)
        val one = none.copy(clientCount = 1, traffic = Traffic(down = 2_000))
        val many = none.copy(clientCount = 3, traffic = Traffic(down = 2_000))

        assertEquals("لا توجد أجهزة", SessionTile.render(none, ready, false).subtitle)
        assertEquals("جهاز واحد · 2.0 KB", SessionTile.render(one, ready, false).subtitle)
        assertEquals("3 أجهزة · 2.0 KB", SessionTile.render(many, ready, false).subtitle)
    }

    @Test
    fun `offers a retry after a failure`() {
        val render = SessionTile.render(SessionUiState(status = UiStatus.ERROR), ready, false)

        assertEquals(Tile.STATE_INACTIVE, render.state)
        assertEquals("اضغط لإعادة المحاولة", render.subtitle)
        assertEquals(TileAction.START, render.action)
    }
}
