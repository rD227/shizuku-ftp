package org.primftpd.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal enum class SideMenu { SETTINGS, TOOLS }

/** 侧栏拥有详情状态，避免右侧导航替换整个主屏幕后丢失左栏。 */
@Stable
internal class SideMenuState(
    menu: SideMenu? = null,
    detailRoute: String? = null,
) {
    var menu by mutableStateOf(menu)
        private set
    var detailRoute by mutableStateOf(detailRoute)
        private set

    val isOpen: Boolean get() = menu != null

    fun open(menu: SideMenu) {
        detailRoute = null
        this.menu = menu
    }

    fun showDetail(route: String) {
        check(isOpen) { "A detail must belong to an open side menu" }
        detailRoute = route
    }

    fun dismissDetail() {
        detailRoute = null
    }

    fun close() {
        detailRoute = null
        menu = null
    }

    fun back() {
        if (detailRoute != null) dismissDetail() else close()
    }

    companion object {
        val Saver = listSaver<SideMenuState, String>(
            save = { listOf(it.menu?.name.orEmpty(), it.detailRoute.orEmpty()) },
            restore = {
                SideMenuState(
                    menu = it[0].takeIf(String::isNotEmpty)?.let(SideMenu::valueOf),
                    detailRoute = it[1].takeIf(String::isNotEmpty),
                )
            },
        )
    }
}

@Composable
internal fun rememberSideMenuState(): SideMenuState = rememberSaveable(saver = SideMenuState.Saver) {
    SideMenuState()
}
