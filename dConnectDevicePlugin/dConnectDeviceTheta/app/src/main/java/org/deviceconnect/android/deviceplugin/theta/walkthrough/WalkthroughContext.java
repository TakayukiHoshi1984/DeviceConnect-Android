package org.deviceconnect.android.deviceplugin.theta.walkthrough;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.theta.opengl.PixelBuffer;
import org.deviceconnect.android.deviceplugin.theta.opengl.SphereRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WalkthroughContext  {

    private static final String TAG = "Walk";
    private static final int NUM_PRELOAD = 1;

    private final File[] mAllFiles;
    private final BitmapLoader mBitmapLoader;
    private byte[] mRoi;
    private String mUri;
    private String mSegment;

    private final long mInterval; // milliseconds
    private Timer mTimer;
    private EventListener mListener;

    private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);
    private PixelBuffer mPixelBuffer;
    private final SphereRenderer mRenderer = new SphereRenderer();
    private ByteArrayOutputStream mBaos;

    public WalkthroughContext(final File omniImageDir, final int width, final int height) {
        boolean isDir = omniImageDir.isDirectory();
        if (!isDir) {
            throw new IllegalArgumentException("dir is not directory.");
        }
        mAllFiles = omniImageDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String filename) {
                return filename.endsWith(".jpg");
            }
        });
        mBitmapLoader = new BitmapLoader(mAllFiles);
        mBitmapLoader.setLoaderListener(new BitmapLoaderListener() {
            @Override
            public void onLoad(int pos) {
                Log.d(TAG, "onLoad: " + pos);
                startVideo();
            }

            @Override
            public void onComplete() {
                Log.d(TAG, "onComplete: ");
//                stop();
//                mBitmapLoader.reset();
//                start();
            }

            @Override
            public void onError(int pos, Exception e) {
                e.printStackTrace();
                Log.e("Walk", "Error: " + e.getMessage());
            }
        });

        mInterval = 1000;

        mBaos = new ByteArrayOutputStream(width * height);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mPixelBuffer = new PixelBuffer(width, height, false);
                mPixelBuffer.setRenderer(mRenderer);
            }
        });
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

    public void start() {
        Log.d(TAG, "Walkthrough.start()");
        mBitmapLoader.init(NUM_PRELOAD);
    }

    public void stop() {
        Log.d(TAG, "Walkthrough.stop()");
        if (mTimer == null) {
            return;
        }
        mTimer.cancel();
        mTimer = null;
    }

    private void startVideo() {
        Log.d(TAG, "Walkthrough.startVideo()");
        if (mTimer != null) {
            Log.d(TAG, "Already started video.");
            return;
        }
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                render();
                stop();
            }
        }, 0, mInterval);
    }

    private void render() {
        Log.d(TAG, "Walkthrough.render()");
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {

                Bitmap texture;
                try {
                    texture = mBitmapLoader.pull();
                    if (texture == null) {
                        Log.d("Walk", "no longer bitmap.");
                        return;
                    }
                } catch (InterruptedException e) {
                    Log.d("Walk", "thread is interrupted.");
                    return;
                }
                Log.i(TAG, "Changing Texure: " + texture.getWidth() + " x " + texture.getHeight());

                mRenderer.setTexture(texture);
                mPixelBuffer.render();
                Bitmap result = mPixelBuffer.convertToBitmap();

                mBaos.reset();
                result.compress(Bitmap.CompressFormat.JPEG, 100, mBaos);
                mRoi = mBaos.toByteArray();

                if (mListener != null) {
                    mListener.onUpdate(WalkthroughContext.this, mRoi);
                }
            }
        });
    }

    private File getBitmapFile(final int position) {
        return mAllFiles[position];
    }

    private int getSize() {
        return mAllFiles.length;
    }

    public void setEventListener(final EventListener listener) {
        mListener = listener;
    }

    public static interface EventListener {
        void onUpdate(WalkthroughContext context, byte[] roi);
    }

    private static class BitmapLoader {

        private final File[] mFiles;

        private final Bitmap[] mBitmaps;

        private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);

        private BitmapLoaderListener mListener;

        private int mPos;

        public BitmapLoader(final File[] files) {
            for (File file : files) {
                if (file == null) {
                    throw new IllegalArgumentException("files must not have a null object.");
                }
            }
            mFiles = files;
            mBitmaps = new Bitmap[files.length];
        }

        public void setLoaderListener(final BitmapLoaderListener listener) {
            mListener = listener;
        }

        public void init(final int num) {
            Log.d(TAG, "Preloading bitmaps: num=" + num);
            for (int i = 0; i < num; i++) {
                loadBitmap(i);
            }
        }

        public synchronized void reset() {
            mPos = 0;
            for (int i = 0; i < mBitmaps.length; i++) {
                Bitmap bitmap = mBitmaps[i];
                if (bitmap != null) {
                    bitmap.recycle();
                    mBitmaps[i] = null;
                }
            }
        }

        public synchronized Bitmap pull() throws InterruptedException {
            if (mPos == mBitmaps.length) {
                return null;
            }
            int pos = mPos++;

            File file = mFiles[pos];
            synchronized (file) {
                Bitmap bitmap = mBitmaps[pos];
                if (bitmap != null) {
                    return bitmap;
                }

                while ((bitmap = mBitmaps[pos]) == null) {
                    file.wait(100);
                }

                // Remove pulled bitmap from this buffer.
                mBitmaps[pos] = null;

                // Start to load next bitmap.
                if (0 <= pos && pos < mBitmaps.length - 1) {
                    loadBitmap(pos + 1);
                } else {
                    if (mListener != null) {
                        mListener.onComplete();
                    }
                }

                return bitmap;
            }
        }

        private void loadBitmap(final int pos) {
            Log.d(TAG, "Loading bitmap: pos=" + pos);

            final File file = mFiles[pos];

            if (mBitmaps[pos] != null) {
                Log.d(TAG, "Already loaded bitmap: pos=" + pos);
                synchronized (file) {
                    file.notifyAll();
                }
                return;
            }

            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);
                        Bitmap bitmap = BitmapFactory.decodeStream(fis);
                        synchronized (file) {
                            mBitmaps[pos] = bitmap;
                            file.notifyAll();
                        }

                        if (mListener != null) {
                            mListener.onLoad(pos);
                        }
                    } catch (IOException e) {
                        if (mListener != null) {
                            mListener.onError(pos, e);
                        }
                    } finally {
                        if (fis != null) {
                            try {
                                fis.close();
                            } catch(IOException e) {
                                // Nothing to do.
                            }
                        }
                    }
                }
            });
        }
    }

    private static interface BitmapLoaderListener {

        void onLoad(int pos);

        void onComplete();

        void onError(int pos, Exception e);

    }
}
