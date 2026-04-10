package org.primftpd.shizuku.aidl;

import org.primftpd.shizuku.aidl.FileInfo;
import org.primftpd.shizuku.aidl.FileOperationResult;

/**
 * AIDL interface for Shizuku privileged file operations.
 * This service runs in a privileged process via Shizuku UserService.
 */
interface IShizukuFileService {
    /**
     * Get file/directory information
     */
    FileInfo stat(String absolutePath);

    /**
     * List directory contents
     */
    List<FileInfo> listFiles(String absolutePath);

    /**
     * Check if file/directory exists
     */
    boolean exists(String absolutePath);

    /**
     * Create directory
     */
    FileOperationResult mkdir(String absolutePath);

    /**
     * Delete file or directory
     */
    FileOperationResult delete(String absolutePath);

    /**
     * Rename/move file or directory
     */
    FileOperationResult rename(String oldPath, String newPath);

    /**
     * Read file content (returns byte array)
     */
    byte[] readFile(String absolutePath, long offset, int length);

    /**
     * Write file content
     */
    FileOperationResult writeFile(String absolutePath, in byte[] data, long offset, boolean append);

    /**
     * Set file last modified time
     */
    FileOperationResult setLastModified(String absolutePath, long timestamp);

    /**
     * Get service version for compatibility check
     */
    int getVersion();

    /**
     * Destroy service (cleanup)
     */
    void destroy();
}
