package org.deviceconnect.android.utils;

import org.robolectric.Robolectric;
import org.robolectric.util.Scheduler;

import java.util.concurrent.TimeUnit;

/**
 * 非同期処理のユーティリティクラス。
 */
public class AsyncUtils {

    public static void waitForBackgroundThreadCallback() {
        waitForBackgroundThreadCallback(0);
    }

    public static void waitForBackgroundThreadCallback(long timeout, TimeUnit unit) {
        waitForBackgroundThreadCallback(unit.toMillis(timeout));
    }

    public static void waitForBackgroundThreadCallback(long timeout) {
        waitForCallback(Robolectric.getBackgroundThreadScheduler(), timeout);
    }

    public static void waitForMainThreadCallback() {
        waitForMainThreadCallback(0);
    }

    public static void waitForMainThreadCallback(long timeout, TimeUnit unit) {
        waitForMainThreadCallback(unit.toMillis(timeout));
    }

    public static void waitForMainThreadCallback(long timeout) {
        waitForCallback(Robolectric.getForegroundThreadScheduler(), timeout);
    }

    /**
     * Robolectric のメインスレッドに Runnable が post されることを待ちます。
     *
     * Androidのメインスレッド(Handler)などに post された Runnable を実行します。<br>
     * 2秒間だけ、メインスレッドに Runnable が登録されることを待ちます。<br>
     *
     * NOTE: この関数を呼び出さないとメインスレッドが動作しませんのでご注意ください。
     */
    public static void waitForMainThreadPostedRunnable() {
        waitForMainThreadCallback(2, TimeUnit.SECONDS);
    }

    /**
     * 指定されたスケジューラに post された Runnable を実行します。
     *
     * @param scheduler Robolectricのスケジューラ
     * @param timeout タイムアウト時間
     */
    private static void waitForCallback(Scheduler scheduler, long timeout) {
        try {
            long expirationDate = System.currentTimeMillis() + timeout;
            while (!scheduler.advanceToNextPostedRunnable()) {
                Thread.sleep(100);

                if (timeout != 0 && expirationDate < System.currentTimeMillis()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            // ignore.
        }
    }
}