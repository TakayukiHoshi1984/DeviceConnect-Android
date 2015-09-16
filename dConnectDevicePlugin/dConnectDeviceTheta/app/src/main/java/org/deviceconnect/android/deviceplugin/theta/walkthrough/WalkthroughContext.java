package org.deviceconnect.android.deviceplugin.theta.walkthrough;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.theta.opengl.PixelBuffer;
import org.deviceconnect.android.deviceplugin.theta.opengl.SphereRenderer;
import org.deviceconnect.android.deviceplugin.theta.utils.Quaternion;
import org.deviceconnect.android.deviceplugin.theta.utils.Vector3D;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class WalkthroughContext implements SensorEventListener {

    private static final String TAG = "Walk";
    private static final boolean DEBUG = false; // BuildConfig.DEBUG;

    private static final int NUM_PRELOAD = 40;
    private static final long EXPIRE_INTERVAL = 10 * 1000;
    private static final float NS2S = 1.0f / 1000000000.0f;

    private Logger mLogger = Logger.getLogger("theta.dplugin");

    private final SensorManager mSensorMgr;
    private long mLastEventTimestamp;
    private float mEventInterval;
    private final int mDisplayRotation;
    private Quaternion mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));

    private final File mDir;
    private final File[] mAllFiles;
    private final FrameLoader mFrameLoader;
    private byte[] mRoi;
    private String mUri;
    private String mSegment;

    private final long mInterval; // milliseconds
    private Timer mExpireTimer;
    private EventListener mListener;

    private final ExecutorService mPlayerThread = Executors.newFixedThreadPool(1);
    private PixelBuffer mPixelBuffer;
    private final SphereRenderer mRenderer = new SphereRenderer();
    private ByteArrayOutputStream mBaos;
    private boolean mIsStopped = true;
    private PlayStatus mPlayStatus = new PlayStatus(0, 0);

    public WalkthroughContext(final Context context, final File omniImageDir,
                              final int width, final int height, final float fps) {
        WindowManager windowMgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplayRotation = windowMgr.getDefaultDisplay().getRotation();
        mSensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        mDir = omniImageDir;
        mAllFiles = loadFiles(omniImageDir);
        mFrameLoader = new FrameLoader(mAllFiles);
        mFrameLoader.setLoaderListener(new BitmapLoaderListener() {

            @Override
            public void onLoad(int pos) {
                if (DEBUG) {
                    Log.d(TAG, "onLoad: " + pos);
                }

                if (pos == 0) {
                    startRendering();
                }
            }

            @Override
            public void onComplete() {
                if (DEBUG) {
                    Log.d(TAG, "onComplete: ");
                }

                if (mListener != null) {
                    mListener.onComplete(WalkthroughContext.this);
                }
            }

            @Override
            public void onError(int pos, Exception e) {
                if (DEBUG) {
                    Log.e("Walk", "Error: ", e);
                }
            }
        });

        mInterval = (long) (1000.0f / fps);

        mBaos = new ByteArrayOutputStream(width * height);
        mPlayerThread.execute(new Runnable() {
            @Override
            public void run() {
                mPixelBuffer = new PixelBuffer(width, height, false);
                mPixelBuffer.setRenderer(mRenderer);
            }
        });

        Param param = new Param();
        param.setImageWidth(width);
        param.setImageHeight(height);
        initRendererParam(param);
    }

    private File[] loadFiles(final File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("dir is not directory.");
        }

        if (DEBUG) {
            Log.d(TAG, "Loading Omni images directory: " + dir.getAbsolutePath());
        }

        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String filename) {
                return filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg");
            }
        });

        if (DEBUG) {
            Log.d(TAG, "Files: " + files.length);
        }

        List<File> fileList = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            fileList.add(files[i]);
        }
        Collections.sort(fileList);
        return fileList.toArray(new File[fileList.size()]);
    }

    private boolean startVrMode() {
        // Reset current rotation.
        mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));

        List<Sensor> sensors = mSensorMgr.getSensorList(Sensor.TYPE_ALL);
        if (sensors.size() == 0) {
            mLogger.warning("Failed to start VR mode: any sensor is NOT found.");
            return false;
        }
        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                mLogger.info("Started VR mode: GYROSCOPE sensor is found.");
                mSensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
                return true;
            }
        }
        mLogger.warning("Failed to start VR mode: GYROSCOPE sensor is NOT found.");
        return false;
    }

    private void stopVrMode() {
        mSensorMgr.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        // Nothing to do.
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        if (mLastEventTimestamp != 0) {
            float EPSILON = 0.000000001f;
            float[] vGyroscope = new float[3];
            float[] deltaVGyroscope = new float[4];
            Quaternion qGyroscopeDelta;
            float dT = (event.timestamp - mLastEventTimestamp) * NS2S;

            System.arraycopy(event.values, 0, vGyroscope, 0, vGyroscope.length);
            float tmp = vGyroscope[2];
            vGyroscope[2] = vGyroscope[0] * -1;
            vGyroscope[0] = tmp;

            float magnitude = (float) Math.sqrt(Math.pow(vGyroscope[0], 2)
                    + Math.pow(vGyroscope[1], 2) + Math.pow(vGyroscope[2], 2));
            if (magnitude > EPSILON) {
                vGyroscope[0] /= magnitude;
                vGyroscope[1] /= magnitude;
                vGyroscope[2] /= magnitude;
            }

            float thetaOverTwo = magnitude * dT / 2.0f;
            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

            deltaVGyroscope[0] = sinThetaOverTwo * vGyroscope[0];
            deltaVGyroscope[1] = sinThetaOverTwo * vGyroscope[1];
            deltaVGyroscope[2] = sinThetaOverTwo * vGyroscope[2];
            deltaVGyroscope[3] = cosThetaOverTwo;

            float[] delta = new float[3];
            switch (mDisplayRotation) {
                case Surface.ROTATION_0:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[1];
                    delta[2] = deltaVGyroscope[2];
                    break;
                case Surface.ROTATION_90:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[2] * -1;
                    delta[2] = deltaVGyroscope[1];
                    break;
                case Surface.ROTATION_180:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[1] * -1;
                    delta[2] = deltaVGyroscope[2];
                    break;
                case Surface.ROTATION_270:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[2];
                    delta[2] = deltaVGyroscope[1] * -1;
                    break;
                default:
                    break;
            }

            qGyroscopeDelta = new Quaternion(deltaVGyroscope[3], new Vector3D(delta));

            mCurrentRotation = qGyroscopeDelta.multiply(mCurrentRotation);

            float[] qvOrientation = new float[4];
            qvOrientation[0] = mCurrentRotation.imaginary().x();
            qvOrientation[1] = mCurrentRotation.imaginary().y();
            qvOrientation[2] = mCurrentRotation.imaginary().z();
            qvOrientation[3] = mCurrentRotation.real();

            float[] rmGyroscope = new float[9];
            SensorManager.getRotationMatrixFromVector(rmGyroscope,
                    qvOrientation);

            float[] vOrientation = new float[3];
            SensorManager.getOrientation(rmGyroscope, vOrientation);

            SphereRenderer.Camera currentCamera = mRenderer.getCamera();
            SphereRenderer.CameraBuilder newCamera = new SphereRenderer.CameraBuilder(currentCamera);
            newCamera.rotate(mCurrentRotation);
            mRenderer.setCamera(newCamera.create());
        }
        mLastEventTimestamp = event.timestamp;
    }

    public void setUri(final String uriString) {
        mUri = uriString;
        mSegment = Uri.parse(uriString).getLastPathSegment();
    }

    public String getUri() {
        return mUri;
    }

    public String getSegment() {
        return mSegment;
    }

    public File getOmnidirectionalImageDirectory() {
        return mDir;
    }

    public byte[] getMedia() {
        return mRoi;
    }

    public synchronized void start() {
        if (DEBUG) {
            Log.d(TAG, "Walkthrough.start()");
        }

        if (mIsStopped) {
            mIsStopped = false;
            startVrMode();
            seek(1);
        }
    }

    public synchronized void stop() {
        if (DEBUG) {
            Log.d(TAG, "Walkthrough.stop()");
        }

        if (!mIsStopped) {
            stopRendering();
            stopVrMode();
            mFrameLoader.reset();
            mPixelBuffer.destroy();
            mPlayerThread.shutdownNow();
            mIsStopped = true;
        }
    }

    private Thread mRendererThread;

    private void startRendering() {
        if (mRendererThread == null) {
            mRendererThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!mIsStopped) {
                            long start = System.currentTimeMillis();
                            render();
                            long end = System.currentTimeMillis();
                            long delay = mInterval - (end - start);
                            if (delay > 0) {
                                Thread.sleep(delay);
                            }
                        }
                    } catch (InterruptedException e) {
                        // Nothing to do.
                    }
                }
            });
            mRendererThread.start();
        }
    }

    private void stopRendering() {
        if (mRendererThread != null) {
            mRendererThread.interrupt();
            mRendererThread = null;
        }
    }

    public synchronized int seek(final int delta) {
        if (mIsStopped) {
            return -1;
        }
        mPlayStatus = nextPlayStatus(delta);
        int nextFrom = mPlayStatus.getStartFrame();
        int nextTo = mPlayStatus.getEndFrame();
        mFrameLoader.free(nextFrom, NUM_PRELOAD);
        mFrameLoader.prepareBitmap(nextFrom, NUM_PRELOAD);

        try {
            if (delta > 0) {
                for (int i = nextFrom; i < nextTo; i++) {
                    updateTexture(mFrameLoader.pull(i));
                }
            } else if (delta < 0) {
                for (int i = nextFrom - 1; i >= nextTo; i--) {
                    updateTexture(mFrameLoader.pull(i));
                }
            }
            return Math.abs(delta);
        } catch (InterruptedException e) {
            return -1;
        }
    }

    private void updateTexture(final Bitmap texture) throws InterruptedException {
        if (DEBUG) {
            Log.d(TAG, "updateTexture: bitmap=" + texture);
        }

        long start = System.currentTimeMillis();
        mRenderer.setTexture(texture);
        long end = System.currentTimeMillis();
        Thread.sleep(mInterval - (end - start));
    }

    private int getFrameCount(final int start, final int end) {
        int startIndex = remapFrameIndex(start);
        int endIndex = remapFrameIndex(end);
        if (endIndex < startIndex) {
            startIndex += getAllFrameCount();
        }
        return endIndex - startIndex;
    }

    private int remapFrameIndex(final int index) {
        int count = getAllFrameCount();
        if (index >= count) {
            return index % count;
        }
        if (index < 0) {
            int tmpIndex = index;
            tmpIndex = tmpIndex % count;
            tmpIndex += count;
            return tmpIndex;
        }
        return index;
    }

    private int getAllFrameCount() {
        return mAllFiles.length;
    }

    private PlayStatus nextPlayStatus(final int delta) {
        int nowTo = mPlayStatus.getEndFrame();
        int nextFrom = nowTo;
        int nextTo = remapFrameIndex(nowTo + delta);
        return new PlayStatus(nextFrom, nextTo);
    }

    private static class PlayStatus {
        private int mStartFrame;
        private int mEndFrame;
        private int mCurrentFrame;
        private boolean mIsPlaying;

        public PlayStatus(final int startFrame, final int endFrame) {
            mStartFrame = startFrame;
            mEndFrame = endFrame;
            mCurrentFrame = -1;
        }

        public int getStartFrame() {
            return mStartFrame;
        }

        public int getEndFrame() {
            return mEndFrame;
        }

        public void setCurrentFrame(final int currentFrame) {
            mCurrentFrame = currentFrame;
        }

        public boolean isPlaying() {
            return mIsPlaying;
        }

        public void setPlaying(final boolean isPlaying) {
            mIsPlaying = isPlaying;
        }

        public boolean isFinished() {
            return mCurrentFrame == mEndFrame -1;
        }
    }

    private void render() {
        if (DEBUG) {
            Log.d(TAG, "Walkthrough.render()");
        }

        if (mIsStopped) {
            return;
        }
        mPlayerThread.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mPixelBuffer.render();
                    Bitmap result = mPixelBuffer.convertToBitmap();
                    if (result == null) {
                        return;
                    }
                    mBaos.reset();
                    result.compress(Bitmap.CompressFormat.JPEG, 100, mBaos);
                    mRoi = mBaos.toByteArray();

                    if (mListener != null) {
                        mListener.onUpdate(WalkthroughContext.this, mRoi);
                    }
                } catch (Throwable e) {
                    if (DEBUG) {
                        Log.d(TAG, "ERROR: Executor:", e);
                    }
                }
            }
        });
    }

    private void initRendererParam(final Param param) {
        SphereRenderer.CameraBuilder builder = new SphereRenderer.CameraBuilder();
        builder.setPosition(new Vector3D(
            (float) param.getCameraX(),
            (float) param.getCameraY() * -1,
            (float) param.getCameraZ()));
        builder.setFov((float) param.getCameraFov());
        mRenderer.setCamera(builder.create());
        mRenderer.setSphereRadius((float) param.getSphereSize());
        mRenderer.setScreenWidth(param.getImageWidth());
        mRenderer.setScreenHeight(param.getImageHeight());
    }

    public void startExpireTimer() {
        if (mExpireTimer != null) {
            return;
        }
        long now = System.currentTimeMillis();
        Date expireTime = new Date(now + EXPIRE_INTERVAL);
        mExpireTimer = new Timer();
        mExpireTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mExpireTimer.cancel();
                mExpireTimer = null;
                if (mListener != null) {
                    mListener.onExpire(WalkthroughContext.this);
                }
            }
        }, expireTime);
    }

    public void stopExpireTimer() {
        if (mExpireTimer != null) {
            mExpireTimer.cancel();
            mExpireTimer = null;
        }
    }

    public void restartExpireTimer() {
        stopExpireTimer();
        startExpireTimer();
    }

    public void setEventListener(final EventListener listener) {
        mListener = listener;
    }

    public static interface EventListener {
        void onUpdate(WalkthroughContext context, byte[] roi);

        void onComplete(WalkthroughContext context);

        void onExpire(WalkthroughContext roiContext);
    }

    private static class Frame {

        private final File mFile;

        private Bitmap mBitmap;

        public Frame(final File file) {
            mFile = file;
        }

        public Bitmap pull() {
            Bitmap b = mBitmap;
            mBitmap = null;
            return b;
        }

        public void free() {
            if (mBitmap != null) {
                mBitmap.recycle();
                mBitmap = null;
            }
        }

        public boolean isLoaded() {
            return mBitmap != null;
        }

        public void load() throws IOException {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(mFile);
                Bitmap bitmap = BitmapFactory.decodeStream(fis);
                synchronized (this) {
                    mBitmap = bitmap;
                    notifyAll();
                }
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
        }
    }

    private static class FrameRenderer {

        private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);

        public FrameRenderer() {

        }
    }

    private static class FrameLoader {

        private final Frame[] mFrames;

        private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);

        private BitmapLoaderListener mListener;

        private FrameLoaderTask mTask;

        public FrameLoader(final File[] files) {
            mFrames = new Frame[files.length];
            for (int i = 0; i < files.length; i++) {
                mFrames[i] = new Frame(files[i]);
            }
        }

        public void setLoaderListener(final BitmapLoaderListener listener) {
            mListener = listener;
        }

        public int getTotalCount() {
            return mFrames.length;
        }

        public synchronized void prepareBitmap(final int from, final int maxCount) {
            if (mTask != null) {
                mTask.cancel();
                mTask = null;
            }
            mTask = new FrameLoaderTask();
            mTask.setStartIndex(from);
            mTask.setMaxCount(maxCount);
            mTask.setBitmapLoader(this);
            mExecutor.execute(mTask);
        }

        public void free(final int from, final int maxCount) {
            free(loopIndexes(from, maxCount));
        }

        private void free(final int[] excludedIndexes) {
            for (int i = 0; i < mFrames.length; i++) {
                if (!contains(i, excludedIndexes)) {
                    mFrames[i].free();
                }
            }
        }

        private boolean contains(final int targetIndex, final int[] range) {
            for (int i = 0; i < range.length; i++) {
                if (range[i] == targetIndex) {
                    return true;
                }
            }
            return false;
        }

        private int[] loopIndexes(final int from, final int delta) {
            int length = Math.abs(delta);
            int[] indexes = new int[length];
            int total = getTotalCount();
            int count = 0;
            int origin = from;

            if (delta > 0) {
                for (int i = 0, d = 0; count < length; i++, d++, count++) {
                    int index = origin + d;
                    if (index >= total) {
                        index = 0;
                        origin = 0;
                        d = 0;
                    }
                    indexes[i] = index;
                }
            } else if (delta < 0) {
                for (int i = 0, d = 0; count < length; i++, d++, count++) {
                    int index = origin - d;
                    if (index < 0) {
                        index = total - 1;
                        origin = total - 1;
                        d = 0;
                    }
                    indexes[i] = index;
                }
            }
            return indexes;
        }

        public synchronized void reset() {
            if (DEBUG) {
                Log.d(TAG, "Walkthrough.reset()");
            }

            for (int i = 0; i < mFrames.length; i++) {
                mFrames[i].free();
            }
        }

        public synchronized Bitmap pull(int pos) throws InterruptedException {
            if (0 > pos || pos >= getTotalCount()) {
                Log.w(TAG, "Walkthrough.pull(): out of range");
                return null;
            }

            if (DEBUG) {
                Log.d(TAG, "Walkthrough.pull(): changed pos: " + pos);
            }
            if (pos == getTotalCount() - 1) {
                if (mListener != null) {
                    mListener.onComplete();
                }
            }

            Frame frame = mFrames[pos];
            synchronized (frame) {
                if (!frame.isLoaded()) {
                    loadFrame(pos);
                    while (!frame.isLoaded()) {
                        frame.wait(100);
                    }
                }
                return frame.pull();
            }
        }

        private void loadFrame(final int pos) {
            if (0 > pos || pos >= getTotalCount()) {
                return;
            }

            if (DEBUG) {
                Log.d(TAG, "Loading bitmap: pos=" + pos);
            }

            final Frame frame = mFrames[pos];

            if (frame.isLoaded()) {
                if (DEBUG) {
                    Log.d(TAG, "Already loaded bitmap: pos=" + pos);
                }
                synchronized (frame) {
                    frame.notifyAll();
                }
                return;
            }

            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        frame.load();
                        if (mListener != null) {
                            mListener.onLoad(pos);
                        }
                    } catch (IOException e) {
                        if (mListener != null) {
                            mListener.onError(pos, e);
                        }
                    } catch (Throwable e) {
                        if (DEBUG) {
                            Log.d(TAG, "ERROR: loadBitmap: ", e);
                        }
                    }
                }
            });
        }
    }

    private static class FrameLoaderTask implements Runnable {

        private int mStartIndex;
        private int mMaxCount;
        private FrameLoader mFrameLoader;
        private boolean mIsCanceled;

        public void setStartIndex(final int startIndex) {
            mStartIndex = startIndex;
        }

        public void setMaxCount(final int maxCount) {
            mMaxCount = maxCount;
        }

        public void setBitmapLoader(final FrameLoader loader) {
            mFrameLoader = loader;
        }

        public void cancel() {
            mIsCanceled = true;
        }

        private boolean isCanceled() {
            return mIsCanceled;
        }

        @Override
        public void run() {
            int totalCount = mFrameLoader.getTotalCount();
            int remainedCount = Math.abs(mMaxCount);
            for (int i = mStartIndex; remainedCount > 0; remainedCount--) {

                mFrameLoader.loadFrame(i++);

                if (mMaxCount > 0 && i >= totalCount) {
                    i -= totalCount;
                } else if (mMaxCount < 0 && i < 0) {
                    i += mFrameLoader.getTotalCount();
                }

                if (isCanceled()) {
                    break;
                }
            }
        }
    }

    private static interface BitmapLoaderListener {

        void onLoad(int pos);

        void onComplete();

        void onError(int pos, Exception e);

    }

    public static class Param {

        double mCameraX;

        double mCameraY;

        double mCameraZ;

        double mCameraYaw;

        double mCameraRoll;

        double mCameraPitch;

        double mCameraFov = 90.0d;

        double mSphereSize = 1.0d;

        int mImageWidth = 480;

        int mImageHeight = 270;

        boolean mStereoMode;

        boolean mVrMode;

        public double getCameraX() {
            return mCameraX;
        }

        public void setCameraX(final double x) {
            mCameraX = x;
        }

        public double getCameraY() {
            return mCameraY;
        }

        public void setCameraY(final double y) {
            mCameraY = y;
        }

        public double getCameraZ() {
            return mCameraZ;
        }

        public void setCameraZ(final double z) {
            mCameraZ = z;
        }

        public double getCameraYaw() {
            return mCameraYaw;
        }

        public void setCameraYaw(final double yaw) {
            mCameraYaw = yaw;
        }

        public double getCameraRoll() {
            return mCameraRoll;
        }

        public void setCameraRoll(final double roll) {
            mCameraRoll = roll;
        }

        public double getCameraPitch() {
            return mCameraPitch;
        }

        public void setCameraPitch(final double pitch) {
            mCameraPitch = pitch;
        }

        public double getCameraFov() {
            return mCameraFov;
        }

        public void setCameraFov(final double fov) {
            mCameraFov = fov;
        }

        public double getSphereSize() {
            return mSphereSize;
        }

        public void setSphereSize(final double size) {
            mSphereSize = size;
        }

        public int getImageWidth() {
            return mImageWidth;
        }

        public void setImageWidth(final int width) {
            mImageWidth = width;
        }

        public int getImageHeight() {
            return mImageHeight;
        }

        public void setImageHeight(final int height) {
            mImageHeight = height;
        }

        public boolean isStereoMode() {
            return mStereoMode;
        }

        public void setStereoMode(final boolean isStereo) {
            mStereoMode = isStereo;
        }

        public boolean isVrMode() {
            return mVrMode;
        }

        public void setVrMode(final boolean isVr) {
            mVrMode = isVr;
        }
    }
}
