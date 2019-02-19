package org.deviceconnect.android.deviceplugin.host.mutiwindow;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;
import org.deviceconnect.android.deviceplugin.host.activity.CanvasProfileActivity;
import org.deviceconnect.android.deviceplugin.host.activity.KeyEventProfileActivity;
import org.deviceconnect.android.deviceplugin.host.activity.TouchProfileActivity;
import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoPlayer;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.activity.MultiWindowCanvasProfileActivity;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.activity.MultiWindowKeyEventProfileActivity;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.activity.MultiWindowTouchProfileActivity;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.activity.MultiWindowVideoPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MutiWindowAccessibilityService extends AccessibilityService {
    /** Debug Tag. */
    private static final String TAG = MutiWindowAccessibilityService.class.getSimpleName();

    public static final List<Class<? extends Activity>> SUPPORTED_DEFAULT_ACTIVITY_CLASSES
            = Collections.unmodifiableList(new ArrayList<Class<? extends Activity>>(){
                {
                    add(CanvasProfileActivity.class);
                    add(VideoPlayer.class);
                    add(KeyEventProfileActivity.class);
                    add(TouchProfileActivity.class);
                }
            });

    public static final List<Class<? extends Activity>> SUPPORTED_MULTIWINDOW_ACTIVITY_CLASSES
            = Collections.unmodifiableList(new ArrayList<Class<? extends Activity>>(){
        {
            add(MultiWindowCanvasProfileActivity.class);
            add(MultiWindowVideoPlayer.class);
            add(MultiWindowKeyEventProfileActivity.class);
            add(MultiWindowTouchProfileActivity.class);
        }
    });

    enum MultiWindowState {
        Init,
        OpenDefaultActivity,
        OpenMultiWindowActivity;
    }
    private MultiWindowState mState = MultiWindowState.Init;
    private String mDefaultActivityClass = null;
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                HostDeviceApplication app = (HostDeviceApplication) getApplication();

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "<======");
                    Log.d(TAG, "className:" + event.getClassName());
                    Log.d(TAG, "Text:" + event.getText());
                    Log.d(TAG, "eventType:" + event.getEventType());
                    Log.d(TAG, "packagename:" + event.getPackageName());
                    Log.d(TAG, "======>");
                    WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                    // TODO 画面サイズで判断できないか？
                }
                String className = event.getClassName().toString();
                if (mState == MultiWindowState.Init && isTopActivityForFirst(className)) {
                    mState = MultiWindowState.OpenDefaultActivity;
                    mDefaultActivityClass = className;
                }

                if (mState == MultiWindowState.OpenDefaultActivity && isTopActvityForSecond(className)) {
                    Intent hostActivityData = app.getShowActivityAndData(mDefaultActivityClass);
                    Intent multiActivityData = app.getShowActivityAndData(className);
                    if (hostActivityData != null && multiActivityData != null) {
                        mState = MultiWindowState.OpenMultiWindowActivity;
                        HandlerThread handlerThreadFirst = new HandlerThread("OpenActivityFirst");
                        handlerThreadFirst.start();
                        HandlerThread handlerThreadSecond = new HandlerThread("OpenActivitySecond");
                        handlerThreadSecond.start();
                        new Handler(handlerThreadFirst.getLooper()).post(() -> {
                            hostActivityData.setClassName("org.deviceconnect.android.manager", mDefaultActivityClass);
                            hostActivityData.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            app.putShowActivityFlagFromAvailabilityService(mDefaultActivityClass, true);
                            startActivity(hostActivityData);
                            app.putShowActivityAndData(mDefaultActivityClass, hostActivityData);
                            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
                            new Handler(handlerThreadSecond.getLooper()).postDelayed(() -> {
                                multiActivityData.setClassName("org.deviceconnect.android.manager", className);
                                multiActivityData.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK);
                                app.putShowActivityFlagFromAvailabilityService(className, true);
                                startActivity(multiActivityData);
                                app.putShowActivityAndData(className, multiActivityData);
                                mState = MultiWindowState.Init;
                            }, 800);
                        });


                    }
                }
//        else if (mState == MultiWindowState.Init && isTopActvityForSecond(className)){
//            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
//        }
                break;
        }
    }

    @Override
    public void onInterrupt() {
        mState = MultiWindowState.Init;
    }
    @Override
    public void onCreate() {
        super.onCreate();
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
    }

    private boolean isTopActivityForFirst(final String className) {
        HostDeviceApplication app = (HostDeviceApplication) getApplication();
        for (int i = 0; i < SUPPORTED_DEFAULT_ACTIVITY_CLASSES.size(); i++) {
            Class<? extends Activity> activity = SUPPORTED_DEFAULT_ACTIVITY_CLASSES.get(i);
            Class<? extends Activity> multiWindow = SUPPORTED_MULTIWINDOW_ACTIVITY_CLASSES.get(i);
            if (className.equals(activity.getName())
                    && (!app.getShowActivityFlagFromAvailabilityService(activity.getName())
                    || !app.getShowActivityFlagFromAvailabilityService(multiWindow.getName()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isTopActvityForSecond(final String className) {
        for (Class<? extends Activity> multiWindow : SUPPORTED_MULTIWINDOW_ACTIVITY_CLASSES) {
            if (className.equals(multiWindow.getName())) {
                return true;
            }
        }
        return false;
    }

    private Class<? extends Activity> getDefaultActivityFromMultiWindowActivity(final String className) {
        for (int i = 0; i < SUPPORTED_MULTIWINDOW_ACTIVITY_CLASSES.size(); i++) {
            Class<? extends Activity> multiWindow = SUPPORTED_MULTIWINDOW_ACTIVITY_CLASSES.get(i);
            if (className.equals(multiWindow.getName())) {
                return SUPPORTED_DEFAULT_ACTIVITY_CLASSES.get(i);
            }
        }
        return null;
    }

    private Class<? extends Activity> getMultiWindowActivityFromDefaultActivity(final String className) {
        for (int i = 0; i < SUPPORTED_DEFAULT_ACTIVITY_CLASSES.size(); i++) {
            Class<? extends Activity> activity = SUPPORTED_DEFAULT_ACTIVITY_CLASSES.get(i);
            if (className.equals(activity.getName())) {
                return SUPPORTED_MULTIWINDOW_ACTIVITY_CLASSES.get(i);
            }
        }
        return null;
    }
}
