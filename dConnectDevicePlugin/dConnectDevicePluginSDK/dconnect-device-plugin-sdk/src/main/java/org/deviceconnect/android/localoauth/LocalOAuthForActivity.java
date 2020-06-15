package org.deviceconnect.android.localoauth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.deviceconnect.android.localoauth.activity.ConfirmAuthActivity;
import org.deviceconnect.android.localoauth.exception.AuthorizationException;
import org.deviceconnect.android.localoauth.oauthserver.SampleUserManager;
import org.deviceconnect.android.localoauth.oauthserver.db.SQLiteClient;
import org.deviceconnect.android.localoauth.oauthserver.db.SQLiteToken;
import org.deviceconnect.android.util.NotificationUtils;
import org.restlet.ext.oauth.PackageInfoOAuth;
import org.restlet.ext.oauth.internal.Client;

class LocalOAuthForActivity implements LocalOAuth {

    /** 通知の許可ボタン押下時のアクション */
    protected static final String ACTION_OAUTH_ACCEPT = "org.deviceconnect.android.localoauth.accept";

    /** 通知の拒否ボタン押下時のアクション */
    protected static final String ACTION_OAUTH_DECLINE = "org.deviceconnect.android.localoauth.decline";
    protected LocalOAuth2Main mLocalOAuth2Main;


    LocalOAuthForActivity(final Context context) {
        mLocalOAuth2Main = new LocalOAuth2Main(context,this);
        register();
    }
    @Override
    public ClientData createClient(PackageInfoOAuth packageInfo) throws AuthorizationException {
        return mLocalOAuth2Main.createClient(packageInfo);
    }

    @Override
    public void confirmPublishAccessToken(ConfirmAuthParams params, PublishAccessTokenListener listener) throws AuthorizationException {
        mLocalOAuth2Main.confirmPublishAccessToken(params, listener);
        // ActivityがサービスがBindされていない場合には、
        // Activityを起動する。
        if (mLocalOAuth2Main.requestSize() <= 1) {
            startConfirmAuthController(mLocalOAuth2Main.pickupRequest());
        }
    }

    @Override
    public void destroy() {
        unregister();
        mLocalOAuth2Main.destroy();
    }

    @Override
    public CheckAccessTokenResult checkAccessToken(String accessToken, String scope, String[] specialScopes) {
        return checkAccessToken(accessToken, scope, specialScopes);
    }
    /**
     * リクエストデータを使ってアクセストークン発行承認確認画面を起動する.
     * @param request リクエストデータ
     */
    public void startConfirmAuthController(final ConfirmAuthRequest request) {
        if (request == null) {
            return;
        }

        android.content.Context context = request.getConfirmAuthParams().getContext();
        String[] displayScopes = request.getDisplayScopes();
        ConfirmAuthParams params = request.getConfirmAuthParams();

        // Activity起動(許可・拒否の結果は、ApprovalHandlerへ送られる)
        // 詳細ボタン押下時のIntent
        Intent detailIntent = new Intent();
        putExtras(context, request, displayScopes, detailIntent);
        detailIntent.setClass(params.getContext(), ConfirmAuthActivity.class);
        detailIntent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

        detailIntent.putExtra(ConfirmAuthActivity.EXTRA_RESULT_RECEIVER, new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(final int resultCode, final Bundle resultData) {
                String action = resultData.getString(EXTRA_ACTION);
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ACTION_OAUTH_ACCEPT.equals(action) || ACTION_OAUTH_DECLINE.equals(action)) {
                        NotificationUtils.cancel(context, NOTIFICATION_ID);
                    }
                }
                if (ACTION_TOKEN_APPROVAL.equals(action)) {
                    long threadId = resultData.getLong(EXTRA_THREAD_ID, -1);
                    boolean isApproval = resultData.getBoolean(EXTRA_APPROVAL, false);
                    processApproval(threadId, isApproval);
                } else if (ACTION_OAUTH_ACCEPT.equals(action) || ACTION_OAUTH_DECLINE.equals(action)) {
                    long threadId = resultData.getLong(ConfirmAuthActivity.EXTRA_THREAD_ID, -1);
                    boolean isApproval = resultData.getBoolean(EXTRA_APPROVAL, false);
                    processApproval(threadId, isApproval);
                }
            }
        });
        context.startActivity(detailIntent);

        request.startTimer(new ConfirmAuthRequest.OnTimeoutCallback() {
            @Override
            public void onTimeout() {
                processApproval(request.getThreadId(), false);
            }
        });
    }

    @Override
    public SampleUserManager getSampleUserManager() {
        return mLocalOAuth2Main.getSampleUserManager();
    }

    @Override
    public SQLiteToken[] getAccessTokens() {
        return mLocalOAuth2Main.getAccessTokens();
    }

    @Override
    public SQLiteToken getAccessToken(Client client) {
        return mLocalOAuth2Main.getAccessToken(client);
    }

    @Override
    public void destroyAllAccessToken() {
        mLocalOAuth2Main.destroyAllAccessToken();
    }

    @Override
    public void destroyAccessToken(long tokenId) {
        mLocalOAuth2Main.destroyAccessToken(tokenId);
    }

    @Override
    public SQLiteClient findClientByClientId(String clientId) {
        return mLocalOAuth2Main.findClientByClientId(clientId);
    }

    /**
     * 認証処理を行う.
     * @param threadId 認証用スレッドID
     * @param isApproval 承認する場合はtrue、拒否する場合はfalse
     */
    private void processApproval(final long threadId, final boolean isApproval) {
        // 承認確認画面を表示する直前に保存しておいたパラメータデータを取得する
        ConfirmAuthRequest request = mLocalOAuth2Main.dequeueRequest(threadId, false);
        if (request != null && !request.isDoneResponse()) {
            request.setDoneResponse(true);
            request.stopTimer();

            PublishAccessTokenListener publishAccessTokenListener = request.getPublishAccessTokenListener();
            ConfirmAuthParams params = request.getConfirmAuthParams();

            if (isApproval) {
                AccessTokenData accessTokenData = null;
                AuthorizationException exception = null;

                try {
                    accessTokenData = mLocalOAuth2Main.refleshAccessToken(params);
                } catch(AuthorizationException e) {
                    exception = e;
                }

                if (exception == null) {
                    // リスナーを通じてアクセストークンを返す
                    callPublishAccessTokenListener(accessTokenData, publishAccessTokenListener);
                } else {
                    // リスナーを通じて発生した例外を返す
                    callExceptionListener(exception, publishAccessTokenListener);
                }
            } else {
                // リスナーを通じて拒否通知を返す
                callPublishAccessTokenListener(null, publishAccessTokenListener);
            }

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Activityが終了するタイミングが取得できないので、ここでキューからリクエストを削除する
                    mLocalOAuth2Main.dequeueRequest(threadId, true);

                    // キューにリクエストが残っていれば、次のキューを取得してActivityを起動する
                    final ConfirmAuthRequest nextRequest = mLocalOAuth2Main.pickupRequest();
                    if (nextRequest != null) {
                        startConfirmAuthController(nextRequest);
                    }
                }
            }, 2000);
        }
    }

    /**
     * リスナーを通じてアクセストークンを返す.
     *
     * @param accessTokenData アクセストークンデータ
     * @param publishAccessTokenListener アクセストークン発行リスナー
     */
    private void callPublishAccessTokenListener(final AccessTokenData accessTokenData, final PublishAccessTokenListener publishAccessTokenListener) {
        if (publishAccessTokenListener != null) {
            // リスナーを実行してアクセストークンデータを返す
            publishAccessTokenListener.onReceiveAccessToken(accessTokenData);
        } else {
            // リスナーが登録されていないので通知できない
            throw new RuntimeException("publishAccessTokenListener is null.");
        }
    }

    /**
     * リスナーを通じてアクセストークンを返す.
     *
     * @param exception 例外
     * @param publishAccessTokenListener アクセストークン発行リスナー
     */
    private void callExceptionListener(final Exception exception,
                                       final PublishAccessTokenListener publishAccessTokenListener) {
        if (publishAccessTokenListener != null) {
            // リスナーを実行して例外データを返す
            publishAccessTokenListener.onReceiveException(exception);
        } else {
            // リスナーが登録されていないので通知できない
            throw new RuntimeException("publishAccessTokenListener is null.");
        }
    }

    /**
     * Intentの共通設定処理
     *
     * @param context コンテキスト
     * @param request リクエストパラメータ
     * @param displayScopes 要求する権限のリスト
     * @param intent Intent
     */
    protected void putExtras(android.content.Context context, ConfirmAuthRequest request, String[] displayScopes, Intent intent) {
        long threadId = request.getThreadId();
        ConfirmAuthParams params = request.getConfirmAuthParams();
        intent.putExtra(ConfirmAuthActivity.EXTRA_THREAD_ID, threadId);
        if (params.getServiceId() != null) {
            intent.putExtra(ConfirmAuthActivity.EXTRA_SERVICE_ID, params.getServiceId());
        }
        intent.putExtra(ConfirmAuthActivity.EXTRA_APPLICATION_NAME, params.getApplicationName());
        intent.putExtra(ConfirmAuthActivity.EXTRA_SCOPES, params.getScopes());
        intent.putExtra(ConfirmAuthActivity.EXTRA_DISPLAY_SCOPES, displayScopes);
        intent.putExtra(ConfirmAuthActivity.EXTRA_REQUEST_TIME, request.getRequestTime());
        intent.putExtra(ConfirmAuthActivity.EXTRA_IS_FOR_DEVICEPLUGIN, params.isForDevicePlugin());
        if (!params.isForDevicePlugin()) {
            intent.putExtra(ConfirmAuthActivity.EXTRA_PACKAGE_NAME, context.getPackageName());
            intent.putExtra(ConfirmAuthActivity.EXTRA_KEYWORD, params.getKeyword());
        }
        intent.putExtra(ConfirmAuthActivity.EXTRA_AUTO_FLAG, request.isAutoFlag());
    }


    /**
     * 認可の結果を受け取るためのLocalBroadcastReceiverを登録します.
     *
     */
    private void register() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOKEN_APPROVAL);
        filter.addAction(ACTION_OAUTH_ACCEPT);
        filter.addAction(ACTION_OAUTH_DECLINE);
        LocalBroadcastManager.getInstance(mLocalOAuth2Main.getContext()).registerReceiver(mBroadcastReceiver, filter);
        mLocalOAuth2Main.getContext().registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * 認可の結果を受け取るためのLocalBroadcastReceiverを解除します.
     *
     */
    private void unregister() {
        LocalBroadcastManager.getInstance(mLocalOAuth2Main.getContext()).unregisterReceiver(mBroadcastReceiver);
        mLocalOAuth2Main.getContext().unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * 認可の結果を受け取るためのLocalBroadcastReceiverクラス.
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            String action = intent.getAction();

            //通知経由の場合チャンネルを削除する
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ACTION_OAUTH_ACCEPT.equals(action) || ACTION_OAUTH_DECLINE.equals(action)) {
                    NotificationUtils.cancel(context, NOTIFICATION_ID);
                }
            }
            if (ACTION_TOKEN_APPROVAL.equals(action)) {
                long threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1);
                boolean isApproval = intent.getBooleanExtra(EXTRA_APPROVAL, false);
                processApproval(threadId, isApproval);
            } else if (ACTION_OAUTH_ACCEPT.equals(action) || ACTION_OAUTH_DECLINE.equals(action)) {
                long threadId = intent.getLongExtra(ConfirmAuthActivity.EXTRA_THREAD_ID, -1);
                boolean isApproval = intent.getBooleanExtra(EXTRA_APPROVAL, false);
                processApproval(threadId, isApproval);
            }
        }
    };
}
