/*
 QRService.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.tag.services.qr;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.deviceconnect.android.deviceplugin.tag.R;
import org.deviceconnect.android.deviceplugin.tag.services.TagConstants;
import org.deviceconnect.android.deviceplugin.tag.services.TagInfo;
import org.deviceconnect.android.deviceplugin.tag.services.TagService;
import org.deviceconnect.android.deviceplugin.tag.activity.QRReaderActivity;
import org.deviceconnect.android.deviceplugin.tag.services.qr.profiles.QRTagProfile;
import org.deviceconnect.android.util.NotificationUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * QRコードのタグを操作するためのサービス.
 *
 * @author NTT DOCOMO, INC.
 */
public class QRService extends TagService {
    /** Notification Id */
    private final int NOTIFICATION_QR_ID = 3519;

    /**
     * コンテキスト.
     */
    private Context mContext;

    /**
     * 読み込み用コールバックを格納するマップ.
     */
    private Map<String, ReaderCallback> mReaderCallbackMap = new HashMap<>();

    /**
     * カウンター.
     */
    private int mCounter;

    /**
     * コールバック.
     */
    private ReaderCallback mCallback;

    /**
     * コンストラクタ.
     * @param context コンテキスト
     */
    public QRService(final Context context) {
        super("qr_service_id");
        mContext = context;
        setName("QRCode Service");
        setOnline(true);
        setNetworkType("QR");
        addProfile(new QRTagProfile());
    }

    /**
     * QRコードを読み込みます.
     * <p>
     * このメソッドを呼び出すと Activity が起動して QR コードを読み込みを開始します。
     * 読み込んだ結果はコールバックに返却されます。
     * </p>
     * @param callback コールバック
     * @param forceActivity フォアグラウンドかどうかの状態
     */
    public void readQRCode(final ReaderCallback callback, final boolean forceActivity) {
        String requestCode = createRequestCode();
        mReaderCallbackMap.put(requestCode, callback);
        startQRReaderActivity(requestCode, true, forceActivity);
    }

    /**
     * QRコードの読み込みを開始します.
     *
     * @param callback コールバック
     * @param forceActivity フォアグラウンドかどうかの状態
     */
    public void startReadQRCode(final ReaderCallback callback, final boolean forceActivity) {
        String requestCode = createRequestCode();
        mCallback = callback;
        startQRReaderActivity(requestCode, false, forceActivity);
    }

    /**
     * QRコードの読み込みを停止します.
     */
    public void stopReadQRCode() {
        mCallback = null;
        stopQRReaderActivity();
    }

    /**
     * リクエストコードを作成します.
     *
     * @return リクエストコード
     */
    private String createRequestCode() {
        return "qr_code_" + (mCounter++);
    }

    /**
     * {@link QRReaderActivity} を起動します.
     *
     * @param requestCode リクエストコード
     * @param once
     * @param forceActivity フォアグラウンドかどうかの状態
     */
    private void startQRReaderActivity(final String requestCode, final boolean once, final boolean forceActivity) {
        Intent intent = new Intent();
        intent.setClass(mContext, QRReaderActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(TagConstants.EXTRA_REQUEST_CODE, requestCode);
        intent.putExtra(TagConstants.EXTRA_ONCE, once);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || forceActivity) {
            getContext().startActivity(intent);
        } else {
            NotificationUtils.createNotificationChannel(getContext());
            NotificationUtils.notify(getContext(),  NOTIFICATION_QR_ID, 0, intent,
                    getContext().getString(R.string.tag_notification_warnning));
        }
    }

    /**
     * {@link QRReaderActivity} を停止します.
     */
    private void stopQRReaderActivity() {
        TagController ctr = getTagController();
        if (ctr != null) {
            ctr.finishActivity();
        }
    }

    @Override
    public void onTagReaderActivityResult(final String requestCode, final int result, final TagInfo tagInfo) {
        if (requestCode != null) {
            ReaderCallback cb = mReaderCallbackMap.remove(requestCode);
            if (cb != null) {
                cb.onResult(result, tagInfo);
            }
        }

        if (mCallback != null) {
            mCallback.onResult(result, tagInfo);
        } else if (mReaderCallbackMap.isEmpty()) {
            stopQRReaderActivity();
        }
    }
}
