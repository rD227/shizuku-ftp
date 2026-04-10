package org.primftpd.filesystem;

import org.apache.sshd.common.Session;
import org.apache.sshd.common.file.SshFile;
import org.primftpd.shizuku.aidl.FileInfo;
import org.primftpd.shizuku.aidl.FileOperationResult;

import java.io.IOException;
import java.util.List;

public class ShizukuSshFile extends ShizukuFile<SshFile, ShizukuSshFileSystemView> implements SshFile {

    private final Session session;

    public ShizukuSshFile(ShizukuSshFileSystemView fileSystemView, String absPath, FileInfo fileInfo, Session session) {
        super(fileSystemView, absPath, fileInfo);
        this.session = session;
    }

    @Override
    protected SshFile createFile(String absPath, FileInfo fileInfo) {
        return new ShizukuSshFile(getFileSystemView(), absPath, fileInfo, session);
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
    public void truncate() throws IOException {
        logger.info("truncate: path={}", absPath);
        // Create empty file
        FileOperationResult result = getFileSystemView().getServiceManager()
                .writeFile(absPath, new byte[0], 0, false);
        if (!result.isSuccess()) {
            throw new IOException("Truncate failed: " + result.getErrorMessage());
        }
    }

    @Override
    public boolean create() throws IOException {
        logger.info("create: path={}", absPath);
        FileOperationResult result = getFileSystemView().getServiceManager()
                .writeFile(absPath, new byte[0], 0, false);
        return result.isSuccess();
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
