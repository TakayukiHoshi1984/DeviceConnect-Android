/*
 UVCDeviceConnectionFragment.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.uvc.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.deviceconnect.android.deviceplugin.uvc.R;
import org.deviceconnect.android.deviceplugin.uvc.UVCDeviceService;
import org.deviceconnect.android.message.DConnectMessageService;


public class UVCDeviceInstructionFragment extends Fragment {
    /**
     * バインドしたサービス.
     */
    private UVCDeviceService mBoundService;


    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_uvc_device_instruction, null);
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
     * {@link UVCDeviceService} にバインドします.
     */
    private void bindMessageService() {
        Intent intent = new Intent();
        intent.setClass(getContext(), UVCDeviceService.class);
        getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * {@link UVCDeviceService} からアンバインドします.
     */
    private void unbindMessageService() {
        try {
            getContext().unbindService(mConnection);
        } catch (Exception e) {
            // ignore.
        }
    }

    /**
     * {@link UVCDeviceService} とのコネクションの通知を受けるリスナー.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            DConnectMessageService.LocalBinder binder = (DConnectMessageService.LocalBinder) service;
            mBoundService = ((UVCDeviceService) binder.getMessageService());
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
