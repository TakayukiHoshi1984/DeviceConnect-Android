/*
 TestHealthProfileConstants.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.test.plugin.profile;

import org.deviceconnect.profile.HealthProfileConstants;

/**
 * JUnit用テストデバイスプラグイン、Healthプロファイル.
 * @author NTT DOCOMO, INC.
 */
public interface TestHealthProfileConstants extends HealthProfileConstants {
    /**
     * テスト用心拍数の値.
     * <p>
     * dConnectDeviceTestプロジェクトにあるTestHealthProfileに同じ値が定義してある。
     */
    int TEST_HEART_RATE = 81;
}
