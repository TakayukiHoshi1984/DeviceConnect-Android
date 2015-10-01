/*
 PixelBuffer.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.opengl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.theta.BuildConfig;

import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import static javax.microedition.khronos.egl.EGL10.EGL_ALPHA_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY;
import static javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_HEIGHT;
import static javax.microedition.khronos.egl.EGL10.EGL_NONE;
import static javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT;
import static javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_RENDERABLE_TYPE;
import static javax.microedition.khronos.egl.EGL10.EGL_STENCIL_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_WIDTH;
import static javax.microedition.khronos.opengles.GL10.GL_UNPACK_ALIGNMENT;

/**
 * Pixel Buffer.
 *
 * @author NTT DOCOMO, INC.
 */
public class PixelBuffer {

    private static final String TAG = "PixelBuffer";

    private final int mWidth;
    private final int mHeight;
    private final EGL10 mEGL;
    private final EGLDisplay mEGLDisplay;
    private final EGLConfig mEGLConfig;
    private final EGLContext mEGLContext;
    private final EGLSurface mEGLSurface;
    private final GL10 mGL;
    private final String mThreadOwner;

    private boolean mIsDestroyed;
    private SphereRenderer mRenderer;
    private ShortBuffer mIb;
    private final Bitmap mBitmap;

    public PixelBuffer(final int width, final int height, final boolean isStereo) {
        mWidth = isStereo ? width * 2 : width;
        mHeight = height;

        Log.d("AAA", "w x h = " + width + " x " + height);

        mIb = ShortBuffer.allocate(mWidth * mHeight);

        mBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.RGB_565);

        mEGL = (EGL10) EGLContext.getEGL();
        mEGLDisplay = mEGL.eglGetDisplay(EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        mEGL.eglInitialize(mEGLDisplay, version);
        mEGLConfig = chooseConfigs();
        if (mEGLConfig == null) {
            throw new RuntimeException("RGB565 is not supported");
        }
        mEGLContext = mEGL.eglCreateContext(mEGLDisplay, mEGLConfig, EGL_NO_CONTEXT, new int[] {
                0x3098, // EGL_CONTEXT_CLIENT_VERSION
                2,      // OpenGL ES 2.0
                EGL10.EGL_NONE });

        mEGLSurface = mEGL.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig, new int[] {
                EGL_WIDTH, mWidth,
                EGL_HEIGHT, mHeight,
                EGL_NONE
            });

        mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
        mGL = (GL10) mEGLContext.getGL();

        mThreadOwner = Thread.currentThread().getName();
    }

    private void checkEGLError(EGL10 egl) {
        int error = egl.eglGetError();
        if (BuildConfig.DEBUG) {
            if (error == EGL10.EGL_SUCCESS) {
                Log.i(TAG, "EGL SUCCESS.");
            } else {
                Log.e(TAG, "EGL Error: " + error);
            }
        }
    }

    public synchronized void destroy() {
        mIsDestroyed = true;
        synchronized (mRenderer) {
            mRenderer.destroy();
        }
        mEGL.eglDestroySurface(mEGLDisplay, mEGLSurface);
        mEGL.eglDestroyContext(mEGLDisplay, mEGLContext);
        mEGL.eglTerminate(mEGLDisplay);
        mBitmap.recycle();
    }

    public void setRenderer(final SphereRenderer renderer) {
        mRenderer = renderer;

        if (!Thread.currentThread().getName().equals(mThreadOwner)) {
            Log.e(TAG, "setRenderer: This thread does not own the OpenGL context.");
            return;
        }

        synchronized (mRenderer) {
            if (!mRenderer.isDestroyed()) {
                mRenderer.onSurfaceCreated();
            }
        }
    }

    public void render() {
        if (mRenderer == null) {
            Log.e(TAG, "PixelBuffer.render: Renderer was not set.");
            return;
        }

        if (!Thread.currentThread().getName().equals(mThreadOwner)) {
            Log.e(TAG, "PixelBuffer.render: This thread does not own the OpenGL context.");
            return;
        }

        synchronized (mRenderer) {
            if (!mRenderer.isDestroyed()) {
                mRenderer.onDrawFrame(mGL);
            }
        }
    }

    private EGLConfig chooseConfigs() {
        int[] attribList = new int[] {
            EGL_DEPTH_SIZE, 0,
            EGL_STENCIL_SIZE, 0,
            EGL_RED_SIZE, 5,
            EGL_GREEN_SIZE, 6,
            EGL_BLUE_SIZE, 5,
            EGL_ALPHA_SIZE, 0,
            EGL_RENDERABLE_TYPE, 4, // OpenGL ES 2.0
            EGL_NONE
        };

        int[] numConfig = new int[1];
        mEGL.eglChooseConfig(mEGLDisplay, attribList, null, 0, numConfig);
        int configSize = numConfig[0];
        EGLConfig[] eGLConfigs = new EGLConfig[configSize];
        mEGL.eglChooseConfig(mEGLDisplay, attribList, eGLConfigs, configSize, numConfig);

        listConfig(eGLConfigs);

        for (EGLConfig config : eGLConfigs) {
            if (attribList[1] == getConfigAttrib(config, EGL_DEPTH_SIZE) &&
                attribList[3] == getConfigAttrib(config, EGL_STENCIL_SIZE) &&
                attribList[5] == getConfigAttrib(config, EGL_RED_SIZE) &&
                attribList[7] == getConfigAttrib(config, EGL_GREEN_SIZE) &&
                attribList[9] == getConfigAttrib(config, EGL_BLUE_SIZE) &&
                attribList[11] == getConfigAttrib(config, EGL_ALPHA_SIZE)) {
                return config;
            }
        }

        return null;
    }

    private void listConfig(EGLConfig[] eGLConfigs) {
        Log.i(TAG, "Config List {");

        for (EGLConfig config : eGLConfigs) {
            int d, s, r, g, b, a, R;

            // Expand on this logic to dump other attributes
            d = getConfigAttrib(config, EGL_DEPTH_SIZE);
            s = getConfigAttrib(config, EGL_STENCIL_SIZE);
            r = getConfigAttrib(config, EGL_RED_SIZE);
            g = getConfigAttrib(config, EGL_GREEN_SIZE);
            b = getConfigAttrib(config, EGL_BLUE_SIZE);
            a = getConfigAttrib(config, EGL_ALPHA_SIZE);
            R = getConfigAttrib(config, EGL_RENDERABLE_TYPE);
            Log.i(TAG, "    <d,s,r,g,b,a,R> = <" + d + "," + s + "," + r + "," + g + "," + b + ","
                + a + "," + R + ">");
        }

        Log.i(TAG, "}");
    }

    private int getConfigAttrib(EGLConfig config, int attribute) {
        int[] value = new int[1];
        return mEGL.eglGetConfigAttrib(mEGLDisplay, config, attribute, value) ? value[0] : 0;
    }

    public synchronized Bitmap convertToBitmap() {
        if (mIsDestroyed) {
            return null;
        }

//        long start, end;

//        start = System.currentTimeMillis();
        mGL.glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
        mGL.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, mIb);
        //Log.d("AAA", "glReadPixels: time = " + (System.currentTimeMillis() - start) + " msec");

//        start = System.currentTimeMillis();
        mBitmap.copyPixelsFromBuffer(mIb);
        //Log.d("AAA", "copyPixelsFromBuffer: = " + (System.currentTimeMillis() - start) + " msec");

        mIb.clear();
        return mBitmap;
    }
}