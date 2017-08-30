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
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class MyDeviceOrientationProfile extends DConnectProfile {

    public MyDeviceOrientationProfile() {

        addApi(new DeleteApi() {
            @Override
            public String getAttribute() {
                return "onDeviceOrientation";
            }

            @Override
            public boolean onRequest(final Intent request, final Intent response) {
                String serviceId = (String) request.getExtras().get("serviceId");

                EventError error = EventManager.INSTANCE.removeEvent(request);
                switch (error) {
                    case NONE:
                        setResult(response, DConnectMessage.RESULT_OK);

                        // 以下、サンプルのイベントの定期的送信を停止.
                        String taskId = serviceId;
                        stopTimer(taskId);
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
                String serviceId = (String) request.getExtras().get("serviceId");

                // インターバル設定
                Long interval = parseLong(request, "interval");
                if (interval == null) {
                    interval = 0L;
                }

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
                        String taskId = serviceId;
                        TimerTask task = new TimerTask() {
                            @Override
                            public void run() {
                                Event event = EventManager.INSTANCE.getEvent(request);
                                Intent message = EventManager.createEventMessage(event);
                                Bundle root = message.getExtras();

                                // タイムスタンプ(Unix時刻)を設定
                                root.putLong("plugin-created-time", System.currentTimeMillis());

                                // 計測用のダミーデータを追加
                                root.putString("payload", dummyPayload);

                                message.putExtras(root);
                                sendEvent(message, event.getAccessToken());
                            }
                        };
                        startTimer(taskId, task, interval);
                        break;
                    case INVALID_PARAMETER:
                        MessageUtils.setInvalidRequestParameterError(response);
                        break;
                    default:
                        MessageUtils.setUnknownError(response);
                        break;
                }
                return true;
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

    private void startTimer(final String taskId, final TimerTask task, final long interval) {
        synchronized (mTimerTasks) {
            stopTimer(taskId);
            mTimerTasks.put(taskId, task);
            mTimer.scheduleAtFixedRate(task, 0, interval);
        }
    }

    private void stopTimer(final String taskId) {
        synchronized (mTimerTasks) {
            TimerTask timer = mTimerTasks.remove(taskId);
            if (timer != null) {
                timer.cancel();
            }
        }
    }
}