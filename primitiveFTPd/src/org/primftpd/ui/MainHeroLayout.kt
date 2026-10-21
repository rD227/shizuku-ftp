package org.primftpd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.primftpd.R
import org.primftpd.ui.util.getCarmaHeight
import org.primftpd.ui.util.getRadius

/** 矮横屏的图片独立占据背景，避免控制区和固定高度的图表挤压图片。 */
@Composable
internal fun MainHeroLayout(
    wallpaperBitmap: ImageBitmap?,
    topBarPressedDown: Boolean,
    onWallpaperLongPress: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useBackgroundHero = maxWidth > maxHeight && maxHeight < 600.dp
        if (useBackgroundHero) {
            MainHeroImage(
                wallpaperBitmap = wallpaperBitmap,
                extendedFade = true,
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            val cutoutHeight = if (LocalInspectionMode.current) 16.dp else getCarmaHeight()
            if (topBarPressedDown) Spacer(Modifier.height(cutoutHeight - 8.dp))

            val heroModifier = Modifier.weight(0.7f).fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onWallpaperLongPress() })
                }
            if (useBackgroundHero) {
                // 只保留前景布局和长按入口；图片的测量不再依赖这段剩余高度。
                Spacer(heroModifier)
            } else {
                MainHeroImage(wallpaperBitmap = wallpaperBitmap, modifier = heroModifier)
            }

            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MainHeroImage(
    wallpaperBitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    extendedFade: Boolean = false,
) {
    val cornerRadius = if (wallpaperBitmap != null) getRadius() else 32.dp
    val imageModifier = Modifier.fillMaxSize()
        .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
    val background = MaterialTheme.colorScheme.background

    Box(modifier.fillMaxHeight().padding(horizontal = 8.dp)) {
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.my_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
            )
        }

        // 横屏把渐变延伸到按钮及图表下方，图表轴线附近逐渐收敛为主题背景色。
        val fade = if (extendedFade) {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.18f to background.copy(alpha = 0.12f),
                0.45f to background.copy(alpha = 0.78f),
                0.78f to background.copy(alpha = 0.97f),
                1f to background,
            )
        } else {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.8f to Color.Transparent,
                1f to background,
            )
        }
        Box(Modifier.fillMaxSize().background(fade))
    }
}
