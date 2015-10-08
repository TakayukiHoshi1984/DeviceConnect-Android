/*
 SphereRenderer.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.opengl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.theta.opengl.model.UVSphere;
import org.deviceconnect.android.deviceplugin.theta.utils.Quaternion;
import org.deviceconnect.android.deviceplugin.theta.utils.Vector3D;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * Renderer class for photo display.
 *
 * @author NTT DOCOMO, INC.
 */
public class SphereRenderer implements GLSurfaceView.Renderer {

    /**
     * Distance of left and right eye: {@value} cm.
     */
    private static final float DISTANCE_EYES = 10.0f / 100.0f;
    /**
     * Radius of sphere for photo.
     */
    private static final float DEFAULT_TEXTURE_SHELL_RADIUS = 1.0f;
    /**
     * Number of sphere polygon partitions for photo, which must be an even number.
     */
    private static final int SHELL_DIVIDES = 40;

    private static final String TAG = "GL";

    private final String VSHADER_SRC =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aUV;\n" +
            "uniform mat4 uProjection;\n" +
            "uniform mat4 uView;\n" +
            "uniform mat4 uModel;\n" +
            "varying vec2 vUV;\n" +
            "void main() {\n" +
            "  gl_Position = uProjection * uView * uModel * aPosition;\n" +
            "  vUV = aUV;\n" +
            "}\n";

    private final String FSHADER_SRC =
            "precision mediump float;\n" +
            "varying vec2 vUV;\n" +
            "uniform sampler2D uTex;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(uTex, vUV);\n" +
            "}\n";

    public static final float Z_NEAR = 0.1f;
    public static final float Z_FAR = 1000.0f;

    private int mScreenWidth;
    private int mScreenHeight;
    private boolean mIsStereo;

    private final Camera mCamera = new Camera();

    private UVSphere mShell;

    private Bitmap mTexture;
    private boolean mTextureUpdate = false;
    private int[] mTextures = new int[1];

    private int mVShader;
    private int mFShader;
    private int mProgram;

    private int mPositionHandle;
    private int mProjectionMatrixHandle;
    private int mViewMatrixHandle;
    private int mUVHandle;
    private int mTexHandle;
    private int mModelMatrixHandle;

    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mModelMatrix = new float[16];

    private final Object mLockTexture = new Object();
    private boolean mIsDestroyed;

    /**
     * Constructor
     */
    public SphereRenderer() {
        mShell = new UVSphere(DEFAULT_TEXTURE_SHELL_RADIUS, SHELL_DIVIDES);
    }

    /**
     * onDrawFrame Method
     *
     * @param gl GLObject (not used)
     */
    public void onDrawFrame(final GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

//        if (BuildConfig.DEBUG) {
//            Log.d("SphereRenderer", "Screen : width = " + mScreenWidth + ", height = " + mScreenHeight);
//        }
        if (mIsStereo) {
            SphereRenderer.Camera[] cameras = mCamera.getCamerasForStereo(DISTANCE_EYES);
            GLES20.glViewport(0, 0, mScreenWidth, mScreenHeight);
            draw(cameras[0]);
            GLES20.glViewport(mScreenWidth, 0, mScreenWidth, mScreenHeight);
            draw(cameras[1]);
        } else {
            GLES20.glViewport(0, 0, mScreenWidth, mScreenHeight);
            draw(mCamera);
        }
    }

    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    public void destroy() {
        mIsDestroyed = true;

        synchronized (mLockTexture) {
            GLES20.glDeleteTextures(1, mTextures, 0);
        }
        GLES20.glDeleteProgram(mProgram);
        GLES20.glDeleteShader(mVShader);
        GLES20.glDeleteShader(mFShader);
    }

    private void draw(final Camera camera) {
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.setIdentityM(mViewMatrix, 0);
        Matrix.setIdentityM(mProjectionMatrix, 0);
        checkGlError(TAG, "setIdentityM");

        synchronized (mLockTexture) {
            if (mTextureUpdate && null != mTexture) {
                GLES20.glDeleteTextures(1, mTextures, 0);
                loadTexture(mTexture);
                mTextureUpdate = false;
            }
        }

        float x = (float) camera.getPositionX();
        float y = (float) camera.getPositionY();
        float z = (float) camera.getPositionZ();
        float frontX = (float) camera.getFrontDirectionX();
        float frontY = (float) camera.getFrontDirectionY();
        float frontZ = (float) camera.getFrontDirectionZ();
        float upX = (float) camera.getUpperDirectionX();
        float upY = (float) camera.getUpperDirectionY();
        float upZ = (float) camera.getUpperDirectionZ();
        Matrix.setLookAtM(mViewMatrix, 0, x, y, z, frontX, frontY, frontZ, upX, upY, upZ);
        checkGlError(TAG, "setLookAtM");

        Matrix.perspectiveM(mProjectionMatrix, 0, (float) camera.mFovDegree, getScreenAspect(), Z_NEAR, Z_FAR);
        checkGlError(TAG, "perspectiveM");

        GLES20.glUniformMatrix4fv(mModelMatrixHandle, 1, false, mModelMatrix, 0);
        GLES20.glUniformMatrix4fv(mProjectionMatrixHandle, 1, false, mProjectionMatrix, 0);
        GLES20.glUniformMatrix4fv(mViewMatrixHandle, 1, false, mViewMatrix, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
        GLES20.glUniform1i(mTexHandle, 0);

        mShell.draw(mPositionHandle, mUVHandle);
    }

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        setScreenWidth(width);
        setScreenHeight(height);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        mVShader = loadShader(GLES20.GL_VERTEX_SHADER, VSHADER_SRC);
        mFShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FSHADER_SRC);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, mVShader);
        GLES20.glAttachShader(mProgram, mFShader);
        GLES20.glLinkProgram(mProgram);
        GLES20.glUseProgram(mProgram);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        mUVHandle = GLES20.glGetAttribLocation(mProgram, "aUV");
        mProjectionMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uProjection");
        mViewMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uView");
        mTexHandle = GLES20.glGetUniformLocation(mProgram, "uTex");
        mModelMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uModel");

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    }

    private float getScreenAspect() {
        return (float) mScreenWidth / (float) (mScreenHeight == 0 ? 1 : mScreenHeight);
    }

    /**
     * Sets the texture for the sphere
     *
     * @param texture Photo object for texture
     */
    public void setTexture(Bitmap texture) {
        synchronized (mLockTexture) {
            mTexture = texture;
            mTextureUpdate = true;
        }
    }

    public void clearTexture() {
        synchronized (mLockTexture) {
            mTexture = null;
            mTextureUpdate = false;
        }
    }

    /**
     * Acquires the set texture
     *
     * @return Photo object for texture
     */
    public Bitmap getTexture() {
        return mTexture;
    }


    /**
     * GL error judgment method for debugging
     * @param TAG TAG output character string
     * @param glOperation Message output character string
     */
    public static void checkGlError(String TAG, String glOperation) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, glOperation + ": glError " + error);
            throw new RuntimeException(glOperation + ": glError " + error);
        }
    }

    /**
     * Texture setting method
     *
     * @param texture Setting texture
     */
    public void loadTexture(final Bitmap texture) {
        GLES20.glGenTextures(1, mTextures, 0);
        checkGlError(TAG, "glGenTextures");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        checkGlError(TAG, "glActiveTexture");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
        checkGlError(TAG, "glBindTexture");

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        checkGlError(TAG, "glTexParameterf: GL_TEXTURE_MIN_FILTER");
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        checkGlError(TAG, "glTexParameterf: GL_TEXTURE_MAG_FILTER");

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, texture, 0);
        checkGlError(TAG, "texImage2D");
    }

    private int loadShader(final int type, final String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    public void setScreenWidth(final int screenWidth) {
        mScreenWidth = screenWidth;
    }

    public void setScreenHeight(final int screenHeight) {
        mScreenHeight = screenHeight;
    }

    public void setSphereRadius(final float radius) {
        if (radius != mShell.getRadius()) {
            mShell = new UVSphere(radius, SHELL_DIVIDES);
        }
    }

    public void setStereoMode(final boolean isStereo) {
        mIsStereo = isStereo;
    }

    public Camera getCamera() {
        return mCamera;
    }

    public void resetCamera() {
        mCamera.reset();
    }

    public static class Camera {
        private double mFovDegree;
        private final double[] mPosition;
        private final double[] mFrontDirection;
        private final double[] mUpperDirection;
        private final double[] mRightDirection;

        public Camera(final double fovDegree, final double[] position,
                      final double[] frontDirection, final double[] upperDirection,
                      final double[] rightDirection) {
            mFovDegree = fovDegree;
            mPosition = position;
            mFrontDirection = frontDirection;
            mUpperDirection = upperDirection;
            mRightDirection = rightDirection;
        }

        public Camera(final Camera camera) {
            this(camera.mFovDegree,
                copyVector(camera.mPosition),
                copyVector(camera.mFrontDirection),
                copyVector(camera.mUpperDirection),
                copyVector(camera.mRightDirection));
        }

        private static double[] copyVector(final double[] vector) {
            double[] result = new double[vector.length];
            System.arraycopy(vector, 0, result, 0, result.length);
            return result;
        }

        public Camera() {
            this(90.0f,
                new double[]{0, 0, 0, 0},
                new double[]{0, 1, 0, 0},
                new double[]{0, 0, 1, 0},
                new double[]{0, 0, 0, 1});
        }

        public void reset() {
            setFrontDirection(1, 0, 0);
            setUpperDirection(0, 1, 0);
            setRightDirection(0, 0, 1);
        }

        public double[] getPosition() {
            return mPosition;
        }

        public double getPositionX() {
            return mPosition[1];
        }

        public double getPositionY() {
            return mPosition[2];
        }

        public double getPositionZ() {
            return mPosition[3];
        }

        public void setPosition(final double x, final double y, final double z) {
            mPosition[1] = x;
            mPosition[2] = y;
            mPosition[3] = z;
        }

        public double[] getFrontDirection() {
            return mFrontDirection;
        }

        public double getFrontDirectionX() {
            return mFrontDirection[1];
        }

        public double getFrontDirectionY() {
            return mFrontDirection[2];
        }

        public double getFrontDirectionZ() {
            return mFrontDirection[3];
        }

        public void setFrontDirection(final double x, final double y, final double z) {
            mFrontDirection[1] = x;
            mFrontDirection[2] = y;
            mFrontDirection[3] = z;
        }

        public double[] getUpperDirection() {
            return mUpperDirection;
        }

        public double getUpperDirectionX() {
            return mUpperDirection[1];
        }

        public double getUpperDirectionY() {
            return mUpperDirection[2];
        }

        public double getUpperDirectionZ() {
            return mUpperDirection[3];
        }

        public void setUpperDirection(final double x, final double y, final double z) {
            mUpperDirection[1] = x;
            mUpperDirection[2] = y;
            mUpperDirection[3] = z;
        }

        public double[] getRightDirection() {
            return mRightDirection;
        }

        public double getRightDirectionX() {
            return mRightDirection[1];
        }

        public double getRightDirectionY() {
            return mRightDirection[2];
        }

        public double getRightDirectionZ() {
            return mRightDirection[3];
        }

        public void setRightDirection(final double x, final double y, final double z) {
            mRightDirection[1] = x;
            mRightDirection[2] = y;
            mRightDirection[3] = z;
        }

        public void slideHorizontal(final float delta) {
            double x = getPositionX();
            double y = getPositionY();
            double z = getPositionZ();
            setPosition(
                delta * getRightDirectionX() + x,
                delta * getRightDirectionY() + y,
                delta * getRightDirectionZ() + z
            );
        }

        public void setFov(float degree) {
            mFovDegree = degree;
        }

        public void setPosition(final Vector3D p) {
            setPosition(p.x(), p.y(), p.z());
        }

        public void rotateByEulerAngle(final float roll, final float yaw, final float pitch) {
            double currentX = getFrontDirectionX();
            double currentY = getFrontDirectionY();
            double currentZ = getFrontDirectionZ();
            double radianPerDegree = (Math.PI / 180.0);

            double lat = (90.0f - pitch) * radianPerDegree;
            double lng = yaw * radianPerDegree;
            double x = (double) (Math.sin(lat) * Math.cos(lng));
            double y = (double) (Math.cos(lat));
            double z = (double) (Math.sin(lat) * Math.sin(lng));
            setFrontDirection(x, y, z);

            double dx = getFrontDirectionX() - currentX;
            double dy = getFrontDirectionY() - currentY;
            double dz = getFrontDirectionZ() - currentZ;

            double theta = (roll * radianPerDegree) / 2.0f;
            double sin = (double) Math.sin(theta);
            double[] q = new double[] {
                (float) Math.cos(theta),
                sin * getFrontDirectionX(),
                sin * getFrontDirectionY(),
                sin * getFrontDirectionZ()
            };
            Quaternion.rotate(mUpperDirection, q, mUpperDirection);

            setRightDirection(
                getRightDirectionX() + dx,
                getRightDirectionY() + dy,
                getRightDirectionZ() + dz
            );
            Quaternion.rotate(mRightDirection, q, mRightDirection);
        }

        public void rotate(final Camera defaultCamera, final double[] rotation) {
            Quaternion.rotate(defaultCamera.getFrontDirection(), rotation, mFrontDirection);
            //Quaternion.rotate(defaultCamera.getUpperDirection(), rotation, mUpperDirection);
            Quaternion.rotate(defaultCamera.getRightDirection(), rotation, mRightDirection);
        }

        public Camera[] getCamerasForStereo(final float distance) {
            Camera leftCamera = new Camera(this);
            leftCamera.slideHorizontal(-1 * (distance / 2.0f));
            Camera rightCamera = new Camera(this);
            rightCamera.slideHorizontal((distance / 2.0f));

            return new Camera[] {
                leftCamera,
                rightCamera
            };
        }
    }
}