package org.primftpd.filesystem;

import org.primftpd.pojo.LsOutputBean;

import rikka.shizuku.Shizuku;
import eu.chainfire.libsuperuser.Shell;
import org.primftpd.pojo.LsOutputParser;
import org.primftpd.services.PftpdService;

/**
 * Debug-friendly Shizuku file system view.
 *
 * NOTE: To keep the build compiling across Shizuku API versions,
 * this view currently DOES NOT execute remote commands.
 */
public abstract class ShizukuFileSystemView<TFile extends ShizukuFile<TMina, ? extends ShizukuFileSystemView>, TMina>
        extends AbstractFileSystemView {

    private final MediaScannerClient mediaScannerClient;
    protected final Shell.Interactive shell;

    public ShizukuFileSystemView(PftpdService pftpdService, Shell.Interactive shell) {
        super(pftpdService);
        this.mediaScannerClient = new MediaScannerClient(pftpdService.getContext());
        this.shell = shell;
    }

    public final MediaScannerClient getMediaScannerClient() {
        return mediaScannerClient;
    }

    public final Shell.Interactive getShell() {
        return shell;
    }

    protected abstract TFile createFile(String absPath, LsOutputBean bean);

    protected abstract String absolute(String file);

    public TFile getFile(String file) {
        logger.info(">>> SHIZUKU_DEBUG >>> getFile(path: {})", file);

        String abs = absolute(file);
        boolean binderOk = false;
        try {
            binderOk = Shizuku.pingBinder();
        } catch (Throwable t) {
            logger.warn(">>> SHIZUKU_DEBUG >>> pingBinder() failed in getFile", t);
        }
        logger.info(">>> SHIZUKU_DEBUG >>> absPath: {}, pingBinder: {}", abs, binderOk);

        // Return dummy bean for now to keep the server running
        String name = abs.contains("/") ? abs.substring(abs.lastIndexOf('/') + 1) : abs;
        LsOutputBean bean = new LsOutputBean(name);
        return createFile(abs, bean);
    }
}
