package org.primftpd.filesystem;

import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;
import org.primftpd.shizuku.aidl.FileInfo;

import java.util.List;

public class ShizukuFtpFile extends ShizukuFile<FtpFile, ShizukuFtpFileSystemView> implements FtpFile {

    private final User user;

    public ShizukuFtpFile(ShizukuFtpFileSystemView fileSystemView, String absPath, FileInfo fileInfo, User user) {
        super(fileSystemView, absPath, fileInfo);
        this.user = user;
    }

    @Override
    protected FtpFile createFile(String absPath, FileInfo fileInfo) {
        return new ShizukuFtpFile(getFileSystemView(), absPath, fileInfo, user);
    }

    @Override
    public String getClientIp() {
        return FtpUtils.getClientIp(user);
    }

    @Override
    public List<FtpFile> listFiles() {
        return super.listFiles();
    }

    @Override
    public boolean move(FtpFile target) {
        return super.move((ShizukuFtpFile) target);
    }

    @Override
    public String                                                                                                                                                                                                                                                                                                                                                                                      getOwnerName() {
        return user.getName();
    }

    @Override
    public String getGroupName() {
        return user.getName();
    }
}
