package org.primftpd.shizuku;

import org.primftpd.pojo.LsOutputBean;

import java.util.List;

/**
 * Phase-1 lightweight contract for future Shizuku UserService migration.
 *
 * This is intentionally a plain Java interface for now so the project can
 * compile while we refactor away from root-shell based access.
 */
public interface IPrivilegedFileService {

    LsOutputBean stat(String absolutePath);

    List<LsOutputBean> list(String absolutePath);
}
