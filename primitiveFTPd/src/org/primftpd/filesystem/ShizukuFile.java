package org.primftpd.filesystem;

import org.apache.sshd.common.file.SshFile;
import org.primftpd.events.ClientActionEvent;
import org.primftpd.pojo.LsOutputBean;
import org.primftpd.pojo.LsOutputParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import eu.chainfire.libsuperuser.Shell;

public abstract class ShizukuFile<TMina, TFileSystemView extends ShizukuFileSystemView> extends AbstractFile<TFileSystemView> {

    protected final LsOutputBean bean;

    public ShizukuFile(TFileSystemView fileSystemView, String absPath, LsOutputBean bean) {
        super(fileSystemView, absPath, bean.getName());
        this.bean = bean;
    }

    protected abstract TMina createFile(String absPath, LsOutputBean bean);

    public static String escapePath(String path) {
        return RootFile.escapePath(path);
    }

    protected boolean runCommand(String cmd) {
        logger.trace("running cmd: '{}'", cmd);
        final boolean[] result = new boolean[1];
        getFileSystemView().getShell().addCommand(cmd, 0, new Shell.OnCommandLineListener() {
            @Override
            public void onSTDOUT(@NonNull String s) {
                logger.trace("stdout: {}", s);
            }

            @Override
            public void onSTDERR(@NonNull String s) {
                logger.debug("stderr: {}", s);
            }

            @Override
            public void onCommandResult(int commandCode, int exitCode) {
                result[0] = exitCode == 0;
            }
        });
        getFileSystemView().getShell().waitForIdle();
        return result[0];
    }

    @Override
    public ClientActionEvent.Storage getClientActionStorage() {
        return ClientActionEvent.Storage.VIRTUAL;
    }

    @Override
    public boolean isDirectory() {
        boolean result = bean.isDir();
        logger.trace("[{}] isDirectory() -> {}", name, result);
        return result;
    }

    @Override
    public boolean doesExist() {
        boolean result = bean.isExists();
        logger.trace("[{}] doesExist() -> {}", name, result);
        return result;
    }

    @Override
    public boolean isReadable() {
        boolean result = bean.isUserReadable();
        logger.trace("[{}] isReadable() -> {}", name, result);
        return result;
    }

    @Override
    public long getLastModified() {
        long result = bean.getTimestamp();
        logger.trace("[{}] getLastModified() -> {}", name, result);
        return result;
    }

    @Override
    public long getSize() {
        long result = bean.getSize();
        logger.trace("[{}] getSize() -> {}", name, result);
        return result;
    }

    @Override
    public boolean isFile() {
        boolean result = bean.isFile();
        logger.trace("[{}] isFile() -> {}", name, result);
        return result;
    }

    @Override
    public boolean isWritable() {
        boolean result = bean.isUserWritable();
        logger.trace("[{}] isWritable() -> {}", name, result);
        return result;
    }

    @Override
    public boolean isRemovable() {
        return true;
    }

    @Override
    public boolean setLastModified(long time) {
        return runCommand("touch -m -t " + Utils.touchDate(time) + " " + escapePath(absPath));
    }

    @Override
    public boolean mkdir() {
        postClientAction(ClientActionEvent.ClientAction.CREATE_DIR);
        return runCommand("mkdir " + escapePath(absPath));
    }

    @Override
    public boolean delete() {
        postClientAction(ClientActionEvent.ClientAction.DELETE);
        boolean success = runCommand("rm -rf " + escapePath(absPath));
        if (success) {
            getFileSystemView().getMediaScannerClient().scanFile(absPath);
        }
        return success;
    }

    @Override
    public boolean move(AbstractFile<TFileSystemView> destination) {
        postClientAction(ClientActionEvent.ClientAction.RENAME);
        boolean success = runCommand("mv " + escapePath(absPath) + " " + escapePath(destination.getAbsolutePath()));
        if (success) {
            getFileSystemView().getMediaScannerClient().scanFile(absPath);
            getFileSystemView().getMediaScannerClient().scanFile(destination.getAbsolutePath());
        }
        return success;
    }

    public List<TMina> listFiles() {
        logger.trace("[{}] listFiles()", name);
        postClientAction(ClientActionEvent.ClientAction.LIST_DIR);

        final List<TMina> result = new ArrayList<>();
        final LsOutputParser parser = new LsOutputParser();
        final List<LsOutputBean> beans = new ArrayList<>();
        getFileSystemView().getShell().addCommand("ls -la " + escapePath(absPath), 0, new Shell.OnCommandLineListener() {
            @Override
            public void onSTDOUT(@NonNull String s) {
                LsOutputBean child = parser.parseLine(s);
                if (child != null && !".".equals(child.getName()) && !"..".equals(child.getName())) {
                    beans.add(child);
                }
            }

            @Override
            public void onSTDERR(@NonNull String s) {
                logger.debug("stderr: {}", s);
            }

            @Override
            public void onCommandResult(int commandCode, int exitCode) {
            }
        });
        getFileSystemView().getShell().waitForIdle();

        for (LsOutputBean child : beans) {
            String path = "/".equals(absPath) ? "/" + child.getName() : absPath + "/" + child.getName();
            result.add(createFile(path, child));
        }

        return result;
    }

    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        postClientAction(ClientActionEvent.ClientAction.UPLOAD);
        if (!bean.isExists()) {
            runCommand("touch " + escapePath(absPath));
        }
        OutputStream os = new BufferedOutputStream(new ProcessBuilder("sh", "-c",
                "su -c \"dd of=" + absPath.replace("\"", "\\\"") + " bs=4096 seek=" + Math.max(0, offset / 4096) + " conv=notrunc\"")
                .start().getOutputStream());
        return new BufferedOutputStream(os) {
            @Override
            public void close() throws IOException {
                super.close();
                getFileSystemView().getMediaScannerClient().scanFile(absPath);
            }
        };
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
        postClientAction(ClientActionEvent.ClientAction.DOWNLOAD);
        return new BufferedInputStream(new ProcessBuilder("sh", "-c",
                "su -c \"dd if=" + absPath.replace("\"", "\\\"") + " bs=4096 skip=" + Math.max(0, offset / 4096) + "\"")
                .start().getInputStream());
    }

    public String readSymbolicLink() {
        logger.trace("[{}] readSymbolicLink()", name);
        return bean.getLinkTarget();
    }

    public Object getAttribute(SshFile.Attribute attribute, boolean followLinks) throws IOException {
        logger.trace("[{}] getAttribute({})", name, attribute);
        switch (attribute) {
            case Owner:
                return bean.getUser();
            case Group:
                return bean.getGroup();
            case IsSymbolicLink:
                return bean.isLink();
            case Permissions:
                Set<SshFile.Permission> tmp = new HashSet<>();
                if (bean.isUserReadable()) tmp.add(SshFile.Permission.UserRead);
                if (bean.isUserWritable()) tmp.add(SshFile.Permission.UserWrite);
                if (bean.isUserExecutable()) tmp.add(SshFile.Permission.UserExecute);
                if (bean.isGroupReadable()) tmp.add(SshFile.Permission.GroupRead);
                if (bean.isGroupWritable()) tmp.add(SshFile.Permission.GroupWrite);
                if (bean.isGroupExecutable()) tmp.add(SshFile.Permission.GroupExecute);
                if (bean.isOtherReadable()) tmp.add(SshFile.Permission.OthersRead);
                if (bean.isOtherWritable()) tmp.add(SshFile.Permission.OthersWrite);
                if (bean.isOtherExecutable()) tmp.add(SshFile.Permission.OthersExecute);
                return tmp.isEmpty() ? EnumSet.noneOf(SshFile.Permission.class) : EnumSet.copyOf(tmp);
            default:
                return SshUtils.getAttribute((SshFile) this, attribute);
        }
    }

    public Map<SshFile.Attribute, Object> getAttributes(boolean followLinks) throws IOException {
        Map<SshFile.Attribute, Object> attributes = new HashMap<>();
        for (SshFile.Attribute attr : SshFile.Attribute.values()) {
            attributes.put(attr, getAttribute(attr, followLinks));
        }
        return attributes;
    }
}
