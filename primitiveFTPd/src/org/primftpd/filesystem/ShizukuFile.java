package org.primftpd.filesystem;

import org.apache.sshd.common.file.SshFile;
import org.primftpd.events.ClientActionEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.chainfire.libsuperuser.Shell;
import org.primftpd.pojo.LsOutputBean;

/**
 * Debug stub: avoids executing Shizuku remote commands to keep build compatible.
 */
public abstract class ShizukuFile<TMina, TFileSystemView extends ShizukuFileSystemView>
        extends AbstractFile<TFileSystemView> {

    protected final LsOutputBean bean;

    public ShizukuFile(TFileSystemView fileSystemView, String absPath, LsOutputBean bean) {
        super(fileSystemView, absPath, bean.getName());
        this.bean = bean;
    }

    protected abstract TMina createFile(String absPath, LsOutputBean bean);

    @Override
    public ClientActionEvent.Storage getClientActionStorage() {
        return ClientActionEvent.Storage.SHIZUKU;
    }

    @Override
    public boolean isDirectory() {
        return bean.isDir();
    }

    @Override
    public boolean doesExist() {
        return bean.isExists();
    }

    @Override
    public boolean isReadable() {
        return bean.isUserReadable();
    }

    @Override
    public long getLastModified() {
        return bean.getTimestamp();
    }

    @Override
    public long getSize() {
        return bean.getSize();
    }

    @Override
    public boolean isFile() {
        return bean.isFile();
    }

    @Override
    public boolean isWritable() {
        return bean.isUserWritable();
    }

    @Override
    public boolean isRemovable() {
        return true;
    }

    @Override
    public boolean setLastModified(long time) {
        logger.info(">>> SHIZUKU_DEBUG >>> setLastModified (disabled), path={}", absPath);
        return false;
    }

    @Override
    public boolean mkdir() {
        logger.info(">>> SHIZUKU_DEBUG >>> mkdir (disabled), path={}", absPath);
        postClientAction(ClientActionEvent.ClientAction.CREATE_DIR);
        return false;
    }

    @Override
    public boolean delete() {
        logger.info(">>> SHIZUKU_DEBUG >>> delete (disabled), path={}", absPath);
        postClientAction(ClientActionEvent.ClientAction.DELETE);
        return false;
    }

    @Override
    public boolean move(AbstractFile<TFileSystemView> destination) {
        logger.info(">>> SHIZUKU_DEBUG >>> move (disabled), src={}, dst={}", absPath, destination.getAbsolutePath());
        postClientAction(ClientActionEvent.ClientAction.RENAME);
        return false;
    }

    //@Override
    public List<TMina> listFiles() {
        logger.info(">>> SHIZUKU_DEBUG >>> listFiles (disabled), path={}", absPath);
        postClientAction(ClientActionEvent.ClientAction.LIST_DIR);
        return List.of();
    }

    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        logger.info(">>> SHIZUKU_DEBUG >>> createOutputStream (disabled), path={}, offset={}", absPath, offset);
        postClientAction(ClientActionEvent.ClientAction.UPLOAD);
        throw new IOException("Shizuku disabled in debug mode");
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
        logger.info(">>> SHIZUKU_DEBUG >>> createInputStream (disabled), path={}, offset={}", absPath, offset);
        postClientAction(ClientActionEvent.ClientAction.DOWNLOAD);
        throw new IOException("Shizuku disabled in debug mode");
    }

    public String readSymbolicLink() {
        return bean.getLinkTarget();
    }

    public Object getAttribute(SshFile.Attribute attribute, boolean followLinks) throws IOException {
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
                return null;
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
