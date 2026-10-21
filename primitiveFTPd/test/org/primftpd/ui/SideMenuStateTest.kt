package org.primftpd.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SideMenuStateTest {
    @Test
    fun backDismissesDetailBeforeClosingMenu() {
        val state = SideMenuState()
        state.open(SideMenu.SETTINGS)
        state.showDetail("settings/ui")

        state.back()

        assertEquals(SideMenu.SETTINGS, state.menu)
        assertNull(state.detailRoute)
        assertTrue(state.isOpen)

        state.back()

        assertFalse(state.isOpen)
    }

    @Test
    fun closeDismissesBothPanesAndReopeningStartsWithoutDetail() {
        val state = SideMenuState()
        state.open(SideMenu.SETTINGS)
        state.showDetail("settings/system")

        state.close()

        assertNull(state.menu)
        assertNull(state.detailRoute)

        state.open(SideMenu.SETTINGS)

        assertNull(state.detailRoute)
    }

    @Test
    fun switchingMenuClearsPreviousDetail() {
        val state = SideMenuState()
        state.open(SideMenu.SETTINGS)
        state.showDetail("settings/auth")

        state.open(SideMenu.TOOLS)

        assertEquals(SideMenu.TOOLS, state.menu)
        assertNull(state.detailRoute)
    }

    @Test
    fun configurationRestoreKeepsBothPanesAndBackOrder() {
        val state = SideMenuState()
        state.open(SideMenu.TOOLS)
        state.showDetail("about")
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        val saved = with(SideMenuState.Saver) { scope.save(state) }
        val restored = checkNotNull(SideMenuState.Saver.restore(checkNotNull(saved)))

        assertEquals(SideMenu.TOOLS, restored.menu)
        assertEquals("about", restored.detailRoute)

        restored.back()

        assertEquals(SideMenu.TOOLS, restored.menu)
        assertNull(restored.detailRoute)
    }

    @Test(expected = IllegalStateException::class)
    fun detailCannotOutliveItsMenu() {
        SideMenuState().showDetail("settings/ui")
    }
}
