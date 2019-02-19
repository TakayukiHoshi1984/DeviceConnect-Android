/*
 TouchProfileActivity.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.activity;

import java.util.ArrayList;
import java.util.List;

import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;
import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.profile.HostTouchProfile;
import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.profile.TouchProfile;
import org.deviceconnect.message.DConnectMessage;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;

import static org.deviceconnect.android.deviceplugin.host.profile.HostTouchProfile.ACTION_TOUCH;
import static org.deviceconnect.android.deviceplugin.host.profile.HostTouchProfile.ATTRIBUTE_ON_TOUCH_CHANGE;

/**
 * Touch Profile Activity.
 * 
 * @author NTT DOCOMO, INC.
 */
public class TouchProfileActivity extends Activity {

    /** Application class instance. */
    protected HostDeviceApplication mApp;

    /** Gesture detector. */
    private GestureDetector mGestureDetector;
    /** Service Id. */
    private String mServiceId;
    /**
     * Implementation of BroadcastReceiver.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            if (getActionForFinishTouchActivity().equals(action)) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.touch_main);
        
        // Get Application class instance.
        mApp = (HostDeviceApplication) this.getApplication();

        // Get serviceId.
        Intent intent = getIntent();
        mServiceId = intent.getStringExtra(DConnectMessage.EXTRA_SERVICE_ID);
        // Create GestureDetector instance.
        mGestureDetector = new GestureDetector(this, mSimpleOnGestureListener);
        // onclicklistener register.
        Button button = findViewById(R.id.button_touch_close);
        button.setOnClickListener((v) -> {
            mApp.removeShowActivityAndData(getActivityName());
            mApp.putShowActivityFlagFromAvailabilityService(getActivityName(), false);
            finish();
        });

    }

    @Override
    protected void onDestroy() {
        ((HostDeviceApplication) getApplication()).putActivityResumePauseFlag(getActivityName(), false);
        if (!((HostDeviceApplication) getApplication()).getShowActivityFlagFromAvailabilityService(getActivityName())) {
            mApp.removeShowActivityAndData(getActivityName());
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(getActionForFinishTouchActivity());
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
        ((HostDeviceApplication) getApplication()).putActivityResumePauseFlag(getActivityName(), true);
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        mApp.removeShowActivityAndData(getActivityName());
        mApp.putShowActivityFlagFromAvailabilityService(getActivityName(), false);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        List<Event> events;
        String state = null;
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN: // 1st touch only.
        case MotionEvent.ACTION_POINTER_DOWN: // Others touch.
            state = HostDeviceApplication.STATE_START;
            // "ontouch" event processing.
            events = EventManager.INSTANCE.getEventList(mServiceId, TouchProfile.PROFILE_NAME, null,
                    TouchProfile.ATTRIBUTE_ON_TOUCH);
            if (events != null) {
                sendEventData(state, event, events);
            }

            // "ontouchstart" event processing.
            events = EventManager.INSTANCE.getEventList(mServiceId, TouchProfile.PROFILE_NAME, null,
                    TouchProfile.ATTRIBUTE_ON_TOUCH_START);
            break;
        case MotionEvent.ACTION_UP: // Last touch remove only.
        case MotionEvent.ACTION_POINTER_UP: // Others touch move.
            state = HostDeviceApplication.STATE_END;
            // "ontouchend" event processing.
            events = EventManager.INSTANCE.getEventList(mServiceId, TouchProfile.PROFILE_NAME, null,
                    TouchProfile.ATTRIBUTE_ON_TOUCH_END);
            break;
        case MotionEvent.ACTION_MOVE:
            state = HostDeviceApplication.STATE_MOVE;
            // "ontouchmove" event processing.
            events = EventManager.INSTANCE.getEventList(mServiceId, TouchProfile.PROFILE_NAME, null,
                    TouchProfile.ATTRIBUTE_ON_TOUCH_MOVE);
            break;
        case MotionEvent.ACTION_CANCEL:
            state = HostDeviceApplication.STATE_CANCEL;
            // "ontouchcancel" event processing.
            events = EventManager.INSTANCE.getEventList(mServiceId, TouchProfile.PROFILE_NAME, null,
                    TouchProfile.ATTRIBUTE_ON_TOUCH_CANCEL);
            break;
        default:
            return mGestureDetector.onTouchEvent(event);
        }

        if (events != null) {
            sendEventData(state, event, events);
        }
        return mGestureDetector.onTouchEvent(event);
    }

    /**
     * Gesture Listener.
     */
    private final SimpleOnGestureListener mSimpleOnGestureListener = new SimpleOnGestureListener() {

        @Override
        public boolean onDoubleTap(final MotionEvent event) {
            List<Event> events = EventManager.INSTANCE.getEventList(mServiceId, TouchProfile.PROFILE_NAME, null,
                    TouchProfile.ATTRIBUTE_ON_DOUBLE_TAP);

            sendEventData(HostDeviceApplication.STATE_DOUBLE_TAP, event, events);
            return super.onDoubleTap(event);
        }
    };

    /**
     * Send event data.
     *
     * @param state MotionEvent state.
     * @param event MotionEvent.
     * @param events Event request list.
     */
    private void sendEventData(final String state, final MotionEvent event, final List<Event> events) {
        List<Event> touchEvents = EventManager.INSTANCE.getEventList(mServiceId, TouchProfile.PROFILE_NAME, null,
                ATTRIBUTE_ON_TOUCH_CHANGE);
        Bundle touchdata = new Bundle();
        List<Bundle> touchlist = new ArrayList<Bundle>();
        Bundle touches = new Bundle();
        for (int n = 0; n < event.getPointerCount(); n++) {
            int pointerId = event.getPointerId(n);
            touchdata.putInt(TouchProfile.PARAM_ID, pointerId);
            touchdata.putFloat(TouchProfile.PARAM_X, event.getX(n));
            touchdata.putFloat(TouchProfile.PARAM_Y, event.getY(n));
            touchlist.add((Bundle) touchdata.clone());
        }
        touches.putParcelableArray(TouchProfile.PARAM_TOUCHES, touchlist.toArray(new Bundle[touchlist.size()]));
        for (int i = 0; i < events.size(); i++) {
            Event eventdata = events.get(i);
            String attr = eventdata.getAttribute();
            Intent intent = EventManager.createEventMessage(eventdata);
            intent.putExtra(TouchProfile.PARAM_TOUCH, touches);
            intent.setAction(getActionForSendEvent());
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            mApp.setTouchCache(attr, touches);
        }
        for (int i = 0; i < touchEvents.size(); i++) {
            Event eventdata = touchEvents.get(i);
            String attr = eventdata.getAttribute();
            touches.putString("state", state);
            Intent intent = EventManager.createEventMessage(eventdata);
            intent.putExtra(TouchProfile.PARAM_TOUCH, touches);
            intent.setAction(getActionForSendEvent());
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            mApp.setTouchCache(attr, touches);
        }
    }


    protected String getActivityName() {
        return TouchProfileActivity.class.getName();
    }

    protected String getActionForFinishTouchActivity() {
        return HostTouchProfile.ACTION_FINISH_TOUCH_ACTIVITY;
    }
    protected String getActionForSendEvent() {
        return ACTION_TOUCH;
    }
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        if (!isInMultiWindowMode) {
            mApp.putShowActivityFlagFromAvailabilityService(getActivityName(), false);
        }
    }

}
