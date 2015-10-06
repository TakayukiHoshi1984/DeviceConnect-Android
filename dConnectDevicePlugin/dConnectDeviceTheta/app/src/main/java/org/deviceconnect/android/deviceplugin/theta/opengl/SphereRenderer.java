/*
 SphereRenderer.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.opengl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.theta.opengl.model.UVSphere;
import org.deviceconnect.android.deviceplugin.theta.utils.Quaternion;
import org.deviceconnect.android.deviceplugin.theta.utils.Vector3D;

import javax.microedition.khronos.opengles.GL10;


/**
 * Renderer class for photo display.
 *
 * @author NTT DOCOMO, INC.
 */
public class SphereRenderer {

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

        float x = camera.getPositionX();
        float y = camera.getPositionY();
        float z = camera.getPositionZ();
        float frontX = camera.getFrontDirectionX();
        float frontY = camera.getFrontDirectionY();
        float frontZ = camera.getFrontDirectionZ();
        float upX = camera.getUpperDirectionX();
        float upY = camera.getUpperDirectionY();
        float upZ = camera.getUpperDirectionZ();
        Matrix.setLookAtM(mViewMatrix, 0, x, y, z, frontX, frontY, frontZ, upX, upY, upZ);
        checkGlError(TAG, "setLookAtM");

        Matrix.perspectiveM(mProjectionMatrix, 0, camera.mFovDegree, getScreenAspect(), Z_NEAR, Z_FAR);
        checkGlError(TAG, "perspectiveM");

        GLES20.glUniformMatrix4fv(mModelMatrixHandle, 1, false, mModelMatrix, 0);
        GLES20.glUniformMatrix4fv(mProjectionMatrixHandle, 1, false, mProjectionMatrix, 0);
        GLES20.glUniformMatrix4fv(mViewMatrixHandle, 1, false, mViewMatrix, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
        GLES20.glUniform1i(mTexHandle, 0);

        mShell.draw(mPositionHandle, mUVHandle);
    }

    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
    }

    public void onSurfaceCreated() {
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
        private float mFovDegree;
        private final float[] mPosition;
        private final float[] mFrontDirection;
        private final float[] mUpperDirection;
        private final float[] mRightDirection;

        public Camera(final float fovDegree, final float[] position,
                      final float[] frontDirection, final float[] upperDirection,
                      final float[] rightDirection) {
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

        private static float[] copyVector(final float[] vector) {
            float[] result = new float[vector.length];
            System.arraycopy(vector, 0, result, 0, result.length);
            return result;
        }

        public Camera() {
            this(90.0f,
                new float[]{0, 0, 0, 0},
                new float[]{0, 1, 0, 0},
                new float[]{0, 0, 1, 0},
                new float[]{0, 0, 0, 1});
        }

        public void reset() {
            setFrontDirection(1, 0, 0);
            setUpperDirection(0, 1, 0);
            setRightDirection(0, 0, 1);
        }

        public float[] getPosition() {
            return mPosition;
        }

        public float getPositionX() {
            return mPosition[1];
        }

        public float getPositionY() {
            return mPosition[2];
        }

        public float getPositionZ() {
            return mPosition[3];
        }

        public void setPosition(final float x, final float y, final float z) {
            mPosition[1] = x;
            mPosition[2] = y;
            mPosition[3] = z;
        }

        public float[] getFrontDirection() {
            return mFrontDirection;
        }

        public float getFrontDirectionX() {
            return mFrontDirection[1];
        }

        public float getFrontDirectionY() {
            return mFrontDirection[2];
        }

        public float getFrontDirectionZ() {
            return mFrontDirection[3];
        }

        public void setFrontDirection(final float x, final float y, final float z) {
            mFrontDirection[1] = x;
            mFrontDirection[2] = y;
            mFrontDirection[3] = z;
        }

        public float[] getUpperDirection() {
            return mUpperDirection;
        }

        public float getUpperDirectionX() {
            return mUpperDirection[1];
        }

        public float getUpperDirectionY() {
            return mUpperDirection[2];
        }

        public float getUpperDirectionZ() {
            return mUpperDirection[3];
        }

        public void setUpperDirection(final float x, final float y, final float z) {
            mUpperDirection[1] = x;
            mUpperDirection[2] = y;
            mUpperDirection[3] = z;
        }

        public float[] getRightDirection() {
            return mRightDirection;
        }

        public float getRightDirectionX() {
            return mRightDirection[1];
        }

        public float getRightDirectionY() {
            return mRightDirection[2];
        }

        public float getRightDirectionZ() {
            return mRightDirection[3];
        }

        public void setRightDirection(final float x, final float y, final float z) {
            mRightDirection[1] = x;
            mRightDirection[2] = y;
            mRightDirection[3] = z;
        }

        public void slideHorizontal(final float delta) {
            float x = getPositionX();
            float y = getPositionY();
            float z = getPositionZ();
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
            float currentX = getFrontDirectionX();
            float currentY = getFrontDirectionY();
            float currentZ = getFrontDirectionZ();
            float radianPerDegree = (float) (Math.PI / 180.0);

            float lat = (90.0f - pitch) * radianPerDegree;
            float lng = yaw * radianPerDegree;
            float x = (float) (Math.sin(lat) * Math.cos(lng));
            float y = (float) (Math.cos(lat));
            float z = (float) (Math.sin(lat) * Math.sin(lng));
            setFrontDirection(x, y, z);

            float dx = getFrontDirectionX() - currentX;
            float dy = getFrontDirectionY() - currentY;
            float dz = getFrontDirectionZ() - currentZ;

            float theta = (roll * radianPerDegree) / 2.0f;
            float sin = (float) Math.sin(theta);
            float[] q = new float[] {
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

        public void rotate(final Camera defaultCamera, final float[] rotation) {
            Quaternion.rotate(defaultCamera.getFrontDirection(), rotation, mFrontDirection);
            Quaternion.rotate(defaultCamera.getUpperDirection(), rotation, mUpperDirection);
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