package org.primftpd.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.primftpd.R;
import org.primftpd.prefs.LoadPrefsUtil;
import org.primftpd.prefs.PrefsBean;
import org.primftpd.util.IpAddressBean;
import org.primftpd.util.IpAddressProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class QrFragment extends DialogFragment {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View view = inflater.inflate(R.layout.qr, container, false);
        LinearLayout urlsParent = view.findViewById(R.id.qrUrlsParent);
        View loading = view.findViewById(R.id.qrLoading);

        IpAddressProvider ipAddressProvider = new IpAddressProvider();
        List<IpAddressBean> ipAddressBeans = ipAddressProvider.ipAddressTexts(getContext(), true, true);

        PrefsBean prefsBean = LoadPrefsUtil.loadPrefs(logger, LoadPrefsUtil.getPrefs(getContext()));

        String ip = null;
        for (IpAddressBean bean : ipAddressBeans) {
            // 修复方法名: getInterfaceName
            if (bean.getInterfaceName().contains("wlan")) {
                ip = bean.getIpAddress();
                break;
            }
        }
        if (ip == null && !ipAddressBeans.isEmpty()) {
            ip = ipAddressBeans.get(0).getIpAddress();
        }

        if (ip != null) {
            loading.setVisibility(View.GONE);
            String url = "ftp://" + prefsBean.getUserName() + "@" + ip + ":" + prefsBean.getPortStr();
            
            // 动态添加 URL 文本
            TextView urlTextView = new TextView(getContext());
            urlTextView.setText(url);
            urlTextView.setTextIsSelectable(true);
            urlsParent.addView(urlTextView);

            QRCodeWriter writer = new QRCodeWriter();
            try {
                BitMatrix bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512);
                int width = bitMatrix.getWidth();
                int height = bitMatrix.getHeight();
                Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }
                // 修复 ID: qrImage
                ((ImageView) view.findViewById(R.id.qrImage)).setImageBitmap(bmp);
            } catch (WriterException e) {
                logger.error("could not create QR code", e);
            }
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
