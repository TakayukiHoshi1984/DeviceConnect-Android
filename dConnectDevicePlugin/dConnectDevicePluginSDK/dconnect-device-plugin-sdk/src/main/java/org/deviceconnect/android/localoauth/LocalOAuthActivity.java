package org.deviceconnect.android.localoauth;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.deviceconnect.android.localoauth.activity.ConfirmAuthActivity;
import org.deviceconnect.android.localoauth.exception.AuthorizationException;
import org.deviceconnect.android.util.NotificationUtils;
import org.restlet.ext.oauth.PackageInfoOAuth;

import static org.deviceconnect.android.localoauth.LocalOAuth2Main.EXTRA_APPROVAL;

public class LocalOAuthActivity implements LocalOAuth {


    private LocalOAuth2Main mLocalOAuth2Main;


    public LocalOAuthActivity(final Context context) {
        mLocalOAuth2Main = new LocalOAuth2Main(context);
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
            startConfirmAuthActivity(mLocalOAuth2Main.pickupRequest());
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
    public void startConfirmAuthActivity(final ConfirmAuthRequest request) {
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

        // 許可ボタン押下時のIntent
        Intent acceptIntent = new Intent();
        putExtras(context, request, displayScopes, acceptIntent);
        acceptIntent.setAction(ACTION_OAUTH_ACCEPT);
        acceptIntent.putExtra(EXTRA_APPROVAL, true);

        // 拒否ボタン押下時のIntent
        Intent declineIntent = new Intent();
        putExtras(context, request, displayScopes, declineIntent);
        declineIntent.setAction(ACTION_OAUTH_DECLINE);
        declineIntent.putExtra(EXTRA_APPROVAL, false);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            context.startActivity(detailIntent);

            request.startTimer(new ConfirmAuthRequest.OnTimeoutCallback() {
                @Override
                public void onTimeout() {
                    processApproval(request.getThreadId(), false);
                }
            });
        } else {
            //許可するボタン押下時のAction
            Notification.Action acceptAction = new Notification.Action.Builder(null,
                    ACCEPT_BUTTON_TITLE,
                    PendingIntent.getBroadcast(context, 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT)).build();

            //拒否するボタン押下時のAction
            Notification.Action declineAction = new Notification.Action.Builder(null,
                    DECLINE_BUTTON_TITLE,
                    PendingIntent.getBroadcast(context, 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT)).build();

            //詳細を表示ボタン押下時のAction
            Notification.Action detailAction = new Notification.Action.Builder(null,
                    DETAIL_BUTTON_TITLE,
                    PendingIntent.getActivity(context, 3, detailIntent, PendingIntent.FLAG_UPDATE_CURRENT)).build();

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("使用するプロファイル:");
            for (String i : displayScopes) {
                stringBuilder.append(i);
                stringBuilder.append(", ");
            }
            stringBuilder.setLength(stringBuilder.length() - 2);

            NotificationUtils.createNotificationChannel(context);
            NotificationUtils.notify(context, NOTIFICATION_ID, stringBuilder.toString(), acceptAction, declineAction, detailAction);
        }
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
                        startConfirmAuthActivity(nextRequest);
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
