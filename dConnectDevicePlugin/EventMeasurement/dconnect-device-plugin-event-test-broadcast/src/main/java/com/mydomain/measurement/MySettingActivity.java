package com.mydomain.measurement;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import org.deviceconnect.message.DConnectEventMessage;
import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.message.DConnectResponseMessage;
import org.deviceconnect.message.DConnectSDK;
import org.deviceconnect.message.DConnectSDKFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class MySettingActivity extends Activity {

    private static final String TAG = "Measurement";

    // バインダー
    //private static final String SERVICE_ID = "my_service_id.28c952d22db291d6ca4e832c41da7231.localhost.deviceconnect.org";

    // ブロードキャスト
    private static final String SERVICE_ID = "my_service_id.40799c401f3f743ba8a6ddddd670eaaf.localhost.deviceconnect.org";

    // 同梱
    //private static final String SERVICE_ID = "my_service_id.1677367425856ccefbfdba246cb66e.localhost.deviceconnect.org";

    private DConnectSDK mSDK;

    private DConnectSDK.OnEventListener mEventListener = new DConnectSDK.OnEventListener() {
        @Override
        public void onResponse(final DConnectResponseMessage response) {
            debug("onResponse: result = " + response.getResult());
            if (response.getResult() == 1) { // Error
                debug("onResponse: error = " + response.getErrorMessage());

                DConnectResponseMessage.ErrorCode errorCode =
                        DConnectResponseMessage.ErrorCode.getInstance(response.getErrorCode());
                switch (errorCode) {
                    case AUTHORIZATION:
                    case EMPTY_ACCESS_TOKEN:
                    case EXPIRED_ACCESS_TOKEN:
                        String appName = "EventMeasurement";
                        String[] scope = {"deviceOrientation"};
                        mSDK.authorization(appName, scope, new DConnectSDK.OnAuthorizationListener() {
                            @Override
                            public void onResponse(final String clientId, final String accessToken) {
                                debug("Authorization: onResponse: " + accessToken);
                                mSDK.setAccessToken(accessToken);
                                startEvent(getServiceId());
                            }

                            @Override
                            public void onError(final int errorCode, final String errorMessage) {
                                debug("Authorization: onError: " + errorMessage);
                            }
                        });
                        break;
                }
            } else { // Success
                if (!mSDK.isConnectedWebSocket()) {
                    mSDK.connectWebSocket(mWebSocketListener);
                }
            }
        }

        @Override
        public void onMessage(final DConnectEventMessage event) {
            event.put("app-receive-time", System.currentTimeMillis());

            check(event);
        }
    };

    private static final int MAX_COUNT = 1000;
    private int mCount = 0;
    private boolean mIsFinished = false;

    public void check(DConnectEventMessage event) {

        long[] timestamps = {
            event.getLong("plugin-created-time"),
            //event.getLong("plugin-local-oauth-time"),
            event.getLong("plugin-sent-time"),
            event.getLong("manager-receive-time"),
            event.getLong("manager-json-time"),
            event.getLong("manager-sent-time"),
            event.getLong("app-receive-time")
        };
        long[] times = new long[timestamps.length - 1];
        for (int i = 1; i < timestamps.length; i++) {
            times[i - 1] = timestamps[i] - timestamps[i - 1];
        }

        if (mCount < MAX_COUNT) {
            mCount++;

            int length = times.length;
            StringBuilder builder = new StringBuilder();
            builder.append(timestamps[timestamps.length - 1] - timestamps[0] + ","); // end to end
            for (int i = 0; i < length; i++) {
                builder.append(times[i]);
                if (i == length - 1) {
                    builder.append("\n");
                } else {
                    builder.append(",");
                }
            }
            String row = builder.toString();

            mBuffer.append(row);
            debug(mCount + ": " + row);
        } else {
            if (!mIsFinished) {
                mIsFinished = true;
                try {
                    stopEvent(getServiceId());
                    storeFile();
                    debug("Finished!");
                } catch (IOException e) {
                    debug("Finished with error!!!");
                    e.printStackTrace();
                }
            }
        }
    }

    private void storeFile() throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(mFile);
            fos.write(mBuffer.toString().getBytes("UTF-8"));
            fos.flush();
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }

    private File mFile;
    private StringBuffer mBuffer = new StringBuffer();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        mSDK = DConnectSDKFactory.create(this, DConnectSDKFactory.Type.HTTP);

        String dir = Environment.getExternalStorageDirectory().getPath();
        mFile = new File(dir, "event.csv");
        if (!mFile.exists()) {
            try {
                mFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsResumed = true;

        mCount = 0;
        mBuffer = new StringBuffer();
        mIsFinished = false;

        startEvent(getServiceId());

    }

    private boolean mIsResumed;

    DConnectSDK.OnWebSocketListener mWebSocketListener = new DConnectSDK.OnWebSocketListener() {
        @Override
        public void onOpen() {
            debug("websocket: open");


        }

        @Override
        public void onClose() {
            debug("websocket: close");

            if (mIsResumed) {
                mSDK.connectWebSocket(mWebSocketListener);
            }
        }

        @Override
        public void onError(final Exception e) {
            debug("websocket: error = " + e.getMessage());
            e.printStackTrace();
        }
    };

    @Override
    protected void onPause() {
        mIsResumed = false;

        stopEvent(getServiceId());
        mSDK.disconnectWebSocket();

        super.onPause();
    }



    private static void debug(final String message) {
        Log.d(TAG, message);
    }

    private String getServiceId() {
        return SERVICE_ID;
    }

    private DConnectSDK.URIBuilder createURIBuilder() {
        return mSDK.createURIBuilder()
                .setApi("gotapi")
                .setProfile("deviceOrientation")
                .setAttribute("onDeviceOrientation");
    }

    private void startEvent(final String serviceId) {
        Uri uri = createURIBuilder()
                .setServiceId(serviceId)
                .addParameter("interval", "200")
                .build();
        mSDK.addEventListener(uri, mEventListener);
    }

    private void stopEvent(final String serviceId) {
        Uri uri = createURIBuilder()
                .setServiceId(serviceId)
                .build();
        mSDK.removeEventListener(uri);
    }
}