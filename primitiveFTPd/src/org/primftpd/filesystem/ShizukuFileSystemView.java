package org.primftpd.filesystem;

import org.primftpd.pojo.LsOutputBean;
import org.primftpd.pojo.LsOutputParser;
import org.primftpd.services.PftpdService;

import java.util.List;

import androidx.annotation.NonNull;
import eu.chainfire.libsuperuser.Shell;

public abstract class ShizukuFileSystemView<TFile extends ShizukuFile<TMina, ? extends ShizukuFileSystemView>, TMina> extends AbstractFileSystemView {

    private final MediaScannerClient mediaScannerClient;
    protected final Shell.Interactive shell;

    public ShizukuFileSystemView(PftpdService pftpdService, Shell.Interactive shell) {
        super(pftpdService);
        this.mediaScannerClient = new MediaScannerClient(pftpdService.getContext());
        this.shell = shell;
    }

    public final MediaScannerClient getMediaScannerClient() {
        return mediaScannerClient;
    }

    public final Shell.Interactive getShell() {
        return shell;
    }

    protected abstract TFile createFile(String absPath, LsOutputBean bean);

    protected abstract String absolute(String file);

    public TFile getFile(String file) {
        logger.trace("getFile({})", file);

        String abs = absolute(file);
        logger.info("  getFile(abs: {})", abs);

        final LsOutputParser parser = new LsOutputParser();
        final LsOutputBean[] wrapper = new LsOutputBean[1];
        final String cmd = "ls -lad " + ShizukuFile.escapePath(abs);
        logger.info("  running command: {}", cmd);
        shell.addCommand(cmd, 0, new Shell.OnCommandResultListener() {
            @Override
            public void onCommandResult(int commandCode, int exitCode, @NonNull List<String> output) {
                logger.info("  command result: exitCode={}, outputLines={}", exitCode, output.size());
                if (exitCode == 0 && !output.isEmpty()) {
                    String line = output.get(0);
                    logger.info("  parsing output line: '{}'", line);
                    wrapper[0] = parser.parseLine(line);
                } else {
                    logger.warn("  could not run 'ls' command (exitCode: {}), output: {}", exitCode, output);
                }
            }
        });
        shell.waitForIdle();
        LsOutputBean bean = wrapper[0];
        if (bean != null) {
            logger.info("  successfully got bean for {}: isDir={}, size={}", abs, bean.isDir(), bean.getSize());
            return createFile(abs, bean);
        } else {
            logger.warn("  bean is null for {}, returning dummy bean", abs);
            String name;
            if (abs.contains("/")) {
                name = abs.substring(abs.lastIndexOf('/') + 1);
            } else {
                name = abs;
            }
            bean = new LsOutputBean(name);
            return createFile(abs, bean);
        }
    }
}
