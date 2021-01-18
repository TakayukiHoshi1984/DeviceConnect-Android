package org.deviceconnect.android.activity;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import androidx.annotation.NonNull;

import org.deviceconnect.android.util.NotificationUtils;

/**
 * An Activity with interfaces to make a request for permissions and return the
 * result.
 *
 * @author NTT DOCOMO, INC.
 */
public class PermissionRequestActivity extends Activity {
    static final String EXTRA_PERMISSIONS = "EXTRA_PERMISSIONS";
    static final String EXTRA_GRANT_RESULTS = "EXTRA_GRANT_RESULTS";

    private static final String EXTRA_CALLBACK = "EXTRA_CALLBACK";
    private static final int REQUEST_CODE = 123456789;

    /** Notification Content */
    private static final String NOTIFICATION_CONTENT = "実行時権限の要求";

    /** Notification Id */
    private static final int NOTIFICATION_ID = 3467;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            Intent callingIntent = getIntent();
            if (callingIntent == null || callingIntent.getExtras() == null || !callingIntent.hasExtra(EXTRA_PERMISSIONS)
                    || !callingIntent.hasExtra(EXTRA_CALLBACK)) {
                finish();
                return;
            }

            Bundle extras = callingIntent.getExtras();
            String[] permissions = extras.getStringArray(EXTRA_PERMISSIONS);
            if (permissions == null) {
                finish();
                return;
            }

            requestPermissions(permissions, REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
        }

        Intent callingIntent = getIntent();
        if (callingIntent == null || callingIntent.getExtras() == null || !callingIntent.hasExtra(EXTRA_CALLBACK)) {
            finish();
            return;
        }

        Bundle extras = callingIntent.getExtras();
        ResultReceiver callback = extras.getParcelable(EXTRA_CALLBACK);
        if (callback == null) {
            finish();
            return;
        }

        Bundle resultData = new Bundle();
        resultData.putStringArray(EXTRA_PERMISSIONS, permissions);
        resultData.putIntArray(EXTRA_GRANT_RESULTS, grantResults);
        callback.send(Activity.RESULT_OK, resultData);
        finish();
    }

    public static void requestPermissions(@NonNull Context context, @NonNull String[] permissions,
            @NonNull ResultReceiver resultReceiver, @NonNull boolean isActivity) {
        Intent callIntent = new Intent(context, PermissionRequestActivity.class);
        callIntent.putExtra(EXTRA_PERMISSIONS, permissions);
        callIntent.putExtra(EXTRA_CALLBACK, resultReceiver);
        // NOTE: FLAG_ACTIVITY_SINGLE_TASK causes Activity#onActivityResult()
        // being called prematurely. FLAG_ACTIVITY_SINGLE_TOP, on the other
        // hand, does not cause that.
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || isActivity) {
            context.startActivity(callIntent);
        } else {
            NotificationUtils.createNotificationChannel(context);
            NotificationUtils.notify(context, NOTIFICATION_ID, REQUEST_CODE, callIntent, NOTIFICATION_CONTENT);
        }
    }
}
