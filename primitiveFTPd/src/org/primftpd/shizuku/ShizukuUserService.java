package org.primftpd.shizuku;

import android.util.Log;

import androidx.annotation.Keep;

import org.primftpd.shizuku.aidl.IShizukuFileService;
import org.primftpd.shizuku.aidl.FileInfo;
import org.primftpd.shizuku.aidl.FileOperationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shizuku UserService implementation running in privileged process.
 * This service has root-level file access via Shizuku framework.
 */
@Keep
public class ShizukuUserService extends IShizukuFileService.Stub {

    private static final String TAG = "ShizukuUserService";
    private static final int SERVICE_VERSION = 1;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ShizukuUserService() {
        super();
        Log.i(TAG, "[ShizukuUserService] Service created <<< Debug");
        logger.info("[ShizukuUserService]Service created");
    }

    @Override
    public FileInfo stat(String absolutePath) {
        Log.d(TAG, "[stat] path=" + absolutePath);
        
        try {
            File file = new File(absolutePath);
            String name = file.getName();// /emulated/storage/0/name -> name // / -> ""
            if (name.isEmpty()) {
                name = absolutePath;
            }

            if (!file.exists()) {
                Log.d(TAG, "[stat] File does not exist: " + absolutePath);
                return FileInfo.nonExistent(absolutePath, name);
            }

            boolean isSymlink = Files.isSymbolicLink(file.toPath());
            String symlinkTarget = null;
            if (isSymlink) {
                try {
                    Path target = Files.readSymbolicLink(file.toPath());
                    symlinkTarget = target.toString();
                } catch (IOException e) {
                    Log.w(TAG, "[stat] Failed to read symlink: " + absolutePath, e);
                }
            }

            FileInfo result = new FileInfo(
                    absolutePath,
                    name,
                    true,
                    file.isFile(),
                    file.isDirectory(),
                    isSymlink,
                    file.length(),
                    file.lastModified(),
                    file.canRead(),
                    true,// didn't do any judgment in fact
                    file.canExecute(),
                    symlinkTarget
            );
            
            Log.d(TAG, "[stat] Success: exists=" + result.exists() +
                    ", isFile=" + result.isFile() + 
                    ", isDir=" + result.isDirectory() + 
                    ", size=" + result.getSize());
            
            return result;
        } catch (Exception e) {
            Log.e(TAG, "[stat] Failed for: " + absolutePath, e);
            return FileInfo.nonExistent(absolutePath, extractName(absolutePath));
        }
    }

    @Override
    public List<FileInfo> listFiles(String absolutePath) {
        Log.d(TAG, "[listFiles] path=" + absolutePath);
        
        try {
            File dir = new File(absolutePath);
            List<FileInfo> result = new ArrayList<>();

            if (!dir.exists() || !dir.isDirectory()) {
                Log.w(TAG, "[listFiles] Not a directory or doesn't exist: " + absolutePath);
                return result;
            }

            File[] files = dir.listFiles();
            if (files == null) {
                Log.w(TAG, "[listFiles] listFiles() returned null for: " + absolutePath);
                return result;
            }

            Log.d(TAG, "[listFiles] Found " + files.length + " files");
            
            for (File file : files) {
                result.add(stat(file.getAbsolutePath()));
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "[listFiles] Failed for: " + absolutePath, e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean exists(String absolutePath) {
        try {
            boolean result = new File(absolutePath).exists();
            Log.d(TAG, "[exists] path=" + absolutePath + ", result=" + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "[exists] Failed for: " + absolutePath, e);
            return false;
        }
    }

    @Override
    public FileOperationResult mkdir(String absolutePath) {
        Log.d(TAG, "[mkdir] path=" + absolutePath);
        
        try {
            File dir = new File(absolutePath);
            if (dir.exists()) {
                Log.w(TAG, "[mkdir] Directory already exists: " + absolutePath);
                return FileOperationResult.failure("Directory already exists");
            }
            boolean success = dir.mkdirs();
            Log.d(TAG, "[mkdir] Result: " + success);
            return success ? FileOperationResult.success() 
                          : FileOperationResult.failure("Failed to create directory");
        } catch (Exception e) {
            Log.e(TAG, "[mkdir] Failed for: " + absolutePath, e);
            return FileOperationResult.failure("mkdir failed: " + e.getMessage());
        }
    }

    @Override
    public FileOperationResult delete(String absolutePath) {
        Log.d(TAG, "[delete] path=" + absolutePath);
        
        try {
            File file = new File(absolutePath);
            if (!file.exists()) {
                Log.w(TAG, "[delete] File does not exist: " + absolutePath);
                return FileOperationResult.failure("File does not exist");
            }
            
            boolean success = deleteRecursive(file);
            Log.d(TAG, "[delete] Result: " + success);
            return success ? FileOperationResult.success() 
                          : FileOperationResult.failure("Failed to delete");
        } catch (Exception e) {
            Log.e(TAG, "[delete] Failed for: " + absolutePath, e);
            return FileOperationResult.failure("delete failed: " + e.getMessage());
        }
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    @Override
    public FileOperationResult rename(String oldPath, String newPath) {
        Log.d(TAG, "[rename] from=" + oldPath + ", to=" + newPath);
        
        try {
            File oldFile = new File(oldPath);
            File newFile = new File(newPath);
            
            if (!oldFile.exists()) {
                Log.w(TAG, "[rename] Source file does not exist: " + oldPath);
                return FileOperationResult.failure("Source file does not exist");
            }
            if (newFile.exists()) {
                Log.w(TAG, "[rename] Target file already exists: " + newPath);
                return FileOperationResult.failure("Target file already exists");
            }
            
            boolean success = oldFile.renameTo(newFile);
            Log.d(TAG, "[rename] Result: " + success);
            return success ? FileOperationResult.success() 
                          : FileOperationResult.failure("Failed to rename");
        } catch (Exception e) {
            Log.e(TAG, "[rename] Failed from " + oldPath + " to " + newPath, e);
            return FileOperationResult.failure("rename failed: " + e.getMessage());
        }
    }

    @Override
    public byte[] readFile(String absolutePath, long offset, int length) {
        Log.d(TAG, "[readFile] path=" + absolutePath + ", offset=" + offset + ", length=" + length);
        
        try {
            File file = new File(absolutePath);
            if (!file.exists() || !file.isFile()) {
                Log.w(TAG, "[readFile] File does not exist or is not a file: " + absolutePath);
                return new byte[0];
            }

            long fileSize = file.length();
            Log.d(TAG, "[readFile] File size: " + fileSize);

            try (FileInputStream fis = new FileInputStream(file)) {
                if (offset > 0) {
                    long skipped = fis.skip(offset);
                    Log.d(TAG, "[readFile] Skipped " + skipped + " bytes");
                }
                
                int toRead = length > 0 ? Math.min(length, (int)(fileSize - offset)) 
                                       : (int)(fileSize - offset);
                
                if (toRead <= 0) {
                    Log.d(TAG, "[readFile] Nothing to read (toRead=" + toRead + ")");
                    return new byte[0];
                }
                
                Log.d(TAG, "[readFile] Reading " + toRead + " bytes");
                byte[] buffer = new byte[toRead];
                int bytesRead = fis.read(buffer);
                
                Log.d(TAG, "[readFile] Actually read " + bytesRead + " bytes");
                
                if (bytesRead < toRead) {
                    byte[] result = new byte[bytesRead];
                    System.arraycopy(buffer, 0, result, 0, bytesRead);
                    return result;
                }
                return buffer;
            }
        } catch (Exception e) {
            Log.e(TAG, "[readFile] Failed for: " + absolutePath, e);
            return new byte[0];
        }
    }

    @Override
    public FileOperationResult writeFile(String absolutePath, byte[] data, long offset, boolean append) {
        Log.d(TAG, "[writeFile] path=" + absolutePath + ", dataSize=" + data.length + 
                ", offset=" + offset + ", append=" + append);
        
        try {
            File file = new File(absolutePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                Log.d(TAG, "[writeFile] Creating parent directories");
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(file, append)) {
                if (offset > 0 && !append) {
                    fos.getChannel().position(offset);
                }
                fos.write(data);
                Log.d(TAG, "[writeFile] Success");
                return FileOperationResult.success();
            }
        } catch (Exception e) {
            Log.e(TAG, "[writeFile] Failed for: " + absolutePath, e);
            return FileOperationResult.failure("writeFile failed: " + e.getMessage());
        }
    }

    @Override
    public FileOperationResult setLastModified(String absolutePath, long timestamp) {
        Log.d(TAG, "[setLastModified] path=" + absolutePath + ", timestamp=" + timestamp);
        
        try {
            File file = new File(absolutePath);
            if (!file.exists()) {
                Log.w(TAG, "[setLastModified] File does not exist: " + absolutePath);
                return FileOperationResult.failure("File does not exist");
            }
            
            boolean success = file.setLastModified(timestamp);
            Log.d(TAG, "[setLastModified] Result: " + success);
            return success ? FileOperationResult.success() 
                          : FileOperationResult.failure("Failed to set last modified time");
        } catch (Exception e) {
            Log.e(TAG, "[setLastModified] Failed for: " + absolutePath, e);
            return FileOperationResult.failure("setLastModified failed: " + e.getMessage());
        }
    }

    @Override
    public int getVersion() {
        return SERVICE_VERSION;
    }

    @Override
    public void destroy() {
        Log.i(TAG, "[destroy] Service destroying");
        System.exit(0);
    }

    private String extractName(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return "";
        }
        int lastSlash = absolutePath.lastIndexOf('/');
        return lastSlash >= 0 ? absolutePath.substring(lastSlash + 1) : absolutePath;
    }
}
