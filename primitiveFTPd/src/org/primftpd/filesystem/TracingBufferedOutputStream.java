package org.primftpd.filesystem;

import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TracingBufferedOutputStream extends BufferedOutputStream {

    public static final int BUFFER_SIZE = 1024 * 1024;

    protected final Logger logger;
    private final boolean flushRightAway;

    public TracingBufferedOutputStream(OutputStream os, Logger logger) {
        this(os, logger, true);
    }

    public TracingBufferedOutputStream(OutputStream os, Logger logger, boolean flushRightAway) {
        super(os ,BUFFER_SIZE);
        this.logger = logger;
        this.flushRightAway = flushRightAway;
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
        if (flushRightAway) {
            super.flush();
        }
        logger.trace("write(single byte)");
    }

    @Override
    public void write(byte[] b) throws IOException {
        super.write(b);
        if (flushRightAway) {
            super.flush();
        }
        logger.trace("write(arr len: {})", b.length);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        if (flushRightAway) {
            super.flush();
        }
        logger.trace("write(len: {})", len);
    }
}
