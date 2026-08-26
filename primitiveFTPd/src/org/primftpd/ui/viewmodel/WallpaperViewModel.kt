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
            if (path.isNullOrBlank()) return@launch
            val file = File(path)
            if (!file.exists()) return@launch
            _wallpaper.value = runCatching {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }

    fun update(bitmap: ImageBitmap?) {
        _wallpaper.value = bitmap
    }
}