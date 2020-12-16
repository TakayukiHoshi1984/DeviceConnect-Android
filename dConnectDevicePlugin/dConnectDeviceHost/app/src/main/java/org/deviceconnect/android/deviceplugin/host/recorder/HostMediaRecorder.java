/*
 HostMediaRecorder.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.recorder;

import android.util.Range;
import android.util.Size;

import org.deviceconnect.android.deviceplugin.host.recorder.util.PropertyUtil;
import org.deviceconnect.android.libmedia.streaming.gles.EGLSurfaceDrawingThread;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Host プラグインで使用する MediaRecorder のインターフェース.
 *
 * @author NTT DOCOMO, INC.
 */
public interface HostMediaRecorder {
    /**
     * MediaRecorder で使用するデフォルトのマイムタイプ.
     */
    String MIME_TYPE_JPEG = "image/jpeg";

    /**
     * MediaRecorder の初期化処理を行います.
     */
    void initialize();

    /**
     * プロセス起動時の状態に戻す.
     *
     * プラグイン再起動時に呼び出すこと.
     */
    void clean();

    /**
     * オブジェクトを破棄する.
     *
     * プロセス終了時に呼び出すこと.
     */
    void destroy();

    /**
     * MediaRecorder を識別する ID を取得します.
     *
     * @return MediaRecorder を識別する ID
     */
    String getId();

    /**
     * MediaRecorder の名前を取得します.
     *
     * @return MediaRecorder の名前
     */
    String getName();

    /**
     * MediaRecorder のマイムタイプを取得します.
     *
     * @return マイムタイプ
     */
    String getMimeType();

    /**
     * サポートしているマイムタイプを取得します.
     *
     * @return サポートしているマイムタイプ
     */
    List<String> getSupportedMimeTypes();

    /**
     * MediaRecorder の状態を取得します.
     *
     * @return MediaRecorder の状態
     */
    State getState();

    /**
     * HostMediaRecorder の設定を取得します.
     *
     * @return HostMediaRecorder の設定
     */
    Settings getSettings();

    /**
     * プレビュー配信サーバの管理クラスを取得します.
     *
     * @return プレビュー配信サーバ
     */
    PreviewServerProvider getServerProvider();

    /**
     * プレビュー配信管理クラスを取得します.
     *
     * @return BroadcasterProvider の実装クラス
     */
    BroadcasterProvider getBroadcasterProvider();

    // テスト
    EGLSurfaceDrawingThread getSurfaceDrawingThread();

    /**
     * 端末の画面が回転したタイミングで実行されるメソッド.
     * @param degree 角度を示す定数
     */
    void onDisplayRotation(int degree);

    /**
     * パーミッションの要求結果を通知するコールバックを設定します.
     *
     * @param callback コールバック
     */
    void requestPermission(PermissionCallback callback);

    /**
     * パーミッション結果通知用コールバック.
     */
    interface PermissionCallback {
        /**
         * 許可された場合に呼び出されます.
         */
        void onAllowed();

        /**
         * 拒否された場合に呼び出されます.
         */
        void onDisallowed();
    }

    /**
     * MediaRecorder の状態.
     */
    enum State {
        /**
         * 動作していない.
         */
        INACTIVE,

        /**
         * 録画が一時停止中の状態.
         */
        PAUSED,

        /**
         * 録画・静止画撮影中の状態.
         */
        RECORDING,

        /**
         * プレビューが表示されている状態.
         */
        PREVIEW,

        /**
         * エラーで停止している状態.
         */
        ERROR
    }

    class Settings {
        // 映像
        private boolean mVideoEnabled = true;
        private Size mPictureSize;
        private Size mPreviewSize;
        private Integer mPreviewMaxFrameRate = 30;
        private Integer mPreviewBitRate = 2 * 1024 * 1024;
        private Integer mPreviewKeyFrameInterval = 1;
        private Integer mPreviewWhiteBalance = 0;
        private Range<Integer> mFps;
        private Integer mPreviewQuality = 80;
        private String mPreviewMimeType = "video/avc";

        // 音声
        private boolean mAudioEnabled;
        private Integer mPreviewAudioBitRate = 64 * 1024;
        private Integer mPreviewSampleRate = 8000;
        private Integer mPreviewChannel = 1;
        private boolean mUseAEC = true;
        private String mAudioMimeType = "audio/aac";

        // ポート
        private Map<String, Integer> mPorts = new HashMap<>();

        // サポート範囲
        private List<Size> mSupportedPictureSizes;
        private List<Size> mSupportedPreviewSizes;
        private List<Range<Integer>> mSupportedFps;
        private List<Integer> mSupportedWhiteBalances;

        /**
         * 設定データを読み込みます.
         *
         * @param file 設定データが格納されたファイル
         */
        public boolean load(File file) {
            try {
                PropertyUtil property = new PropertyUtil();
                property.load(file);

                // 映像
                mVideoEnabled = property.getBoolean("video_enabled", true);
                mPictureSize = property.getSize("picture_size_width","picture_size_height");
                mPreviewSize = property.getSize("preview_size_width", "preview_size_height");
                mPreviewMaxFrameRate = property.getInteger("preview_framerate", 30);
                mPreviewBitRate = property.getInteger("preview_bitrate", 2 * 1024 * 1024);
                mPreviewKeyFrameInterval = property.getInteger("preview_i_frame_interval", 1);
                mFps = property.getRange("picture_fps_min", "picture_fps_max");
                mPreviewWhiteBalance = property.getInteger("preview_white_balance", 0);
                mPreviewQuality = property.getInteger("preview_quality", 80);
                mPreviewMimeType = property.getString("preview_mime_type", "video/avc");

                // 音声
                mAudioEnabled = property.getBoolean("audio_enabled", false);
                mPreviewAudioBitRate = property.getInteger("preview_audio_bitrate", 64 * 1024);
                mPreviewSampleRate = property.getInteger("preview_audio_sample_rate", 8000);
                mPreviewChannel = property.getInteger("preview_audio_channel", 1);
                mUseAEC = property.getBoolean("preview_audio_aec", false);
                mAudioMimeType = property.getString("preview_audio_mime_type", "audio/aac");

                // ポート
                for (String key : property.getKeys()) {
                    if (key.startsWith("preview_port_")) {
                        String k = key.substring("preview_port_".length());
                        mPorts.put(k, property.getInteger(key, 0));
                    }
                }

                return true;
            } catch (IOException e) {
                return false;
            }
        }

        /**
         * 設定データを書き込みます.
         *
         * @param file 設定データを書き込むファイル
         */
        public void save(File file) {
            try {
                PropertyUtil property = new PropertyUtil();

                // 映像
                property.put("video_enabled", mVideoEnabled);
                property.put("picture_size_width", "picture_size_height", mPictureSize);
                property.put("preview_size_width", "preview_size_height", mPreviewSize);
                property.put("preview_framerate", mPreviewMaxFrameRate);
                property.put("preview_bitrate", mPreviewBitRate);
                property.put("preview_i_frame_interval", mPreviewKeyFrameInterval);
                if (mFps != null) {
                    property.put("picture_fps_min", "picture_fps_max", mFps);
                }
                if (mPreviewWhiteBalance != null) {
                    property.put("preview_white_balance", mPreviewWhiteBalance);
                }
                property.put("preview_quality", mPreviewQuality);
                property.put("preview_mime_type", mPreviewMimeType);

                // 音声
                property.put("audio_enabled", mAudioEnabled);
                property.put("preview_audio_bitrate", mPreviewAudioBitRate);
                property.put("preview_audio_sample_rate", mPreviewSampleRate);
                property.put("preview_audio_channel", mPreviewChannel);
                property.put("preview_audio_aec", mUseAEC);
                property.put("preview_audio_mime_type", mAudioMimeType);

                // ポート
                for (String key : mPorts.keySet()) {
                    Integer value = mPorts.get(key);
                    if (value != null) {
                        property.put("preview_port_" + key, String.valueOf(value));
                    }
                }

                property.save(file);
            } catch (IOException e) {
                // ignore.
            }
        }

        /**
         * プレビューの配信エンコードのマイムタイプを取得します.
         *
         * @return プレビューの配信エンコードのマイムタイプ
         */
        public String getPreviewMimeType() {
            return mPreviewMimeType;
        }

        /**
         * プレビューの配信エンコードのマイムタイプを設定します.
         *
         * @param mimeType プレビューの配信エンコードのマイムタイプ
         */
        public void setPreviewMimeType(String mimeType) {
            mPreviewMimeType = mimeType;
        }

        /**
         * 写真サイズを取得します.
         *
         * @return 写真サイズ
         */
        public Size getPictureSize() {
            return mPictureSize;
        }

        /**
         * 写真サイズを設定します.
         *
         * サポートされていない写真サイズの場合は IllegalArgumentException を発生させます。
         *
         * @param pictureSize 写真サイズ
         */
        public void setPictureSize(Size pictureSize) {
            if (!isSupportedPictureSize(pictureSize)) {
                throw new IllegalArgumentException("pictureSize is not supported.");
            }
            mPictureSize = pictureSize;
        }

        /**
         * プレビューサイズを取得します.
         *
         * @return プレビューサイズ
         */
        public Size getPreviewSize() {
            return mPreviewSize;
        }

        /**
         * プレビューサイズを設定します.
         *
         * サポートされていないプレビューサイズの場合は IllegalArgumentException を発生させます。
         *
         * @param previewSize プレビューサイズ
         */
        public void setPreviewSize(Size previewSize) {
            if (!isSupportedPreviewSize(previewSize)) {
                throw new IllegalArgumentException("previewSize is not supported.");
            }
            mPreviewSize = previewSize;
        }

        /**
         * フレームレートを取得します.
         *
         * @return フレームレート
         */
        public int getPreviewMaxFrameRate() {
            return mPreviewMaxFrameRate;
        }

        /**
         * フレームレートを設定します.
         *
         * @param previewMaxFrameRate フレームレート
         */
        public void setPreviewMaxFrameRate(Integer previewMaxFrameRate) {
            if (previewMaxFrameRate <= 0) {
                throw new IllegalArgumentException("previewMaxFrameRate is zero or negative.");
            }
            mPreviewMaxFrameRate = previewMaxFrameRate;
        }

        /**
         * ビットレートを取得します.
         *
         * @return ビットレート(byte)
         */
        public int getPreviewBitRate() {
            return mPreviewBitRate;
        }

        /**
         * ビットレートを設定します.
         *
         * @param previewBitRate ビットレート(byte)
         */
        public void setPreviewBitRate(int previewBitRate) {
            if (previewBitRate <= 0) {
                throw new IllegalArgumentException("previewBitRate is zero or negative.");
            }
            mPreviewBitRate = previewBitRate;
        }

        /**
         * キーフレームインターバルを取得します.
         *
         * @return キーフレームを発行する間隔(ミリ秒)
         */
        public int getPreviewKeyFrameInterval() {
            return mPreviewKeyFrameInterval;
        }

        /**
         * キーフレームインターバルを設定します.
         *
         * @param previewKeyFrameInterval キーフレームを発行する間隔(ミリ秒)
         */
        public void setPreviewKeyFrameInterval(int previewKeyFrameInterval) {
            if (previewKeyFrameInterval <= 0) {
                throw new IllegalArgumentException("previewKeyFrameInterval is zero or negative.");
            }
            mPreviewKeyFrameInterval = previewKeyFrameInterval;
        }

        /**
         * プレビューの品質を取得します.
         *
         * @return プレビューの品質
         */
        public int getPreviewQuality() {
            return mPreviewQuality;
        }

        /**
         * プレビューの品質を設定します.
         *
         * 0 から 100 の間で設定することができます。
         * それ以外は例外が発生します。
         *
         * @param quality プレビューの品質
         */
        public void setPreviewQuality(int quality) {
            if (quality < 0) {
                throw new IllegalArgumentException("quality is negative value.");
            }
            if (quality > 100) {
                throw new IllegalArgumentException("quality is over 100.");
            }
            mPreviewQuality = quality;
        }

        /**
         * 設定されている FPS を取得します.
         *
         * @return FPS
         */
        public Range<Integer> getFps() {
            return mFps;
        }

        /**
         * FPS を設定します.
         *
         * @param fps FPS
         */
        public void setFps(Range<Integer> fps) {
            if (fps != null && !isSupportedFps(fps)) {
                throw new IllegalArgumentException("fps is unsupported value.");
            }
            mFps = fps;
        }

        /**
         * ホワイトバランスの設定を取得します.
         *
         * @return ホワイトバランス
         */
        public Integer getPreviewWhiteBalance() {
            return mPreviewWhiteBalance;
        }

        /**
         * ホワイトバランスを設定します.
         *
         * @param whiteBalance ホワイトバランス
         */
        public void setPreviewWhiteBalance(Integer whiteBalance) {
            if (!isSupportedWhiteBalance(whiteBalance)) {
                throw new IllegalArgumentException("whiteBalance is unsupported value.");
            }
            mPreviewWhiteBalance = whiteBalance;
        }

        /**
         * ポート番号を取得します.
         *
         * @param key ポート番号のキー
         * @return ポート番号
         */
        public Integer getPort(String key) {
            return mPorts.get(key);
        }

        /**
         * ポート番号を設定します.
         *
         * @param key ポート番号のキー
         * @param port ポート番号
         */
        public void setPort(String key, Integer port) {
            mPorts.put(key, port);
        }

        /**
         * サポートしている写真サイズを取得します.
         *
         * @return サポートしている写真サイズ
         */
        public List<Size> getSupportedPictureSizes() {
            return mSupportedPictureSizes;
        }

        /**
         * サポートしている写真サイズを設定します.
         *
         * @param sizes サポートサイズ
         */
        public void setSupportedPictureSizes(List<Size> sizes) {
            mSupportedPictureSizes = sizes;
        }

        /**
         * サポートしているプレビューサイズを取得します.
         *
         * @return サポートしているプレビューサイズ
         */
        public List<Size> getSupportedPreviewSizes() {
            return mSupportedPreviewSizes;
        }

        /**
         * サポートしているプレビューサイズを設定します.
         *
         * @param sizes サポートサイズ
         */
        public void setSupportedPreviewSizes(List<Size> sizes) {
            mSupportedPreviewSizes = sizes;
        }

        /**
         * サポートしている FPS のリストを取得します.
         *
         * @return サポートしている FPS のリスト
         */
        public List<Range<Integer>> getSupportedFps() {
            return mSupportedFps;
        }

        /**
         * サポートしている FPS のリストを設定します.
         *
         * @param fps サポートしている FPS のリスト
         */
        public void setSupportedFps(List<Range<Integer>> fps) {
            mSupportedFps = fps;
        }

        /**
         * サポートしているホワイトバランスのリストを取得します.
         *
         * @return サポートしているホワイトバランスのリスト
         */
        public List<Integer> getSupportedWhiteBalances() {
            return mSupportedWhiteBalances;
        }

        /**
         * サポートしているホワイトバランスのリストを設定します.
         *
         * @param whiteBalances サポートしているホワイトバランスのリスト
         */
        public void setSupportedWhiteBalances(List<Integer> whiteBalances) {
            mSupportedWhiteBalances = whiteBalances;
        }

        /**
         * 指定されたサイズがサポートされているか確認します.
         *
         * @param size 確認するサイズ
         * @return サポートされている場合はtrue、それ以外はfalse
         */
        public boolean isSupportedPictureSize(final Size size) {
            for (Size s : mSupportedPictureSizes) {
                if (s.getWidth() == size.getWidth() &&
                        s.getHeight() == size.getHeight()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 指定されたサイズが静止画でサポートされているか確認します.
         *
         * @param width 横幅
         * @param height 縦幅
         * @return サポートされている場合はtrue、それ以外はfalse
         */
        public boolean isSupportedPictureSize(int width, int height) {
            return isSupportedPictureSize(new Size(width, height));
        }

        /**
         * 指定されたサイズがサポートされているか確認します.
         *
         * @param size 確認するサイズ
         * @return サポートされている場合はtrue、それ以外はfalse
         */
        public boolean isSupportedPreviewSize(final Size size) {
            for (Size s : mSupportedPreviewSizes) {
                if (s.getWidth() == size.getWidth() &&
                        s.getHeight() == size.getHeight()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 指定されたサイズがプレビューでサポートされているか確認します.
         *
         * @param width 横幅
         * @param height 縦幅
         * @return サポートされている場合はtrue、それ以外はfalse
         */
        public boolean isSupportedPreviewSize(int width, int height) {
            return isSupportedPreviewSize(new Size(width, height));
        }

        /**
         * 指定されたサイズがサポートされているか確認します.
         *
         * @param fps 確認する FPS
         * @return サポートされている場合はtrue、それ以外はfalse
         */
        public boolean isSupportedFps(Range<Integer> fps) {
            for (Range<Integer> r : mSupportedFps) {
                if (r.getLower().equals(fps.getLower()) &&
                        r.getUpper().equals(fps.getUpper())){
                    return true;
                }
            }
            return false;
        }

        public boolean isSupportedWhiteBalance(int whiteBalance) {
            for (Integer wb : mSupportedWhiteBalances) {
                if (wb == whiteBalance) {
                    return true;
                }
            }
            return false;
        }

        // 音声

        /**
         * プレビュー音声が有効化確認します.
         *
         * @return プレビュー音声が有効の場合はtrue、それ以外はfalse
         */
        public boolean isAudioEnabled() {
            return mAudioEnabled;
        }

        /**
         * プレビュー音声が有効化を設定します.
         *
         * @param enabled プレビュー音声が有効の場合はtrue、それ以外はfalse
         */
        public void setAudioEnabled(boolean enabled) {
            mAudioEnabled = enabled;
        }

        /**
         * プレビュー音声のビットレートを取得します.
         *
         * @return プレビュー音声のビットレート
         */
        public int getPreviewAudioBitRate() {
            return mPreviewAudioBitRate;
        }

        /**
         * プレビュー音声のビットレートを設定します.
         *
         * @param bitRate プレビュー音声のビットレート
         */
        public void setPreviewAudioBitRate(int bitRate) {
            if (bitRate <= 0) {
                throw new IllegalArgumentException("previewAudioBitRate is zero or negative value.");
            }
            mPreviewAudioBitRate = bitRate;
        }

        /**
         * プレビュー音声のサンプルレートを取得します.
         *
         * @return プレビュー音声のサンプルレート
         */
        public int getPreviewSampleRate() {
            return mPreviewSampleRate;
        }

        /**
         * プレビュー音声のサンプルレートを設定します.
         *
         * @param sampleRate プレビュー音声のサンプルレート
         */
        public void setPreviewSampleRate(int sampleRate) {
            mPreviewSampleRate = sampleRate;
        }

        /**
         * プレビュー音声のチャンネル数を取得します.
         *
         * @return プレビュー音声のチャンネル数
         */
        public int getPreviewChannel() {
            return mPreviewChannel;
        }

        /**
         * プレビュー音声のチャンネル数を設定します.
         *
         * @param channel プレビュー音声のチャンネル数
         */
        public void setPreviewChannel(int channel) {
            mPreviewChannel = channel;
        }

        /**
         * プレビュー配信のエコーキャンセラーを取得します.
         *
         * @return プレビュー配信のエコーキャンセラー
         */
        public boolean isUseAEC() {
            return mUseAEC;
        }

        /**
         * プレビュー配信のエコーキャンセラーを設定します.
         *
         * @param used プレビュー配信のエコーキャンセラー
         */
        public void setUseAEC(boolean used) {
            mUseAEC = used;
        }

        public Integer getMjpegPort() {
            return mPorts.get("mjpeg_port");
        }

        public void setMjpegPort(int port) {
            mPorts.put("mjpeg_port", port);
        }

        public Integer getMjpegSSLPort() {
            return mPorts.get("mjpeg_ssl_port");
        }

        public void setMjpegSSLPort(int port) {
            mPorts.put("mjpeg_ssl_port", port);
        }

        public Integer getRtspPort() {
            return mPorts.get("rtsp_port");
        }

        public void setRtspPort(int port) {
            mPorts.put("rtsp_port", port);
        }

        public Integer getSrtPort() {
            return mPorts.get("srt_port");
        }

        public void setSrtPort(int port) {
            mPorts.put("srt_port", port);
        }
    }
}
