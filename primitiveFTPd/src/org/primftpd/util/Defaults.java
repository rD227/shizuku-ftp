package org.primftpd.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;


import org.primftpd.crypto.HostKeyAlgorithm;
import org.primftpd.filepicker.ResettingFilePickerActivity;
import org.primftpd.filepicker.nononsenseapps.AbstractFilePickerActivity;
import org.primftpd.filepicker.nononsenseapps.FilePickerActivity;

import java.io.File;
import java.util.UUID;

@SuppressLint("SdCardPath")
public final class Defaults {
	private Defaults(){}

	public static final File HOME_DIR;
	public static final File DOWNLOADS_DIR;
	static {
		File home;
		try {
			// Environment.getExternalStorageDirectory() can throw NPE in Compose Preview/Layoutlib
			home = Environment.getExternalStorageDirectory();
		} catch (Throwable t) {
			home = new File("/sdcard");
		}
		HOME_DIR = home;

		File downloads;
		try {
			downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		} catch (Throwable t) {
			downloads = new File(HOME_DIR, "Download");
		}
		DOWNLOADS_DIR = downloads;
	}

	public static final String PUB_KEY_AUTH_KEY_PATH_OLD =
		HOME_DIR.getAbsolutePath() + "/.ssh/authorized_keys";
	public static final String PUB_KEY_AUTH_KEY_PATH_OLDER =
			HOME_DIR.getAbsolutePath() + "/.ssh/authorized_key.pub";

	public static final HostKeyAlgorithm DEFAULT_HOST_KEY_ALGO = HostKeyAlgorithm.ED_25519;

	public static File homeDirScoped(Context ctxt) {
		File dir = null;
		try {
			dir = ctxt.getExternalFilesDir(null);
		} catch (Throwable t) {
			// ignore
		}
		if (dir != null) return dir;
		try {
			dir = ctxt.getFilesDir();
		} catch (Throwable t) {
			// ignore
		}
		return dir != null ? dir : HOME_DIR;
	}
	public static String pubKeyAuthKeyPath(Context ctxt) {
		return homeDirScoped(ctxt).getAbsolutePath() + "/.ssh/authorized_keys";
	}
	public static File quickShareTmpDir(Context ctxt) {
		return new File(homeDirScoped(ctxt), "quick-share");
	}
	public static File rootCopyTmpDir(Context ctxt) {
		return new File(homeDirScoped(ctxt), "root-copy");
	}

	public static File buildTmpDir(Context ctxt, TmpDirType type) {
		File tmpDir = null;
		switch (type) {
			case QUICK_SHARE:
				tmpDir = Defaults.quickShareTmpDir(ctxt);
				break;
			case ROOT_COPY:
				tmpDir = Defaults.rootCopyTmpDir(ctxt);
				break;
		}
		if (tmpDir != null) {
			tmpDir.mkdirs();
			UUID uuid = UUID.randomUUID();
			File targetPath = new File(tmpDir, uuid.toString());
			targetPath.mkdir();
			return targetPath;
		}
		return null;
	}

	public static Intent createDefaultDirPicker(Context ctxt) {
		return createDefaultDirPicker(ctxt, HOME_DIR);
	}

	public static Intent createDefaultDirPicker(Context ctxt, File initialVal) {
		Intent dirPickerIntent = new Intent(ctxt, ResettingFilePickerActivity.class);
		dirPickerIntent.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
		dirPickerIntent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
		dirPickerIntent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);
		dirPickerIntent.putExtra(FilePickerActivity.EXTRA_START_PATH, initialVal.getAbsolutePath());
		return dirPickerIntent;
	}

	public static Intent createPrefDirPicker(Context ctxt, File initialVal, String prefKey) {
		Intent dirPickerIntent = createDefaultDirPicker(ctxt, initialVal);
		dirPickerIntent.putExtra(AbstractFilePickerActivity.MODE_SAFE_PREFERENCE, prefKey);
		return dirPickerIntent;
	}

	public static Intent exportDirPicker(Context ctxt, File initialVal) {
		Intent dirPickerIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

		// 添加读写权限标志，以便返回的 URI 可以持久化访问
		dirPickerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
				| Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				| Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

		// 如果提供了初始目录，将其转换为 URI 并作为初始位置传入
		if (initialVal != null) {
			Uri initialUri = Uri.fromFile(initialVal);
			dirPickerIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
		}
		return dirPickerIntent;
	}

	public static Intent createDirAndFilePicker(Context ctxt) {
		Intent intent = new Intent(ctxt, ResettingFilePickerActivity.class);
		intent.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
		intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
		intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE_AND_DIR);
		intent.putExtra(FilePickerActivity.EXTRA_START_PATH, HOME_DIR.getAbsolutePath());
		return intent;
	}
}
