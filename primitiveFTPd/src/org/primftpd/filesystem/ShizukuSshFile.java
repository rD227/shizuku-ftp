package org.primftpd.filesystem;

import org.apache.sshd.common.Session;
import org.apache.sshd.common.file.SshFile;
import org.primftpd.pojo.LsOutputBean;

import java.io.IOException;
import java.util.List;

public class ShizukuSshFile extends ShizukuFile<SshFile, ShizukuSshFileSystemView> implements SshFile {

    private final Session session;

    public ShizukuSshFile(ShizukuSshFileSystemView fileSystemView, String absPath, LsOutputBean bean, Session session) {
        super(fileSystemView, absPath, bean);
        this.session = session;
    }

    @Override
    protected SshFile createFile(String absPath, LsOutputBean bean) {
        return new ShizukuSshFile(getFileSystemView(), absPath, bean, session);
    }

    @Override
    public String getClientIp() {
        return SshUtils.getClientIp(session);
    }

    @Override
    public boolean move(SshFile target) {
        return super.move((ShizukuSshFile) target);
    }

    @Override
    public String getOwner() {
        try {
            return (String) getAttribute(Attribute.Owner, false);
        } catch (IOException e) {
            logger.error("getOwner()", e);
            return null;
        }
    }

    @Override
    public void truncate() {
        runCommand("truncate -c -s 0 " + escapePath(absPath));
    }

    @Override
    public boolean create() throws IOException {
        return runCommand("touch " + escapePath(absPath));
    }

    @Override
    public SshFile getParentFile() {
        String parentPath = Utils.parent(absPath);
        return getFileSystemView().getFile(parentPath);
    }

    @Override
    public List<SshFile> listSshFiles() {
        return super.listFiles();
    }
}
