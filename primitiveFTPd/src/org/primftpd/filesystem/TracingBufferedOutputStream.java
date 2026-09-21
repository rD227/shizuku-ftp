package org.primftpd.filesystem;

import org.primftpd.data.TransmissionStruct;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TracingBufferedOutputStream extends BufferedOutputStream {

    public static final int BUFFER_SIZE = 1024 * 1024;

    protected final Logger logger;

    private final TransmissionStruct transmissionStruct;

    public TracingBufferedOutputStream(OutputStream os, Logger logger) {
        this(os, logger, new TransmissionStruct(true, false));
    }

    public TracingBufferedOutputStream(
            OutputStream os,
            Logger logger,
            TransmissionStruct transmissionStruct) {
        super(os, BUFFER_SIZE);
        this.logger = logger;
        this.transmissionStruct = transmissionStruct;
        logger.info(
                "TracingBufferedOutputStream: flushRightAway={}, autoZipTransmission={}",
                transmissionStruct.getFlushRightAway(),
                transmissionStruct.getAutoZipTransmission());
    }

    public TransmissionStruct getTransmissionStruct() {
        return transmissionStruct;
    }

    @Override
    public void close() throws IOException {
        super.close();
        logger.trace("sizes in close(), count: '{}', buf len: '{}'", count, buf.length);
    }

    @Override
    public synchronized void flush() throws IOException {
        super.flush();
        logger.trace("flush()");
    }

    @Override
    public synchronized void write(int b) throws IOException {
        super.write(b);
        if (transmissionStruct.getFlushRightAway()) {
            super.flush();
        }
        logger.trace("write(single byte)");
    }

    @Override
    public void write(byte[] b) throws IOException {
        super.write(b);
        if (transmissionStruct.getFlushRightAway()) {
            super.flush();
        }
        logger.trace("write(arr len: {})", b.length);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        if (transmissionStruct.getFlushRightAway()) {
            super.flush();
        }
        logger.trace("write(len: {})", len);
    }
}
