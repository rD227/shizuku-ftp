package org.primftpd.events;

public class DataTransferredEvent {
    private final long timestamp;
    private final long bytes;
    private final boolean write;
    private final boolean sftp;

    public DataTransferredEvent(long timestamp, long bytes, boolean isWrite, boolean isSftp) {
        this.timestamp = timestamp;
        this.bytes = bytes;
        this.write = isWrite;
        this.sftp = isSftp;
    }

    /** @deprecated use {@link #DataTransferredEvent(long, long, boolean, boolean)} */
    public DataTransferredEvent(long timestamp, long bytes, boolean isWrite) {
        this(timestamp, bytes, isWrite, false);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getBytes() {
        return bytes;
    }

    public boolean isWrite() {
        return write;
    }

    public boolean isSftp() {
        return sftp;
    }
}
