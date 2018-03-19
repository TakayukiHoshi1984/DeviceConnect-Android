/*
 PreviewServer.java
 Copyright (c) 2017 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.recorder;


public interface PreviewServer {

    String getMimeType();

    /**
     * サーバを開始します.
     * @param callback 開始結果を通知するコールバック
     */
    void startWebServer(OnWebServerStartCallback callback);

    /**
     * サーバを停止します.
     */
    void stopWebServer();

    /**
     * Callback interface used to receive the result of starting a web server.
     */
    interface OnWebServerStartCallback {
        /**
         * Called when a web server successfully started.
         *
         * @param uri An ever-updating, static image URI.
         */
        void onStart(String uri);

        /**
         * Called when a web server failed to start.
         */
        void onFail();
    }
}
