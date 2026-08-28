package org.primftpd.ui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation.NavType

/**
 * 壁纸取色的输入配置：把"用哪张图"和"是否取色"两个参数包成一个对象。
 * 以后要加细粒度选项（blur、tint、圆角…）就往这里加字段即可。
 */
data class WallpaperPalette(
    val bitmap: ImageBitmap? = null,
    val pickImageColor: Boolean = true,
)

/**
 * 把 [WallpaperPalette] 解析成最终颜色。
 * 取色只做一次（按 bitmap 记忆），多个按钮共享同一个结果，避免各自重复调 Palette。
 */

@Composable
fun rememberWallpaperAccentColor(palette: WallpaperPalette,type: String = "vibrant"): Color {
    val fallback = MaterialTheme.colorScheme.secondaryContainer
    when (type) {
        "vibrant" -> {
            if (LocalInspectionMode.current) {
                val bmp = palette.bitmap ?: return fallback
                return if (palette.pickImageColor) ImagePrefHandler.extractSync(bmp, fallback) else fallback
            }
            var color by remember(palette.bitmap) { mutableStateOf(fallback) }
            LaunchedEffect(palette.bitmap) {
                val bmp = palette.bitmap ?: return@LaunchedEffect
                ImagePrefHandler.extractAsync(bmp, fallback) { color = it }
            }
            return if (palette.pickImageColor) color else fallback
        }
        "dark_muted" -> {
            if(LocalInspectionMode.current) {
                val bmp = palette.bitmap ?: return fallback
                return if (palette.pickImageColor) ImagePrefHandler.extractDarkMutedColor(bmp, fallback) else fallback
            }
            var color by remember(palette.bitmap) { mutableStateOf(fallback) }
            LaunchedEffect(palette.bitmap) {
                val bmp = palette.bitmap ?: return@LaunchedEffect
                ImagePrefHandler.extractDarkMutedColorAsync(bmp, fallback) { color = it }
            }
            return if (palette.pickImageColor) color else fallback
        }
        //因为preview的表现太诡异了，所以就不写同步方法了
        "light_vibrant" -> {
            var color by remember(palette.bitmap) { mutableStateOf(fallback) }
            if(LocalInspectionMode.current) {
                val bmp = palette.bitmap ?: return fallback
                if (palette.pickImageColor) ImagePrefHandler.extractLightVibrantColorAsync(
                    bmp, fallback,
                    onResult = {color = it}
                )
                return if (palette.pickImageColor) color else fallback
            }
            LaunchedEffect(palette.bitmap) {
                val bmp = palette.bitmap ?: return@LaunchedEffect
                ImagePrefHandler.extractLightVibrantColorAsync(bmp, fallback) { color = it }
            }
            return if (palette.pickImageColor) color else fallback
        }
    }
    return Color.Red
}
