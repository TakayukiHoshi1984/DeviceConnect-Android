package org.deviceconnect.android.deviceplugin.midi;

import androidx.fragment.app.Fragment;

import org.deviceconnect.android.deviceplugin.midi.fragment.BluetoothSettingsFragment;
import org.deviceconnect.android.deviceplugin.midi.fragment.MidiDeviceSettingsFragment;
import org.deviceconnect.android.deviceplugin.midi.fragment.SummaryFragment;
import org.deviceconnect.android.ui.activity.DConnectSettingPageFragmentActivity;

public class DConnectMidiSettingsActivity extends DConnectSettingPageFragmentActivity {

    @Override
    public int getPageCount() {
        return 3;
    }

    @Override
    public Fragment createPage(int position) {
        switch (position) {
            case 0:
                return new SummaryFragment();
            case 1:
                return new BluetoothSettingsFragment();
            case 2:
                return new MidiDeviceSettingsFragment();
            default:
                return null;
        }
    }
}
