package org.primftpd.services;

import org.apache.ftpserver.command.impl.MODE;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * 包一层默认的 MODE 命令，只为了在客户端请求 MODE Z 时打日志。
 * 真正把数据连接切到 zlib 的逻辑仍然由 Apache FtpServer 的 MODE + IODataConnection 完成。
 */
public class LoggingModeCommand extends MODE {

    private final Logger logger;

    public LoggingModeCommand(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void execute(
            FtpIoSession session,
            FtpServerContext context,
            FtpRequest request) throws IOException {
        String argument = request.getArgument();
        if (argument != null && ("Z".equalsIgnoreCase(argument) || "z".equalsIgnoreCase(argument))) {
            logger.info("=== FTP client requested MODE Z: zlib data compression will be used ===");
        } else {
            logger.info("=== FTP client requested MODE {}: no data compression ===", argument);
        }
        super.execute(session, context, request);
    }
}
