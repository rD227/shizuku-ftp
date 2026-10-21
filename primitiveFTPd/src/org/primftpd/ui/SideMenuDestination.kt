package org.primftpd.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.primftpd.ui.data.ColorBag
import org.primftpd.ui.viewmodel.UiPreferencesViewModel
import org.primftpd.ui.viewmodel.WallpaperViewModel

/** 手机的全屏导航和平板右栏共用页面实现，保持设置行为及 ViewModel 一致。 */
internal fun NavGraphBuilder.sideMenuDestinations(
    isServerRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onBack: () -> Unit,
    colorBag: ColorBag,
    uiPreferencesViewModel: UiPreferencesViewModel,
    wallpaperViewModel: WallpaperViewModel,
) {
    composable(
        route = "settings/{section}",
        arguments = listOf(navArgument("section") { type = NavType.StringType }),
    ) { entry ->
        SideMenuDestinationScreen(
            route = "settings/${entry.arguments?.getString("section")}",
            onBack = onBack,
            isServerRunning = isServerRunning,
            onStartServer = onStartServer,
            onStopServer = onStopServer,
            colorBag = colorBag,
            uiPreferencesViewModel = uiPreferencesViewModel,
            wallpaperViewModel = wallpaperViewModel,
        )
    }
    listOf("about", "qr", "netWorkStatus", "clientStatus", "VerificationKey", "fingerPrint", "clean")
        .forEach { route ->
            composable(route) {
                SideMenuDestinationScreen(
                    route = route,
                    onBack = onBack,
                    isServerRunning = isServerRunning,
                    onStartServer = onStartServer,
                    onStopServer = onStopServer,
                    colorBag = colorBag,
                    uiPreferencesViewModel = uiPreferencesViewModel,
                    wallpaperViewModel = wallpaperViewModel,
                )
            }
        }
}

@Composable
internal fun SideMenuDestinationScreen(
    route: String,
    onBack: () -> Unit,
    isServerRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    colorBag: ColorBag,
    uiPreferencesViewModel: UiPreferencesViewModel?,
    wallpaperViewModel: WallpaperViewModel?,
    singleSettingsSection: Boolean = false,
) {
    when (route) {
        "settings/auth", "settings/connecting", "settings/ui", "settings/system" -> SettingsScreen(
            section = SettingsSection.entries.first { route == "settings/${it.route}" },
            onBack = onBack,
            uiPreferencesViewModel = uiPreferencesViewModel,
            wallpaperViewModel = wallpaperViewModel,
            providedColorBag = colorBag,
            singleSection = singleSettingsSection,
        )
        "about" -> AboutScreen(onBack = onBack, colorBag = colorBag)
        "netWorkStatus" -> NetworkStatusScreen(
            isServerRunning = isServerRunning,
            onStartServer = onStartServer,
            onStopServer = onStopServer,
            onBack = onBack,
        )
        "qr" -> FragmentContainerScreen("扫码了", { QrFragment(null) }, onBack)
        "clientStatus" -> FragmentContainerScreen("clientStatus", { ClientActionFragment() }, onBack)
        "VerificationKey" -> FragmentContainerScreen("Verification Key", { PubKeyAuthKeysFragment(true) }, onBack)
        "fingerPrint" -> FragmentContainerScreen("fingerPrint", { KeysFingerprintsFragment() }, onBack)
        "clean" -> FragmentContainerScreen("cleaner", { CleanSpaceFragment() }, onBack)
        else -> error("Unknown side menu destination: $route")
    }
}
