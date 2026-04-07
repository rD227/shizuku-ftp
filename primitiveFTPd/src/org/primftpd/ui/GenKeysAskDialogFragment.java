package org.primftpd.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ViewGroup;

import org.primftpd.R;
import org.primftpd.crypto.HostKeyAlgorithm;
import org.primftpd.prefs.LoadPrefsUtil;
import org.primftpd.util.KeyFingerprintProvider;
import org.primftpd.util.ServicesStartStopUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class GenKeysAskDialogFragment extends DialogFragment {
    public static final String KEY_START_SERVER = "START_SERVER";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private boolean startServerOnFinish = false;

    @Override
    public void setArguments(Bundle args) {
        super.setArguments(args);
        if (args != null) {
            startServerOnFinish = args.getBoolean(KEY_START_SERVER, false);
        }
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        logger.debug("showing gen key dialog");
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.generateKeysMessage);
        builder.setPositiveButton(R.string.generate, (dialog, id) ->
                genKeysAndShowProgressDiag(startServerOnFinish)
        );
        builder.setNegativeButton(R.string.cancel, (dialog, id) -> {
            // nothing
        });
        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public void genKeysAndShowProgressDiag(boolean startServerOnFinish) {
        logger.trace("genKeysAndShowProgressDiag()");

        Context ctxt = getContext();
        if (ctxt == null) {
            logger.warn("context is null");
            return;
        }

        KeyFingerprintProvider keyFingerprintProvider = new KeyFingerprintProvider();

        // 异步生成密钥
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try {
                for (HostKeyAlgorithm hka : HostKeyAlgorithm.values()) {
                    try {
                        keyFingerprintProvider.deleteKeyFiles(ctxt, hka);
                    } catch (Exception e) {
                        logger.warn("could not delete old key files for {}", hka.getAlgorithmName());
                    }
                }

                SharedPreferences prefs = LoadPrefsUtil.getPrefs(ctxt);
                Set<String> configuredAlgos = prefs.getStringSet(
                        LoadPrefsUtil.PREF_KEY_HOSTKEY_ALGOS,
                        LoadPrefsUtil.HOSTKEY_ALGOS_DEFAULTS);

                for (HostKeyAlgorithm hka : HostKeyAlgorithm.values()) {
                    if (configuredAlgos != null && configuredAlgos.contains(hka.getPreferenceValue())) {
                        try (
                                FileOutputStream publickeyFos = keyFingerprintProvider.buildPublickeyOutStream(ctxt, hka);
                                FileOutputStream privatekeyFos = keyFingerprintProvider.buildPrivatekeyOutStream(ctxt, hka)
                        ) {
                            hka.generateKey(publickeyFos, privatekeyFos);
                        } catch (Exception e) {
                            logger.error("could not generate key {}", hka.getAlgorithmName(), e);
                        }
                    }
                }

                keyFingerprintProvider.calcPubkeyFingerprints(ctxt);

                if (startServerOnFinish) {
                    ServicesStartStopUtil.startServers(ctxt);
                }
            } finally {
                executorService.shutdown();
            }
        });
    }
}
