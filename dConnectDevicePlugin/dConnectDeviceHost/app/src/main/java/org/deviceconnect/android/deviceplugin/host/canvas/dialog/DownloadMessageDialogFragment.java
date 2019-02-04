package org.deviceconnect.android.deviceplugin.host.canvas.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.deviceconnect.android.deviceplugin.host.R;

/**
 * ダウンロードダイアログ.
 */
public class DownloadMessageDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        String title = getString(R.string.host_canvas_download_title);
        String msg = getString(R.string.host_canvas_download_message);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.dialog_progress, null);
        TextView titleView = v.findViewById(R.id.title);
        TextView messageView = v.findViewById(R.id.message);
        titleView.setText(title);
        messageView.setText(msg);
        builder.setView(v);

        return builder.create();
    }

    @Override
    public void onPause() {
        dismiss();
        super.onPause();
    }
}
