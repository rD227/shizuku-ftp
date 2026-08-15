package org.primftpd.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import android.os.RemoteException;

import org.primftpd.shizuku.aidl.FileInfo;
import org.primftpd.shizuku.aidl.FileOperationResult;
import org.primftpd.shizuku.aidl.IShizukuFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * Manager for Shizuku UserService binding and communication.
 * Handles service lifecycle and provides file operation APIs.
 */
public class ShizukuServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(ShizukuServiceManager.class);
    
    private final Context context;
    private IShizukuFileService service;
    private boolean isBound = false;
    private final Object bindLock = new Object();

    private final Shizuku.UserServiceArgs serviceArgs;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (bindLock) {
                logger.info("=== Shizuku onServiceConnected called ===");
                service = IShizukuFileService.Stub.asInterface(binder);
                isBound = true;
                logger.info("=== Shizuku service connected successfully, isBound={} ===", isBound);
                bindLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (bindLock) {
                logger.warn("=== Shizuku service disconnected ===");
                service = null;
                isBound = false;
            }
        }
    };

    public ShizukuServiceManager(Context context) {
        this.context = context.getApplicationContext();
        boolean debuggable = (this.context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        this.serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(
                        this.context.getPackageName(),
                        ShizukuUserService.class.getName()
                )
        )
        .daemon(false)
        .processNameSuffix("shizuku_file_service")
        .debuggable(debuggable)
        .version(1);
        logger.info("=== ShizukuServiceManager created, package={}, debuggable={} ===",
                this.context.getPackageName(), debuggable);
    }

    /**
     * Check if Shizuku binder is available
     */
    public boolean isShizukuAvailable() {
        try {
            boolean available = Shizuku.pingBinder();
            logger.info("=== Shizuku pingBinder result: {} ===", available);
            return available;
        } catch (Throwable t) {
            logger.error("=== Shizuku pingBinder failed ===", t);
            return false;
        }
    }

    /**
     * Bind to Shizuku UserService
     */
    public boolean bindService() {
        synchronized (bindLock) {
            logger.info("=== bindService called, isBound={} ===", isBound);
            
            if (isBound) {
                logger.info("=== Already bound, returning true ===");
                return true;
            }

            if (!isShizukuAvailable()) {
                logger.error("=== Shizuku is not available, cannot bind ===");
                return false;
            }

            try {
                logger.info("=== Calling Shizuku.bindUserService with class: {} ===",
                        ShizukuUserService.class.getName());
                
                Shizuku.bindUserService(serviceArgs, serviceConnection);
                logger.info("=== Shizuku.bindUserService called, waiting for connection... ===");
                
                // Wait for connection (with timeout)
                bindLock.wait(5000);
                
                logger.info("=== Wait finished, isBound={} ===", isBound);
                return isBound;
            } catch (Exception e) {
                logger.error("=== Failed to bind Shizuku service ===", e);
                return false;
            }
        }
    }

    /**
     * Unbind from Shizuku UserService
     */
    public void unbindService() {
        synchronized (bindLock) {
            if (!isBound) {
                logger.info("=== unbindService called but not bound ===");
                return;
            }

            try {
                Shizuku.unbindUserService(serviceArgs, serviceConnection, true);
                service = null;
                isBound = false;
                logger.info("=== Unbound from Shizuku service ===");
            } catch (Exception e) {
                logger.error("=== Failed to unbind Shizuku service ===", e);
            }
        }
    }

    /**
     * Ensure service is bound before operation
     */
    private boolean ensureBound() {
        synchronized (bindLock) {
            logger.debug("=== ensureBound: isBound={}, service={} ===", isBound, service != null);
            if (isBound && service != null) {
                return true;
            }
            return bindService();
        }
    }

    // File operation APIs

    public FileInfo stat(String absolutePath) {
        logger.info("=== stat called for: {} ===", absolutePath);
        
        if (!ensureBound()) {
            logger.error("=== Service not bound for stat: {} ===", absolutePath);
            return FileInfo.nonExistent(absolutePath, extractName(absolutePath));
        }

        try {
            FileInfo result = service.stat(absolutePath);
            logger.info("=== stat result: exists={}, isDir={}, name={} ===", 
                    result.exists(), result.isDirectory(), result.getName());
            return result;
        } catch (RemoteException e) {
            logger.error("=== stat failed for: {} ===", absolutePath, e);
            return FileInfo.nonExistent(absolutePath, extractName(absolutePath));
        }
    }

    public List<FileInfo> listFiles(String absolutePath) {
        logger.info("=== listFiles called for: {} ===", absolutePath);

        if (!ensureBound()) {
            logger.error("=== Service not bound for listFiles: {} ===", absolutePath);
            return new ArrayList<>();
        }

        try {
            List<FileInfo> result = service.listFiles(absolutePath);
            logger.info("=== listFiles result: {} files ===", result.size());
            return result;
        } catch (RemoteException e) {
            logger.error("=== listFiles failed for: {} ===", absolutePath, e);
            return new ArrayList<>();
        }
    }

    public boolean exists(String absolutePath) {
        if (!ensureBound()) {
            return false;
        }

        try {
            return service.exists(absolutePath);
        } catch (RemoteException e) {
            logger.error("=== exists failed for: {} ===", absolutePath, e);
            return false;
        }
    }

    public FileOperationResult mkdir(String absolutePath) {
        if (!ensureBound()) {
            return FileOperationResult.failure("Service not bound");
        }

        try {
            return service.mkdir(absolutePath);
        } catch (RemoteException e) {
            logger.error("=== mkdir failed for: {} ===", absolutePath, e);
            return FileOperationResult.failure(e.getMessage());
        }
    }

    public FileOperationResult delete(String absolutePath) {
        if (!ensureBound()) {
            return FileOperationResult.failure("Service not bound");
        }

        try {
            return service.delete(absolutePath);
        } catch (RemoteException e) {
            logger.error("=== delete failed for: {} ===", absolutePath, e);
            return FileOperationResult.failure(e.getMessage());
        }
    }

    public FileOperationResult rename(String oldPath, String newPath) {
        if (!ensureBound()) {
            return FileOperationResult.failure("Service not bound");
        }

        try {
            return service.rename(oldPath, newPath);
        } catch (RemoteException e) {
            logger.error("=== rename failed from {} to {} ===", oldPath, newPath, e);
            return FileOperationResult.failure(e.getMessage());
        }
    }

    public byte[] readFile(String absolutePath, long offset, int length) {
        if (!ensureBound()) {
            return new byte[0];
        }

        try {
            return service.readFile(absolutePath, offset, length);
        } catch (RemoteException e) {
            logger.error("=== readFile failed for: {} ===", absolutePath, e);
            return new byte[0];
        }
    }

    public FileOperationResult writeFile(String absolutePath, byte[] data, long offset, boolean append) {
        if (!ensureBound()) {
            return FileOperationResult.failure("Service not bound");
        }

        try {
            return service.writeFile(absolutePath, data, offset, append);
        } catch (RemoteException e) {
            logger.error("=== writeFile failed for: {} ===", absolutePath, e);
            return FileOperationResult.failure(e.getMessage());
        }
    }

    public FileOperationResult setLastModified(String absolutePath, long timestamp) {
        if (!ensureBound()) {
            return FileOperationResult.failure("Service not bound");
        }

        try {
            return service.setLastModified(absolutePath, timestamp);
        } catch (RemoteException e) {
            logger.error("=== setLastModified failed for: {} ===", absolutePath, e);
            return FileOperationResult.failure(e.getMessage());
        }
    }

    private String extractName(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return "";
        }
        int lastSlash = absolutePath.lastIndexOf('/');
        return lastSlash >= 0 ? absolutePath.substring(lastSlash + 1) : absolutePath;
    }
}
