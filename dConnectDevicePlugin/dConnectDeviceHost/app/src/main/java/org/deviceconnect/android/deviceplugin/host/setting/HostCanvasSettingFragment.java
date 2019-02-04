package org.deviceconnect.android.deviceplugin.host.setting;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;

/**
 * Cavansに関する設定を行う.
 */
public class HostCanvasSettingFragment extends Fragment {
    /**
     * Canvasの設定値.
     */
    private HostCanvasSettings mSettings;
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mSettings = new HostCanvasSettings(getContext());
        View rootView = inflater.inflate(R.layout.host_setting_canvas, null);
        Button multipleShowConfirm = rootView.findViewById(R.id.button_multiple_show_permission);
        switchButton(multipleShowConfirm, mSettings.isCanvasActivityNeverShowFlag(), getMultipleShowRes(mSettings.isCanvasActivityNeverShowFlag()));
        multipleShowConfirm.setOnClickListener((view -> {
            boolean enabled = !mSettings.isCanvasActivityNeverShowFlag();
            mSettings.setCanvasActivityNeverShowFlag(enabled);
            switchButton(multipleShowConfirm, enabled, getMultipleShowRes(enabled));
        }));
        Button externalAccessConfirm = rootView.findViewById(R.id.button_external_access_permission);
        switchButton(externalAccessConfirm, mSettings.isCanvasActivityAccessExternalNetworkFlag(),
                    getExternalAccessRes(mSettings.isCanvasActivityAccessExternalNetworkFlag()));
        externalAccessConfirm.setOnClickListener((view -> {
            boolean enabled = !mSettings.isCanvasActivityAccessExternalNetworkFlag();
            mSettings.setCanvasActivityAccessExternalNetworkFlag(enabled);
            switchButton(externalAccessConfirm, enabled, getExternalAccessRes(enabled));
        }));
        return rootView;
    }


    private int getMultipleShowRes(final boolean enabled) {
        if (enabled) {
            return R.string.canvas_settings_multiple_show_permission_on;
        } else {
            return R.string.canvas_settings_multiple_show_permission_off;
        }
    }
    private int getExternalAccessRes(final boolean enabled) {
        if (enabled) {
            return R.string.canvas_settings_external_access_permission_on;
        } else {
            return R.string.canvas_settings_external_access_permission_off;
        }
    }
    /**
     * ボタンのON/OFF.
     * @param confirmBtn ON/OFFを切り替えるボタン
     * @param enabled ONにするかOFFにするか
     */
    private void switchButton(final Button confirmBtn,
                              final boolean enabled,
                              final int textRes) {

        if (enabled) {
            confirmBtn.setBackgroundResource(R.drawable.button_blue);
            confirmBtn.setText(textRes);
        } else {
            confirmBtn.setBackgroundResource(R.drawable.button_red);
            confirmBtn.setText(textRes);
        }
    }
}
