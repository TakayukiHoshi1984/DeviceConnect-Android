/*
 SummaryFragment
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.heartrate.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.deviceconnect.android.deviceplugin.heartrate.HeartRateDeviceService;
import org.deviceconnect.android.deviceplugin.heartrate.R;
import org.deviceconnect.android.deviceplugin.heartrate.ble.BleUtils;
import org.deviceconnect.android.deviceplugin.heartrate.fragment.dialog.ErrorDialogFragment;
import org.deviceconnect.android.message.DConnectMessageService;

/**
 * This fragment explain summary of this device plug-in.
 * @author NTT DOCOMO, INC.
 */
public class SummaryFragment extends Fragment {
    /**
     * Error Dialog.
     */
    private ErrorDialogFragment mErrorDialogFragment;

    /**
     * バインドしたサービス.
     */
    private HeartRateDeviceService mBoundService;
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_summary, null);
        if (!BleUtils.isBLESupported(getActivity())) {
            showErrorDialog();
        }
        Switch authSwitch = (Switch) rootView.findViewById(R.id.local_oauth);
        authSwitch.setEnabled(false);
        authSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mBoundService != null) {
                mBoundService.setEnableOAuth(isChecked);
            }
        });
        return rootView;
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
     * Display the error dialog.
     */
    private void showErrorDialog() {
        dismissErrorDialog();

        Resources res = getActivity().getResources();
        String title = res.getString(R.string.summary_not_support_title);
        String message = res.getString(R.string.summary_not_support_message);
        mErrorDialogFragment = ErrorDialogFragment.newInstance(title, message);
        mErrorDialogFragment.show(getFragmentManager(), "error_dialog");
        mErrorDialogFragment.setOnDismissListener((dialog) -> {
            mErrorDialogFragment = null;
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
    }

    /**
     * Dismiss the error dialog.
     */
    private void dismissErrorDialog() {
        if (mErrorDialogFragment != null) {
            mErrorDialogFragment.dismiss();
            mErrorDialogFragment = null;
        }
    }
    /**
     * {@link HeartRateDeviceService} にバインドします.
     */
    private void bindMessageService() {
        Intent intent = new Intent();
        intent.setClass(getContext(), HeartRateDeviceService.class);
        getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * {@link HeartRateDeviceService} からアンバインドします.
     */
    private void unbindMessageService() {
        try {
            getContext().unbindService(mConnection);
        } catch (Exception e) {
            // ignore.
        }
    }

    /**
     * {@link HeartRateDeviceService} とのコネクションの通知を受けるリスナー.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            DConnectMessageService.LocalBinder binder = (DConnectMessageService.LocalBinder) service;
            mBoundService = ((HeartRateDeviceService) binder.getMessageService());
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
