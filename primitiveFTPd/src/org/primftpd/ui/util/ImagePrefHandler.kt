package org.primftpd.ui.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlin.math.min
import androidx.core.graphics.scale

/**
 * 壁纸取色的纯工具（无 Compose 依赖）。
 * 手机壁纸通常很大（1080x2400+），直接把整张交给 Palette 会又慢又只能取到平庸的渐变中间色，
 * 所以先缩放到小尺寸再取色，再用"占比最多"的 swatch 兜底，比只依赖 vibrant 更稳。
 */
object ImagePrefHandler {

    /** 异步：用 Palette 提取主色，结果回调给 onResult */
    fun extractSync(bitmap: ImageBitmap, fallback: Color): Color {
        val android = bitmap.asAndroidBitmap()
        if (android.isRecycled) return fallback
        val sampled = scaleDown(android, maxSize = 192)
        val p = Palette.from(sampled).maximumColorCount(16).generate()  // 同步
        val dominant = p.swatches.maxByOrNull { it.population }?.rgb
        val argb = p.getVibrantColor(dominant ?: fallback.toArgb())
        return Color(argb)
    }
    fun extractAsync(
        bitmap: ImageBitmap,
        fallback: Color,
        onResult: (Color) -> Unit
    ) {
        val android = bitmap.asAndroidBitmap()
        if (android.isRecycled) {
            onResult(fallback)
            return
        }
        val sampled = scaleDown(android, maxSize = 192)
        Palette.from(sampled)
            .maximumColorCount(16)
            .generate { palette ->
                val dominant = palette?.swatches?.maxByOrNull { it.population }?.rgb
                val argb = palette?.getVibrantColor(dominant ?: fallback.toArgb())
                    ?: dominant
                    ?: fallback.toArgb()
                onResult(Color(argb))
            }
    }
    fun extractDarkMutedColor(bitmap: ImageBitmap, fallback: Color): Color {
        val android = bitmap.asAndroidBitmap()
        if (android.isRecycled) return fallback
        val sampled = scaleDown(android, maxSize = 192)
        val p = Palette.from(sampled).maximumColorCount(16).generate()  // 同步
        val dominant = p.swatches.maxByOrNull { it.population }?.rgb
        val argb = p.getDarkMutedColor(dominant ?: fallback.toArgb())
        return Color(argb)
    }
    fun extractDarkMutedColorAsync(
        bitmap: ImageBitmap,
        fallback: Color,
        onResult: (Color) -> Unit
    ) {
        val android = bitmap.asAndroidBitmap()
        if (android.isRecycled) {
            onResult(fallback)
            return
        }
        val sampled = scaleDown(android, maxSize = 192)
        Palette.from(sampled)
            .maximumColorCount(16)
            .generate { palette ->
                val dominant = palette?.swatches?.maxByOrNull { it.population }?.rgb
                val argb = palette?.getDarkMutedColor(dominant ?: fallback.toArgb())
                    ?: dominant
                    ?: fallback.toArgb()
                onResult(Color(argb))
            }
    }
    //_______
    fun extractLightVibrantColor(bitmap: ImageBitmap, fallback: Color): Color {
        val android = bitmap.asAndroidBitmap()
        if (android.isRecycled) return fallback
        val sampled = scaleDown(android, maxSize = 192)
        val p = Palette.from(sampled).maximumColorCount(16).generate()  // 同步
        val dominant = p.swatches.maxByOrNull { it.population }?.rgb
        val argb = p.getLightVibrantColor(dominant ?: fallback.toArgb())
        return Color(argb)
    }
    fun extractLightMutedColorAsync(
        bitmap: ImageBitmap,
        fallback: Color,
        onResult: (Color) -> Unit
    ) {
        val android = bitmap.asAndroidBitmap()
        if (android.isRecycled) {
            onResult(fallback)
            return
        }
        val sampled = scaleDown(android, maxSize = 192)
        Palette.from(sampled)
            .maximumColorCount(16)
            .generate { palette ->
                val dominant = palette?.swatches?.maxByOrNull { it.population }?.rgb
                val argb = palette?.getLightMutedColor(dominant ?: fallback.toArgb())
                    ?: dominant
                    ?: fallback.toArgb()
                onResult(Color(argb))
            }
    }
    fun extractMutedColorAsync(
        bitmap: ImageBitmap,
        fallback: Color,
        onResult: (Color) -> Unit
    ) {
        val android = bitmap.asAndroidBitmap()
        if (android.isRecycled) {
            onResult(fallback)
            return
        }
        val sampled = scaleDown(android, maxSize = 192)
        Palette.from(sampled)
            .maximumColorCount(16)
            .generate { palette ->
                val dominant = palette?.swatches?.maxByOrNull { it.population }?.rgb
                val argb = palette?.getMutedColor(dominant ?: fallback.toArgb())
                    ?: dominant
                    ?: fallback.toArgb()
                onResult(Color(argb))
            }
    }

    /** 最长边缩到不超过 [maxSize]，避免大图让 Palette 卡顿或取色失败 */
    private fun scaleDown(src: Bitmap, maxSize: Int): Bitmap {
        val ratio = min(maxSize.toFloat() / src.width, maxSize.toFloat() / src.height)
        if (ratio >= 1f) return src
        return src.scale(
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1)
        )
    }
}
