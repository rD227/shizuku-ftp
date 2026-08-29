package org.primftpd.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.primftpd.R
import java.io.File

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    val wallpaper = _wallpaper.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val prefs = ctx.getSharedPreferences("main_wallpaper", Context.MODE_PRIVATE)
            val path = prefs.getString("wallpaper_path", null)
            val bitmap = if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists()) {
                    runCatching {
                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                    }.getOrNull()
                } else {
                    null
                }
            } else {
                null
            }
            // If no user-selected wallpaper is available yet, fall back to the bundled
            // default background so color extraction works from the very first launch.
            _wallpaper.value = bitmap ?: decodeDefaultWallpaper(ctx)
        }
    }

    private fun decodeDefaultWallpaper(ctx: Context): ImageBitmap? = runCatching {
        // The bundled background is very large (4000x4249). Decode with a sample size so
        // it won't OOM on low-memory devices and still gives Palette enough pixels.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(ctx.resources, R.drawable.my_background, bounds)
        var sampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sampleSize > 1080) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inScaled = false
        }
        BitmapFactory.decodeResource(ctx.resources, R.drawable.my_background, opts)?.asImageBitmap()
    }.getOrNull()

    fun update(bitmap: ImageBitmap?) {
        _wallpaper.value = bitmap
    }
}