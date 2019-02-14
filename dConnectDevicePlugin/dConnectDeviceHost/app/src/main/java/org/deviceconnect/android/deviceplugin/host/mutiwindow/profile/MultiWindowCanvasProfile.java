package org.deviceconnect.android.deviceplugin.host.mutiwindow.profile;

import android.app.Activity;
import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;
import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.activity.MultiWindowCanvasProfileActivity;
import org.deviceconnect.android.deviceplugin.host.profile.HostCanvasProfile;

public class MultiWindowCanvasProfile extends HostCanvasProfile {
    /** Canvasプロファイルのファイル名プレフィックス。 */
    private static final String CANVAS_PREFIX = "multiwindow_canvas";

    /**
     * コンストラクタ.
     *
     * @param settings Canvasの設定
     */
    public MultiWindowCanvasProfile(HostCanvasSettings settings) {
        super(settings);
    }

    protected boolean isCanvasMultipleShowFlag() {
        return mSettings.isCanvasContinuousAccessForMultiWindow()
                && ((HostDeviceApplication) ((HostDeviceService) getContext()).getApplication())
                .getShowActivityAndData(getTopOfActivity().getName()) != null;
    }

    protected String getTempFileName() {
        return CANVAS_PREFIX;
    }

    protected boolean isActivityNeverShow() {
        return mSettings.isCanvasActivityNeverShowFlag();
    }

    protected Class<? extends Activity> getTopOfActivity() {
        return MultiWindowCanvasProfileActivity.class;
    }

    protected String getDrawCanvasActionName() {
        return CanvasDrawImageObject.ACTION_MULTI_WINDOW_DRAW_CANVAS;
    }

    protected String getDeleteCanvasActionName() {
        return CanvasDrawImageObject.ACTION_MULTI_WINDOW_DELETE_CANVAS;
    }
}
