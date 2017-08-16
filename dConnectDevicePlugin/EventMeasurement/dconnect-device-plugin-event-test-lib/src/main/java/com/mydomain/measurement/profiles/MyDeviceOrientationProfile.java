package com.mydomain.measurement.profiles;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventError;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.android.profile.api.GetApi;
import org.deviceconnect.android.profile.api.PutApi;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.message.DConnectMessage;

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
                Long interval = parseLong(request, "interval");

                if (interval == null) {
                    interval = 200L;
                }
                EventError error = EventManager.INSTANCE.addEvent(request);
                switch (error) {
                    case NONE:
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