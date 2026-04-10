package org.primftpd.shizuku.service;

import org.primftpd.shizuku.aidl.IShizukuFileService;
import org.primftpd.shizuku.aidl.FileInfo;
import org.primftpd.shizuku.aidl.FileOperationResult;

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
public class ShizukuUserService extends IShizukuFileService.Stub {

    private static final int SERVICE_VERSION = 1;

    public ShizukuUserService() {
        super();
    }

    @Override
    public FileInfo stat(String absolutePath) {
        try {
            File file = new File(absolutePath);
            String name = file.getName();
            if (name.isEmpty()) {
                name = absolutePath;
            }

            if (!file.exists()) {
                return FileInfo.nonExistent(absolutePath, name);
            }

            boolean isSymlink = Files.isSymbolicLink(file.toPath());
            String symlinkTarget = null;
            if (isSymlink) {
                try {
                    Path target = Files.readSymbolicLink(file.toPath());
                    symlinkTarget = target.toString();
                } catch (IOException e) {
                    // Ignore
                }
            }

            return new FileInfo(
                    absolutePath,
                    name,
                    true,
                    file.isFile(),
                    file.isDirectory(),
                    isSymlink,
                    file.length(),
                    file.lastModified(),
                    file.canRead(),
                    file.canWrite(),
                    file.canExecute(),
                    symlinkTarget
            );
        } catch (Exception e) {
            return FileInfo.nonExistent(absolutePath, extractName(absolutePath));
        }
    }

    @Override
    public List<FileInfo> listFiles(String absolutePath) {
        try {
            File dir = new File(absolutePath);
            List<FileInfo> result = new ArrayList<>();

            if (!dir.exists() || !dir.isDirectory()) {
                return result;
            }

            File[] files = dir.listFiles();
            if (files == null) {
                return result;
            }

            for (File file : files) {
                result.add(stat(file.getAbsolutePath()));
            }

            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public boolean exists(String absolutePath) {
        try {
            return new File(absolutePath).exists();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public FileOperationResult mkdir(String absolutePath) {
        try {
            File dir = new File(absolutePath);
            if (dir.exists()) {
                return FileOperationResult.failure("Directory already exists");
            }
            boolean success = dir.mkdirs();
            return success ? FileOperationResult.success() 
                          : FileOperationResult.failure("Failed to create directory");
        } catch (Exception e) {
            return FileOperationResult.failure("mkdir failed: " + e.getMessage());
        }
    }

    @Override
    public FileOperationResult delete(String absolutePath) {
        try {
            File file = new File(absolutePath);
            if (!file.exists()) {
                return FileOperationResult.failure("File does not exist");
            }
            
            boolean success = deleteRecursive(file);
            return success ? FileOperationResult.success() 
                          : FileOperationResult.failure("Failed to delete");
        } catch (Exception e) {
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
        try {
            File oldFile = new File(oldPath);
            File newFile = new File(newPath);
            
            if (!oldFile.exists()) {
                return FileOperationResult.failure("Source file does not exist");
            }
            if (newFile.exists()) {
                return FileOperationResult.failure("Target file already exists");
            }
            
            boolean success = oldFile.renameTo(newFile);
            return success ? FileOperationResult.success() 
                          : FileOperationResult.failure("Failed to rename");
        } catch (Exception e) {
            return FileOperationResult.failure("rename failed: " + e.getMessage());
        }
    }

    @Override
    public byte[] readFile(String absolutePath, long offset, int length) {
        try {
            File file = new File(absolutePath);
            if (!file.exists() || !file.isFile()) {
                return new byte[0];
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                if (offset > 0) {
                    fis.skip(offset);
                }
                
                int toRead = length > 0 ? Math.min(length, (int)(file.length() - offset)) 
                                       : (int)(file.length() - offset);
                if (toRead <= 0) {
                    return new byte[0];
                }
                
                byte[] buffer = new byte[toRead];
                int bytesRead = fis.read(buffer);
                
                if (bytesRead < toRead) {
                    byte[] result = new byte[bytesRead];
                    System.arraycopy(buffer, 0, result, 0, bytesRead);
                    return result;
                }
                return buffer;
            }
        } catch (Exception e) {
            return new byte[0];
        }
    }

    @Override
    public FileOperationResult writeFile(String absolutePath, byte[] data, long offset, boolean append) {
        try {
            File file = new File(absolutePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(file, append)) {
                if (offset > 0 && !append) {
                    fos.getChannel().position(offset);
                }
                fos.write(data);
                return FileOperationResult.success();
            }
        } catch (Exception e) {
            return FileOperationResult.failure("writeFile failed: " + e.getMessage());
        }
    }

    @Override
    public FileOperationResult setLastModified(String absolutePath, long timestamp) {
        try {
            File file = new File(absolutePath);
            if (!file.exists()) {
                return FileOperationResult.failure("File does not exist");
            }
            
            boolean success = file.setLastModified(timestamp);
            return success ? FileOperationResult.success() 
                          : FileOperationResult.failure("Failed to set last modified time");
        } catch (Exception e) {
            return FileOperationResult.failure("setLastModified failed: " + e.getMessage());
        }
    }

    @Override
    public int getVersion() {
        return SERVICE_VERSION;
    }

    @Override
    public void destroy() {
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
