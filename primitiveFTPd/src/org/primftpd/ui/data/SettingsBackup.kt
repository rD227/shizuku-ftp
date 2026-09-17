package org.primftpd.ui.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 把默认 SharedPreferences、自定义 UI SharedPreferences 和 heroImage 壁纸
 * 导出到用户选择的目录，或从该目录导入。
 *
 * 备份文件是一个 zip：
 *  - settings.json：所有 SharedPreferences
 *  - wallpaper：heroImage 壁纸原始文件，如果用户没有自定义壁纸则不存在
 */
object SettingsBackup {

    const val FILE_NAME = "shizuku-ftp-backup.zip"

    private const val SETTINGS_ENTRY = "settings.json"
    private const val WALLPAPER_ENTRY = "wallpaper"

    private const val WALLPAPER_PREFS = "main_wallpaper"
    private const val WALLPAPER_PATH_KEY = "wallpaper_path"
    private const val WALLPAPER_DIR = "wallpaper"
    private const val WALLPAPER_FILE_NAME = "main_wallpaper"

    private const val FORMAT_VERSION = 3

    /**
     * 这些设置没有存在默认 SharedPreferences 里，而是各自独立的文件。
     * main_wallpaper 单独处理，不放进这里，避免导入后路径指向旧设备。
     */
    private val CUSTOM_PREF_FILES = listOf(
        "ui_state",
        "blur_intensity",
        "usr_m3_to_pick_colors",
        "glass_side_menu_wallpaper",
        "side_menu_spring_animation",
        "experimental_haze",
    )

    fun export(
        context: Context,
        prefs: SharedPreferences,
        treeUri: Uri,
    ): Result<Unit> = runCatching {
        val directory = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Cannot open selected directory")

        val file = findBackupFile(directory)
            ?: directory.createFile("application/zip", FILE_NAME)
            ?: error("Cannot create $FILE_NAME")

        val json = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("prefs", prefs.all.toJson())
            put("customPrefs", customPrefsToJson(context))
        }.toString(2)

        context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                writeEntry(zip, SETTINGS_ENTRY, json.toByteArray(Charsets.UTF_8))

                val wallpaperFile = currentWallpaperFile(context)
                if (wallpaperFile != null && wallpaperFile.exists() && wallpaperFile.length() > 0L) {
                    zip.putNextEntry(ZipEntry(WALLPAPER_ENTRY))
                    wallpaperFile.inputStream().use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Cannot open output stream")
    }

    fun import(
        context: Context,
        prefs: SharedPreferences,
        treeUri: Uri,
    ): Result<Unit> = runCatching {
        val directory = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Cannot open selected directory")

        val file = findBackupFile(directory)
            ?: error("Backup file not found: $FILE_NAME")

        var settingsJson: JSONObject? = null
        var wallpaperBytes: ByteArray? = null

        context.contentResolver.openInputStream(file.uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        SETTINGS_ENTRY -> {
                            settingsJson = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        WALLPAPER_ENTRY -> {
                            wallpaperBytes = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Cannot open input stream")

        val root = settingsJson ?: error("settings.json not found in backup")

        // 默认 SharedPreferences
        applyPrefs(prefs, root.getJSONObject("prefs"))

        // 独立的 UI SharedPreferences。旧版本备份没有 customPrefs，这里会直接跳过。
        root.optJSONObject("customPrefs")?.let { customPrefs ->
            CUSTOM_PREF_FILES.forEach { name ->
                val fileJson = customPrefs.optJSONObject(name) ?: return@forEach
                val targetPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                applyPrefs(targetPrefs, fileJson)
            }
        }

        // 壁纸存在时才恢复，否则保留当前壁纸。
        val restoredWallpaperBytes = wallpaperBytes
        if (restoredWallpaperBytes != null && restoredWallpaperBytes.isNotEmpty()) {
            val target = targetWallpaperFile(context)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeBytes(restoredWallpaperBytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target)) { "Failed to restore wallpaper" }
            }
            context.getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    putString(WALLPAPER_PATH_KEY, target.absolutePath)
                }
        }
    }

    private fun customPrefsToJson(context: Context): JSONObject {
        val result = JSONObject()
        CUSTOM_PREF_FILES.forEach { name ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            result.put(name, prefs.all.toJson())
        }
        return result
    }

    private fun applyPrefs(prefs: SharedPreferences, json: JSONObject) {
        // 先把所有类型都解析出来，避免文件一半不合法时污染现有设置。
        val entries = json.keys().asSequence()
            .map { key -> key to json.getJSONObject(key) }
            .toList()

        val editor = prefs.edit().clear()
        entries.forEach { (key, entry) ->
            when (entry.getString("type")) {
                "boolean" -> editor.putBoolean(key, entry.getBoolean("value"))
                "string" -> editor.putString(key, entry.getString("value"))
                "int" -> editor.putInt(key, entry.getInt("value"))
                "long" -> editor.putLong(key, entry.getLong("value"))
                "float" -> editor.putFloat(key, entry.getDouble("value").toFloat())
                "string_set" -> {
                    val array = entry.getJSONArray("value")
                    val values = buildSet {
                        for (index in 0 until array.length()) {
                            add(array.getString(index))
                        }
                    }
                    editor.putStringSet(key, values)
                }
                else -> error("Unsupported preference type for key: $key")
            }
        }

        if (!editor.commit()) {
            error("Failed to save imported preferences")
        }
    }

    private fun findBackupFile(directory: DocumentFile): DocumentFile? {
        val baseName = FILE_NAME.removeSuffix(".zip")

        directory.findFile(FILE_NAME)?.let { return it }
        directory.findFile(baseName)?.let { return it }

        // 某些 SAF provider 会给 zip 再加一次扩展名，所以兜底扫描一遍。
        return directory.listFiles().firstOrNull { child ->
            child.isFile && child.name?.startsWith(baseName) == true
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun currentWallpaperFile(context: Context): File? {
        val path = context.getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE)
            .getString(WALLPAPER_PATH_KEY, null)
        return path?.takeIf { it.isNotBlank() }?.let(::File)
    }

    private fun targetWallpaperFile(context: Context): File {
        val directory = File(context.filesDir, WALLPAPER_DIR).apply { mkdirs() }
        return File(directory, WALLPAPER_FILE_NAME)
    }

    private fun Map<String, *>.toJson(): JSONObject {
        val result = JSONObject()
        forEach { (key, value) ->
            val jsonValue = when (value) {
                is Boolean -> JSONObject()
                    .put("type", "boolean")
                    .put("value", value)

                is String -> JSONObject()
                    .put("type", "string")
                    .put("value", value)

                is Int -> JSONObject()
                    .put("type", "int")
                    .put("value", value)

                is Long -> JSONObject()
                    .put("type", "long")
                    .put("value", value)

                is Float -> JSONObject()
                    .put("type", "float")
                    .put("value", value.toDouble())

                is Set<*> -> JSONObject()
                    .put("type", "string_set")
                    .put(
                        "value",
                        JSONArray().apply {
                            value.forEach { item ->
                                if (item is String) put(item)
                                else put(item.toString())
                            }
                        },
                    )

                else -> null
            }

            if (jsonValue != null) {
                result.put(key, jsonValue)
            }
        }
        return result
    }
}
