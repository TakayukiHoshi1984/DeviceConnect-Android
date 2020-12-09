/*
 ChromeCastSettingFragmentPage1.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.chromecast.setting;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import org.deviceconnect.android.deviceplugin.chromecast.ChromeCastService;
import org.deviceconnect.android.deviceplugin.chromecast.R;
import org.deviceconnect.android.message.DConnectMessageService;

/**
 * チュートリアル画面.
 * <p>
 * 画面を作成する
 * </p>
 * Chromecastの接続
 * 
 * @author NTT DOCOMO, INC.
 */
public class ChromeCastSettingFragmentPage1 extends Fragment {
    /**
     * バインドしたサービス.
     */
    private ChromeCastService mBoundService;
    @Override
    public View onCreateView(final LayoutInflater inflater,
            final ViewGroup container, final Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.chromecast_settings_step_1, container, false);
        Switch authSwitch = (Switch) root.findViewById(R.id.local_oauth);
        authSwitch.setEnabled(false);
        authSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mBoundService != null) {
                mBoundService.setEnableOAuth(isChecked);
            }
        });
        return root;
    }
    @Override
    public void onResume() {
        super.onResume();
        bindMessageService();
    }

    @Override
    public void onPause() {
        unbindMessageService();
        super.onPause();
    }

    /**
     * {@link ChromeCastService} にバインドします.
     */
    private void bindMessageService() {
        Intent intent = new Intent();
        intent.setClass(getContext(), ChromeCastService.class);
        getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * {@link ChromeCastService} からアンバインドします.
     */
    private void unbindMessageService() {
        try {
            getContext().unbindService(mConnection);
        } catch (Exception e) {
            // ignore.
        }
    }

    /**
     * {@link ChromeCastService} とのコネクションの通知を受けるリスナー.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            DConnectMessageService.LocalBinder binder = (DConnectMessageService.LocalBinder) service;
            mBoundService = ((ChromeCastService) binder.getMessageService());
            if (mBoundService != null) {
                updateLocalOAuth();
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mBoundService = null;
        }
    };

    /**
     * 認可の設定を更新します.
     */
    private void updateLocalOAuth() {
        if (mBoundService != null) {
            getActivity().runOnUiThread(() -> {
                if (mBoundService != null) {
                    Switch authSwitch = (Switch) getActivity().findViewById(R.id.local_oauth);
                    authSwitch.setEnabled(true);
                    authSwitch.setChecked(mBoundService.isEnabledOAuth());
                }
            });
        }
    }

}
