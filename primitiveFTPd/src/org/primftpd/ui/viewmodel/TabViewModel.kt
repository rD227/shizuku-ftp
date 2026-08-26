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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.primftpd.ui.data.TabState
import java.io.File

class TabViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TabState(isServerRunning = false))
    val uiState = _uiState.asStateFlow()

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    val wallpaper = _wallpaper.asStateFlow()

    private val _navigationEvent = MutableStateFlow<String?>(null)
    val navigationEvent = _navigationEvent.asStateFlow()

    init {
        loadWallpaper()
    }

    fun updateServerRunning(running: Boolean) {
        _uiState.update { it.copy(isServerRunning = running) }
    }

    fun updateWallpaper(bitmap: ImageBitmap?) {
        _wallpaper.value = bitmap
    }

    fun loadWallpaper() {
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

    fun onNavigate(destination: String) {
        viewModelScope.launch {
            _navigationEvent.emit(destination)
        }
    }
}