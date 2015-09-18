package org.deviceconnect.android.deviceplugin.theta.walkthrough;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


class Video {

    private final Frame[] mAllFrames;
    private final int mWidth;
    private final int mHeight;
    private final long mFrameInterval;

    private Video(final Frame[] allFrames, final int width, final int height, final double fps) {
        mAllFrames = allFrames;
        mWidth = width;
        mHeight = height;
        mFrameInterval = (long) (1000.0 / fps);
    }

    public int getLength() {
        return mAllFrames.length;
    }

    public Frame getFrame(int position) {
        return mAllFrames[position];
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public long getFrameInterval() {
        return mFrameInterval;
    }

    public static Video createVideo(final File dir, final int width, final int height, final double fps) throws IOException {
        File[] files = listFilesFromDirectory(dir);
        Frame[] frames = new Frame[files.length];
        for (int i = 0; i < files.length; i++) {
            frames[i] = new Frame(i, files[i]);
        }
        return new Video(frames, width, height, fps);
    }

    private static File[] listFilesFromDirectory(final File dir) throws IOException {
        if (dir == null) {
            throw new IllegalArgumentException("dir is null.");
        }
        if (!dir.exists()) {
            throw new IOException("the specified directory does not exist.");
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("dir is not a directory.");
        }
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File file, final String name) {
                String lowerName = name.toLowerCase();
                return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
            }
        });

        // Sort JPEG files.
        List<File> fileList = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            fileList.add(files[i]);
        }
        Collections.sort(fileList);
        files = fileList.toArray(new File[fileList.size()]);

        return files;
    }
}
