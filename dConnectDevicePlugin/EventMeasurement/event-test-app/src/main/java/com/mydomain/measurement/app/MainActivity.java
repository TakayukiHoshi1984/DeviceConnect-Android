package com.mydomain.measurement.app;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

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
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Measurement";
    private static final String APP_NAME = "Event Measurement";
    private static final String[] SCOPE = {
            "serviceDiscovery",
            "serviceInformation",
            "system",
            "deviceOrientation"
    };

    private DConnectSDK mSDK;
    private WebSocketConnector mWebSocket;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService mWebSocketMonitor = Executors.newSingleThreadExecutor();
    private final Map<String, EventCollector> mEventCollectors = Collections.synchronizedMap(new HashMap<String, EventCollector>());

    private Ui mUI;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUI = new Ui() {
            @Override
            void onStartBtn() {
                startMeasurement();
            }

            @Override
            void onStopBtn() {
                stopMeasurement();
            }
        };

        // デバイスコネクトSDKの初期化
        mSDK = DConnectSDKFactory.create(this, DConnectSDKFactory.Type.HTTP);

        // WebSocket管理クラスの初期化
        mWebSocket = new WebSocketConnector(mSDK, mWebSocketMonitor);
        mWebSocket.setOnCloseListener(new OnWebSocketCloseListener() {
            @Override
            public void onClose() {
                if (!isMeasurement()) {
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
                    error("Failed to re-open websocket.");
                }
            }
        });
    }

    private synchronized void onFinished() {
        mUI.setEnabledStopBtn(false);
        mUI.setEnabledStartBtn(true);
        mUI.setEnabledParameters(true);
        mMeasurementThread = null;
    }

    private boolean isMeasurement() {
        return mMeasurementThread != null;
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private Thread mMeasurementThread;

    private void startMeasurement() {
        synchronized (this) {
            if (mMeasurementThread == null) {
                mUI.setEnabledStopBtn(true);
                mUI.setEnabledStartBtn(false);
                mUI.setEnabledParameters(false);

                mMeasurementThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String interval = mUI.getEventInterval();
                        final String size = mUI.getEventByteSize();
                        final String count = mUI.getEventCount();
                        final boolean isDataSaved = mUI.isDataSaved();
                        EventCollector collector;
                        String serviceId;
                        try {
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
                                error("Failed to open websocket.");
                                return;
                            }

                            // 計測用サービス検索
                            List<DConnectServiceInfo> services = serviceDiscoveryForTest();

                            // 計測実行
                            for (DConnectServiceInfo service : services) {
                                serviceId = service.getId();

//                                if (!serviceId.contains("binder")) { // TODO 消す
//                                    continue;
//                                }

                                collector = new EventCollector(mSDK, service, interval, size, count);

                                try {
                                    if (collector.isReported()) {
                                        collector.removeReport();
                                        warn("Deleted: " + collector.getReportFileName(service));
                                    }

                                    mEventCollectors.put(serviceId, collector);
                                    if (!collector.startEvent()) {
                                        return;
                                    }
                                    collector.waitEvent();
                                } catch (InterruptedException e) {
                                    // 計測中断
                                    warn("Interrupted test.");
                                    return;
                                } catch (IOException e) {
                                    warn("Stopped test");
                                    e.printStackTrace();
                                    return;
                                } finally {
                                    if (serviceId != null) {
                                        mEventCollectors.remove(serviceId);
                                    }
                                    collector.stopEvent();

                                    if (isDataSaved) {
                                        try {
                                            if (collector.mCount > 0) {
                                                debug("Invalid Sequence Rate" + collector.mInvalidSequenceCount + " / " + collector.mCount + " = " + (100 * collector.mInvalidSequenceCount  /  collector.mCount) + "%");
                                            }
                                            collector.report();
                                        } catch (IOException e) {
                                            // 計測結果の保存に失敗
                                            e.printStackTrace();
                                            error("Failed to store report: serviceId = " + collector.mServiceInfo.getId());
                                        }
                                    } else {
                                        warn("Data save is skipped.");
                                    }
                                }
                            }
                        } finally {
                            mSDK.disconnectWebSocket();
                            onFinished();
                        }
                    }
                });
                mMeasurementThread.start();
            }
        }
    }

    private synchronized void stopMeasurement() {
        if (mMeasurementThread != null) {
            mMeasurementThread.interrupt();
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

    private void debug(final String message) {
        mUI.debug(message);
        Log.d(TAG, message);
    }

    private void warn(final String message) {
        mUI.warn(message);
        Log.w(TAG, message);
    }

    private void error(final String message) {
        mUI.error(message);
        Log.e(TAG, message);
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

    private class WebSocketConnector {

        private final DConnectSDK mSDK;
        private final ExecutorService mExecutor;
        private WebSocketState mState = WebSocketState.CLOSED;
        private Exception mError;
        private OnWebSocketCloseListener mOnCloseListener;

        WebSocketConnector(final DConnectSDK sdk, final ExecutorService executorService) {
            mSDK = sdk;
            mExecutor = executorService;
        }

        synchronized void connect() throws Exception {
            switch (mState) {
                case CLOSED:
                    mError = null;
                    mState = WebSocketState.OPENING;
                    mSDK.connectWebSocket(new DConnectSDK.OnWebSocketListener() {
                        @Override
                        public void onOpen() {
                            debug("websocket: open");
                            mState = WebSocketState.OPEN;
                            unlock();
                        }

                        @Override
                        public void onClose() {
                            debug("websocket: close");
                            mState = WebSocketState.CLOSED;
                            unlock();

                            mExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    OnWebSocketCloseListener listener = mOnCloseListener;
                                    if (listener != null) {
                                        listener.onClose();
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(final Exception e) {
                            //error("websocket: error = " + e.getMessage());
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

        void setOnCloseListener(final OnWebSocketCloseListener l) {
            mOnCloseListener = l;
        }

    }

    private interface OnWebSocketCloseListener {
        void onClose();
    }

    private enum WebSocketState {
        OPENING,
        OPEN,
        CLOSED
    }

    private class EventCollector {

        private final String mTransactionId;

        private final DConnectSDK mSDK;

        private final StringBuffer mBuffer = new StringBuffer();

        private final Object mLock = new Object();

        private final DConnectServiceInfo mServiceInfo;

        private final String mEventInterval;

        private final String mEventPayloadSize;

        private final String mEventCount;

        private final File mFile;

        private DConnectResponseMessage mStartResponse;

        private int mCount;

        private int mLastNumber = Integer.MAX_VALUE;

        private int mInvalidSequenceCount;

        private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

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
                mExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        Object num = event.get("num");
                        if (num == null || !(num instanceof Integer)) {
                            error("Number of event was not specified.");
                            return;
                        }
                        int number = (int) num;
                        String transactionId = event.getString("transactionId");

                        // 計測用データの検証
                        String dummyPayload = event.getString("payload");
                        int length = getByteSize(dummyPayload) / 1024;
                        if (transactionId == null || !transactionId.equals(mTransactionId)) {
                            error("Invalid transaction. Expected = " + mTransactionId + ", Actual = " + transactionId);
                            return;
                        }
                        String route = event.getString("route-0");
                        if (route == null || !route.equals(mServiceInfo.getConnectionTypeName())) {
                            error("Invalid route. Expected = " + mServiceInfo.getConnectionTypeName() + ", Actual = " + route);
                            return;
                        }
                        synchronized (mEventListener) {
                            if (mLastNumber <= number) {
                                error("Invalid number. Expected = " + (mLastNumber - 1) + ", Actual = " + number);
                                mInvalidSequenceCount++;
                            }
                            mLastNumber = number;
                        }

                        event.put("app-receive-time", System.currentTimeMillis());
                        add(event);

                        //debug("message: num = " + num + " + cnt = " + mCount + ", length = " + length + " KB, serviceId = " + event.getString("serviceId"));

                        if (((Integer) num) == 0) {
                            debug("message: finished.");
                            synchronized (mLock) {
                                mLock.notifyAll();
                            }
                        }
                    }
                });
            }
        };

        private int getByteSize(final String str) {
            try {
                return str.getBytes("US-ASCII").length;
            } catch (UnsupportedEncodingException e) {
                return -1;
            }
        }

        private String generateTransactionId(final int cnt) {
            final String chars ="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            Random rnd = new Random();
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < cnt; i++){
                int val = rnd.nextInt(chars.length());
                buf.append(chars.charAt(val));
            }
            return buf.toString();
        }

        EventCollector(final DConnectSDK sdk,
                       final DConnectServiceInfo serviceInfo,
                       final String interval,
                       final String payloadSize,
                       final String count) {
            mTransactionId = generateTransactionId(4);

            mSDK = sdk;
            mServiceInfo = serviceInfo;
            mEventInterval = interval;
            mEventPayloadSize = payloadSize;
            mEventCount = count;

            String dir = Environment.getExternalStorageDirectory().getPath();
            mFile = new File(dir, getReportFileName(mServiceInfo));
        }

        private String getReportFileName(final DConnectServiceInfo serviceInfo) {
            String name = Build.MODEL + "_" +
                    Build.VERSION.RELEASE + "_" +
                    serviceInfo.getConnectionTypeName() + "_" +
                    mEventInterval + "ms" + "_" +
                    mEventPayloadSize + "bytes" + "_" +
                    mEventCount +
                    ".csv";
            return name.toLowerCase();
        }

        private void add(final DConnectEventMessage event) {
            int max = Integer.parseInt(mUI.getEventCount());
            long rowNo = (max - (++mCount) + 1);
            String row = rowNo + "," + createCSVForEvent(event) + "\n";
            debug("row: " + row);
            mBuffer.append(row);
        }

        void waitEvent() throws InterruptedException {
            synchronized (mLock) {
                mLock.wait();
            }
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
                    .addParameter("interval", mEventInterval)
                    .addParameter("payload", mEventPayloadSize)
                    .addParameter("count", mEventCount)
                    .addParameter("transactionId", mTransactionId)
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
                    error("Failed to start event: serviceId = " + serviceId + ", errorMessage = " + mStartResponse.getErrorMessage());
                    return false;
                }
            } else {
                error("No response to start event: serviceId = " + serviceId);
                return false;
            }
        }

        void stopEvent() {
            final String serviceId = mServiceInfo.getId();

            Uri uri = createURIBuilder()
                    .setServiceId(serviceId)
                    .addParameter("transactionId", mTransactionId)
                    .build();
            mSDK.removeEventListener(uri);

            debug("Stopped event: serviceId = " + serviceId);
        }

        boolean isReported() {
            return mFile.exists();
        }

        void removeReport() throws IOException {
            if (!mFile.delete()) {
                throw new IOException("Failed to remove file: " + mFile.getAbsolutePath());
            }
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

    private abstract class Ui implements View.OnClickListener {
        private final Button mStartBtn;
        private final Button mStopBtn;
        private final EditText mParamInterval;
        private final EditText mParamPayloadSize;
        private final EditText mParamCount;
        private final CheckBox mDataSavedCheckBox;
        private final LogView mLogView;

        Ui() {
            mStartBtn = (Button) findViewById(R.id.button_start_measurement);
            mStartBtn.setOnClickListener(this);
            mStopBtn = (Button) findViewById(R.id.button_stop_measurement);
            mStopBtn.setOnClickListener(this);
            mParamInterval = (EditText) findViewById(R.id.param_interval);
            mParamPayloadSize = (EditText) findViewById(R.id.param_payload_size);
            mParamCount = (EditText) findViewById(R.id.param_count);
            mLogView = new LogView((ViewGroup) findViewById(R.id.log_view));
            mDataSavedCheckBox = (CheckBox) findViewById(R.id.param_data_saved);

            mParamInterval.setText("100"); // ms
            mParamPayloadSize.setText("1024");  // バイト
            mParamCount.setText("4320000"); // 回
            setEnabledStopBtn(false);
        }

        @Override
        public void onClick(final View view) {
            if (view == mStartBtn) {
                onStartBtn();
            } else if (view == mStopBtn) {
                onStopBtn();
            }
        }

        abstract void onStartBtn();
        abstract void onStopBtn();

        void setEnabledStartBtn(final boolean isEnabled) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStartBtn.setEnabled(isEnabled);
                }
            });
        }
        void setEnabledStopBtn(final boolean isEnabled) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStopBtn.setEnabled(isEnabled);
                }
            });
        }
        void setEnabledParameters(final boolean isEnabled) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mParamInterval.setEnabled(isEnabled);
                    mParamCount.setEnabled(isEnabled);
                    mParamPayloadSize.setEnabled(isEnabled);
                    mDataSavedCheckBox.setEnabled(isEnabled);
                }
            });
        }

        String getEventInterval() {
            return mParamInterval.getText().toString();
        }
        String getEventByteSize() {
            return mParamPayloadSize.getText().toString();
        }
        String getEventCount() {
            return mParamCount.getText().toString();
        }
        boolean isDataSaved() {
            return mDataSavedCheckBox.isChecked();
        }

        void debug(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLogView.debug(message);
                }
            });
        }

        void warn(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLogView.warn(message);
                }
            });
        }

        void error(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLogView.error(message);
                }
            });
        }
    }

    private class LogView {
        private final ViewGroup mRoot;
        private int mMaxCount = 10;

        LogView(final ViewGroup root) {
            if (root == null) {
                throw new NullPointerException("root is null.");
            }
            mRoot = root;
        }

        void debug(final String message) {
            addMessage(LogLevel.DEBUG, message);
        }

        void warn(final String message) {
            addMessage(LogLevel.WARN, message);
        }

        void error(final String message) {
            addMessage(LogLevel.ERROR, message);
        }

        void addMessage(final LogLevel level, final String message) {
            addTextView(level, message);
            if (isMaxCount()) {
                removeLog();
            }
        }

        void addTextView(final LogLevel level, final String message) {
            int id;
            switch (level) {
                case DEBUG:
                    id = R.layout.log_view_debug;
                    break;
                case WARN:
                    id = R.layout.log_view_warn;
                    break;
                case ERROR:
                    id = R.layout.log_view_error;
                    break;
                default:
                    return;
            }
            getLayoutInflater().inflate(id, mRoot);
            ((TextView) mRoot.getChildAt(mRoot.getChildCount() - 1)).setText(message);
        }

        boolean isMaxCount() {
            int size = mRoot.getChildCount();
            return size >= mMaxCount;
        }

        void removeLog() {
            mRoot.removeViewAt(0);
        }
    }

    private enum LogLevel {
        DEBUG,
        WARN,
        ERROR
    }
}
