package org.primftpd.filesystem;

import org.apache.sshd.common.Session;
import org.apache.sshd.common.file.FileSystemView;
import org.apache.sshd.common.file.SshFile;
import org.primftpd.services.PftpdService;
import org.primftpd.shizuku.ShizukuServiceManager;
import org.primftpd.shizuku.aidl.FileInfo;

import java.io.File;

public class ShizukuSshFileSystemView extends ShizukuFileSystemView<ShizukuSshFile, SshFile> implements FileSystemView {

    private final File homeDir;
    private final Session session;

    public ShizukuSshFileSystemView(PftpdService pftpdService, ShizukuServiceManager serviceManager, File homeDir, Session session) {
        super(pftpdService, serviceManager);
        this.homeDir = homeDir;
        this.session = session;
    }

    @Override
    protected ShizukuSshFile createFile(String absPath, FileInfo fileInfo) {
        return new ShizukuSshFile(this, absPath, fileInfo, session);
    }

    @Override
    protected String absolute(String file) {
        return Utils.absoluteOrHome(file, homeDir.getAbsolutePath());
    }

    @Override
    public SshFile getFile(SshFile baseDir, String file) {
        logger.trace("getFile(baseDir: {}, file: {})", baseDir.getAbsolutePath(), file);
        if (file == null || file.isEmpty()) {
            return getFile(baseDir.getAbsolutePath());
        }
        if (file.charAt(0) == '/') {
            return getFile(file);
        }
        return getFile(baseDir.getAbsolutePath() + "/" + file);
    }



    @Override
    public FileSystemView getNormalizedView() {
        return this;
    }
}
