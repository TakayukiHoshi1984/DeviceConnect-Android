package org.deviceconnect.android.localoauth;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.deviceconnect.android.localoauth.activity.ConfirmAuthActivity;
import org.deviceconnect.android.util.NotificationUtils;

import static org.deviceconnect.android.localoauth.LocalOAuth2Main.EXTRA_APPROVAL;

public class LocalOAuthNotification extends LocalOAuthActivity {
    public LocalOAuthNotification(Context context) {
        super(context);
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

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
}
