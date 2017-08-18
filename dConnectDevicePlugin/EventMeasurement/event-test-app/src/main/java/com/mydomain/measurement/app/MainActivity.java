package com.mydomain.measurement.app;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.deviceconnect.message.DConnectEventMessage;
import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.message.DConnectResponseMessage;
import org.deviceconnect.message.DConnectSDK;
import org.deviceconnect.message.DConnectSDKFactory;
import org.deviceconnect.profile.AuthorizationProfileConstants;
import org.deviceconnect.profile.ServiceDiscoveryProfileConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Measurement";
    private static final int EVENT_COUNT_PER_SERVICE = 1000;
    private static final long EVENT_INTERVAL = 1000; //msec.
    private static final String APP_NAME = "Event Measurement";
    private static final String[] SCOPE = {
            "serviceDiscovery",
            "serviceInformation",
            "system",
            "deviceOrientation"
    };

    private DConnectSDK mSDK;
    private WebSocketConnector mWebSocket;
    private boolean mIsExecuting = false;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService mWebSocketMonitor = Executors.newSingleThreadExecutor();
    private final Map<String, EventCollector> mEventCollectors = Collections.synchronizedMap(new HashMap<String, EventCollector>());

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // デバイスコネクトSDKの初期化
        mSDK = DConnectSDKFactory.create(this, DConnectSDKFactory.Type.HTTP);

        // WebSocket管理クラスの初期化
        mWebSocket = new WebSocketConnector(mSDK, mWebSocketMonitor);
        mWebSocket.setOnCloseListener(new WebSocketConnector.OnCloseListener() {
            @Override
            public void onClose() {
                if (!mIsExecuting) {
                    return;
                }
                if (openWebSocket()) {
                    debug("Re-open websocket");
                    synchronized (mEventCollectors) {
                        for (Map.Entry<String, EventCollector> entry : mEventCollectors.entrySet()) {
                            entry.getValue().startEvent();
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to re-open websocket.");
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mIsExecuting) {
            mIsExecuting = true;
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    // アクセストークン取得
                    String accessToken = authorization();
                    if (accessToken != null) {
                        debug("accessToken: " + accessToken);
                        mSDK.setAccessToken(accessToken);
                    } else {
                        debug("accessToken: <Not needed>");
                    }

                    // WebSocket接続
                    if (openWebSocket()) {
                        debug("WebSocket is open");
                    } else {
                        Log.e(TAG, "Failed to open websocket.");
                        return;

                    }

                    // 計測用サービス検索
                    List<DConnectServiceInfo> services = serviceDiscoveryForTest();

                    // 計測実行
                    int[] payloadSizes = {
                            256, // <= 256 KB
                            128, // <= 128 KB
                            1    // <=   1 KB
                    };
                    for (int size : payloadSizes) {
                        for (DConnectServiceInfo service : services) {
                            final String serviceId = service.getId();
                            final EventCollector collector = new EventCollector(mSDK, service, size);

                            if (collector.isReported()) {
                                Log.w(TAG, "Already Reported: " + collector.getReportFileName(service));
                                continue;
                            }

                            try {

                                mEventCollectors.put(serviceId, collector);
                                if (!collector.startEvent()) {
                                    return;
                                }
                                collector.waitEvent();
                                mEventCollectors.remove(serviceId);
                                collector.stopEvent();

                                collector.report();
                            } catch (InterruptedException e) {
                                // 計測中断
                                Log.e(TAG, "Interrupted test.");
                                return;
                            } catch (IOException e) {
                                // 計測結果の保存に失敗
                                e.printStackTrace();
                                Log.e(TAG, "Failed to store report: serviceId = " + serviceId);
                                return;
                            }
                        }
                    }


                    mIsExecuting = false;
                    mSDK.disconnectWebSocket();
                }
            });
        }

    }

    private String authorization() {
        DConnectResponseMessage response = mSDK.authorization(APP_NAME, SCOPE);
        if (response.getResult() == DConnectMessage.RESULT_OK) {
            return response.getString(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN);
        } else {
            return null;
        }
    }

    private boolean openWebSocket() {
        try {
            mWebSocket.connect();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private synchronized List<DConnectServiceInfo> serviceDiscoveryForTest() {
        DConnectResponseMessage response = mSDK.serviceDiscovery();
        List<DConnectServiceInfo> list = new ArrayList<>();
        if (response.getResult() == DConnectMessage.RESULT_OK) {
            List<Object> services = response.getList(ServiceDiscoveryProfileConstants.PARAM_SERVICES);

            for (Object obj : services) {
                if (obj instanceof DConnectMessage) {
                    DConnectMessage service = (DConnectMessage) obj;
                    String id = service.getString(ServiceDiscoveryProfileConstants.PARAM_ID);

                    if (id != null && id.startsWith("event-test-")) {
                        DConnectServiceInfo info = new DConnectServiceInfo(id);
                        list.add(info);
                    }
                }
            }
        }
        return list;
    }

    private static DConnectMessage.ErrorCode getErrorCode(final DConnectResponseMessage response) {
        return DConnectMessage.ErrorCode.getInstance(response.getErrorCode());
    }

    private static void debug(final String message) {
        Log.d(TAG, message);
    }

    private static class DConnectServiceInfo {
        private final String mId;

        DConnectServiceInfo(final String id) {
            if (id == null) {
                throw new IllegalArgumentException("id is null.");
            }
            mId = id;
        }

        String getId() {
            return mId;
        }

        String getConnectionTypeName() {
            if (mId.contains("-binder")) {
                return "binder";
            } else if (mId.contains("-broadcast")) {
                return "broadcast";
            } else if (mId.contains("-included")) {
                return "included";
            } else {
                return "unknown";
            }
        }
    }

    private static class WebSocketConnector {

        private final DConnectSDK mSDK;
        private final ExecutorService mExecutor;
        private State mState = State.CLOSED;
        private Exception mError;
        private OnCloseListener mOnCloseListener;

        WebSocketConnector(final DConnectSDK sdk, final ExecutorService executorService) {
            mSDK = sdk;
            mExecutor = executorService;
        }

        synchronized void connect() throws Exception {
            switch (mState) {
                case CLOSED:
                    mError = null;
                    mState = State.OPENING;
                    mSDK.connectWebSocket(new DConnectSDK.OnWebSocketListener() {
                        @Override
                        public void onOpen() {
                            debug("websocket: open");
                            mState = State.OPEN;
                            unlock();
                        }

                        @Override
                        public void onClose() {
                            debug("websocket: close");
                            mState = State.CLOSED;
                            //mSDK.disconnectWebSocket();
                            unlock();

                            mExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    OnCloseListener listener = mOnCloseListener;
                                    if (listener != null) {
                                        listener.onClose();
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(final Exception e) {
                            debug("websocket: error = " + e.getMessage());
                            mError = e;
                            unlock();
                        }
                    });
                    wait();
                    if (mError != null) {
                        throw mError;
                    }
                    break;
                default:
                    break;
            }
        }

        synchronized void unlock() {
            notifyAll();
        }

        void setOnCloseListener(final OnCloseListener l) {
            mOnCloseListener = l;
        }

        interface OnCloseListener {
            void onClose();
        }

        enum State {
            OPENING,
            OPEN,
            CLOSED
        }
    }

    private static class EventCollector {

        private final DConnectSDK mSDK;

        private final StringBuffer mBuffer = new StringBuffer();

        private final CountDownLatch mLock = new CountDownLatch(EVENT_COUNT_PER_SERVICE);

        private final DConnectServiceInfo mServiceInfo;

        private final int mEventPayloadSize;

        private final File mFile;

        private DConnectResponseMessage mStartResponse;

        private final DConnectSDK.OnEventListener mEventListener = new DConnectSDK.OnEventListener() {
            @Override
            public void onResponse(final DConnectResponseMessage response) {
                debug("response: result = " + response.getResult());
                mStartResponse = response;
                synchronized (EventCollector.this) {
                    EventCollector.this.notifyAll();
                }
            }

            @Override
            public void onMessage(final DConnectEventMessage event) {
                // 計測用データの検証
                String dummyPayload = event.getString("payload");
                int length = getByteSize(dummyPayload) / 1024;
                if (length != mEventPayloadSize) {
                    Log.e(TAG, "Invalid length payload. Expected length is " + mEventPayloadSize + " KB.");
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            throw new RuntimeException("Invalid length payload. Expected length is " + mEventPayloadSize + " KB.");
                        }
                    });
                    return;
                }

                String serviceId = event.getString("serviceId");
                debug("message: cnt = " + mLock.getCount() + ", length = " + length + " KB, serviceId = " + serviceId);

                event.put("app-receive-time", System.currentTimeMillis());
                add(event);
            }
        };

        private int getByteSize(final String str) {
            try {
                return str.getBytes("US-ASCII").length;
            } catch (UnsupportedEncodingException e) {
                return -1;
            }
        }

        EventCollector(final DConnectSDK sdk,
                       final DConnectServiceInfo serviceInfo,
                       final int payloadSize) {
            mSDK = sdk;
            mServiceInfo = serviceInfo;
            mEventPayloadSize = payloadSize;

            String dir = Environment.getExternalStorageDirectory().getPath();
            mFile = new File(dir, getReportFileName(mServiceInfo));
        }

        private String getReportFileName(final DConnectServiceInfo serviceInfo) {
            String name = Build.MODEL + "_" + Build.VERSION.RELEASE + "_" + serviceInfo.getConnectionTypeName() + "_" + mEventPayloadSize + "KB.csv";
            return name.toLowerCase();
        }

        private void add(final DConnectEventMessage event) {
            long rowNo = (EVENT_COUNT_PER_SERVICE - mLock.getCount() + 1);
            mBuffer.append(rowNo + "," + createCSVForEvent(event) + "\n");

            mLock.countDown();
        }

        void waitEvent() throws InterruptedException {
            mLock.await();
        }

        private DConnectSDK.URIBuilder createURIBuilder() {
            return mSDK.createURIBuilder()
                    .setApi("gotapi")
                    .setProfile("deviceOrientation")
                    .setAttribute("onDeviceOrientation");
        }

        boolean startEvent() {
            final String serviceId = mServiceInfo.getId();

            Uri uri = createURIBuilder()
                    .setServiceId(serviceId)
                    .addParameter("interval", Long.toString(EVENT_INTERVAL))
                    .addParameter("payload", Integer.toString(mEventPayloadSize * 1024))
                    .build();
            mSDK.addEventListener(uri, mEventListener);

            try {
                synchronized (this) {
                    wait(1000);
                }
            } catch (InterruptedException e) {
                // NOP.
            }

            if (mStartResponse != null) {
                if (mStartResponse.getResult() == DConnectMessage.RESULT_OK) {
                    debug("Started event: serviceId = " + serviceId);
                    return true;
                } else {
                    Log.e(TAG, "Failed to start event: serviceId = " + serviceId + ", errorMessage = " + mStartResponse.getErrorMessage());
                    return false;
                }
            } else {
                Log.e(TAG, "No response to start event: serviceId = " + serviceId);
                return false;
            }
        }

        void stopEvent() {
            final String serviceId = mServiceInfo.getId();

            Uri uri = createURIBuilder()
                    .setServiceId(serviceId)
                    .build();
            mSDK.removeEventListener(uri);

            debug("Stopped event: serviceId = " + serviceId);
        }

        boolean isReported() {
            return mFile.exists();
        }

        void report() throws IOException {
            if (!mFile.exists()) {
                try {
                    if (!mFile.createNewFile()) {
                        throw new IOException("Failed to create new file: " + mFile.getName());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            storeAsFile(mFile, mBuffer);

            debug("Reported: " + mFile.getAbsolutePath());
        }

        private String createCSVForEvent(DConnectEventMessage event) {

            long[] timestamps = {
                    event.getLong("plugin-created-time"),
                    event.getLong("plugin-sent-time"),
                    event.getLong("manager-receive-time"),
                    event.getLong("manager-json-time"),
                    event.getLong("manager-sent-time"),
                    event.getLong("app-receive-time")
            };
            StringBuffer row = new StringBuffer();
            for (int i = 1; i < timestamps.length; i++) {
                long t = timestamps[i] - timestamps[i - 1];
                if (i != 1) {
                    row.append(",");
                }
                row.append(t);
            }
            long total = timestamps[timestamps.length - 1] - timestamps[0];
            row.append("," + total);

            debug("row: " + row.toString());

            return row.toString();
        }

        private void storeAsFile(final File file, final StringBuffer buffer) throws IOException {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                fos.write(buffer.toString().getBytes("UTF-8"));
                fos.flush();
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }
        }
    }
}
