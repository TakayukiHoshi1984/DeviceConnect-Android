package org.deviceconnect.android.deviceplugin.theta.utils;

import android.graphics.Bitmap;

public final class JpegLoader {

    public native void load(String path, Bitmap bitmap);

    static {
        System.loadLibrary("jpegloader");
    }

}
