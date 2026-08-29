package org.primftpd.ui.util

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun getCarmaHeight(): Dp {
    return WindowInsets.displayCutout
        .asPaddingValues()
        .calculateTopPadding()
}
@Composable
fun getRadius(): Dp {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val windowInsets = LocalView.current.rootWindowInsets
        val roundedCorner = windowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        roundedCorner?.radius?.let { with(LocalDensity.current) { it.toDp() } } ?: 32.dp
    } else {
        32.dp
    }
}
