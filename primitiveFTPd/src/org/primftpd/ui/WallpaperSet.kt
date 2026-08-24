package org.primftpd.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.copyTo

@Composable
fun WallpaperComposable() {
    var wallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedPath = saveWallpaperToLocal(context, uri)
                if (savedPath != null) {
                    wallpaperBitmap = BitmapFactory.decodeFile(savedPath)?.asImageBitmap()
                } else {
                    Toast.makeText(context, "Failed to import wallpaper", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
@SuppressLint("UseKtx")
internal suspend fun saveWallpaperToLocal(context: Context, sourceUri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "wallpaper").apply { mkdirs() }
            val target = File(dir, "main_wallpaper")
            val tmp = File(dir, "main_wallpaper.tmp")
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "Unable to open wallpaper source: $sourceUri" }
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target)) { "Failed to swap wallpaper temp file" }
            }
            context.getSharedPreferences("main_wallpaper", Context.MODE_PRIVATE)
                .edit(commit = true) { putString("wallpaper_path", target.absolutePath) }
            target.absolutePath
        }.getOrNull()
    }
