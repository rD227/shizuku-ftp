package org.primftpd.filesystem;

import org.primftpd.pojo.LsOutputBean;
import org.primftpd.services.PftpdService;
import org.primftpd.shizuku.ShizukuServiceManager;

/**
 * Phase-1 Shizuku file system view.
 *
 * This no longer depends on libsuperuser root shell. Instead, it talks to a
 * manager abstraction that will later bind to a real Shizuku UserService.
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

    protected abstract TFile createFile(String absPath, LsOutputBean bean);

    protected abstract String absolute(String file);

    public TFile getFile(String file) {
        logger.info(">>> SHIZUKU_DEBUG >>> getFile(path: {})", file);

        String abs = absolute(file);
        LsOutputBean bean = serviceManager.stat(abs);
        logger.info(">>> SHIZUKU_DEBUG >>> absPath: {}, beanName: {}, exists: {}, dir: {}",
                abs,
                bean != null ? bean.getName() : "null",
                bean != null && bean.isExists(),
                bean != null && bean.isDir());

        if (bean == null) {
            String name = abs.contains("/") ? abs.substring(abs.lastIndexOf('/') + 1) : abs;
            bean = new LsOutputBean(name);
        }
        return createFile(abs, bean);
    }
}
