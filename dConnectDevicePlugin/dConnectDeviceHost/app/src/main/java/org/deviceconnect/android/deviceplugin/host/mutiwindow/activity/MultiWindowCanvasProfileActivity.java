package org.deviceconnect.android.deviceplugin.host.mutiwindow.activity;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebView;

import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;
import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.activity.CanvasProfileActivity;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasController;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;

public class MultiWindowCanvasProfileActivity extends CanvasProfileActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    protected  void onResume() {
        super.onResume();
        ((HostDeviceApplication) getApplication()).putActivityResumePauseFlag(getActivityName(), true);
    }
    @Override
    protected void onPause() {
        super.onPause();
        ((HostDeviceApplication) getApplication()).putActivityResumePauseFlag(getActivityName(), false);
    }
    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    protected CanvasController getCanvasController() {
        return new CanvasController(this, getCanvasWebView(), findViewById(R.id.canvasProfileView), findViewById(R.id.canvasProfileVideoView),
                this, mDrawImageObject, mSettings, CanvasDrawImageObject.ACTION_MULTI_WINDOW_DRAW_CANVAS,
                            CanvasDrawImageObject.ACTION_MULTI_WINDOW_DELETE_CANVAS);
    }

    protected String getActivityName() {
        return MultiWindowCanvasProfileActivity.class.getName();
    }

    protected void enableCanvasContinuousAccessFlag() {
        mSettings.setCanvasContinuousAccessForMultiWindow(true);
    }

    protected void disableCanvasContinuousAccessFlag() {
        mSettings.setCanvasContinuousAccessForMultiWindow(false);
    }

    protected WebView getCanvasWebView() {
        return findViewById(R.id.canvasProfileWebView);
    }
    @Override
    public boolean isCanvasContinuousAccess() {
        return mSettings.isCanvasContinuousAccessForMultiWindow();
    }

    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        if (!isInMultiWindowMode) {
            HostDeviceApplication app = (HostDeviceApplication) getApplication();
            app.putShowActivityFlagFromAvailabilityService(getActivityName(), false);
        }
    }
}
