package org.primftpd.shizuku;

import org.primftpd.pojo.LsOutputBean;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Placeholder implementation for the future Shizuku UserService.
 *
 * Phase-1 implementation uses java.io.File in-process so the rest of the
 * architecture can be migrated away from root shell first.
 */
public class ShizukuFileService implements IPrivilegedFileService {

    @Override
    public LsOutputBean stat(String absolutePath) {
        String name = absolutePath;
        if (absolutePath != null && absolutePath.contains("/")) {
            name = absolutePath.substring(absolutePath.lastIndexOf('/') + 1);
        }

        File file = absolutePath != null ? new File(absolutePath) : null;
        LsOutputBean bean = new LsOutputBean(name);
        if (file != null && file.exists()) {
            bean = new LsOutputBean(file);
        }
        return bean;
    }

    @Override
    public List<LsOutputBean> list(String absolutePath) {
        if (absolutePath == null) {
            return Collections.emptyList();
        }

        File dir = new File(absolutePath);
        File[] children = dir.listFiles();
        if (children == null || children.length == 0) {
            return Collections.emptyList();
        }

        List<LsOutputBean> result = new ArrayList<>(children.length);
        for (File child : children) {
            result.add(new LsOutputBean(child));
        }
        return result;
    }
}
