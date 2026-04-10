package org.primftpd.filesystem;

import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;
import org.primftpd.services.PftpdService;
import org.primftpd.shizuku.ShizukuServiceManager;
import org.primftpd.shizuku.aidl.FileInfo;

import java.io.File;

public class ShizukuFtpFileSystemView extends ShizukuFileSystemView<ShizukuFtpFile, FtpFile> implements FileSystemView {

    private final File homeDir;
    private final User user;

    private ShizukuFtpFile workingDir;

    public ShizukuFtpFileSystemView(PftpdService pftpdService, ShizukuServiceManager serviceManager, File homeDir, User user) {
        super(pftpdService, serviceManager);
        this.homeDir = homeDir;
        this.user = user;
        this.workingDir = getHomeDirectory();
    }

    @Override
    protected ShizukuFtpFile createFile(String absPath, FileInfo fileInfo) {
        return new ShizukuFtpFile(this, absPath, fileInfo, user);
    }

    @Override
    protected String absolute(String file) {
        logger.trace("  finding abs path for '{}' with wd '{}'", file, (workingDir != null ? workingDir.getAbsolutePath() : "null"));
        if (workingDir == null) {
            return file;
        }
        return Utils.absolute(file, workingDir.getAbsolutePath());
    }

    @Override
    public ShizukuFtpFile getHomeDirectory() {
        return getFile(homeDir.getAbsolutePath());
    }

    @Override
    public ShizukuFtpFile getWorkingDirectory() {
        return workingDir;
    }

    @Override
    public boolean changeWorkingDirectory(String dir) {
        String newPath;
        boolean isAbsolute = dir != null && dir.charAt(0) == '/';
        if (!isAbsolute) {
            FtpFile topLevelDir = getFile("/" + dir);
            if (topLevelDir.doesExist()) {
                newPath = topLevelDir.getAbsolutePath();
            } else {
                newPath = workingDir.getAbsolutePath() + File.separator + dir;
            }
        } else {
            newPath = dir;
        }

        ShizukuFtpFile newWorkingDir = getFile(newPath);
        if (newWorkingDir.doesExist() && newWorkingDir.isDirectory()) {
            workingDir = newWorkingDir;
            return true;
        }
        return false;
    }

    @Override
    public boolean isRandomAccessible() {
        return true;
    }

    @Override
    public void dispose() {
    }
}
