package org.deviceconnect.android.deviceplugin.theta.walkthrough;

import java.io.File;


class Frame {

    private final int mPosition;
    private final File mSource;

    public Frame(final int position, final File source) {
        mPosition = position;
        mSource = source;
    }

    public int getPosition() {
        return mPosition;
    }

    public File getSource() {
        return mSource;
    }
}
