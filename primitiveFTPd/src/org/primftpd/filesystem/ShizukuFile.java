package org.primftpd.filesystem;

import org.apache.sshd.common.file.SshFile;
import org.primftpd.events.ClientActionEvent;
import org.primftpd.shizuku.aidl.FileInfo;
import org.primftpd.shizuku.aidl.FileOperationResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

/**
 * Shizuku-based file implementation using privileged UserService.
 * Replaces libsuperuser root shell approach.
 */
public abstract class ShizukuFile<TMina, TFileSystemView extends ShizukuFileSystemView>
        extends AbstractFile<TFileSystemView> {

    protected final FileInfo fileInfo;

    public ShizukuFile(TFileSystemView fileSystemView, String absPath, FileInfo fileInfo) {
        super(fileSystemView, absPath, fileInfo.getName());
        this.fileInfo = fileInfo;
        logger.debug("[ShizukuFile] Created: path={}, exists={}, isDir={}", 
                absPath, fileInfo.exists(), fileInfo.isDirectory());
    }

    protected abstract TMina createFile(String absPath, FileInfo fileInfo);

    @Override
    public ClientActionEvent.Storage getClientActionStorage() {
        return ClientActionEvent.Storage.SHIZUKU;
    }

    @Override
    public boolean isDirectory() {
        return fileInfo.isDirectory();
    }

    @Override
    public boolean doesExist() {
        return fileInfo.exists();
    }

    @Override
    public boolean isReadable() {
        return fileInfo.canRead();
    }

    @Override
    public long getLastModified() {
        return fileInfo.getLastModified();
    }

    @Override
    public long getSize() {
        return fileInfo.getSize();
    }

    @Override
    public boolean isFile() {
        return fileInfo.isFile();
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public boolean isRemovable() {
        return fileInfo.canWrite();
    }

    @Override
    public boolean setLastModified(long time) {
        logger.info("[ShizukuFile] setLastModified: path={}, time={}", absPath, time);
        FileOperationResult result = getFileSystemView().getServiceManager().setLastModified(absPath, time);
        logger.info("[ShizukuFile] setLastModified result: success={}", result.isSuccess());
        return result.isSuccess();
    }

    @Override
    public boolean mkdir() {
        logger.info("[ShizukuFile] mkdir: path={}", absPath);
        postClientAction(ClientActionEvent.ClientAction.CREATE_DIR);
        FileOperationResult result = getFileSystemView().getServiceManager().mkdir(absPath);
        logger.info("[ShizukuFile] mkdir result: success={}", result.isSuccess());
        return result.isSuccess();
    }

    @Override
    public boolean delete() {
        logger.info("[ShizukuFile] delete: path={}", absPath);
        postClientAction(ClientActionEvent.ClientAction.DELETE);
        FileOperationResult result = getFileSystemView().getServiceManager().delete(absPath);
        logger.info("[ShizukuFile] delete result: success={}", result.isSuccess());
        return result.isSuccess();
    }

    @Override
    public boolean move(AbstractFile<TFileSystemView> destination) {
        logger.info("[ShizukuFile] move: src={}, dst={}", absPath, destination.getAbsolutePath());
        postClientAction(ClientActionEvent.ClientAction.RENAME);
        FileOperationResult result = getFileSystemView().getServiceManager()
                .rename(absPath, destination.getAbsolutePath());
        logger.info("[ShizukuFile] move result: success={}", result.isSuccess());
        return result.isSuccess();
    }

    public List<TMina> listFiles() {
        logger.info("[ShizukuFile] listFiles: path={}", absPath);
        postClientAction(ClientActionEvent.ClientAction.LIST_DIR);
        
        try {
            List<FileInfo> files = getFileSystemView().getServiceManager().listFiles(absPath);
            logger.info("[ShizukuFile] listFiles got {} files", files.size());
            List<TMina> result = new ArrayList<>(files.size());
            
            for (FileInfo info : files) {
                logger.debug("[ShizukuFile] listFiles item: name={}, path={}", 
                        info.getName(), info.getAbsolutePath());
                result.add(createFile(info.getAbsolutePath(), info));
            }
            
            return result;
        } catch (Exception e) {
            logger.error("[ShizukuFile] listFiles failed for: " + absPath, e);
            return new ArrayList<>();
        }
    }

    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        logger.info("[ShizukuFile] createOutputStream: path={}, offset={}", absPath, offset);
        postClientAction(ClientActionEvent.ClientAction.UPLOAD);
        
        return new ShizukuOutputStream(absPath, offset);
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
        logger.info("[ShizukuFile] createInputStream: path={}, offset={}, size={}", 
                absPath, offset, fileInfo.getSize());
        postClientAction(ClientActionEvent.ClientAction.DOWNLOAD);
        
        return new ShizukuInputStream(absPath, offset);
    }

    public String readSymbolicLink() {
        return fileInfo.getSymlinkTarget();
    }

    public Object getAttribute(SshFile.Attribute attribute, boolean followLinks) throws IOException {
        logger.debug("[ShizukuFile] getAttribute: path={}, attr={}", absPath, attribute);
        
        try {
            switch (attribute) {
                case Owner:
                    return "root"; // Shizuku runs as root
                case Group:
                    return "root";
                case IsSymbolicLink:
                    return fileInfo.isSymlink();
                case Permissions:
                    Set<SshFile.Permission> perms = new HashSet<>();
                    if (fileInfo.canRead()) {
                        perms.add(SshFile.Permission.UserRead);
                    }
                    if (fileInfo.canWrite()) {
                        perms.add(SshFile.Permission.UserWrite);
                    }
                    if (fileInfo.canExecute()) {
                        perms.add(SshFile.Permission.UserExecute);
                    }
                    return perms.isEmpty() ? EnumSet.noneOf(SshFile.Permission.class) : EnumSet.copyOf(perms);
                default:
                    return null;
            }
        } catch (Exception e) {
            logger.error("[ShizukuFile] getAttribute failed for: " + absPath + ", attr: " + attribute, e);
            throw new IOException("Failed to get attribute: " + attribute, e);
        }
    }

    public Map<SshFile.Attribute, Object> getAttributes(boolean followLinks) throws IOException {
        logger.debug("[ShizukuFile] getAttributes: path={}", absPath);
        
        try {
            Map<SshFile.Attribute, Object> attributes = new HashMap<>();
            for (SshFile.Attribute attr : SshFile.Attribute.values()) {
                Object value = getAttribute(attr, followLinks);
                if (value != null) {
                    attributes.put(attr, value);
                }
            }
            logger.debug("[ShizukuFile] getAttributes returning {} attributes", attributes.size());
            return attributes;
        } catch (Exception e) {
            logger.error("[ShizukuFile] getAttributes failed for: " + absPath, e);
            throw new IOException("Failed to get attributes", e);
        }
    }

    /**
     * OutputStream implementation for Shizuku file writing
     */
    private class ShizukuOutputStream extends OutputStream {
        private final String path;
        private final long offset;
        private final ByteArrayOutputStream buffer;

        ShizukuOutputStream(String path, long offset) {
            this.path = path;
            this.offset = offset;
            this.buffer = new ByteArrayOutputStream();
            logger.debug("[ShizukuOutputStream] Created: path={}, offset={}", path, offset);
        }

        @Override
        public void write(int b) throws IOException {
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            try {
                byte[] data = buffer.toByteArray();
                logger.info("[ShizukuOutputStream] close: path={}, dataSize={}, offset={}", 
                        path, data.length, offset);
                
                FileOperationResult result = getFileSystemView().getServiceManager()
                        .writeFile(path, data, offset, offset > 0);
                
                if (!result.isSuccess()) {
                    logger.error("[ShizukuOutputStream] Write failed: {}", result.getErrorMessage());
                    throw new IOException("Write failed: " + result.getErrorMessage());
                }
                
                logger.info("[ShizukuOutputStream] Write successful");
            } catch (Exception e) {
                logger.error("[ShizukuOutputStream] close failed for: " + path, e);
                throw new IOException("Failed to write file", e);
            } finally {
                super.close();
            }
        }
    }

    /**
     * InputStream implementation for Shizuku file reading
     */
    private class ShizukuInputStream extends InputStream {
        private final String path;
        private long position;
        private byte[] buffer;
        private int bufferPos;
        private static final int CHUNK_SIZE = 64 * 1024; // 64KB chunks
        private boolean eof = false;

        ShizukuInputStream(String path, long offset) {
            this.path = path;
            this.position = offset;
            this.buffer = null;
            this.bufferPos = 0;
            logger.debug("[ShizukuInputStream] Created: path={}, offset={}", path, offset);
        }

        @Override
        public int read() throws IOException {
            if (eof) {
                return -1;
            }
            
            if (buffer == null || bufferPos >= buffer.length) {
                fillBuffer();
                if (buffer == null || buffer.length == 0) {
                    eof = true;
                    return -1;
                }
            }
            return buffer[bufferPos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            }
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) {
                return 0;
            }
            if (eof) {
                return -1;
            }

            int totalRead = 0;
            while (totalRead < len) {
                if (buffer == null || bufferPos >= buffer.length) {
                    fillBuffer();
                    if (buffer == null || buffer.length == 0) {
                        eof = true;
                        return totalRead == 0 ? -1 : totalRead;
                    }
                }

                int available = buffer.length - bufferPos;
                int toRead = Math.min(available, len - totalRead);
                System.arraycopy(buffer, bufferPos, b, off + totalRead, toRead);
                bufferPos += toRead;
                totalRead += toRead;
            }

            return totalRead;
        }

        private void fillBuffer() throws IOException {
            try {
                logger.debug("[ShizukuInputStream] fillBuffer: path={}, position={}, chunkSize={}", 
                        path, position, CHUNK_SIZE);
                
                buffer = getFileSystemView().getServiceManager().readFile(path, position, CHUNK_SIZE);
                bufferPos = 0;
                
                logger.debug("[ShizukuInputStream] fillBuffer got {} bytes", buffer.length);
                
                if (buffer.length == 0) {
                    eof = true;
                } else {
                    position += buffer.length;
                }
            } catch (Exception e) {
                logger.error("[ShizukuInputStream] fillBuffer failed for: " + path + ", position: " + position, e);
                throw new IOException("Failed to read file", e);
            }
        }
    }
}
