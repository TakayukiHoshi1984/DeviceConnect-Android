/*
 DConnectSDKFactoryTest.java
 Copyright (c) 2016 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.message;

import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * DConnectSDKFactoryのテスト.
 *
 * @author NTT DOCOMO, INC.
 */
@RunWith(RobolectricTestRunner.class)
@Config(maxSdk = Build.VERSION_CODES.P)
public class DConnectSDKFactoryTest {

    /**
     * contextにnullを設定して、DConnectSDKのインスタンスを作成する。
     * <pre>
     * 【期待する動作】
     * ・NullPointerExceptionが発生すること。
     * </pre>
     */
    @Test(expected = NullPointerException.class)
    public void create_context_null() {
        DConnectSDKFactory.create(null, DConnectSDKFactory.Type.HTTP);
    }

    /**
     * typeにnullを設定して、DConnectSDKのインスタンスを作成する。
     * <pre>
     * 【期待する動作】
     * ・NullPointerExceptionが発生すること。
     * </pre>
     */
    @Test(expected = NullPointerException.class)
    public void create_type_null() {
        DConnectSDKFactory.create(InstrumentationRegistry.getInstrumentation().getContext(), null);
    }

    /**
     * typeに不正な文字列を設定して、DConnectSDKのインスタンスを作成する。
     * <pre>
     * 【期待する動作】
     * ・IllegalArgumentExceptionが発生すること。
     * </pre>
     */
    @Test(expected = IllegalArgumentException.class)
    public void create_type_invalid() {
        DConnectSDKFactory.create(InstrumentationRegistry.getInstrumentation().getContext(),
                                    DConnectSDKFactory.Type.valueOf("invalidType"));
    }

    /**
     * typeにDConnectSDKFactory.Type.HTTPを設定して、HttpDConnectSDKのインスタンスが作成されること。
     * <pre>
     * 【期待する動作】
     * ・HttpDConnectSDKのインスタンスが作成される。
     * </pre>
     */
    @Test
    public void create_type_http() {
        DConnectSDK sdk = DConnectSDKFactory.create(InstrumentationRegistry.getInstrumentation().getContext(),
                DConnectSDKFactory.Type.HTTP);
        assertThat(sdk.getClass().getName(), is(HttpDConnectSDK.class.getName()));
    }

    /**
     * typeにDConnectSDKFactory.Type.HTTPを設定して、HttpDConnectSDKのインスタンスが作成されること。
     * <pre>
     * 【期待する動作】
     * ・HttpDConnectSDKのインスタンスが作成される。
     * </pre>
     */
    @Test
    public void create_type_intent() {
        DConnectSDK sdk = DConnectSDKFactory.create(InstrumentationRegistry.getInstrumentation().getContext(),
                DConnectSDKFactory.Type.INTENT);
        assertThat(sdk.getClass().getName(), is(IntentDConnectSDK.class.getName()));
    }
}
