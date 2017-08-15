package com.mydomain.measurement;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import org.deviceconnect.android.message.DConnectMessageServiceProvider;

public class MyMessageServiceProvider<T extends Service> extends DConnectMessageServiceProvider<Service> {
    @SuppressWarnings("unchecked")
    @Override
    protected Class<Service> getServiceClass() {
        Class<? extends Service> clazz = (Class<? extends Service>) MyMessageService.class;
        return (Class<Service>) clazz;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d("DozeTest", "MyMessageServiceProvider: onReceive: action = " + action);
        if (action == null) {
            return;
        }
        super.onReceive(context, intent);
    }
}