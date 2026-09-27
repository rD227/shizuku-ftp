package org.primftpd.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.primftpd.ui.data.ColorBag

/** Supply wallpaper accents to Material controls, including controls inside dialogs. */
@Composable
internal fun WallpaperControlsTheme(colorBag: ColorBag, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val accent = if (dark) colorBag.vibrant else colorBag.muted
    val onAccent = if (dark) colorBag.darkMuted else colorBag.lightMuted
    val colors = if (colorBag.useM3Color) base else base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = colorBag.vibrant,
        onPrimaryContainer = colorBag.darkMuted,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = colorBag.vibrant,
        onSecondaryContainer = colorBag.darkMuted,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = colorBag.lightMuted,
        onTertiaryContainer = colorBag.darkMuted,
        surfaceTint = accent,
    )
    // Keep neutral surfaces and error colors readable; only replace the accent roles.
    MaterialTheme(colorScheme = colors, content = content)
}
