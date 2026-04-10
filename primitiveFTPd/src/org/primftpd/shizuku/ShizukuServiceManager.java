package org.primftpd.shizuku;

import org.primftpd.pojo.LsOutputBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * Phase-1 manager abstraction for future Shizuku UserService binding.
 *
 * At this stage it does not bind a real UserService yet. It exists to remove
 * the fake dependency on root shell from the Shizuku file-system classes.
 */
public class ShizukuServiceManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final IPrivilegedFileService localFallback = new ShizukuFileService();

    public boolean isBinderAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            logger.warn(">>> SHIZUKU_DEBUG >>> pingBinder failed in manager", t);
            return false;
        }
    }

    public LsOutputBean stat(String absolutePath) {
        logger.info(">>> SHIZUKU_DEBUG >>> manager.stat(path={}, binderAvailable={})",
                absolutePath,
                isBinderAvailable());
        return localFallback.stat(absolutePath);
    }

    public List<LsOutputBean> list(String absolutePath) {
        logger.info(">>> SHIZUKU_DEBUG >>> manager.list(path={}, binderAvailable={})",
                absolutePath,
                isBinderAvailable());
        return localFallback.list(absolutePath);
    }
}
