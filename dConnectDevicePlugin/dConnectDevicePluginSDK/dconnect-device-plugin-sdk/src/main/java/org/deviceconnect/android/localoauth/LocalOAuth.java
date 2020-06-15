package org.deviceconnect.android.localoauth;

import org.deviceconnect.android.localoauth.exception.AuthorizationException;
import org.restlet.ext.oauth.PackageInfoOAuth;

public interface LocalOAuth {
    /**
     * 認可の結果を通知するアクションを定義します.
     */
    String ACTION_TOKEN_APPROVAL = "org.deviceconnect.android.localoauth.TOKEN_APPROVAL";

    /**
     * 認可の結果に格納されるThread IDのエクストラキーを定義します.
     */
    String EXTRA_THREAD_ID = "org.deviceconnect.android.localoauth.THREAD_ID";


    /** 通知の許可ボタン押下時のアクション */
    String ACTION_OAUTH_ACCEPT = "org.deviceconnect.android.localoauth.accept";

    /** 通知の拒否ボタン押下時のアクション */
    String ACTION_OAUTH_DECLINE = "org.deviceconnect.android.localoauth.decline";

    /** 通知の許可するボタンのタイトル */
    String ACCEPT_BUTTON_TITLE = "許可する";

    /** 通知の拒否するボタンのタイトル */
    String DECLINE_BUTTON_TITLE = "拒否する";

    /** 通知の詳細を表示ボタンのタイトル */
    String DETAIL_BUTTON_TITLE = "詳細を表示";

    /** Notification Id */
    int NOTIFICATION_ID = 3463;

    interface AuthCallback {
        void callAuth(Callback callback);
        interface Callback {
            void ok();
            void ng();
        }
    }

    ClientData createClient(final PackageInfoOAuth packageInfo) throws AuthorizationException;
    void confirmPublishAccessToken(final ConfirmAuthParams params,
                                   final PublishAccessTokenListener listener) throws AuthorizationException;
    void destroy();

    CheckAccessTokenResult checkAccessToken(final String accessToken, final String scope,
                                            final String[] specialScopes);
}
