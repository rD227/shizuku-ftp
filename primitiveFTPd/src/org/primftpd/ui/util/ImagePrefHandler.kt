package org.primftpd.ui.util

import androidx.compose.material3.AlertDialogDefaults.containerColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

object ImagePrefHandler {

    /** 异步：用 Palette 六 profile 提取，结果回调给 onResult */
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
        Palette.from(android).generate { palette ->
            val argb = palette?.getVibrantColor(fallback.toArgb())
                ?: palette?.getLightVibrantColor(fallback.toArgb())
                ?: palette?.getMutedColor(fallback.toArgb())
                ?: fallback.toArgb()
            onResult(Color(argb))
        }
    }
}