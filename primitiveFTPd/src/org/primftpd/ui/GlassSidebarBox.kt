package org.primftpd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

internal val GlassSideMenuWidth = 270.dp

@Composable
internal fun GlassSidebarBox(
    backdrop: GlassBackdrop,
    content: @Composable ColumnScope.() -> Unit,
    showWallpaper: Boolean = true,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(GlassSideMenuWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .then(
                if (showWallpaper) {
                    Modifier.glassBackdrop(backdrop)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                }
            )
            // 子菜单项消费自己的点击；标题和剩余空白区域统一关闭侧栏及详情。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding(),
            content = content,
        )
    }
}
