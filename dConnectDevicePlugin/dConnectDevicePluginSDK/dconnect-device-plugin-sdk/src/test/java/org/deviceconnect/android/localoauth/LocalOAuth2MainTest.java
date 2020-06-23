/*
 LocalOAuth2MainTest.java
 Copyright (c) 2017 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.localoauth;

import android.content.Context;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import org.deviceconnect.android.PluginSDKTestRunner;
import org.deviceconnect.android.localoauth.exception.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.restlet.ext.oauth.PackageInfoOAuth;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

/**
 * LocalOAuth2Mainの単体テスト.
 *
 * @author NTT DOCOMO, INC.
 */
@RunWith(PluginSDKTestRunner.class)
@Config(maxSdk = Build.VERSION_CODES.P)
public class LocalOAuth2MainTest {

    private Context mContext;

    private LocalOAuth2Main mLocalOAuth2Main;

    @Before
    public void execBeforeClass() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mLocalOAuth2Main = new LocalOAuth2Main(mContext);
    }

    @After
    public void execAfterClass() {
        mLocalOAuth2Main.destroy();
        mLocalOAuth2Main = null;
        mContext = null;
    }

    @Test
    public void LocalOAuth2Main_createClient() {
        final String origin = "test";
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth2Main.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_createClient_packageInfo_null() {
        try {
            mLocalOAuth2Main.createClient(null);
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_createClient_packageName_null() {
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(null);
        try {
            mLocalOAuth2Main.createClient(packageInfo);
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_createClient_packageName_empty() {
        PackageInfoOAuth packageInfo = new PackageInfoOAuth("");
        try {
            mLocalOAuth2Main.createClient(packageInfo);
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test
    public void LocalOAuth2Main_confirmPublishAccessToken() {
        final String origin = "test";
        final String serviceId = "test_service_id";
        final String[] scopes = {
                "serviceDiscovery"
        };
        final CountDownLatch mLatch = new CountDownLatch(1);
        final AtomicReference<AccessTokenData> accessToken = new AtomicReference<>();
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth2Main.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth2Main.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            });

            assertThat(mLocalOAuth2Main.requestSize(), is(1));
            ConfirmAuthRequest request = mLocalOAuth2Main.pickupRequest();
            mLocalOAuth2Main.dequeueRequest(request.getThreadId(), true);
            assertThat(mLocalOAuth2Main.requestSize(), is(0));
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test
    public void LocalOAuth2Main_confirmPublishAccessToken_multiple() {
        final int count = 10;
        final CountDownLatch mLatch = new CountDownLatch(count);
        final AtomicReferenceArray<Boolean> results = new AtomicReferenceArray<>(count);
        for (int i = 0; i < count; i++) {
            final String origin = "test" + i;
            final String serviceId = "test_service_id" + i;
            final String[] scopes = {
                    "serviceDiscovery"
            };
            final int index = i;
            createAccessToken(origin, serviceId, scopes);
        }

        assertThat(mLocalOAuth2Main.requestSize(), is(count));
        for (int i = 0; i < count; i++) {
            ConfirmAuthRequest request = mLocalOAuth2Main.pickupRequest();
            mLocalOAuth2Main.dequeueRequest(request.getThreadId(), true);
        }
        assertThat(mLocalOAuth2Main.requestSize(), is(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_confirmPublishAccessToken_params_null() {
        final String origin = "test";
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth2Main.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            mLocalOAuth2Main.confirmPublishAccessToken(null, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                }

                @Override
                public void onReceiveException(final Exception exception) {
                }
            });
            fail();
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_confirmPublishAccessToken_listener_null() {
        final String origin = "test";
        final String serviceId = "test_service_id";
        final String[] scopes = {
                "serviceDiscovery"
        };
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth2Main.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth2Main.confirmPublishAccessToken(params, null);
            fail();
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_confirmPublishAccessToken_context_null() {
        final String origin = "test";
        final String serviceId = "test_service_id";
        final String[] scopes = {
                "serviceDiscovery"
        };
        final CountDownLatch mLatch = new CountDownLatch(1);
        final AtomicReference<AccessTokenData> accessToken = new AtomicReference<>();
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth2Main.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(null).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth2Main.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            });
            fail();
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_confirmPublishAccessToken_application_name_null() {
        final String origin = "test";
        final String serviceId = "test_service_id";
        final String[] scopes = {
                "serviceDiscovery"
        };
        final CountDownLatch mLatch = new CountDownLatch(1);
        final AtomicReference<AccessTokenData> accessToken = new AtomicReference<>();
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth2Main.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName(null)
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth2Main.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            });
            fail();
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_confirmPublishAccessToken_client_null() {
        final String origin = "test";
        final String serviceId = "test_service_id";
        final String[] scopes = {
                "serviceDiscovery"
        };
        final CountDownLatch mLatch = new CountDownLatch(1);
        final AtomicReference<AccessTokenData> accessToken = new AtomicReference<>();
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth2Main.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(null).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth2Main.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            });
            fail();
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_confirmPublishAccessToken_scopes_null() {
        final String origin = "test";
        final String serviceId = "test_service_id";
        final CountDownLatch mLatch = new CountDownLatch(1);
        final AtomicReference<AccessTokenData> accessToken = new AtomicReference<>();
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth2Main.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId("TEST").scopes(null).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth2Main.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            });
            fail();
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }



    @Test
    public void LocalOAuth2Main_checkAccessToken_illegal_access_token() {
        CheckAccessTokenResult result = mLocalOAuth2Main.checkAccessToken("test", "battery", null);
        assertThat(result, is(notNullValue()));
        assertThat(result.checkResult(), is(false));
        assertThat(result.isExistAccessToken(), is(false));
        assertThat(result.isExistClientId(), is(false));
        assertThat(result.isExistScope(), is(false));
        assertThat(result.isNotExpired(), is(false));
    }

    @Test
    public void LocalOAuth2Main_findClientPackageInfoByAccessToken_illegal_access_token() {
        ClientPackageInfo info = mLocalOAuth2Main.findClientPackageInfoByAccessToken("test");
        assertThat(info, is(nullValue()));
    }


    @LooperMode(PAUSED)
    private AccessTokenData createAccessToken(final String origin, final String serviceId, final String[] scopes) {
        final CountDownLatch mLatch = new CountDownLatch(1);
        final AtomicReference<AccessTokenData> accessToken = new AtomicReference<>();
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth2Main.createClient(packageInfo);
            if (clientData == null) {
                return null;
            }

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();

            mLocalOAuth2Main.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                }
                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            });
            return accessToken.get();
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
        return null;
    }
}
