package org.primftpd.filesystem;

import org.apache.sshd.common.Session;
import org.apache.sshd.common.file.FileSystemView;
import org.apache.sshd.common.file.SshFile;
import org.primftpd.services.PftpdService;

import java.io.File;

import eu.chainfire.libsuperuser.Shell;

public class ShizukuSshFileSystemView extends ShizukuFileSystemView<ShizukuSshFile, SshFile> implements FileSystemView {

    private final File homeDir;
    private final Session session;

    public ShizukuSshFileSystemView(PftpdService pftpdService, Shell.Interactive shell, File homeDir, Session session) {
        super(pftpdService, shell);
        this.homeDir = homeDir;
        this.session = session;
    }

    @Override
    protected ShizukuSshFile createFile(String absPath, org.primftpd.pojo.LsOutputBean bean) {
        return new ShizukuSshFile(this, absPath, bean, session);
    }

    @Override
    protected String absolute(String file) {
        return Utils.absoluteOrHome(file, homeDir.getAbsolutePath());
    }

    @Override
    public SshFile getFile(SshFile baseDir, String file) {
        return getFile(baseDir.getAbsolutePath() + "/" + file);
    }

    @Override
    public FileSystemView getNormalizedView() {
        return this;
    }
}
