package org.deviceconnect.android.deviceplugin.theta.walkthrough;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.deviceconnect.android.event.Event;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FrameLoader {

    private final Frame[] mFrames;

    private LoadingEventListener mLoadingEventListener;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);

    public FrameLoader(final File[] frameFiles) throws IOException {
        mFrames = new Frame[frameFiles.length];
        for (int i = 0; i < frameFiles.length; i++) {
            mFrames[i] = new Frame(frameFiles[i]);
        }
    }

    public int length() {
        return mFrames.length;
    }

    public Bitmap poll(final int pos, final long timeout) throws InterruptedException {
        if (0 > pos || pos > length()) {
            throw new IllegalArgumentException();
        }
        if (timeout < 0) {
            throw new IllegalArgumentException();
        }

        // TODO start, endをバッファサイズまで広げる
        int start = pos;
        int end = pos + 1;
        for (int i = start; i <= end; i++) {
            loadFrameAsync(pos);
        }

        Frame frame = mFrames[pos];
        synchronized (frame) {
            if (timeout != 0 && !frame.isLoaded()) {
                frame.wait(timeout);
            }
            Bitmap bitmap = frame.getBitmap();
            frame.clean();
            return bitmap;
        }
    }

    private void loadFrameAsync(final int pos) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Frame frame = mFrames[pos];
                try {
                    frame.load();
                } catch (IOException e) {
                    notifyLoadingError(frame, e);
                }
            }
        });
    }

    private void notifyLoadingError(final Frame frame, final Throwable e) {
        if (mLoadingEventListener != null) {
            mLoadingEventListener.onError(frame, e);
        }
    }

    private static interface LoadingEventListener {

        void onError(Frame frame, Throwable e);

    }

    static class Frame {

        static final int NOT_LOADED = 0;
        static final int LOADED = 1;

        final File mFile;
        int mLoadingState = NOT_LOADED;
        Bitmap mBitmap;

        Frame(final File file) {
            mFile = file;
        }

        boolean isLoaded() {
            return mLoadingState == LOADED;
        }

        void load() throws IOException {
            if (mLoadingState != NOT_LOADED) {
                return;
            }
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(mFile);
                mBitmap = BitmapFactory.decodeStream(fis);
                mLoadingState = LOADED;

                synchronized (this) {
                    notifyAll();
                }
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
        }

        Bitmap getBitmap() {
            return mBitmap;
        }

        void clean() {
            switch (mLoadingState) {
                case NOT_LOADED:
                    break;
                case LOADED:
                    cleanBitmap();
                    mLoadingState = NOT_LOADED;
                    break;
            }
        }

        private void cleanBitmap() {
            mBitmap = null;
        }
    }

}
