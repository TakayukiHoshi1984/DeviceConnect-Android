/*
 IntentDConnectSDKTest.java
 Copyright (c) 2016 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.message;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;

import org.deviceconnect.message.entity.FileEntity;
import org.deviceconnect.message.entity.MultipartEntity;
import org.deviceconnect.message.entity.StringEntity;
import org.deviceconnect.message.intent.message.IntentDConnectMessage;
import org.deviceconnect.message.util.AsyncUtils;
import org.deviceconnect.profile.AuthorizationProfileConstants;
import org.deviceconnect.profile.AvailabilityProfileConstants;
import org.deviceconnect.profile.DConnectProfileConstants;
import org.deviceconnect.profile.DeviceOrientationProfileConstants;
import org.deviceconnect.profile.ServiceDiscoveryProfileConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.util.concurrent.RoboExecutorService;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static android.os.Looper.getMainLooper;
import static junit.framework.Assert.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

/**
 * IntentDConnectSDKのテスト.
 *
 * @author NTT DOCOMO, INC.
 */
@RunWith(RobolectricTestRunner.class)
@Config(maxSdk = Build.VERSION_CODES.P)
public class IntentDConnectSDKTest {


    private DConnectSDK getSDK(BroadcastReceiver receiver) {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        context.registerReceiver(receiver, new IntentFilter());
        String packageName = context.getPackageName();
        DConnectSDK sdk = DConnectSDKFactory.create(context, DConnectSDKFactory.Type.INTENT);
        IntentDConnectSDK intentSDK = (IntentDConnectSDK)sdk;
        intentSDK.setManagerPackageName(packageName);
        intentSDK.setManagerClassName(receiver.getClass().getName());
        return intentSDK;
    }

    private void writeFile(final File file, final byte[] data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(data);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private byte[] getFile(final File file) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            int len;
            byte[] buf = new byte[1024];
            while ((len = fis.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            fis.close();
        } catch (IOException e) {
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return out.toByteArray();
    }

    private void waitForResponseAsync(final int threadNest) {
        new Thread(() -> {
            for (int i = 0; i < threadNest; i++) {
                AsyncUtils.waitForMainThreadCallback();
            }
        }).start();
    }
    /**
     * availabilityを呼び出し、レスポンスを受け取れることを確認する。
     * <pre>
     * 【期待する動作】
     * ・DConnectResponseMessageが返却されること。
     * ・resultに0が返却されること。
     * ・productにtest-managerが返却されること。
     * ・versionに1.1が返却されること。
     * ・nameにmanagerが返却されること。
     * ・uuidにuuidが返却されること。
     * </pre>
     */
    @Test
    public void availability() {
        final String version = "1.1";
        final String product = "test";
        final String name = "name";
        final String uuid = "uuid";

        BroadcastReceiver receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();

                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                if (!method.equals(IntentDConnectMessage.ACTION_GET)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                } else {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                    response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                    response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                    response.putExtra(AvailabilityProfileConstants.PARAM_NAME, name);
                    response.putExtra(AvailabilityProfileConstants.PARAM_UUID, uuid);
                }

                context.sendBroadcast(response);
            }
        };
        DConnectSDK sdk = getSDK(receiver);
        DConnectResponseMessage response = sdk.availability();
        assertThat(response.getResult(), is(DConnectMessage.RESULT_OK));
        assertThat(response.getString(DConnectProfileConstants.PARAM_VERSION), is(version));
        assertThat(response.getString(DConnectProfileConstants.PARAM_PRODUCT), is(product));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_NAME), is(name));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_UUID), is(uuid));
    }

    /**
     * 非同期にavailabilityを呼び出し、レスポンスを受け取れることを確認する。
     * <pre>
     * 【期待する動作】
     * ・DConnectResponseMessageが返却されること。
     * ・resultに0が返却されること。
     * ・productにtest-managerが返却されること。
     * ・versionに1.1が返却されること。
     * ・nameにmanagerが返却されること。
     * ・uuidにuuidが返却されること。
     * </pre>
     */
    @Test
    public void availability_listener() {
        final String version = "1.1";
        final String product = "test";
        final String name = "name";
        final String uuid = "uuid";
        final CountDownLatch latch = new CountDownLatch(1);

        final AtomicReference<DConnectResponseMessage> result = new AtomicReference<>();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();
                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                if (!method.equals(IntentDConnectMessage.ACTION_GET)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                } else {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                    response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                    response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                    response.putExtra(AvailabilityProfileConstants.PARAM_NAME, name);
                    response.putExtra(AvailabilityProfileConstants.PARAM_UUID, uuid);
                }
                context.sendBroadcast(response);
            }
        };
        DConnectSDK sdk = getSDK(receiver);
        sdk.availability((response) -> {
            result.set(response);
            latch.countDown();
        });
        waitForResponseAsync(1);
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("timeout");
        }
        DConnectResponseMessage response = result.get();
        assertThat(response, is(notNullValue()));
        assertThat(response.getResult(), is(DConnectMessage.RESULT_OK));
        assertThat(response.getString(DConnectProfileConstants.PARAM_VERSION), is(version));
        assertThat(response.getString(DConnectProfileConstants.PARAM_PRODUCT), is(product));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_NAME), is(name));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_UUID), is(uuid));
    }



    /**
     * authorizationを呼び出し、レスポンスを受け取れることを確認する。
     * <pre>
     * 【期待する動作】
     * ・OnResponseListenerにDConnectResponseMessageが返却されること。
     * ・resultに0が返却されること。
     * ・versionに1.1が返却されること。
     * ・accessTokenにtest-accessTokeが返却されること。
     * ・expireに1999が返却されること。
     * ・scopesに配列が返却されること。
     * </pre>
     */
    @Test
    public void authorization() {
        final String appName = "test";
        final String version = "1.1";
        final String product = "test-manager";
        final String clientId = "test-clientId";
        final String accessToken = "test-accessToken";
        final String profile = "battery";
        final int expirePeriod = 1000;
        final int expire = 1999;
        final String[] scopes = {
                "serviceDiscovery",
                "serviceInformation",
                "battery"
        };

        BroadcastReceiver receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();

                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                if (!method.equals(IntentDConnectMessage.ACTION_GET)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                } else {
                    String p = intent.getStringExtra(IntentDConnectMessage.EXTRA_PROFILE);
                    String a = intent.getStringExtra(IntentDConnectMessage.EXTRA_ATTRIBUTE);

                    if (!AuthorizationProfileConstants.PROFILE_NAME.equalsIgnoreCase(p)) {
                        response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                        response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                        response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                    } else if (AuthorizationProfileConstants.ATTRIBUTE_GRANT.equalsIgnoreCase(a)) {
                        response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                        response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                        response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                        response.putExtra(AuthorizationProfileConstants.PARAM_CLIENT_ID, clientId);
                    } else if (AuthorizationProfileConstants.ATTRIBUTE_ACCESS_TOKEN.equalsIgnoreCase(a)) {

                        String cid = intent.getStringExtra(AuthorizationProfileConstants.PARAM_CLIENT_ID);
                        if (!clientId.equals(cid)) {
                            response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                            response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                            response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                        } else {
                            List<Bundle> scopes = new ArrayList<>();

                            Bundle scope1 = new Bundle();
                            scope1.putString(AuthorizationProfileConstants.PARAM_SCOPE, profile);
                            scope1.putLong(AuthorizationProfileConstants.PARAM_EXPIRE_PERIOD, expirePeriod);
                            scopes.add(scope1);

                            Bundle[] s = new Bundle[scopes.size()];
                            scopes.toArray(s);

                            response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                            response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                            response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                            response.putExtra(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, accessToken);
                            response.putExtra(AuthorizationProfileConstants.PARAM_SCOPES, s);
                            response.putExtra(AuthorizationProfileConstants.PARAM_EXPIRE, expire);
                        }
                    }
                }

                context.sendBroadcast(response);
            }
        };

        DConnectSDK sdk = getSDK(receiver);
        DConnectResponseMessage response = sdk.authorization(appName, scopes);
        assertThat(response.getResult(), is(DConnectMessage.RESULT_OK));
        assertThat(response.getString(DConnectProfileConstants.PARAM_VERSION), is(version));
        assertThat(response.getString(DConnectProfileConstants.PARAM_PRODUCT), is(product));
        assertThat(response.getString(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN), is(accessToken));
        assertThat(response.getInt(AuthorizationProfileConstants.PARAM_EXPIRE), is(expire));
        assertThat(response.getList(AuthorizationProfileConstants.PARAM_SCOPES), is(notNullValue()));
    }

    /**
     * 非同期にauthorizationを呼び出し、clientIdとaccessTokenが取得できることを確認する。
     * <pre>
     * 【期待する動作】
     * ・OnResponseListenerにDConnectResponseMessageが返却されること。
     * ・resultに0が返却されること。
     * ・versionに1.1が返却されること。
     * ・accessTokenにtest-accessTokeが返却されること。
     * ・expireに1999が返却されること。
     * ・scopesに配列が返却されること。
     * </pre>
     */
    @Test
    public void authorization_listener() {
        final String appName = "test";
        final String version = "1.1";
        final String product = "test-manager";
        final String clientId = "test-clientId";
        final String accessToken = "test-accessToken";
        final String profile = "battery";
        final int expirePeriod = 1000;
        final int expire = 1999;
        final String[] scopes = {
                "serviceDiscovery",
                "serviceInformation",
                "battery"
        };
        final CountDownLatch latch = new CountDownLatch(1);

        final AtomicReference<String> resultClientId = new AtomicReference<>();
        final AtomicReference<String> resultAccessToken = new AtomicReference<>();
        BroadcastReceiver receiver  = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();

                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                if (!method.equals(IntentDConnectMessage.ACTION_GET)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                } else {
                    String p = intent.getStringExtra(IntentDConnectMessage.EXTRA_PROFILE);
                    String a = intent.getStringExtra(IntentDConnectMessage.EXTRA_ATTRIBUTE);

                    if (!AuthorizationProfileConstants.PROFILE_NAME.equalsIgnoreCase(p)) {
                        response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                        response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                        response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                    } else if (AuthorizationProfileConstants.ATTRIBUTE_GRANT.equalsIgnoreCase(a)) {
                        response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                        response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                        response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                        response.putExtra(AuthorizationProfileConstants.PARAM_CLIENT_ID, clientId);
                    } else if (AuthorizationProfileConstants.ATTRIBUTE_ACCESS_TOKEN.equalsIgnoreCase(a)) {

                        String cid = intent.getStringExtra(AuthorizationProfileConstants.PARAM_CLIENT_ID);
                        if (!clientId.equals(cid)) {
                            response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                            response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                            response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                        } else {
                            List<Bundle> scopes = new ArrayList<>();

                            Bundle scope1 = new Bundle();
                            scope1.putString(AuthorizationProfileConstants.PARAM_SCOPE, profile);
                            scope1.putLong(AuthorizationProfileConstants.PARAM_EXPIRE_PERIOD, expirePeriod);
                            scopes.add(scope1);

                            Bundle[] s = new Bundle[scopes.size()];
                            scopes.toArray(s);

                            response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                            response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                            response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                            response.putExtra(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, accessToken);
                            response.putExtra(AuthorizationProfileConstants.PARAM_SCOPES, s);
                            response.putExtra(AuthorizationProfileConstants.PARAM_EXPIRE, expire);
                        }
                    }
                }

                context.sendBroadcast(response);
            }
        };
        DConnectSDK sdk = getSDK(receiver);
        sdk.authorization(appName, scopes, new DConnectSDK.OnAuthorizationListener() {
            @Override
            public void onResponse(String clientId, String accessToken) {
                resultClientId.set(clientId);
                resultAccessToken.set(accessToken);
                latch.countDown();
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                latch.countDown();
            }
        });
        waitForResponseAsync(2);
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("timeout");
        }
        assertThat(resultClientId.get(), is(clientId));
        assertThat(resultAccessToken.get(), is(accessToken));
    }
    /**
     * パラメータにnullが設定された時にauthorizationを呼び出し、エラーコードが返ってくることを確認する。
     * <pre>
     * 【期待する動作】
     * ・errorCodeが通知されること。
     * </pre>
     */
    @Test
    public void authorization_parameter_null() {
        DConnectSDK sdk = DConnectSDKFactory.create(InstrumentationRegistry.getInstrumentation().getContext(), DConnectSDKFactory.Type.INTENT);
        DConnectResponseMessage response = sdk.authorization(null, null);
        assertEquals(response.getErrorCode(), DConnectMessage.ErrorCode.TIMEOUT.getCode());
    }

    /**
     * パラメータにnullが設定された時にauthorizationを呼び出し、エラーコードが返ってくることを確認する。
     * <pre>
     * 【期待する動作】
     * ・errorCodeが通知されること。
     * </pre>
     */
    @Test
    public void authorization_listener_parameter_null() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Integer> resultErrorCode = new AtomicReference<>();

        DConnectSDK sdk = DConnectSDKFactory.create(InstrumentationRegistry.getInstrumentation().getContext(), DConnectSDKFactory.Type.INTENT);
        sdk.authorization(null, null,new DConnectSDK.OnAuthorizationListener() {
            @Override
            public void onResponse(final String clientId, final String accessToken) {
                latch.countDown();
            }
            @Override
            public void onError(final int errorCode, final String errorMessage) {
                resultErrorCode.set(errorCode);
                latch.countDown();
            }
        });

        try {
            latch.await(60, TimeUnit.SECONDS); //リクエストタイムアウト時間
        } catch (InterruptedException e) {
            fail("timeout");
        }
        assertThat(resultErrorCode.get(), is(DConnectMessage.ErrorCode.TIMEOUT.getCode()));
    }
    /**
     * パラメータに空文字が設定された時にauthorizationを呼び出し、エラーコードが返ってくることを確認する。
     * <pre>
     * 【期待する動作】
     * ・errorCodeが通知されること。
     * </pre>
     */
    @Test
    public void authorization_parameter_empty_text() {
        DConnectSDK sdk = DConnectSDKFactory.create(InstrumentationRegistry.getInstrumentation().getContext(), DConnectSDKFactory.Type.INTENT);
        DConnectResponseMessage response = sdk.authorization("", new String[]{""});
        assertEquals(response.getErrorCode(), DConnectMessage.ErrorCode.TIMEOUT.getCode());
    }

    /**
     * パラメータにnullが設定された時にauthorizationを呼び出し、エラーコードが返ってくることを確認する。
     * <pre>
     * 【期待する動作】
     * ・errorCodeが通知されること。
     * </pre>
     */
    @Test
    public void authorization_listener_parameter_empty_text() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Integer> resultErrorCode = new AtomicReference<>();

        DConnectSDK sdk = DConnectSDKFactory.create(InstrumentationRegistry.getInstrumentation().getContext(), DConnectSDKFactory.Type.INTENT);
        sdk.authorization("", new String[]{""},new DConnectSDK.OnAuthorizationListener() {
            @Override
            public void onResponse(final String clientId, final String accessToken) {
                latch.countDown();
            }
            @Override
            public void onError(final int errorCode, final String errorMessage) {
                resultErrorCode.set(errorCode);
                latch.countDown();
            }
        });

        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("timeout");
        }
        assertThat(resultErrorCode.get(), is(DConnectMessage.ErrorCode.TIMEOUT.getCode()));
    }

    /**
     * serviceDiscoveryを呼び出し、レスポンスを受け取れることを確認する。
     * <pre>
     * 【期待する動作】
     * ・DConnectResponseMessageが返却されること。
     * ・resultに0が返却されること。
     * ・versionに1.1が返却されること。
     * ・servicesに配列が返却されること。
     * ・servicesの中身に指定されたデバイス情報が格納されていること。
     * </pre>
     */
    @Test
    public void serviceDiscovery() {
        final String version = "1.1";
        final String product = "test-manager";
        final String accessToken = "test-accessToken";
        final String[][] aservices = {
                {
                        "serviceId1",
                        "test-service1",
                        ServiceDiscoveryProfileConstants.NetworkType.WIFI.getValue(),
                        "true",
                        "config1"
                }
        };
        BroadcastReceiver receiver  = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();

                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                if (!method.equals(IntentDConnectMessage.ACTION_GET)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                } else {
                    String p = intent.getStringExtra(IntentDConnectMessage.EXTRA_PROFILE);

                    if (!ServiceDiscoveryProfileConstants.PROFILE_NAME.equalsIgnoreCase(p)) {
                        response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                        response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                        response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                    } else {
                        List<Bundle> services = new ArrayList<>();

                        for (String[] a : aservices) {
                            Bundle service = new Bundle();
                            service.putString(ServiceDiscoveryProfileConstants.PARAM_ID, a[0]);
                            service.putString(ServiceDiscoveryProfileConstants.PARAM_NAME, a[1]);
                            service.putString(ServiceDiscoveryProfileConstants.PARAM_TYPE, a[2]);
                            service.putBoolean(ServiceDiscoveryProfileConstants.PARAM_ONLINE, "true".equals(a[3]));
                            service.putString(ServiceDiscoveryProfileConstants.PARAM_CONFIG, a[4]);
                            services.add(service);
                        }

                        Bundle[] ss = new Bundle[services.size()];
                        services.toArray(ss);

                        response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                        response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                        response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                        response.putExtra(ServiceDiscoveryProfileConstants.PARAM_SERVICES, ss);
                    }
                }

                context.sendBroadcast(response);
            }
        };
        DConnectSDK sdk = getSDK(receiver);
        sdk.setAccessToken(accessToken);

        DConnectResponseMessage response = sdk.serviceDiscovery();
        assertThat(response.getResult(), is(DConnectMessage.RESULT_OK));
        assertThat(response.getList(ServiceDiscoveryProfileConstants.PARAM_SERVICES), is(notNullValue()));

        int idx = 0;
        for (Object obj : response.getList(ServiceDiscoveryProfileConstants.PARAM_SERVICES)) {
            Map service = (Map) obj;
            String id = (String) service.get(ServiceDiscoveryProfileConstants.PARAM_ID);
            String name = (String) service.get(ServiceDiscoveryProfileConstants.PARAM_NAME);
            assertThat(id, is(aservices[idx][0]));
            assertThat(name, is(aservices[idx][1]));
        }
    }

    /**
     * 非同期にserviceDiscoveryを呼び出し、レスポンスを受け取れることを確認する。
     * <pre>
     * 【期待する動作】
     * ・DConnectResponseMessageが返却されること。
     * ・resultに0が返却されること。
     * ・versionに1.1が返却されること。
     * ・servicesに配列が返却されること。
     * ・servicesの中身に指定されたデバイス情報が格納されていること。
     * </pre>
     */
    @Test
    public void serviceDiscovery_listener() {
        final String version = "1.1";
        final String product = "test-manager";
        final String accessToken = "test-accessToken";
        final String[][] aservices = {
                {
                        "serviceId1",
                        "test-service1",
                        ServiceDiscoveryProfileConstants.NetworkType.WIFI.getValue(),
                        "true",
                        "config1"
                }
        };
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<DConnectResponseMessage> result = new AtomicReference<>();
        BroadcastReceiver receiver  = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();

                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                if (!method.equals(IntentDConnectMessage.ACTION_GET)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                } else {
                    String p = intent.getStringExtra(IntentDConnectMessage.EXTRA_PROFILE);

                    if (!ServiceDiscoveryProfileConstants.PROFILE_NAME.equalsIgnoreCase(p)) {
                        response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                        response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                        response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                    } else {
                        List<Bundle> services = new ArrayList<>();

                        for (String[] a : aservices) {
                            Bundle service = new Bundle();
                            service.putString(ServiceDiscoveryProfileConstants.PARAM_ID, a[0]);
                            service.putString(ServiceDiscoveryProfileConstants.PARAM_NAME, a[1]);
                            service.putString(ServiceDiscoveryProfileConstants.PARAM_TYPE, a[2]);
                            service.putBoolean(ServiceDiscoveryProfileConstants.PARAM_ONLINE, "true".equals(a[3]));
                            service.putString(ServiceDiscoveryProfileConstants.PARAM_CONFIG, a[4]);
                            services.add(service);
                        }

                        Bundle[] ss = new Bundle[services.size()];
                        services.toArray(ss);

                        response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                        response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                        response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                        response.putExtra(ServiceDiscoveryProfileConstants.PARAM_SERVICES, ss);
                    }
                }

                context.sendBroadcast(response);
            }
        };
        DConnectSDK sdk = getSDK(receiver);
        sdk.setAccessToken(accessToken);

        sdk.serviceDiscovery(response -> {
            result.set(response);
            latch.countDown();
        });
        waitForResponseAsync(1);
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("timeout");
        }
        DConnectResponseMessage response = result.get();
        assertThat(response.getResult(), is(DConnectMessage.RESULT_OK));
        assertThat(response.getList(ServiceDiscoveryProfileConstants.PARAM_SERVICES), is(notNullValue()));

        int idx = 0;
        for (Object obj : response.getList(ServiceDiscoveryProfileConstants.PARAM_SERVICES)) {
            Map service = (Map) obj;
            String id = (String) service.get(ServiceDiscoveryProfileConstants.PARAM_ID);
            String name = (String) service.get(ServiceDiscoveryProfileConstants.PARAM_NAME);
            assertThat(id, is(aservices[idx][0]));
            assertThat(name, is(aservices[idx][1]));
        }
    }

    /**
     * getを呼び出し、レスポンスを受け取れることを確認する。
     * <pre>
     * 【期待する動作】
     * ・DConnectResponseMessageが返却されること。
     * ・resultに0が返却されること。
     * ・productにtest-managerが返却されること。
     * ・versionに1.1が返却されること。
     * ・nameにmanagerが返却されること。
     * ・uuidにuuidが返却されること。
     * </pre>
     */
    @Test
    public void get() {
        final String version = "1.1";
        final String product = "test";
        final String name = "name";
        final String uuid = "uuid";

        BroadcastReceiver receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
               ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();

                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                if (!method.equals(IntentDConnectMessage.ACTION_GET)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "action is invalid.");
                } else {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                    response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                    response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                    response.putExtra(AvailabilityProfileConstants.PARAM_NAME, name);
                    response.putExtra(AvailabilityProfileConstants.PARAM_UUID, uuid);
                }

                context.sendBroadcast(response);
            }
        };

        DConnectSDK sdk = getSDK(receiver);
        DConnectResponseMessage response = sdk.get("http://localhost:4035/gotapi/availability");
        assertThat(response.getResult(), is(DConnectMessage.RESULT_OK));
        assertThat(response.getString(DConnectProfileConstants.PARAM_VERSION), is(version));
        assertThat(response.getString(DConnectProfileConstants.PARAM_PRODUCT), is(product));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_NAME), is(name));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_UUID), is(uuid));
    }

    /**
     * uriにnullを設定して、getを呼び出す。
     * <pre>
     * 【期待する動作】
     * ・NullPointerExceptionが発生すること。
     * </pre>
     */
    @Test(expected = NullPointerException.class)
    public void get_uri_null() {
        BroadcastReceiver receiver  = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };

        DConnectSDK sdk = getSDK(receiver);
        sdk.get((Uri) null);
    }

    /**
     * uriにから文字列を設定して、getを呼び出す。
     * <pre>
     * 【期待する動作】
     * ・IllegalArgumentExceptionが発生すること。
     * </pre>
     */
    @Test(expected = IllegalArgumentException.class)
    public void get_uri_empty() {
        BroadcastReceiver receiver  = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };

        DConnectSDK sdk = getSDK(receiver);
        sdk.get("");
    }

    /**
     * uriにから不正なURIを設定して、getを呼び出す。
     * <pre>
     * 【期待する動作】
     * ・IllegalArgumentExceptionが発生すること。
     * </pre>
     */
    @Test(expected = IllegalArgumentException.class)
    public void get_uri_illegal() {
        BroadcastReceiver receiver  = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };
        DConnectSDK sdk = getSDK(receiver);
        sdk.get("test");
    }

    /**
     * getを呼び出し、OnResponseListenerにレスポンスが通知されることを確認する。
     * <pre>
     * 【期待する動作】
     * ・OnResponseListenerにDConnectResponseMessageが返却されること。
     * ・resultに0が返却されること。
     * ・productにtest-managerが返却されること。
     * ・versionに1.1が返却されること。
     * ・nameにmanagerが返却されること。
     * ・uuidにuuidが返却されること。
     * </pre>
     */
    @Test
    public void get_listener() {
        final String version = "1.1";
        final String product = "test-manager";
        final String name = "manager";
        final String uuid = "uuid";
        final AtomicReference<DConnectResponseMessage> result = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        BroadcastReceiver receiver  = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();

                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                if (!method.equals(IntentDConnectMessage.ACTION_GET)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "action is invalid.");
                } else {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                    response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                    response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                    response.putExtra(AvailabilityProfileConstants.PARAM_NAME, name);
                    response.putExtra(AvailabilityProfileConstants.PARAM_UUID, uuid);
                }

                context.sendBroadcast(response);
            }
        };

        DConnectSDK sdk = getSDK(receiver);
        sdk.get("http://localhost:4035/gotapi/availability", new DConnectSDK.OnResponseListener() {
            @Override
            public void onResponse(final DConnectResponseMessage response) {
                result.set(response);
                latch.countDown();
            }
        });
        waitForResponseAsync(1);
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("timeout");
        }
        DConnectResponseMessage response = result.get();
        assertThat(response, is(notNullValue()));
        assertThat(response.getResult(), is(DConnectMessage.RESULT_OK));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_VERSION), is(version));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_PRODUCT), is(product));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_NAME), is(name));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_UUID), is(uuid));
    }

    /**
     * postを呼び出し、レスポンスを受け取れることを確認する。
     * <pre>
     * 【期待する動作】
     * ・DConnectResponseMessageが返却されること。
     * ・resultに0が返却されること。
     * ・productにtest-managerが返却されること。
     * ・versionに1.1が返却されること。
     * ・nameにmanagerが返却されること。
     * ・uuidにuuidが返却されること。
     * </pre>
     */
    @Test
    public void post() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String path = context.getFilesDir() + "/test.dat";
        final byte[] fileData = "This is a test.".getBytes();
        writeFile(new File(path), fileData);

        final String version = "1.1";
        final String product = "test-manager";
        final String name = "manager";
        final String uuid = "uuid";
        final String key = "key";
        final String value = "value";
        final MultipartEntity data = new MultipartEntity();
        data.add(key, new StringEntity(value));
        data.add("data", new FileEntity(new File(path)));

        BroadcastReceiver receiver  = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();

                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                File file = (File) intent.getExtras().get("data");
                if (!file.isFile() || !Arrays.equals(fileData, getFile(file))) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "data is invalid.");
                } else if (!method.equals(IntentDConnectMessage.ACTION_POST)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "action is invalid.");
                } else {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                    response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                    response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);
                    response.putExtra(AvailabilityProfileConstants.PARAM_NAME, name);
                    response.putExtra(AvailabilityProfileConstants.PARAM_UUID, uuid);
                }

                context.sendBroadcast(response);
            }
        };

        DConnectSDK sdk = getSDK(receiver);
        DConnectResponseMessage response = sdk.post("http://localhost:4035/gotapi/availability", data);
        assertThat(response, notNullValue());
        assertThat(response.getResult(), is(DConnectMessage.RESULT_OK));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_VERSION), is(version));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_PRODUCT), is(product));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_NAME), is(name));
        assertThat(response.getString(AvailabilityProfileConstants.PARAM_UUID), is(uuid));
    }

    /**
     * WebSocketを接続する。
     * <pre>
     * 【期待する動作】
     * ・OnWebSocketListener#onOpenが呼び出されること。
     * </pre>
     */
    @Test
    public void connectWebSocket() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean result = new AtomicBoolean();
        final String accessToken = "test-accessToken";
        BroadcastReceiver receiver  = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };

        DConnectSDK sdk = getSDK(receiver);
        sdk.setAccessToken(accessToken);
        sdk.connectWebSocket(new DConnectSDK.OnWebSocketListener() {
            @Override
            public void onOpen() {
                result.set(true);
                latch.countDown();
            }

            @Override
            public void onClose() {
            }

            @Override
            public void onError(Exception e) {
                latch.countDown();
            }
        });

        try {
            latch.await(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            sdk.disconnectWebSocket();
            fail();
        }

        assertThat(result.get(), is(true));
    }

    /**
     * OnWebSocketListenerにnullを設定してWebSocketを接続する。
     * <pre>
     * 【期待する動作】
     * ・NullPointerExceptionが発生すること。
     * </pre>
     */
    @Test(expected = NullPointerException.class)
    public void connectWebSocket_listener_null() {
        final String accessToken = "test-accessToken";
        DConnectSDK sdk = DConnectSDKFactory.create(InstrumentationRegistry.getInstrumentation().getContext(), DConnectSDKFactory.Type.INTENT);
        sdk.setAccessToken(accessToken);
        sdk.connectWebSocket(null);
    }

    /**
     * addEventListenerを行いイベントを受け取れることを確認する。
     * <pre>
     * 【期待する動作】
     * ・DConnectEventMessageが受け取れること。
     * </pre>
     */
    @Test
    public void addEventListener() {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final boolean[] result = new boolean[1];
        final DConnectEventMessage[] event = new DConnectEventMessage[1];
        final String version = "1.1";
        final String product = "test-manager";
        final String accessToken = "test-accessToken";
        final String profile = DeviceOrientationProfileConstants.PROFILE_NAME;
        final String attribute = DeviceOrientationProfileConstants.ATTRIBUTE_ON_DEVICE_ORIENTATION;
        final String serviceId = "abc";

        final float accelX = 1.0f;
        final float accelY = 1.5f;
        final float accelZ = 3.9f;
        final int interval = 1001;
        BroadcastReceiver receiver  = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final ComponentName cn = (ComponentName) intent.getExtras().get(IntentDConnectMessage.EXTRA_RECEIVER);
                int requestCode = intent.getIntExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, -1);
                String method = intent.getAction();

                Intent response = new Intent();
                response.setComponent(cn);
                response.setAction(IntentDConnectMessage.ACTION_RESPONSE);
                response.putExtra(IntentDConnectMessage.EXTRA_REQUEST_CODE, requestCode);

                if (!method.equals(IntentDConnectMessage.ACTION_PUT)) {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_CODE, 1);
                    response.putExtra(DConnectMessage.EXTRA_ERROR_MESSAGE, "");
                } else {
                    response.putExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_OK);
                    response.putExtra(DConnectProfileConstants.PARAM_VERSION, version);
                    response.putExtra(DConnectProfileConstants.PARAM_PRODUCT, product);

                    // 1秒後にイベントを送信
                    Executors.newSingleThreadScheduledExecutor().schedule(new Runnable() {
                        @Override
                        public void run() {
                            Bundle orientation = new Bundle();

                            Bundle acceleration = new Bundle();
                            acceleration.putFloat(DeviceOrientationProfileConstants.PARAM_X, accelX);
                            acceleration.putFloat(DeviceOrientationProfileConstants.PARAM_Y, accelY);
                            acceleration.putFloat(DeviceOrientationProfileConstants.PARAM_Z, accelZ);
                            orientation.putBundle(DeviceOrientationProfileConstants.PARAM_ACCELERATION, acceleration);
                            orientation.putInt(DeviceOrientationProfileConstants.PARAM_INTERVAL, interval);

                            Intent jsonObject = new Intent();
                            jsonObject.setAction(IntentDConnectMessage.ACTION_EVENT);
                            jsonObject.setComponent(cn);
                            jsonObject.putExtra(DeviceOrientationProfileConstants.PARAM_ORIENTATION, orientation);
                            jsonObject.putExtra(DConnectMessage.EXTRA_API, "gotapi");
                            jsonObject.putExtra(DConnectMessage.EXTRA_PROFILE, profile);
                            jsonObject.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, attribute);
                            jsonObject.putExtra(DConnectMessage.EXTRA_SERVICE_ID, serviceId);

                            context.sendBroadcast(jsonObject);
                        }
                    }, 3, TimeUnit.SECONDS);
                    waitForResponseAsync(1);

                }
                context.sendBroadcast(response);
            }
        };
        DConnectSDK sdk = getSDK(receiver);
        sdk.setAccessToken(accessToken);
        sdk.connectWebSocket(new DConnectSDK.OnWebSocketListener() {
            @Override
            public void onOpen() {
                result[0] = true;
                latch.countDown();
            }

            @Override
            public void onClose() {
            }

            @Override
            public void onError(Exception e) {
                latch.countDown();
            }
        });
        waitForResponseAsync(1);
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            sdk.disconnectWebSocket();
            fail("timeout");
        }

        assertThat(result[0], is(true));

        DConnectSDK.URIBuilder builder = sdk.createURIBuilder();
        builder.setProfile(profile);
        builder.setAttribute(attribute);
        builder.setServiceId(serviceId);

        sdk.addEventListener(builder.toASCIIString(), new DConnectSDK.OnEventListener() {
            @Override
            public void onMessage(final DConnectEventMessage message) {
                event[0] = message;
                latch2.countDown();
            }

            @Override
            public void onResponse(final DConnectResponseMessage response) {
                result[0] = true;
            }
        });

        // イベントからのメッセージを待つ
        try {
            waitForResponseAsync(1);
            latch2.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("timeout");
        } finally {
            sdk.removeEventListener(builder.toASCIIString());
            sdk.disconnectWebSocket();
        }

        assertThat(event[0], is(notNullValue()));
        assertThat(event[0].getString(DConnectMessage.EXTRA_PROFILE), is(profile));
        assertThat(event[0].getString(DConnectMessage.EXTRA_ATTRIBUTE), is(attribute));
        assertThat(event[0].getString(DConnectMessage.EXTRA_SERVICE_ID), is(serviceId));

        DConnectMessage orientation = event[0].getMessage(DeviceOrientationProfileConstants.PARAM_ORIENTATION);
        assertThat(orientation, is(notNullValue()));
        assertThat(orientation.getInt(DeviceOrientationProfileConstants.PARAM_INTERVAL), is(interval));

        DConnectMessage acceleration = orientation.getMessage(DeviceOrientationProfileConstants.PARAM_ACCELERATION);
        assertThat(acceleration, is(notNullValue()));

        assertThat(acceleration.getFloat(DeviceOrientationProfileConstants.PARAM_X), is(accelX));
        assertThat(acceleration.getFloat(DeviceOrientationProfileConstants.PARAM_Y), is(accelY));
        assertThat(acceleration.getFloat(DeviceOrientationProfileConstants.PARAM_Z), is(accelZ));
    }

    /**
     * OnEventListenerにnullを設定してaddEventListenerを行う。
     * <pre>
     * 【期待する動作】
     * ・NullPointerExceptionが発生すること。
     * </pre>
     */
    @Test(expected = NullPointerException.class)
    public void addEventListener_listener_null() {
        BroadcastReceiver receiver  = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };
        DConnectSDK sdk = getSDK(receiver);
        DConnectSDK.URIBuilder builder = sdk.createURIBuilder();
        builder.setProfile("deviceOrientation");
        builder.setAttribute("onDeviceOrientation");
        builder.setServiceId("serviceId");
        sdk.addEventListener(builder.toASCIIString(), null);
    }

    /**
     * uriにnullを設定してaddEventListenerを行う。
     * <pre>
     * 【期待する動作】
     * ・NullPointerExceptionが発生すること。
     * </pre>
     */
    @Test(expected = NullPointerException.class)
    public void addEventListener_uri_null() {
        BroadcastReceiver receiver  = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };

        DConnectSDK sdk = getSDK(receiver);
        sdk.addEventListener((Uri) null, new DConnectSDK.OnEventListener() {
            @Override
            public void onMessage(final DConnectEventMessage message) {
            }
            @Override
            public void onResponse(final DConnectResponseMessage response) {
            }
        });
    }

    /**
     * uriにnullを設定してremoveEventListenerを行う。
     * <pre>
     * 【期待する動作】
     * ・NullPointerExceptionが発生すること。
     * </pre>
     */
    @Test(expected = NullPointerException.class)
    public void removeEventListener_uri_null() {
        BroadcastReceiver receiver  = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };

        DConnectSDK sdk = getSDK(receiver);
        sdk.removeEventListener((Uri) null);
    }
}
