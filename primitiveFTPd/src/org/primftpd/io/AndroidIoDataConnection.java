package org.primftpd.io;

import org.apache.ftpserver.ftplet.DataConnection;
import org.apache.ftpserver.ftplet.DataType;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.impl.DefaultFtpSession;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.ServerDataConnectionFactory;
import org.greenrobot.eventbus.EventBus;
import org.primftpd.events.DataTransferredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class AndroidIoDataConnection implements DataConnection {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private final FtpIoSession session;
    private final ServerDataConnectionFactory factory;
    private SocketChannel dataSocketChannel;

    public AndroidIoDataConnection(final SocketChannel dataSocketChannel, final FtpIoSession session, final ServerDataConnectionFactory factory) {
        LOG.trace("AndroidIoDataConnection()");
        this.session = session;
        this.dataSocketChannel = dataSocketChannel;
        this.factory = factory;
    }

    /*
     * (non-Javadoc)
     *
     * @seeorg.apache.ftpserver.FtpDataConnection2#transferFromClient(java.io.
     * OutputStream)
     */
    public final long transferFromClient(FtpSession session, final OutputStream out) throws IOException {
        LOG.trace("transferFromClient()");

        WritableByteChannel outStreamBacked = new WritableByteChannel() {
            @Override
            public int write(ByteBuffer src) throws IOException {
                // 🔧 修复：使用 remaining() 而不是 position() 来获取要写入的长度
                //   flip/limit+position 之后 position=0, remaining()=实际数据长度
                byte[] buf = src.array();
                int length = src.remaining();
                int offset = src.position();
                if (length < buf.length) {
                    LOG.trace("writing less than buffer length, len: {}, diff: {}", length, (buf.length - length));
                }
                out.write(buf, offset, length);
                src.position(src.limit());
                return length;
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() throws IOException {
                out.close();
            }
        };

        try {
            // 🔧 修复：客户端上传 → 服务器读取，isWrite 应为 false
            return transfer(session, false, dataSocketChannel, outStreamBacked);
        } finally {
            //IoUtils.close(out);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.ftpserver.FtpDataConnection2#transferToClient(java.io.InputStream
     * )
     */
    public final long transferToClient(FtpSession session, final InputStream in) throws IOException {
        LOG.trace("transferToClient()");

        ReadableByteChannel inStreamBacked = new ReadableByteChannel() {
            private int lastRead = 0;

            @Override
            public int read(ByteBuffer dst) throws IOException {
                byte[] buf = dst.array();
                lastRead = in.read(buf);
                if (lastRead < 0) {
                    dst.position(0);
                    dst.limit(0);
                } else if (lastRead < buf.length) {
                    LOG.trace("setting buffer position: 0 & limit: {}", lastRead);
                    dst.position(0);
                    dst.limit(lastRead);
                }
                // 🔧 修复：当 lastRead == buf.length 时（读满缓冲区），
                //   也需要正确设置 position 和 limit，否则 position 不对
                //   对于 SocketChannel.read()，读满后 position=capacity，limit=capacity
                //   对于 InputStream.read()，读满时 position 和 limit 未被设置，可能不正确
                return lastRead;
            }

            @Override
            public boolean isOpen() {
                return lastRead >= 0;
            }

            @Override
            public void close() throws IOException {
                in.close();
            }
        };

        try {
            // 服务器发送给客户端 → 服务器写入，isWrite = true（正确）
            return transfer(session, true, inStreamBacked, dataSocketChannel);
        } finally {
            //IoUtils.close(out);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.ftpserver.FtpDataConnection2#transferToClient(java.lang.String
     * )
     */
    public final void transferToClient(FtpSession session, final String str) throws IOException {
        LOG.trace("transferToClient()");
        //Writer writer = null;
        try {
            //writer = new OutputStreamWriter(out, "UTF-8");
            //writer.write(str);
            byte[] bytes = str.getBytes("UTF-8");
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            dataSocketChannel.write(buffer);

            // update session
            if (session instanceof DefaultFtpSession) {
                ((DefaultFtpSession) session).increaseWrittenDataBytes(str
                        .getBytes("UTF-8").length);
            }
        } finally {
            // if (writer != null) {
            //     writer.flush();
            // }
            // IoUtils.close(writer);
        }
    }

    // D:/Users/xvsu/AndroidStudioProjects/shizuku-ftp/primitiveFTPd/src/org/primftpd/io/AndroidIoDataConnection.java
    private final long transfer(FtpSession session, boolean isWrite, final ReadableByteChannel in, final WritableByteChannel out) throws IOException {
        long transferredSize = 0L;
        long lastPostTime = 0; // 记录上次发送事件的时间
        boolean isAscii = session.getDataType() == DataType.ASCII;

        byte[] buff = new byte[4096];

        if (isAscii) {
            LOG.info("ignoring request for ascii transfer, doing it binary");
        }

        try {
            DefaultFtpSession defaultFtpSession = null;
            if (session instanceof DefaultFtpSession) {
                defaultFtpSession = (DefaultFtpSession) session;
            }

            CountingReadableByteChannel inCounting = new CountingReadableByteChannel(in);
            CountingWritableByteChannel outCounting = new CountingWritableByteChannel(out);

            ByteBuffer buffer = ByteBuffer.wrap(buff);

            while (true) {
                int count = inCounting.read(buffer);
                if (count == -1)
                    break;//永远循环，直到读完文件

                //这里调用了一个CountingReadableButeChannel类，这个类继承了ReadableByteChannel，在这个继承类的构造方法当中
                //持有一个ReadableByteChanel的实例对象，使用这个

                //ai的注释
                // 🔧 核心修复：read 之后，统一将 buffer 准备为 "position=0, limit=count" 的状态
                //   这样 out.write(buffer) 才能写出正确的字节数
                //

                //（zhe li you liang ge wen jian shi li
                //   两种 in 的情况：
                //   - SocketChannel.read(): read 后 position=count, limit=capacity
                //     → 需要 limit(count), position(0)
                //   - inStreamBacked.read(): read 后 position=0, limit=lastRead(=count)
                //     → limit(count) 无变化, position(0) 无变化 (幂等)
                buffer.limit(count);
                buffer.position(0);

                // 更新会话统计
                if (defaultFtpSession != null) {
                    if (isWrite) {
                        defaultFtpSession.increaseWrittenDataBytes(count);
                    } else {
                        defaultFtpSession.increaseReadDataBytes(count);
                    }
                }

                // 写入数据（现在 remaining()=count，能正确写出全部数据）
                outCounting.write(buffer);
                transferredSize += count;
                notifyObserver();

                buffer.clear();

                //每200ms发送一次事件
                long now = System.currentTimeMillis();
                if (now - lastPostTime > 200) {
                    EventBus.getDefault().post(
                        new DataTransferredEvent(now, transferredSize, isWrite)
                    );
                    android.util.Log.d("IoDataConn", ">>>POST 事件 bytes=" + transferredSize);
                    lastPostTime = now;
                }
            }
        } catch(IOException | RuntimeException e) {
            LOG.warn("Exception during data transfer, closing data connection socket", e);
            factory.closeDataConnection();
            throw e;
        }

        if (transferredSize > 0) {
            EventBus.getDefault().post(
                new DataTransferredEvent(System.currentTimeMillis(), transferredSize, isWrite)
            );
            //android.util.Log.d("IoDataConn", ">>>POST 最终事件 bytes=" + transferredSize);
            LOG.info("IoDataConn: transferred {} bytes", transferredSize);
        }

        return transferredSize;
    }

    /**
     * Notify connection manager observer.
     */
    protected void notifyObserver() {
        //LOG.trace("notifyObserver()");
        session.updateLastAccessTime();
        // TODO this has been moved from AbstractConnection, do we need to keep
        // it?
        // serverContext.getConnectionManager().updateConnection(this);
    }
}
