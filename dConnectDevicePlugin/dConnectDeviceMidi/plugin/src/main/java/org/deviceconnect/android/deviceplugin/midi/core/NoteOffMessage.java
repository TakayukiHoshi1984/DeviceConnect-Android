package org.deviceconnect.android.deviceplugin.midi.core;

import java.nio.ByteBuffer;

/**
 * ノート・オフ・メッセージ.
 */
public class NoteOffMessage extends ChannelVoiceMessage {

    public static final int MESSAGE_TYPE = 0b1000;

    private static final int MASK_NOTE_NUMBER = 0x7F;

    private static final int MASK_VELOCITY = 0x7F;

    private int mNoteNumber;

    private int mVelocity;

    /**
     * コンストラクタ.
     * @param message MIDI メッセージのバイト配列
     * @param offset MIDI メッセージの先頭位置
     * @param length MIDI メッセージの長さ
     */
    NoteOffMessage(final byte[] message, final int offset, final int length) {
        super(message, offset, length);
        mNoteNumber = message[offset + 1] & MASK_NOTE_NUMBER;
        mVelocity = message[offset + 2] & MASK_VELOCITY;
    }

    private NoteOffMessage() {}

    public int getNoteNumber() {
        return mNoteNumber;
    }

    public int getVelocity() {
        return mVelocity;
    }

    @Override
    public void append(final ByteBuffer buffer) {
        buffer.put(getStatusByte());
        buffer.put((byte) (mNoteNumber & 0x7F));
        buffer.put((byte) (mVelocity & 0x7F));
    }

    public static class Builder extends ChannelVoiceMessage.Builder {

        private int mNoteNumber;

        private int mVelocity;

        public Builder setNoteNumber(final int noteNumber) {
            mNoteNumber = noteNumber;
            return this;
        }

        public Builder setVelocity(final int velocity) {
            mVelocity = velocity;
            return this;
        }

        public Builder setChannelNumber(final int channelNumber) {
            mChannelNumber = channelNumber;
            return this;
        }

        public NoteOffMessage build() {
            NoteOffMessage m = new NoteOffMessage();
            m.mMessageType = MESSAGE_TYPE;
            m.mChannelNumber = mChannelNumber;
            m.mNoteNumber = mNoteNumber;
            m.mVelocity = mVelocity;
            return m;
        }
    }


}
