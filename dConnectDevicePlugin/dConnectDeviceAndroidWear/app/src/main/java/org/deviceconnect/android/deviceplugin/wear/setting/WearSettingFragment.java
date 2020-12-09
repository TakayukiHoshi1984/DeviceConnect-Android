/*
 WearSettingFragment.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.wear.setting;

import org.deviceconnect.android.deviceplugin.wear.R;
import org.deviceconnect.android.deviceplugin.wear.WearDeviceService;
import org.deviceconnect.android.message.DConnectMessageService;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;

/**
 * Setting screen Fragment.
 * 
 * @author NTT DOCOMO, INC.
 */
public class WearSettingFragment extends Fragment implements OnClickListener {

    /** ImageView. */
    private ImageView mImageView;
    /**
     * バインドしたサービス.
     */
    private WearDeviceService mBoundService;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // Get position.
        Bundle mBundle = getArguments();
        int mPagePosition = mBundle.getInt("position", 0);

        int mPageLayoutId = this.getResources().getIdentifier("wear_setting_" + mPagePosition, "layout",
                getActivity().getPackageName());

        View mView = inflater.inflate(mPageLayoutId, container, false);

        if (mPagePosition == 0) {
            mImageView = (ImageView) mView.findViewById(R.id.dconnect_settings_googleplay);
            mImageView.setOnClickListener(this);
        }
        Switch authSwitch = (Switch) mView.findViewById(R.id.local_oauth);
        authSwitch.setEnabled(false);
        authSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mBoundService != null) {
                mBoundService.setEnableOAuth(isChecked);
            }
        });
        return mView;
    }

    @Override
    public void onClick(final View v) {
        if (v.equals(mImageView)) {
            Uri uri = Uri.parse("market://details?id=com.google.android.wearable.app");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
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
     * {@link WearDeviceService} にバインドします.
     */
    private void bindMessageService() {
        Intent intent = new Intent();
        intent.setClass(getContext(), WearDeviceService.class);
        getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * {@link WearDeviceService} からアンバインドします.
     */
    private void unbindMessageService() {
        try {
            getContext().unbindService(mConnection);
        } catch (Exception e) {
            // ignore.
        }
    }

    /**
     * {@link WearDeviceService} とのコネクションの通知を受けるリスナー.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            DConnectMessageService.LocalBinder binder = (DConnectMessageService.LocalBinder) service;
            mBoundService = ((WearDeviceService) binder.getMessageService());
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
