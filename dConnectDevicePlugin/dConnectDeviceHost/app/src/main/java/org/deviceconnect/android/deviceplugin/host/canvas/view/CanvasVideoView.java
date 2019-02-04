package org.deviceconnect.android.deviceplugin.host.canvas.view;

import android.app.Activity;
import android.view.View;
import android.widget.VideoView;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;

public class CanvasVideoView {

    /**
     * Canvas view object.
     */
    private VideoView mCanvasVideoView;
    private CanvasDrawImageObject mDrawImageObject;

    public CanvasVideoView(final Activity activity,
                         final CanvasDrawImageObject drawObject) {
        mCanvasVideoView = activity.findViewById(R.id.canvasProfileVideoView);
        mDrawImageObject = drawObject;
    }
    public void gone() {
        mCanvasVideoView.setVisibility(View.GONE);
    }

    public void visibility() {
        mCanvasVideoView.setVisibility(View.VISIBLE);
    }
    public void initVideoView() {
        mCanvasVideoView.setVideoPath(mDrawImageObject.getData());
        mCanvasVideoView.requestFocus();
        mCanvasVideoView.start();
        // 再生が完了したらループ再生を行う
        mCanvasVideoView.setOnCompletionListener((mp) -> {
            mp.seekTo(0);
            mp.start();
        });
        mCanvasVideoView.setOnErrorListener((mp, what, extra) -> {
            return false;
        });

    }

}
