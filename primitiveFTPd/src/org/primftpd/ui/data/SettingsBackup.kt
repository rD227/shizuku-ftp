package org.primftpd.ui.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 把默认 SharedPreferences 导出到用户选择的目录，或从该目录导入。
 *
 * 当前只需要支持 SharedPreferences 的 5 种值类型：
 * Boolean、String、Int、Long、Float、Set<String>。
 */
object SettingsBackup {

    const val FILE_NAME = "shizuku-ftp-settings.json"

    private const val FORMAT_VERSION = 1

    fun export(
        context: Context,
        prefs: SharedPreferences,
        treeUri: Uri,
    ): Result<Unit> = runCatching {
        val directory = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Cannot open selected directory")

        val file = directory.findFile(FILE_NAME)
            ?: directory.createFile("application/json", FILE_NAME)
            ?: error("Cannot create $FILE_NAME")

        val root = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("prefs", prefs.all.toJson())
        }

        context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.write(root.toString(2))
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

        val file = directory.findFile(FILE_NAME)
            ?: error("Backup file not found: $FILE_NAME")

        val text = context.contentResolver.openInputStream(file.uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        } ?: error("Cannot open input stream")

        val root = JSONObject(text)
        val prefsJson = root.getJSONObject("prefs")

        // 先把所有类型都解析出来，避免文件一半不合法时污染现有设置。
        val entries = prefsJson.keys().asSequence()
            .map { key -> key to prefsJson.getJSONObject(key) }
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
