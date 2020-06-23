/*
 LocalOAuth2MainTest.java
 Copyright (c) 2017 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.localoauth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.deviceconnect.android.localoauth.exception.AuthorizationException;
import org.deviceconnect.android.localoauth.oauthserver.db.SQLiteToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.restlet.ext.oauth.PackageInfoOAuth;
import org.restlet.ext.oauth.internal.Client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * LocalOAuth2Mainの単体テスト.
 *
 * @author NTT DOCOMO, INC.
 */
@RunWith(AndroidJUnit4.class)
public class LocalOAuth2MainTest {

    private Context mContext;

    private LocalOAuth mLocalOAuth;

    @Before
    public void execBeforeClass() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mLocalOAuth = LocalOAuthFactory.create(mContext);
    }

    @After
    public void execAfterClass() {
        mLocalOAuth.destroy();
        mLocalOAuth = null;
        mContext = null;
    }

    @Test
    public void LocalOAuth2Main_createClient() {
        final String origin = "test";
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_createClient_packageInfo_null() {
        try {
            mLocalOAuth.createClient(null);
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_createClient_packageName_null() {
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(null);
        try {
            mLocalOAuth.createClient(packageInfo);
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_createClient_packageName_empty() {
        PackageInfoOAuth packageInfo = new PackageInfoOAuth("");
        try {
            mLocalOAuth.createClient(packageInfo);
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
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            }, new Handler(Looper.getMainLooper()));

            mLatch.await(30, TimeUnit.SECONDS);

            AccessTokenData data = accessToken.get();
            assertThat(data, is(notNullValue()));
            assertThat(data.getAccessToken(), is(notNullValue()));
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        } catch (InterruptedException e) {
            fail("timeout");
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
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AccessTokenData data = createAccessToken(origin, serviceId, scopes);
                        CheckAccessTokenResult result = mLocalOAuth.checkAccessToken(data.getAccessToken(), scopes[0], null);
                        if (result != null) {
                            results.set(index, result.checkResult());
                        } else {
                            results.set(index, false);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mLatch.countDown();
                }
            }).start();
        }

        try {
            mLatch.await(180, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("timeout");
        }

        for (int i = 0; i < count; i++) {
            assertThat(results.get(i), is(true));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LocalOAuth2Main_confirmPublishAccessToken_params_null() {
        final String origin = "test";
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            mLocalOAuth.confirmPublishAccessToken(null, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                }

                @Override
                public void onReceiveException(final Exception exception) {
                }
            }, new Handler(Looper.getMainLooper()));
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
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth.confirmPublishAccessToken(params, null, new Handler(Looper.getMainLooper()));
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
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(null).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            }, new Handler(Looper.getMainLooper()));
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
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName(null)
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            }, new Handler(Looper.getMainLooper()));
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
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(null).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            }, new Handler(Looper.getMainLooper()));
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
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId("TEST").scopes(null).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            }, new Handler(Looper.getMainLooper()));
            fail();
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        }
    }

    @Test
    public void LocalOAuth2Main_checkAccessToken() {
        final String origin = "test_check";
        final String serviceId = "test_service_id_check";
        final String[] scopes = {
                "serviceDiscovery"
        };
        AccessTokenData data = createAccessToken(origin, serviceId, scopes);

        CheckAccessTokenResult result = mLocalOAuth.checkAccessToken(data.getAccessToken(), scopes[0], null);
        assertThat(result, is(notNullValue()));
        assertThat(result.checkResult(), is(true));
        assertThat(result.isExistAccessToken(), is(true));
        assertThat(result.isExistClientId(), is(true));
        assertThat(result.isExistScope(), is(true));
        assertThat(result.isNotExpired(), is(true));
    }

    @Test
    public void LocalOAuth2Main_checkAccessToken_out_scope() {
        final String origin = "test_check_scope";
        final String serviceId = "test_service_id_check_scope";
        final String[] scopes = {
                "serviceDiscovery"
        };
        AccessTokenData data = createAccessToken(origin, serviceId, scopes);

        CheckAccessTokenResult result = mLocalOAuth.checkAccessToken(data.getAccessToken(), "battery", null);
        assertThat(result, is(notNullValue()));
        assertThat(result.checkResult(), is(false));
        assertThat(result.isExistAccessToken(), is(true));
        assertThat(result.isExistClientId(), is(true));
        assertThat(result.isExistScope(), is(false));
        assertThat(result.isNotExpired(), is(false));
    }

    @Test
    public void LocalOAuth2Main_checkAccessToken_illegal_access_token() {
        CheckAccessTokenResult result = mLocalOAuth.checkAccessToken("test", "battery", null);
        assertThat(result, is(notNullValue()));
        assertThat(result.checkResult(), is(false));
        assertThat(result.isExistAccessToken(), is(false));
        assertThat(result.isExistClientId(), is(false));
        assertThat(result.isExistScope(), is(false));
        assertThat(result.isNotExpired(), is(false));
    }

    @Test
    public void LocalOAuth2Main_findClientPackageInfoByAccessToken() {
        final String origin = "test_find_client";
        final String serviceId = "test_service_id_find_client";
        final String[] scopes = {
                "serviceDiscovery"
        };
        AccessTokenData data = createAccessToken(origin, serviceId, scopes);

        ClientPackageInfo info = mLocalOAuth.findClientPackageInfoByAccessToken(data.getAccessToken());
        assertThat(info, is(notNullValue()));
        assertThat(info.getPackageInfo(), is(notNullValue()));
        assertThat(info.getPackageInfo().getPackageName(), is(origin));
    }

    @Test
    public void LocalOAuth2Main_findClientPackageInfoByAccessToken_illegal_access_token() {
        ClientPackageInfo info = mLocalOAuth.findClientPackageInfoByAccessToken("test");
        assertThat(info, is(nullValue()));
    }

    @Test
    public void LocalOAuth2Main_getAccessTokens() {
        final String origin = "test_access_token";
        final String serviceId = "test_service_id_access_token";
        final String[] scopes = {
                "serviceDiscovery"
        };
        AccessTokenData data = createAccessToken(origin, serviceId, scopes);
        assertThat(data, is(notNullValue()));
        assertThat(data.getAccessToken(), is(notNullValue()));

        SQLiteToken[] tokens = mLocalOAuth.getAccessTokens();
        assertThat(tokens, is(notNullValue()));
    }

    @Test
    public void LocalOAuth2Main_getAccessToken() {
        final String origin = "test";
        final String serviceId = "test_service_id";
        final String[] scopes = {
                "serviceDiscovery"
        };
        final CountDownLatch mLatch = new CountDownLatch(1);
        final AtomicReference<AccessTokenData> accessToken = new AtomicReference<>();
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            assertThat(clientData, is(notNullValue()));

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();
            mLocalOAuth.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }

                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            }, new Handler(Looper.getMainLooper()));

            mLatch.await(30, TimeUnit.SECONDS);

            AccessTokenData data = accessToken.get();
            assertThat(data, is(notNullValue()));
            assertThat(data.getAccessToken(), is(notNullValue()));

            Client client = mLocalOAuth.findClientByClientId(clientData.getClientId());
            assertThat(client, is(notNullValue()));

            SQLiteToken token = mLocalOAuth.getAccessToken(client);
            assertThat(token, is(notNullValue()));
            assertThat(token.getAccessToken(), is(data.getAccessToken()));
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        } catch (InterruptedException e) {
            fail("timeout");
        }
    }

    @Test
    public void LocalOAuth2Main_destroyAccessToken() {
        final String origin = "test_delete_access_token";
        final String serviceId = "test_service_id_access_token";
        final String[] scopes = {
                "serviceDiscovery"
        };
        AccessTokenData data = createAccessToken(origin, serviceId, scopes);

        CheckAccessTokenResult result = mLocalOAuth.checkAccessToken(data.getAccessToken(), scopes[0], null);
        assertThat(result, is(notNullValue()));
        assertThat(result.checkResult(), is(true));
        assertThat(result.isExistAccessToken(), is(true));
        assertThat(result.isExistClientId(), is(true));
        assertThat(result.isExistScope(), is(true));
        assertThat(result.isNotExpired(), is(true));

        SQLiteToken[] tokens = mLocalOAuth.getAccessTokens();
        assertThat(tokens, is(notNullValue()));

        for (SQLiteToken token : tokens) {
            if (token.getAccessToken() != null && token.getAccessToken().equals(data.getAccessToken())) {
                mLocalOAuth.destroyAccessToken(token.getId());
                break;
            }
        }

        result = mLocalOAuth.checkAccessToken(data.getAccessToken(), scopes[0], null);
        assertThat(result, is(notNullValue()));
        assertThat(result.checkResult(), is(false));
        assertThat(result.isExistAccessToken(), is(false));
        assertThat(result.isExistClientId(), is(false));
        assertThat(result.isExistScope(), is(false));
        assertThat(result.isNotExpired(), is(false));
    }

    @Test
    public void LocalOAuth2Main_destroyAllAccessToken() {
        final String origin = "test_delete_all_access_token";
        final String serviceId = "test_service_id_access_token";
        final String[] scopes = {
                "serviceDiscovery"
        };
        AccessTokenData data = createAccessToken(origin, serviceId, scopes);

        CheckAccessTokenResult result = mLocalOAuth.checkAccessToken(data.getAccessToken(), scopes[0], null);
        assertThat(result, is(notNullValue()));
        assertThat(result.checkResult(), is(true));
        assertThat(result.isExistAccessToken(), is(true));
        assertThat(result.isExistClientId(), is(true));
        assertThat(result.isExistScope(), is(true));
        assertThat(result.isNotExpired(), is(true));

        ClientPackageInfo clientPackageInfo = mLocalOAuth.findClientPackageInfoByAccessToken(data.getAccessToken());
        assertThat(clientPackageInfo, is(notNullValue()));

        mLocalOAuth.destroyAllAccessToken();

        result = mLocalOAuth.checkAccessToken(data.getAccessToken(), scopes[0], null);
        assertThat(result, is(notNullValue()));
        assertThat(result.checkResult(), is(false));
        assertThat(result.isExistAccessToken(), is(false));
        assertThat(result.isExistClientId(), is(false));
        assertThat(result.isExistScope(), is(false));
        assertThat(result.isNotExpired(), is(false));

        Client client = mLocalOAuth.findClientByClientId(clientPackageInfo.getClientId());
        assertThat(client, is(notNullValue()));
    }

    private AccessTokenData createAccessToken(final String origin, final String serviceId, final String[] scopes) {
        final CountDownLatch mLatch = new CountDownLatch(1);
        final AtomicReference<AccessTokenData> accessToken = new AtomicReference<>();
        PackageInfoOAuth packageInfo = new PackageInfoOAuth(origin);
        try {
            ClientData clientData = mLocalOAuth.createClient(packageInfo);
            if (clientData == null) {
                return null;
            }

            ConfirmAuthParams params = new ConfirmAuthParams.Builder().context(mContext).serviceId(serviceId)
                    .clientId(clientData.getClientId()).scopes(scopes).applicationName("JUnit")
                    .isForDevicePlugin(false)
                    .isAutoFlag(true)
                    .keyword("Keyword")
                    .build();

            mLocalOAuth.confirmPublishAccessToken(params, new PublishAccessTokenListener() {
                @Override
                public void onReceiveAccessToken(final AccessTokenData accessTokenData) {
                    accessToken.set(accessTokenData);
                    mLatch.countDown();
                }
                @Override
                public void onReceiveException(final Exception exception) {
                    mLatch.countDown();
                }
            }, new Handler(Looper.getMainLooper()));

            mLatch.await(180, TimeUnit.SECONDS);

            return accessToken.get();
        } catch (AuthorizationException e) {
            fail("Failed to create client.");
        } catch (InterruptedException e) {
            fail("timeout");
        }
        return null;
    }
}
