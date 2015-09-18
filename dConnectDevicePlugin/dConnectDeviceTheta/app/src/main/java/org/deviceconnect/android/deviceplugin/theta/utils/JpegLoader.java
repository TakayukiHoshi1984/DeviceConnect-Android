package org.deviceconnect.android.deviceplugin.theta.utils;

import android.graphics.Bitmap;

import java.nio.Buffer;

public final class JpegLoader {

    public native void load(String path, Bitmap bitmap);

    public native void loadToBuffer(String path, Buffer buffer);

    static {
        System.loadLibrary("jpegloader");
    }

}
