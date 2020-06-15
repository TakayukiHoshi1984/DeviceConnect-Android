package org.deviceconnect.android.localoauth;

import android.content.Context;
import android.os.Build;


public final class LocalOAuthFactory {
    private LocalOAuthFactory() {}
    public static LocalOAuth create(final Context context) {
        if (context == null) {
            throw new NullPointerException("context is null.");
        }

        LocalOAuth lOauth;
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            lOauth = new LocalOAuthActivity(context);
        } else {
            lOauth = new LocalOAuthNotification(context);
        }
        return lOauth;
    }
}
