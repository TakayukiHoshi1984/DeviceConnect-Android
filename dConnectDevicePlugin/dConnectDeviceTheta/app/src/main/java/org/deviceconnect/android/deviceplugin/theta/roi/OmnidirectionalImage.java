/*
 OmnidirectionalImage.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.roi;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import com.theta360.lib.rexif.ExifReadException;
import com.theta360.lib.rexif.ExifReader;
import com.theta360.lib.rexif.entity.OmniInfo;

import org.deviceconnect.message.DConnectMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Representation of an omnidirectional image.
 *
 * @author NTT DOCOMO, INC.
 */
public class OmnidirectionalImage {

    private final String mUri;
    private final Bitmap mBitmap;

    private double mYaw;
    private double mRoll;
    private double mPitch;

    /**
     * Constructor.
     *
     * @param uri URI of an omnidirectional image.
     * @param requestOrigin Origin (RFC6454) as an HTTP client which gets an omnidirectional image.
     * @param width width to be resized
     * @param height height to be resized
     * @throws IOException if URL connection is failed.
     * @throws ExifReadException
     */
    public OmnidirectionalImage(final String uri, final String requestOrigin,
                                final Integer width, final Integer height) throws IOException {
        mUri = uri;

        BitmapFactory.Options options = readBitmapOptions(uri, requestOrigin);
        int resizeWidth = width != null ? width : options.outWidth;
        int resizeHeight = height != null ? height : options.outHeight;
        Log.d("AAA", "Omni. image: resizeWidth = " + resizeWidth + ", resizeHeight = " + resizeHeight);

        mBitmap = decodeBitmap(uri, requestOrigin, options, resizeWidth, resizeHeight);

        readExif(uri);
    }

    private InputStream openInputStream(final String uri, final String requestOrigin) throws IOException {
        URLConnection conn = new URL(uri).openConnection();
        conn.setRequestProperty(DConnectMessage.HEADER_GOTAPI_ORIGIN, requestOrigin);
        return conn.getInputStream();
    }

    private BitmapFactory.Options readBitmapOptions(final String uri, final String requestOrigin) throws IOException {
        InputStream is = null;
        try {
            is = openInputStream(uri, requestOrigin);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            return options;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private Bitmap decodeBitmap(final String uri, final String requestOrigin, final BitmapFactory.Options option, final int width, final int height) throws IOException {
        InputStream is = null;
        try {
            is = openInputStream(uri, requestOrigin);
            Bitmap bitmap = createBitmap(is, option, width, height);

            Log.d("AAA", "decodeBitmap: bitmap = " + bitmap);

            bitmap = resize(bitmap, width, height);
            return bitmap;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private void readExif(final String uri) throws IOException {
        File file = File.createTempFile("temp_omni_image", null);
        OutputStream os = new FileOutputStream(file);
        InputStream is = null;

        try {
            URLConnection conn = new URL(uri).openConnection();
            is = conn.getInputStream();

            int c;
            byte[] buf = new byte[1024];
            while ((c = is.read(buf)) > 0) {
                os.write(buf, 0, c);
            }
            os.flush();

            ExifReader exif = new ExifReader(file);
            OmniInfo omniInfo = exif.getOmniInfo();
            if (omniInfo != null) {
                mYaw = omniInfo.getOrientationAngle() != null ? omniInfo.getOrientationAngle() : 0.0;
                mRoll = omniInfo.getHorizontalAngle() != null ? omniInfo.getHorizontalAngle() : 0.0;
                mPitch = omniInfo.getElevationAngle() != null ? omniInfo.getElevationAngle() : 0.0;
            }
        } catch (ExifReadException e) {
            // Nothing to do.
        } finally {
            if (is != null) {
                is.close();
            }
            os.close();
            file.delete();
        }
    }

    public double getYaw() {
        return mYaw;
    }

    public double getRoll() {
        return mRoll;
    }

    public double getPitch() {
        return mPitch;
    }

    /**
     * Gets the URI of this omnidirectional image.
     * @return the URI of this omnidirectional image
     */
    public String getUri() {
        return mUri;
    }

    /**
     * Gets the bitmap data of this omnidirectional image.
     * @return the bitmap data of this omnidirectional image.
     */
    public Bitmap getData() {
        return mBitmap;
    }

    public static Bitmap createBitmap(InputStream is, BitmapFactory.Options option, int width, int height) {

        float scaleWidth = ((float) width) / option.outWidth;
        float scaleHeight = ((float) height) / option.outHeight;

        int newSize;
        int oldSize;
        if (scaleWidth > scaleHeight) {
            newSize = width;
            oldSize = option.outWidth;
        } else {
            newSize = height;
            oldSize = option.outHeight;
        }

        // option.inSampleSizeに設定する値を求める
        // option.inSampleSizeは2の乗数のみ設定可能
        int sampleSize = 1;
        int tmpSize = oldSize;
        while (tmpSize > newSize) {
            sampleSize = sampleSize * 2;
            tmpSize = oldSize / sampleSize;
        }
        if (sampleSize != 1) {
            sampleSize = sampleSize / 2;
        }

        option.inJustDecodeBounds = false;
        option.inSampleSize = sampleSize;

        return BitmapFactory.decodeStream(is, null, option);
    }

    public static Bitmap resize(Bitmap bitmap, int newWidth, int newHeight) {

        if (bitmap == null) {
            return null;
        }

        int oldWidth = bitmap.getWidth();
        int oldHeight = bitmap.getHeight();

        if (oldWidth < newWidth && oldHeight < newHeight) {
            // 縦も横も指定サイズより小さい場合は何もしない

            Log.d("AAA", "width = " + oldWidth + ", height = " + oldHeight + ", no scale");

            return bitmap;
        }

        float scaleWidth = ((float) newWidth) / oldWidth;
        float scaleHeight = ((float) newHeight) / oldHeight;
        float scaleFactor = Math.min(scaleWidth, scaleHeight);

        Matrix scale = new Matrix();
        scale.postScale(scaleFactor, scaleFactor);

        Bitmap resizeBitmap = Bitmap.createBitmap(bitmap, 0, 0, oldWidth, oldHeight, scale, false);
        //bitmap.recycle();

        Log.d("AAA", "width = " + oldWidth + ", height = " + oldHeight + ", scale = " + scale);

        return resizeBitmap;

    }

}
