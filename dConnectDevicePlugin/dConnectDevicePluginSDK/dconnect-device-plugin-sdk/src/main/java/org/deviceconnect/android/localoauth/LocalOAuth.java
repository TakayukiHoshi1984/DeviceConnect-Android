package org.deviceconnect.android.localoauth;

import org.deviceconnect.android.localoauth.exception.AuthorizationException;
import org.deviceconnect.android.localoauth.oauthserver.SampleUserManager;
import org.deviceconnect.android.localoauth.oauthserver.db.SQLiteClient;
import org.deviceconnect.android.localoauth.oauthserver.db.SQLiteToken;
import org.restlet.ext.oauth.PackageInfoOAuth;
import org.restlet.ext.oauth.internal.Client;

public interface LocalOAuth {
    /** ダミー値(RedirectURI). */
    String DUMMY_REDIRECT_URI = "dummyRedirectURI";

    /**
     * 認可の結果を通知するアクションを定義します.
     */
    String ACTION_TOKEN_APPROVAL = "org.deviceconnect.android.localoauth.TOKEN_APPROVAL";

    /**
     * 認可の結果に格納されるThread IDのエクストラキーを定義します.
     */
    String EXTRA_THREAD_ID = "org.deviceconnect.android.localoauth.THREAD_ID";
    /**
     * 認可の結果に格納される認可フラグのエクストラキーを定義します.
     */
    String EXTRA_APPROVAL = "org.deviceconnect.android.localoauth.APPROVAL";
    /** Notification Id */
    int NOTIFICATION_ID = 3463;

    ClientData createClient(final PackageInfoOAuth packageInfo) throws AuthorizationException;
    void confirmPublishAccessToken(final ConfirmAuthParams params,
                                   final PublishAccessTokenListener listener) throws AuthorizationException;
    void destroy();

    CheckAccessTokenResult checkAccessToken(final String accessToken, final String scope,
                                            final String[] specialScopes);

    void startConfirmAuthController(final ConfirmAuthRequest request);

    SampleUserManager getSampleUserManager();
    SQLiteToken[] getAccessTokens();
    SQLiteToken getAccessToken(final getAccessToken client);
    void destroyAllAccessToken();
    void destroyAccessToken(final long tokenId);
    SQLiteClient findClientByClientId(final String clientId);
}
