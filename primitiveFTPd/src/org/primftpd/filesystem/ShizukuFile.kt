package org.primftpd.filesystem

import org.apache.sshd.common.file.SshFile
import org.primftpd.events.ClientActionEvent
import org.primftpd.shizuku.aidl.FileInfo
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import kotlin.math.min

/**
 * Shizuku-based file implementation using privileged UserService.
 * Replaces libsuperuser root shell approach.
 *
 * Definition, there are two type generic placeholders, among which TFileSystemView must be a subclass of AbstractFileSystemView.
 *  ~~So why did it choose this name? It makes people think of trademark tm or plant magic (mana) or something like that. lol~~
 *
 *  **This is a handle to remotely transfer the file name**
 *
 *  当主进程向 Shizuku 的服务端请求文件列表时，服务端只能把文件的基本信息（如名字、大小、读写权限等）序列化成一个数据结构传回给主 App，
 *  这个用来承载原始元数据的载体就是 FileInfo
 *
 *  It's different from TMina4
 */
abstract class ShizukuFile<TMina, TFileSystemView : AbstractFileSystemView>//Kotlin的类型声明
    //定义，有两个类型泛型占位符，其中TFileSystemView 必须是 AbstractFileSystemView 的子类
    //~~所以为什么起这个名字，这个容易让人想到商标tm或者植物魔法（mana）之类的~~
    //这是个远程传递文件名字的句柄
    (fileSystemView: TFileSystemView?, absPath: String?, protected val fileInfo: FileInfo)
    :
    AbstractFile<TFileSystemView>(fileSystemView, absPath, fileInfo.name) {
    
    init {
        logger.debug(
            "[ShizukuFile] Created: path={}, exists={}, isDir={}",
            absPath, fileInfo.exists(), fileInfo.isDirectory()
        )
    }

    protected abstract fun createFile(absPath: String?, fileInfo: FileInfo?): TMina?

    override fun getClientActionStorage(): ClientActionEvent.Storage {
        return ClientActionEvent.Storage.SHIZUKU
    }

    override fun isDirectory(): Boolean {
        return fileInfo.isDirectory()
    }

    override fun doesExist(): Boolean {
        return fileInfo.exists()
    }

    override fun isReadable(): Boolean {
        return fileInfo.canRead()
    }

    override fun getLastModified(): Long {
        return fileInfo.lastModified
    }

    override fun getSize(): Long {
        return fileInfo.size
    }

    override fun isFile(): Boolean {
        return fileInfo.isFile
    }

    override fun isWritable(): Boolean {
        if (fileInfo.exists()) {
            return fileInfo.canWrite()
        }

        // File does not exist yet — probably an upload of a new file.
        // Creating it requires write permission on the parent directory, so walk up
        // until we find an existing parent and check that. (Some clients, like
        // FileZilla, never issue mkdir commands — see FsFile.isWritable().)
        val serviceManager = (fileSystemView as? ShizukuFileSystemView<*, *>)?.serviceManager
            ?: return false

        var parentPath = parentPathOf(absPath)
        while (parentPath != null) {
            val parentInfo = serviceManager.stat(parentPath)
            if (parentInfo.exists()) {
                return parentInfo.canWrite()
            }
            parentPath = parentPathOf(parentPath)
        }
        return false
    }

    private fun parentPathOf(path: String?): String? {
        if (path.isNullOrEmpty() || path == "/") return null
        val idx = path.lastIndexOf('/')
        return when {
            idx < 0 -> null //Maybe it's not absolute path
            idx == 0 -> "/"
            else -> path.substring(0, idx)
        }
    }

    override fun isRemovable(): Boolean {
        return fileInfo.canWrite()
    }

    override fun setLastModified(time: Long): Boolean {
        logger.info("[ShizukuFile] setLastModified: path={}, time={}", absPath, time)
        val serviceManager = (fileSystemView as? ShizukuFileSystemView<*, *>)?.serviceManager
        val result = serviceManager?.setLastModified(absPath, time)
        logger.info("[ShizukuFile] setLastModified result: success={}", result?.isSuccess)
        return result?.isSuccess == true
    }

    override fun mkdir(): Boolean {
        logger.info("[ShizukuFile] mkdir: path={}", absPath)
        postClientAction(ClientActionEvent.ClientAction.CREATE_DIR)
        val serviceManager = (fileSystemView as? ShizukuFileSystemView<*, *>)?.serviceManager
        val result = serviceManager?.mkdir(absPath)
        logger.info("[ShizukuFile] mkdir result: success={}", result?.isSuccess)
        return result?.isSuccess == true
    }

    override fun delete(): Boolean {
        logger.info("[ShizukuFile] delete: path={}", absPath)
        postClientAction(ClientActionEvent.ClientAction.DELETE)
        val serviceManager = (fileSystemView as? ShizukuFileSystemView<*, *>)?.serviceManager
        val result = serviceManager?.delete(absPath)
        logger.info("[ShizukuFile] delete result: success={}", result?.isSuccess)
        return result?.isSuccess == true
    }

    override fun move(destination: AbstractFile<TFileSystemView>): Boolean {
        logger.info("[ShizukuFile] move: src={}, dst={}", absPath, destination.absolutePath)
        postClientAction(ClientActionEvent.ClientAction.RENAME)
        val serviceManager = (fileSystemView as? ShizukuFileSystemView<*, *>)?.serviceManager
        val result = serviceManager?.rename(absPath, destination.absolutePath)
        logger.info("[ShizukuFile] move result: success={}", result?.isSuccess)
        return result?.isSuccess == true
    }

    open fun listFiles(): MutableList<TMina?>? {
        logger.info("[ShizukuFile] listFiles: path={}", absPath)
        postClientAction(ClientActionEvent.ClientAction.LIST_DIR)

        return try {
            val serviceManager = (fileSystemView as? ShizukuFileSystemView<*, *>)?.serviceManager
            val files = serviceManager?.listFiles(absPath) ?: emptyList()
            logger.info("[ShizukuFile] listFiles got {} files", files.size)
            val result: MutableList<TMina?> = ArrayList(files.size)

            for (info in files) {
                logger.debug(
                    "[ShizukuFile] listFiles item: name={}, path={}",
                    info.name, info.absolutePath
                )
                result.add(createFile(info.absolutePath, info))
            }

            result
        } catch (e: Exception) {
            logger.error("[ShizukuFile] listFiles failed for: $absPath", e)
            ArrayList()
        }
    }

    @Throws(IOException::class)
    override fun createOutputStream(offset: Long): OutputStream {
        logger.info("[ShizukuFile] createOutputStream: path={}, offset={}", absPath, offset)
        postClientAction(ClientActionEvent.ClientAction.UPLOAD)

        return ShizukuOutputStream(absPath, offset)
    }

    @Throws(IOException::class)
    override fun createInputStream(offset: Long): InputStream {
        logger.info(
            "[ShizukuFile] createInputStream: path={}, offset={}, size={}",
            absPath, offset, fileInfo.size
        )
        postClientAction(ClientActionEvent.ClientAction.DOWNLOAD)

        return ShizukuInputStream(absPath, offset)
    }

    override fun readSymbolicLink(): String? {
        return fileInfo.symlinkTarget
    }

    @Throws(IOException::class)
    override fun getAttribute(attribute: SshFile.Attribute, followLinks: Boolean): Any? {
        logger.debug("[ShizukuFile] getAttribute: path={}, attr={}", absPath, attribute)

        return try {
            when (attribute) {
                SshFile.Attribute.Size -> fileInfo.size
                SshFile.Attribute.Uid -> 0 // root uid
                SshFile.Attribute.Owner -> "root"
                SshFile.Attribute.Gid -> 0 // root gid
                SshFile.Attribute.Group -> "root"
                SshFile.Attribute.IsDirectory -> fileInfo.isDirectory()
                SshFile.Attribute.IsRegularFile -> fileInfo.isFile()
                SshFile.Attribute.IsSymbolicLink -> fileInfo.isSymlink()
                SshFile.Attribute.Permissions -> {
                    val perms = mutableSetOf<SshFile.Permission>()
                    if (fileInfo.canRead()) {
                        perms.add(SshFile.Permission.UserRead)
                        perms.add(SshFile.Permission.GroupRead)
                        perms.add(SshFile.Permission.OthersRead)
                    }
                    if (fileInfo.canWrite()) {
                        perms.add(SshFile.Permission.UserWrite)
                        perms.add(SshFile.Permission.GroupWrite)
                    }
                    if (fileInfo.canExecute() || fileInfo.isDirectory()) {
                        perms.add(SshFile.Permission.UserExecute)
                        perms.add(SshFile.Permission.GroupExecute)
                        perms.add(SshFile.Permission.OthersExecute)
                    }
                    if (perms.isEmpty()) EnumSet.noneOf(SshFile.Permission::class.java) else EnumSet.copyOf(perms)
                }
                SshFile.Attribute.CreationTime -> fileInfo.lastModified
                SshFile.Attribute.LastModifiedTime -> fileInfo.lastModified
                SshFile.Attribute.LastAccessTime -> fileInfo.lastModified
                SshFile.Attribute.NLink -> 1
            }
        } catch (e: Exception) {
            logger.error("[ShizukuFile] getAttribute failed for: $absPath, attr: $attribute", e)
            throw IOException("Failed to get attribute: $attribute", e)
        }
    }

    @Throws(IOException::class)
    override fun getAttributes(followLinks: Boolean): MutableMap<SshFile.Attribute, Any> {
        logger.debug("[ShizukuFile] getAttributes: path={}", absPath)

        return try {
            val attributes = mutableMapOf<SshFile.Attribute, Any>()
            for (attr in SshFile.Attribute.entries) {
                val value = getAttribute(attr, followLinks)
                if (value != null) {
                    attributes[attr] = value
                }
            }
            logger.debug("[ShizukuFile] getAttributes returning {} attributes", attributes.size)
            attributes
        } catch (e: Exception) {
            logger.error("[ShizukuFile] getAttributes failed for: $absPath", e)
            throw IOException("Failed to get attributes", e)
        }
    }

    /**
     * OutputStream implementation for Shizuku file writing.
     * Writes in fixed-size chunks to avoid buffering the entire file in memory.
     */
    private inner class ShizukuOutputStream(private val path: String?, private val offset: Long) :
        OutputStream() {
        private val buffer = ByteArray(CHUNK_SIZE)
        private var bufferPos = 0
        private var isFirstWrite = true

        init {
            logger.debug("[ShizukuOutputStream] Created: path={}, offset={}", path, offset)
        }

        @Throws(IOException::class)
        override fun write(b: Int) {
            if (bufferPos == buffer.size) flushBuffer()
            buffer[bufferPos++] = b.toByte()
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            var remaining = len
            var srcPos = off
            while (remaining > 0) {
                val space = buffer.size - bufferPos
                val toCopy = minOf(remaining, space)
                System.arraycopy(b, srcPos, buffer, bufferPos, toCopy)
                bufferPos += toCopy
                srcPos += toCopy
                remaining -= toCopy
                if (bufferPos == buffer.size) flushBuffer()
            }
        }

        @Throws(IOException::class)
        override fun close() {
            try {
                flushBuffer()
            } catch (e: Exception) {
                logger.error("[ShizukuOutputStream] close failed for: $path", e)
                throw IOException("Failed to write file", e)
            } finally {
                super.close()
            }
        }

        @Throws(IOException::class)
        private fun flushBuffer() {
            if (bufferPos == 0) return
            val data = buffer.copyOf(bufferPos)
            logger.debug(
                "[ShizukuOutputStream] flush: path={}, dataSize={}, firstWrite={}, offset={}",
                path, data.size, isFirstWrite, offset
            )

            val serviceManager = (fileSystemView as? ShizukuFileSystemView<*, *>)?.serviceManager
            val writeOffset = if (isFirstWrite) offset else 0
            val result = serviceManager?.writeFile(path, data, writeOffset, !isFirstWrite)

            if (result?.isSuccess != true) {
                logger.error("[ShizukuOutputStream] Write failed: {}", result?.errorMessage)
                throw IOException("Write failed: ${result?.errorMessage}")
            }

            bufferPos = 0
            isFirstWrite = false
            logger.debug("[ShizukuOutputStream] flush successful")
        }
    }

    /**
     * InputStream implementation for Shizuku file reading
     */
    private inner class ShizukuInputStream(private val path: String?, private var position: Long) :
        InputStream() {
        private var buffer: ByteArray? = null
        private var bufferPos = 0
        private var eof = false

        init {
            logger.debug("[ShizukuInputStream] Created: path={}, offset={}", path, position)
        }

        @Throws(IOException::class)
        override fun read(): Int {
            if (eof) {
                return -1
            }

            if (buffer == null || bufferPos >= buffer!!.size) {
                fillBuffer()
                if (buffer == null || buffer!!.isEmpty()) {
                    eof = true
                    return -1
                }
            }
            return buffer!![bufferPos++].toInt() and 0xFF
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) {
                return 0
            }
            if (eof) {
                return -1
            }

            var totalRead = 0
            while (totalRead < len) {
                if (buffer == null || bufferPos >= buffer!!.size) {
                    fillBuffer()
                    if (buffer == null || buffer!!.isEmpty()) {
                        eof = true
                        return if (totalRead == 0) -1 else totalRead
                    }
                }

                val available = buffer!!.size - bufferPos
                val toRead = min(available, len - totalRead)
                System.arraycopy(buffer!!, bufferPos, b, off + totalRead, toRead)
                bufferPos += toRead
                totalRead += toRead
            }

            return totalRead
        }

        @Throws(IOException::class)
        fun fillBuffer() {
            try {
                logger.debug(
                    "[ShizukuInputStream] fillBuffer: path={}, position={}, chunkSize={}",
                    path, position, CHUNK_SIZE
                )

                val serviceManager = (fileSystemView as? ShizukuFileSystemView<*, *>)?.serviceManager
                buffer = serviceManager?.readFile(path, position, CHUNK_SIZE)
                bufferPos = 0

                logger.debug("[ShizukuInputStream] fillBuffer got {} bytes", buffer?.size ?: 0)

                if (buffer?.isEmpty() != false) {
                    eof = true
                } else {
                    position += buffer!!.size.toLong()
                }
            } catch (e: Exception) {
                logger.error("[ShizukuInputStream] fillBuffer failed for: $path, position: $position", e)
                throw IOException("Failed to read file", e)
            }
        }
    }

    companion object {
        private const val CHUNK_SIZE = 64 * 1024
    }
}
