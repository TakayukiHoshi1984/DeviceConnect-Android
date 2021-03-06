/*
 HostDeviceStreamRecorder.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.recorder;

/**
 * 録画・録音を行うためのインターフェース.
 *
 * @author NTT DOCOMO, INC.
 */
public interface HostDeviceStreamRecorder {

    /**
     * 録画の一時停止がサポートされているか確認します.
     *
     * @return 録画の一時停止ができる場合はtrue、それ以外はfalse
     */
    boolean canPauseRecording();

    /**
     * 録画を開始します.
     *
     * @param callback コールバック
     */
    void startRecording(RecordingCallback callback);

    /**
     * 録画を停止します.
     *
     * @param callback コールバック
     */
    void stopRecording(StoppingCallback callback);

    /**
     * 録画を一時停止します.
     *
     * {@link #canPauseRecording()} が false の場合には使用することができません。
     */
    void pauseRecording();

    /**
     * 一時停止した録画を再開します.
     *
     * {@link #canPauseRecording()} が false の場合には使用することができません。
     */
    void resumeRecording();

    /**
     * 録画を行うマイムタイプを定義します.
     *
     * @return マイムタイプ
     */
    String getStreamMimeType();

    /**
     * 録画の音声をミュートにします.
     */
    void muteTrack();

    /**
     * 録画の音声のミュートを解除します.
     */
    void unMuteTrack();

    /**
     * 録画の音声をミュート状態を取得します.
     *
     * @return ミュートの場合はtrue、それ以外はfalse
     */
    boolean isMutedTrack();

    /**
     * 録画開始結果を通知するコールバック.
     */
    interface RecordingCallback {
        /**
         * 録画開始に成功したことを通知します.
         *
         * @param recorder 録画を開始したレコーダ
         * @param fileName 録画しているファイル名
         */
        void onRecorded(HostDeviceStreamRecorder recorder, String fileName);

        /**
         * 録画開始に失敗したことを通知します.
         *
         * @param recorder 録画開始に失敗したレコーダ
         * @param errorMessage エラーメッセージ
         */
        void onFailed(HostDeviceStreamRecorder recorder, String errorMessage);
    }

    /**
     * 録画停止結果を通知するコールバック.
     */
    interface StoppingCallback {
        /**
         * 録画停止に成功したことを通知します.
         *
         * @param recorder 録画を停止したレコーダ
         * @param fileName 録画したファイル名
         */
        void onStopped(HostDeviceStreamRecorder recorder, String fileName);

        /**
         * 録画停止に失敗したことを通知します.
         *
         * @param recorder 録画停止に失敗したレコーダ
         * @param errorMessage エラーメッセージ
         */
        void onFailed(HostDeviceStreamRecorder recorder, String errorMessage);
    }

    /**
     * 録画・録音のイベントを通知するリスナー.
     */
    interface OnEventListener {
        /**
         * 録画・録音を開始したことを通知します.
         *
         * @param fileName ファイル名
         */
        void onRecordingStarted(String fileName);

        /**
         * 録画・録音を一時停止したことを通知します.
         */
        void onRecordingPause();

        /**
         * 録画・録音を再開したことを通知します.
         */
        void onRecordingResume();

        /**
         * 録画・録音を停止したことを通知します.
         *
         * @param fileName ファイル名
         */
        void onRecordingStopped(String fileName);
    }
}
