package org.primftpd.shizuku.data

import android.os.Parcelable

import kotlinx.parcelize.Parcelize

// Ready to @Parcelize to refactor
// 需要在 build.gradle 中启用 kotlin-parcelize 插件
//

/**
 * Parcelable file information for cross-process communication via AIDL.
 * Replaces LsOutputBean for Shizuku service.
 */

@Parcelize
class FileInfo(
    val absolutePath: String?,
    val name: String?,
    private val exists: Boolean,
    val isFile: Boolean,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val lastModified: Long,
    private val canRead: Boolean,
    private val canWrite: Boolean,
    private val canExecute: Boolean,
    val symlinkTarget: String?
) : Parcelable {
    // Getters
    companion object {
        fun nonExistent(absolutePath: String?, name: String?): FileInfo {
            return FileInfo(
                absolutePath = absolutePath,
                name = name,
                exists = false,
                isFile = false,
                isDirectory = false,
                isSymlink = false,
                size = 0,
                lastModified = 0,
                canRead = false,
                canWrite = false,
                canExecute = false,
                symlinkTarget = null,
            )
        }
    }
}
