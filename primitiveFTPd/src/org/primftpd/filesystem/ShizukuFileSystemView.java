package org.primftpd.filesystem;

import org.primftpd.services.PftpdService;
import org.primftpd.shizuku.ShizukuServiceManager;
import org.primftpd.shizuku.aidl.FileInfo;

/**
 * Shizuku file system view using privileged UserService.
 * No longer depends on libsuperuser root shell.
 */
public abstract class ShizukuFileSystemView<TFile extends ShizukuFile<TMina, ? extends ShizukuFileSystemView>, TMina>
        extends AbstractFileSystemView {

    private final MediaScannerClient mediaScannerClient;
    protected final ShizukuServiceManager serviceManager;

    public ShizukuFileSystemView(PftpdService pftpdService, ShizukuServiceManager serviceManager) {
        super(pftpdService);
        this.mediaScannerClient = new MediaScannerClient(pftpdService.getContext());
        this.serviceManager = serviceManager;
    }

    public final MediaScannerClient getMediaScannerClient() {
        return mediaScannerClient;
    }

    public final ShizukuServiceManager getServiceManager() {
        return serviceManager;
    }

    protected abstract TFile createFile(String absPath, FileInfo fileInfo);

    protected abstract String absolute(String file);

    public TFile getFile(String file) {
        logger.trace("getFile(path: {})", file);

        String abs = absolute(file);
        FileInfo fileInfo = serviceManager.stat(abs);
        
        logger.trace("absPath: {}, name: {}, exists: {}, isDir: {}",
                abs,
                fileInfo.getName(),
                fileInfo.exists(),
                fileInfo.isDirectory());

        return createFile(abs, fileInfo);
    }
}
