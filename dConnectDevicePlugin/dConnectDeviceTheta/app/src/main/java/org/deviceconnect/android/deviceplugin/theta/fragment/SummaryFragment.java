/*
 SummaryFragment
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Switch;

import org.deviceconnect.android.deviceplugin.theta.R;
import org.deviceconnect.android.deviceplugin.theta.ThetaDeviceService;
import org.deviceconnect.android.deviceplugin.theta.activity.ThetaDeviceSettingsActivity;
import org.deviceconnect.android.deviceplugin.theta.core.ThetaDeviceModel;
import org.deviceconnect.android.message.DConnectMessageService;

/**
 * The page which summarize the settings window of THETA device plug-in.
 *
 * @author NTT DOCOMO, INC.
 */
public class SummaryFragment extends SettingsFragment implements RadioGroup.OnCheckedChangeListener {

    private View mRoot;
    /**
     * バインドしたサービス.
     */
    private ThetaDeviceService mBoundService;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        if (mRoot == null) {
            mRoot = inflater.inflate(R.layout.fragment_summary, null);
            RadioGroup group = (RadioGroup) mRoot.findViewById(R.id.settings_theta);
            group.setOnCheckedChangeListener(this);
            Switch authSwitch = (Switch) mRoot.findViewById(R.id.local_oauth);
            authSwitch.setEnabled(false);
            authSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (mBoundService != null) {
                    mBoundService.setEnableOAuth(isChecked);
                }
            });
        }
        return mRoot;
    }

    @Override
    public void onCheckedChanged(final RadioGroup radioGroup, final int id) {
        ThetaDeviceModel model;
        switch (id) {
            case R.id.settings_theta_s:
                model = ThetaDeviceModel.THETA_S;
                break;
            case R.id.settings_theta_m15:
                model = ThetaDeviceModel.THETA_M15;
                break;
            default:
                return;
        }

        ThetaDeviceSettingsActivity activity = (ThetaDeviceSettingsActivity) getActivity();
        if (activity != null) {
            activity.setSelectedModel(model);
        }
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
     * {@link ThetaDeviceService} にバインドします.
     */
    private void bindMessageService() {
        Intent intent = new Intent();
        intent.setClass(getContext(), ThetaDeviceService.class);
        getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * {@link ThetaDeviceService} からアンバインドします.
     */
    private void unbindMessageService() {
        try {
            getContext().unbindService(mConnection);
        } catch (Exception e) {
            // ignore.
        }
    }

    /**
     * {@link ThetaDeviceService} とのコネクションの通知を受けるリスナー.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            DConnectMessageService.LocalBinder binder = (DConnectMessageService.LocalBinder) service;
            mBoundService = ((ThetaDeviceService) binder.getMessageService());
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
