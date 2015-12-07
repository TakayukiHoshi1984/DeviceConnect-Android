/*
 TestMeasurementProfile.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.test.profile.unique;

import org.deviceconnect.android.deviceplugin.test.DeviceTestService;
import org.deviceconnect.android.deviceplugin.test.profile.Util;
import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventError;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.message.intent.message.IntentDConnectMessage;

import android.content.Intent;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Measurement profile (for test).
 * @author NTT DOCOMO, INC.
 */
public class TestMeasurementProfile extends DConnectProfile {

    /**
     * Profile name: {@value} .
     */
    public static final String PROFILE_NAME = "measurement";

    /**
     * Attribute name: {@value} .
     */
    public static final String ATTRIBUTE_GETTEST = "gettest";

    /**
     * Attribute name: {@value} .
     */
    public static final String ATTRIBUTE_POSTTEST = "posttest";

    /**
     * Attribute name: {@value} .
     */
    public static final String ATTRIBUTE_EVENTTEST = "eventtest";

    /**
     * Parameter: {@value}.
     */
    public static final String PARAM_REQUEST_TIME = "request_time";

    /**
     * Parameter: {@value}.
     */
    public static final String PARAM_MGR_REQUEST_TIME = "mgr_request_time";

    /**
     * Parameter: {@value}.
     */
    public static final String PARAM_PLUGIN_RESPONSE_TIME = "plugin_response_time";

    /**
     * Parameter: {@value}.
     */
    public static final String PARAM_PLUGIN_EVENT_TIME = "plugin_event_time";

    /**
     * Parameter: {@value}.
     */
    public static final String PARAM_PATH = "path";

    /**
     * Parameter: {@value}.
     */
    public static final String PARAM_INTERVAL = "interval";

    /**
     * Event timer.
     */
    private Timer mEventTimer;

    /**
     * Event interval.
     */
    private long mIntervalTime = 500;

    @Override
    public String getProfileName() {
        return PROFILE_NAME;
    }

    @Override
    public boolean onRequest(final Intent request, final Intent response) {
        final String attribute = getAttribute(request);
        final String path = createPath(request);
        final String action = request.getAction();
        final String serviceId = getServiceID(request);
        final String interval = request.getStringExtra(PARAM_INTERVAL);
        if (ATTRIBUTE_EVENTTEST.equals(attribute)) {
            if (IntentDConnectMessage.ACTION_PUT.equals(action)) {
                if (interval == null) {
                    MessageUtils.setInvalidRequestParameterError(response, "Parameters interval is not enough.");
                    return true;
                }

                if (interval.equals("1")) {
                    mIntervalTime = 100;
                } else if (interval.equals("2")) {
                    mIntervalTime = 200;
                } else if (interval.equals("3")) {
                    mIntervalTime = 500;
                } else if (interval.equals("4")) {
                    mIntervalTime = 1000;
                } else {
                    MessageUtils.setInvalidRequestParameterError(response, "Parameters interval is invalid.");
                    return true;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        EventError error = EventManager.INSTANCE.addEvent(request);
                        if (error == EventError.NONE) {
                            setResult(response, DConnectMessage.RESULT_OK);
                            
                            // Event start.
                            startEvent(serviceId);
                        } else {
                            MessageUtils.setUnknownError(response, "event error: " + error.name());
                        }
                        ((DeviceTestService) getContext()).sendResponse(response);
                    }
                }).start();
                return false;
            } else if (IntentDConnectMessage.ACTION_DELETE.equals(action)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Event stop
                        stopEvent();
                        
                        EventManager.INSTANCE.removeEvent(request);
                        setResult(response, DConnectMessage.RESULT_OK);
                        ((DeviceTestService) getContext()).sendResponse(response);
                    }
                }).start();
                return false;
            } else {
                MessageUtils.setUnknownAttributeError(response);
                return true;
            }
        } else if (ATTRIBUTE_GETTEST.equals(attribute) ||
                ATTRIBUTE_POSTTEST.equals(attribute)) {
            setResult(response, DConnectMessage.RESULT_OK);
            setPath(response, path);
            response.putExtra(PARAM_REQUEST_TIME, request.getStringExtra(PARAM_REQUEST_TIME));
            response.putExtra(PARAM_MGR_REQUEST_TIME, request.getStringExtra(PARAM_MGR_REQUEST_TIME));
            response.putExtra(PARAM_PLUGIN_RESPONSE_TIME, System.currentTimeMillis());
            return true;
        } else {
            MessageUtils.setUnknownAttributeError(response);
            return true;
        }
    }

    /**
     * Start Event.
     * @param serviceId serviceId
     */
    private synchronized void startEvent(final String serviceId) {
        if (mEventTimer == null) {
            mEventTimer = new Timer(true);
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    List<Event> events = EventManager.INSTANCE.getEventList(
                          serviceId, getProfileName(), null, ATTRIBUTE_EVENTTEST);
                    for (Event event : events) {
                        Intent eventMsg = EventManager.createEventMessage(event);
                        eventMsg.putExtra(PARAM_PLUGIN_EVENT_TIME, System.currentTimeMillis());
                        Util.sendBroadcast(getContext(), eventMsg, 0);
                    }
                }
            };
            mEventTimer.schedule(task, 0, mIntervalTime);
        }
    }

    /**
     * Stop Event.
     */
    private synchronized void stopEvent() {
        if (mEventTimer != null) {
            mEventTimer.cancel();
            mEventTimer = null;
        }
    }

    /**
     * Set Path.
     * @param response Response
     * @param path Path
     */
    private void setPath(final Intent response, final String path) {
        response.putExtra(PARAM_PATH, path);
    }

    /**
     * Create Path.
     * @param request Request
     * @return Create Path
     */
    private String createPath(final Intent request) {
        String action = request.getAction();
        if (action == null) {
            return null;
        }
        String method;
        if (IntentDConnectMessage.ACTION_GET.equals(action)) {
            method = "GET";
        } else if (IntentDConnectMessage.ACTION_POST.equals(action)) {
            method = "POST";
        } else if (IntentDConnectMessage.ACTION_PUT.equals(action)) {
            method = "PUT";
        } else if (IntentDConnectMessage.ACTION_DELETE.equals(action)) {
            method = "DELETE";
        } else {
            return null;
        }
        String inter = getInterface(request);
        String attribute = getAttribute(request);
        StringBuilder builder = new StringBuilder();
        builder.append(method);
        builder.append(" /");
        builder.append(PROFILE_NAME);
        if (inter != null) {
            builder.append("/");
            builder.append(inter);
        }
        if (attribute != null) {
            builder.append("/");
            builder.append(attribute);
        }
        return builder.toString();
    }
}
