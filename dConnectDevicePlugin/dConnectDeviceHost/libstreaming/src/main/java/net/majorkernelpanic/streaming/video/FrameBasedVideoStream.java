/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package net.majorkernelpanic.streaming.video;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import net.majorkernelpanic.streaming.Stream;
import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;


public abstract class FrameBasedVideoStream extends VideoStream {

	protected final static String TAG = "VideoStream";

	protected final VideoQuality mQuality;
	protected final SharedPreferences mSettings;

	private final PipedOutputStream mOutStream;

    @SuppressLint({ "InlinedApi", "NewApi" })
    FrameBasedVideoStream(final SharedPreferences prefs, final VideoQuality quality) throws IOException {
		super();
        mSettings = prefs;
        mQuality = quality;
        mOutStream = new PipedOutputStream();;
	}

	public void addFrame(final byte[] frame) {
        try {
            mOutStream.write(frame, 0, frame.length);
            mOutStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint({ "InlinedApi", "NewApi" })
	protected void encodeWithMediaCodec() throws IOException {
        PipedInputStream in = new PipedInputStream();
        in.connect(mOutStream);
        mPacketizer.setInputStream(in);
        mPacketizer.start();

        mStreaming = true;
	}

	/**
	 * Returns a description of the stream using SDP. 
	 * This method can only be called after {@link Stream#configure()}.
	 * @throws IllegalStateException Thrown when {@link Stream#configure()} wa not called.
	 */	
	public abstract String getSessionDescription() throws IllegalStateException;

}
