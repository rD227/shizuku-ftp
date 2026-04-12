package org.primftpd.shizuku.data

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

// Ready to @Parcelize to refactor
// 需要在 build.gradle 中启用 kotlin-parcelize 插件
//

/**
 * Parcelable file information for cross-process communication via AIDL.
 * Replaces LsOutputBean for Shizuku service.
 */
class FileInfo : Parcelable {
    // Getters
    val absolutePath: String?
    val name: String?
    private val exists: Boolean
    val isFile: Boolean
    val isDirectory: Boolean
    val isSymlink: Boolean
    val size: Long
    val lastModified: Long
    private val canRead: Boolean
    private val canWrite: Boolean
    private val canExecute: Boolean
    val symlinkTarget: String?

    constructor(
        absolutePath: String?, name: String?, exists: Boolean, isFile: Boolean,
        isDirectory: Boolean, isSymlink: Boolean, size: Long, lastModified: Long,
        canRead: Boolean, canWrite: Boolean, canExecute: Boolean, symlinkTarget: String?
    ) {
        this.absolutePath = absolutePath
        this.name = name
        this.exists = exists
        this.isFile = isFile
        this.isDirectory = isDirectory
        this.isSymlink = isSymlink
        this.size = size
        this.lastModified = lastModified
        this.canRead = canRead
        this.canWrite = canWrite
        this.canExecute = canExecute
        this.symlinkTarget = symlinkTarget
    }

    protected constructor(inPut: Parcel) {
        absolutePath = inPut.readString()
        name = inPut.readString()
        exists = inPut.readByte().toInt() != 0
        isFile = inPut.readByte().toInt() != 0
        isDirectory = inPut.readByte().toInt() != 0
        isSymlink = inPut.readByte().toInt() != 0
        size = inPut.readLong()
        lastModified = inPut.readLong()
        canRead = inPut.readByte().toInt() != 0
        canWrite = inPut.readByte().toInt() != 0
        canExecute = inPut.readByte().toInt() != 0
        symlinkTarget = inPut.readString()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(absolutePath)
        dest.writeString(name)
        dest.writeByte((if (exists) 1 else 0).toByte())
        dest.writeByte((if (isFile) 1 else 0).toByte())
        dest.writeByte((if (isDirectory) 1 else 0).toByte())
        dest.writeByte((if (isSymlink) 1 else 0).toByte())
        dest.writeLong(size)
        dest.writeLong(lastModified)
        dest.writeByte((if (canRead) 1 else 0).toByte())
        dest.writeByte((if (canWrite) 1 else 0).toByte())
        dest.writeByte((if (canExecute) 1 else 0).toByte())
        dest.writeString(symlinkTarget)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun exists(): Boolean {
        return exists
    }

    fun canRead(): Boolean {
        return canRead
    }

    fun canWrite(): Boolean {
        return canWrite
    }

    fun canExecute(): Boolean {
        return canExecute
    }

    companion object {
        @JvmField
        val CREATOR: Creator<FileInfo?> = object : Creator<FileInfo?> { //相当于Java内部类new接口
            override fun createFromParcel(`in`: Parcel): FileInfo {
                return FileInfo(`in`)
            }

            override fun newArray(size: Int): Array<FileInfo?> {
                return arrayOfNulls<FileInfo>(size)
            }
        }

        /**
         * Create a non-existent FileInfo
         */
        fun nonExistent(absolutePath: String?, name: String?): FileInfo {
            return FileInfo(
                absolutePath, name, false, false, false, false,
                0, 0, false, false, false, null
            )
        }
    }
}
