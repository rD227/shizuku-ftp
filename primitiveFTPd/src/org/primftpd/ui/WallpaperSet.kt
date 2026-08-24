package org.primftpd.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


@Composable
fun rememberWallpaperPicker(
    onLoaded: (ImageBitmap?) -> Unit
): ManagedActivityResultLauncher<String, Uri?> {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = saveWallpaperToLocal(context, uri)
                val bitmap = savedPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                if (bitmap != null) onLoaded(bitmap)
                else Toast.makeText(context, "Failed to import wallpaper", Toast.LENGTH_SHORT).show()
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
