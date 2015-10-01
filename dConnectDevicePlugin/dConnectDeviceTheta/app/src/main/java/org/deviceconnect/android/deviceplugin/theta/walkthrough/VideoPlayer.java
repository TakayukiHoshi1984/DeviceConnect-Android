package org.deviceconnect.android.deviceplugin.theta.walkthrough;


import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


class VideoPlayer {

    private static final boolean DEBUG = false; // BuildConfig.DEBUG;
    private static final String TAG = "VideoPlayer";

    private final Video mVideo;
    private final long mInterval;
    private final FrameJpegBuffer mBuffer;
    private final FrameJpegLoader mPositiveLoader;
    private final FrameJpegLoader mNegativeLoader;
    private final ExecutorService mJpegLoaderThread = Executors.newFixedThreadPool(1);

    private boolean mIsLoop = true;
    private Frame mCurrentFrame;
    private Display mDisplay;

    public VideoPlayer(final Video video, final int bufferSize) {
        mVideo = video;
        mInterval = video.getFrameInterval();

        int width = video.getWidth();
        int height = video.getHeight();
        mBuffer = new FrameJpegBuffer(bufferSize, width, height);

        mPositiveLoader = new PositiveDirectionLoader();
        mNegativeLoader = new NegativeDirectionLoader();
    }

    public void destroy() {
        Log.d(TAG, "VideoPlayer.destroy()");
        mBuffer.destroy();
    }

    public void prepare() {
        if (mDisplay != null) {
            mDisplay.onPrepared();
        }
    }

    public synchronized void playBy(final int delta) {
        FrameJpegLoader loader = selectFrameJpegLoader(delta);

        try {
            long start, end;
            long delay = 0;
            int total = Math.abs(delta);

            for (int count = 0; count < total; ) {
                start = System.currentTimeMillis();

                int num = 1;
                if (delay > mInterval) {
                    num = (int) Math.ceil(delay / mInterval);
                }

                Frame frame = loader.nextFrame(num);
                if (frame == null) {
                    if (mDisplay != null) {
                        mDisplay.onFinish();
                    }
                    break;
                }
                mCurrentFrame = frame;
                loader.load(frame);

                end = System.currentTimeMillis();
                delay = end - start;

                if (mInterval - delay > 0) {
                    Thread.sleep(mInterval - delay);
                }

                count += num;
            }
        } catch (InterruptedException e) {
            if (mDisplay != null) {
                mDisplay.onError(e);
            }
        }
    }

    private FrameJpegLoader selectFrameJpegLoader(final int direction) {
        return (direction >= 0) ? mPositiveLoader : mNegativeLoader;
    }

    public void setDisplay(final Display display) {
        mDisplay = display;
    }

    public interface Display {

        void onPrepared();

        void onDraw(FrameJpeg jpeg);

        void onFinish();

        void onError(Throwable e);
    }

    private static class FrameJpegBuffer {

        public static final int NEXT = 0;
        public static final int PREV = 1;

        private FrameJpeg mCurrentJpeg;
        private final LinkedList<FrameJpeg>[] mJpegList = new LinkedList[2];
        private boolean isDestroyed;

        public FrameJpegBuffer(final int capacity, final int width, final int height) {
            mCurrentJpeg = new FrameJpeg(width, height);
            mJpegList[NEXT] = createJpegList(capacity, width, height);
            mJpegList[PREV] = createJpegList(capacity, width, height);
        }

        public boolean isDestroyed() {
            return isDestroyed;
        }

        public synchronized void destroy() {
            isDestroyed = true;

            Log.d(TAG, "FrameJpegBuffer.destroy()");
            for (int i = 0; i < mJpegList.length; i++) {
                List<FrameJpeg> list = mJpegList[i];
                for (FrameJpeg jpeg : list) {
                    jpeg.destroy();
                }
            }
        }

        private static LinkedList<FrameJpeg> createJpegList(final int capacity, final int width, final int height) {
            LinkedList<FrameJpeg> list = new LinkedList<FrameJpeg>();
            for (int i = 0; i < capacity; i++) {
                FrameJpeg jpeg = new FrameJpeg(width, height);
                list.add(jpeg);
            }
            return list;
        }

        public FrameJpeg getCurrentJpeg() {
            return mCurrentJpeg;
        }

        public void setCurrentJpeg(final FrameJpeg jpeg) {
            mCurrentJpeg = jpeg;
        }

        public LinkedList<FrameJpeg> getJpegList(final int direction) {
            return mJpegList[direction];
        }
    }

    private abstract class FrameJpegLoader {

        private final int mNextDirection;
        private final int mPrevDirection;

        protected FrameJpegLoader(final int nextDirection, final int prevDirection) {
            mNextDirection = nextDirection;
            mPrevDirection = prevDirection;
        }

        protected abstract int nextFramePosition(int delta);

        public Frame nextFrame(int delta) {
            int nextPos = nextFramePosition(delta);

            if (DEBUG) {
                Log.d(TAG, "***** nextFrame: " + nextPos);
            }

            if (nextPos < 0 || nextPos >= mVideo.getLength()) {
                return null;
            }
            return mVideo.getFrame(nextPos);
        }

        public void load(final Frame frame) {
            mJpegLoaderThread.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        FrameJpeg jpeg;
                        synchronized (mBuffer) {
                            if (mBuffer.isDestroyed()) {
                                return;
                            }

                            jpeg = popNext();

                            //long start = System.currentTimeMillis();
                            if (!jpeg.isLoaded(frame)) {
                                jpeg.load(frame);
                            }
                            //long end = System.currentTimeMillis();
                            //Log.d(TAG, "Frame = " +  frame.getPosition() + " , Load time = " + (end - start) + " msec.");

                            pushPrev(mBuffer.getCurrentJpeg());

                            mBuffer.setCurrentJpeg(jpeg);
                            pushNext(popPrev());
                        }

                        if (mDisplay != null) {
                            mDisplay.onDraw(jpeg);
                        }
                    } catch (Throwable e) {
                        if (mDisplay != null) {
                            mDisplay.onError(e);
                        }
                    }
                }
            });
        }

        private FrameJpeg popNext() {
            return mBuffer.getJpegList(mNextDirection).removeFirst();
        }

        private void pushNext(final FrameJpeg jpeg) {
            mBuffer.getJpegList(mNextDirection).addLast(jpeg);
        }

        private FrameJpeg popPrev() {
            return mBuffer.getJpegList(mPrevDirection).removeLast();
        }

        private void pushPrev(final FrameJpeg jpeg) {
            mBuffer.getJpegList(mPrevDirection).addFirst(jpeg);
        }
    }

    private class PositiveDirectionLoader extends FrameJpegLoader {

        public PositiveDirectionLoader() {
            super(FrameJpegBuffer.NEXT, FrameJpegBuffer.PREV);
        }

        @Override
        protected int nextFramePosition(final int delta) {
            if (mCurrentFrame == null) {
                return 0;
            }
            int current = mCurrentFrame.getPosition();
            int next = current + delta;
            if (next >= mVideo.getLength() && mIsLoop) {
                return next % mVideo.getLength();
            }
            return next;
        }
    }

    private class NegativeDirectionLoader extends FrameJpegLoader {

        public NegativeDirectionLoader() {
            super(FrameJpegBuffer.PREV, FrameJpegBuffer.NEXT);
        }

        @Override
        protected int nextFramePosition(final int delta) {
            if (mCurrentFrame == null) {
                return 0;
            }
            int current = mCurrentFrame.getPosition();
            int next = current - delta;
            if (next < 0 && mIsLoop) {
                return (next + mVideo.getLength()) % mVideo.getLength();
            }
            return next;
        }
    }

}
