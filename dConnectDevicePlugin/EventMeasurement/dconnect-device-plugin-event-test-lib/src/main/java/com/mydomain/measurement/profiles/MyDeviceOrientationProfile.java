package com.mydomain.measurement.profiles;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventError;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.android.profile.api.PutApi;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.message.DConnectMessage;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MyDeviceOrientationProfile extends DConnectProfile {

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Object mLock = new Object();
    private Map<String, Future<Void>> mEventTaskMap = new HashMap<>();

    public MyDeviceOrientationProfile() {

        addApi(new DeleteApi() {
            @Override
            public String getAttribute() {
                return "onDeviceOrientation";
            }

            @Override
            public boolean onRequest(final Intent request, final Intent response) {
                EventError error = EventManager.INSTANCE.removeEvent(request);
                switch (error) {
                    case NONE:
                        setResult(response, DConnectMessage.RESULT_OK);

                        // イベントの定期的送信を停止.
                        String transactionId = request.getStringExtra("transactionId");
                        synchronized (mLock) {
                            Future<Void> task = mEventTaskMap.remove(transactionId);
                            if (task != null) {
                                task.cancel(true);
                            }
                        }
                        break;
                    case INVALID_PARAMETER:
                        MessageUtils.setInvalidRequestParameterError(response);
                        break;
                    case NOT_FOUND:
                        MessageUtils.setUnknownError(response, "Event is not registered.");
                        break;
                    default:
                        MessageUtils.setUnknownError(response);
                        break;
                }
                return true;
            }
        });

        // PUT /deviceOrientation/onDeviceOrientation
        addApi(new PutApi() {
            @Override
            public String getAttribute() {
                return "onDeviceOrientation";
            }

            @Override
            public boolean onRequest(final Intent request, final Intent response) {
                try {
                    EventError error = EventManager.INSTANCE.addEvent(request);
                    switch (error) {
                        case NONE:

                            // 計測用のダミーデータを生成
                            Integer payload = parseInteger(request, "payload");
                            if (payload == null) {
                                payload = 1 * 1024;
                            }
                            Log.d("event-test", "payload = " + payload + " bytes");
                            final String dummyPayload = generateDummyPayload(payload);

                            setResult(response, DConnectMessage.RESULT_OK);

                            // 以下、サンプルのイベントの定期的送信を開始.
                            synchronized (mLock) {
                                final String transactionId = request.getStringExtra("transactionId");
                                Future<Void> task = mEventTaskMap.get(transactionId);
                                if (task != null) {
                                    task.cancel(true);
                                }
                                task = mExecutor.submit(new Callable<Void>() {
                                    @Override
                                    public Void call() throws Exception {

                                        final int count = Integer.parseInt(request.getStringExtra("count"));

                                        try {
                                            for (int num = count - 1; num >= 0; num--) {
                                                Event event = EventManager.INSTANCE.getEvent(request);
                                                Intent message = EventManager.createEventMessage(event);
                                                Bundle root = message.getExtras();

                                                // トランザクションID を設定
                                                root.putString("transactionId", transactionId);

                                                // タイムスタンプ(Unix時刻)を設定
                                                root.putLong("plugin-created-time", System.currentTimeMillis());

                                                // 計測用のダミーデータを追加
                                                root.putString("payload", dummyPayload);

                                                // 連番
                                                root.putInt("num", num);

                                                Log.d("event-test", "#" + num + ": " + transactionId);

                                                message.putExtras(root);
                                                sendEvent(message, event.getAccessToken());

                                                if (Thread.currentThread().isInterrupted()) {
                                                    Log.d("Test", "Event sending was canceled.");
                                                    break;
                                                }

                                                // インターバル
                                                Long interval = parseLong(request, "interval");
                                                if (interval == null) {
                                                    interval = 0L;
                                                }
                                                Thread.sleep(interval);
                                            }
                                        } catch (InterruptedException e) {
                                            Log.d("Test", "Event sending was canceled.");
                                        }

                                        return null;
                                    }
                                });
                                mEventTaskMap.put(transactionId, task);
                            }
                            break;
                        case INVALID_PARAMETER:
                            MessageUtils.setInvalidRequestParameterError(response);
                            break;
                        default:
                            MessageUtils.setUnknownError(response);
                            break;
                    }
                    return true;
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        });

    }

    private String generateDummyPayload(final int size) {
        try {
            byte[] data = new byte[size];
            for (int i = 0; i < data.length; i++) {
                data[i] = 0x30; // == '0'
            }
            return new String(data, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getProfileName() {
        return "deviceOrientation";
    }

    private final Map<String, TimerTask> mTimerTasks = new ConcurrentHashMap<>();
    private final Timer mTimer = new Timer();

    private void stopTimer(final String taskId) {
        synchronized (mTimerTasks) {
            TimerTask timer = mTimerTasks.remove(taskId);
            if (timer != null) {
                timer.cancel();
            }
        }
    }
}