package org.primftpd.services;

import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.Listener;
import org.apache.ftpserver.listener.ListenerFactory;
import org.primftpd.events.ClientActionEvent;
import org.primftpd.filesystem.FsFtpFileSystemView;
import org.primftpd.filesystem.QuickShareFtpFileSystemView;
import org.primftpd.filesystem.RoSafFtpFileSystemView;
import org.primftpd.filesystem.RootFtpFileSystemView;
import org.primftpd.filesystem.SafFtpFileSystemView;
import org.primftpd.filesystem.ShizukuFtpFileSystemView;
import org.primftpd.filesystem.VirtualFtpFileSystemView;
import org.primftpd.io.PrimNioListener;
import org.primftpd.shizuku.ShizukuServiceManager;
import org.primftpd.util.RemoteIpChecker;
import org.primftpd.util.StringUtils;

import java.net.SocketAddress;

import eu.chainfire.libsuperuser.Shell;

/**
 * Implements a FTP server.
 */
public class FtpServerService extends AbstractServerService
{
	private FtpServer ftpServer;

	@Override
	protected ServerServiceHandler createServiceHandler(
			Looper serviceLooper,
			AbstractServerService service)
	{
		return new ServerServiceHandler(serviceLooper, service, getServiceName());
	}

	@Override
	protected Object getServer()
	{
		return ftpServer;
	}

	@Override
	protected int getPort()
	{
		return prefsBean.getPort();
	}

	@Override
	protected String getServiceName()
	{
		return "ftp";
	}

	@Override
	protected ClientActionEvent.Protocol getProtocol() {
		return ClientActionEvent.Protocol.FTP;
	}

	@Override
	protected void stopServer()
	{
		if (ftpServer != null) {
			ftpServer.stop();
			ftpServer = null;
		} else {
			logger.info("ssh server already null");
		}
	}

	@Override
	protected boolean launchServer(final Shell.Interactive shell) {
		ListenerFactory listenerFactory = new ListenerFactory();
		listenerFactory.setPort(prefsBean.getPort());
		String bindIp = getBindIp();
		if (bindIp != null) {
			listenerFactory.setServerAddress(bindIp);
		}

		DataConnectionConfigurationFactory dataConConfigFactory = new DataConnectionConfigurationFactory();
		String passivePorts = prefsBean.getFtpPassivePorts();
		if (StringUtils.isNotBlank(passivePorts)){
			dataConConfigFactory.setPassivePorts(passivePorts);
		}
		if (prefsBean.getIdleTimeout() != null) {
			listenerFactory.setIdleTimeout(prefsBean.getIdleTimeout());
			dataConConfigFactory.setIdleTime(prefsBean.getIdleTimeout());
		}

		listenerFactory.setSessionFilter(session -> {
			SocketAddress remoteAddress = session.getRemoteAddress();
			return RemoteIpChecker.ipAllowed(remoteAddress, this, logger);
		});
		listenerFactory.setDataConnectionConfiguration(dataConConfigFactory.createDataConnectionConfiguration());

		FtpServerFactory serverFactory = new FtpServerFactory();
		serverFactory.addListener("default", createListener(listenerFactory));

		serverFactory.setUserManager(new AndroidPrefsUserManager(prefsBean));
		serverFactory.setFileSystem(user -> {
			logger.info("SHIZUKU_DEBUG <<< FTP setFileSystem storageType={}", prefsBean.getStorageType());
			if (quickShareBean != null) {
				logger.info("SHIZUKU_DEBUG <<< FTP using QuickShareFtpFileSystemView");
				return new QuickShareFtpFileSystemView(
						FtpServerService.this,
						quickShareBean.getTmpDir(),
						user);
			} else {
				switch (prefsBean.getStorageType()) {
						case PLAIN:
							logger.info("SHIZUKU_DEBUG <<< FTP using FsFtpFileSystemView");
							return new FsFtpFileSystemView(
									FtpServerService.this,
									prefsBean.getStartDir(),
									user);
						case ROOT:
							logger.info("SHIZUKU_DEBUG <<< FTP using RootFtpFileSystemView");
							return new RootFtpFileSystemView(
									FtpServerService.this,
									shell,
									prefsBean.getStartDir(),
									user);
						case SHIZUKU:
							logger.info("SHIZUKU_DEBUG <<< FTP using ShizukuFtpFileSystemView");
							return new ShizukuFtpFileSystemView(
										FtpServerService.this,
										new ShizukuServiceManager(),
										prefsBean.getStartDir(),
										user);
						case SAF:
							logger.info("SHIZUKU_DEBUG <<< FTP using SafFtpFileSystemView");
							return new SafFtpFileSystemView(
										FtpServerService.this,
										Uri.parse(prefsBean.getSafUrl()),
										user);
						case RO_SAF:
							logger.info("SHIZUKU_DEBUG <<< FTP using RoSafFtpFileSystemView");
							return new RoSafFtpFileSystemView(
										FtpServerService.this,
										Uri.parse(prefsBean.getSafUrl()),
										user);
						case VIRTUAL:
							logger.info("SHIZUKU_DEBUG <<< FTP using VirtualFtpFileSystemView");
							return new VirtualFtpFileSystemView(
										FtpServerService.this,
										new FsFtpFileSystemView(
													FtpServerService.this,
													prefsBean.getStartDir(),
													user),
										new RootFtpFileSystemView(
													FtpServerService.this,
													shell,
													prefsBean.getStartDir(),
													user),
										new SafFtpFileSystemView(
													FtpServerService.this,
													Uri.parse(prefsBean.getSafUrl()),
													user),
										new RoSafFtpFileSystemView(
													FtpServerService.this,
													Uri.parse(prefsBean.getSafUrl()),
													user),
										new ShizukuFtpFileSystemView(
													FtpServerService.this,
													new ShizukuServiceManager(),
													prefsBean.getStartDir(),
													user),
										prefsBean.getStartDir(),
										user
									);
					}
			}
			return null;
		});

		ConnectionConfigFactory conCfg = new ConnectionConfigFactory();
		conCfg.setAnonymousLoginEnabled(prefsBean.isAnonymousLogin());
		conCfg.setMaxLoginFailures(5);
		conCfg.setLoginFailureDelay(2000);
		serverFactory.setConnectionConfig(conCfg.createConnectionConfig());

		ftpServer = serverFactory.createServer();
		try {
			ftpServer.start();
			return true;
		} catch (Throwable e) {
			ftpServer = null;
			handleServerStartError(e);
			return false;
		}
	}

	private Listener createListener(@NonNull ListenerFactory listenerFactory) {
		return new PrimNioListener(
				listenerFactory.getServerAddress(),
				listenerFactory.getPort(),
				listenerFactory.isImplicitSsl(),
				listenerFactory.getSslConfiguration(),
				listenerFactory.getDataConnectionConfiguration(),
				listenerFactory.getIdleTimeout(),
				listenerFactory.getSessionFilter()
		);
	}
}
