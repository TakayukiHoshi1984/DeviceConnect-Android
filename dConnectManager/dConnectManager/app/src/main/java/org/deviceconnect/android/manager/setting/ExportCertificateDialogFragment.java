package org.deviceconnect.android.manager.setting;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.deviceconnect.android.manager.DConnectService;
import org.deviceconnect.android.manager.R;

public class ExportCertificateDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        String fileDirsStr = getContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).getPath();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_export_certificate, null);

        final EditText exportPathEdit = (EditText) view.findViewById(R.id.export_certificate_path);
        exportPathEdit.setText(fileDirsStr, TextView.BufferType.EDITABLE);

        builder.setView(view)
                .setTitle(R.string.activity_settings_export_server_certificate_dialog_path_title)
                .setPositiveButton(R.string.activity_settings_export_server_certificate_dialog_button_export, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialogInterface, final int i) {
                        Activity activity = getActivity();
                        if (activity != null && activity instanceof SettingActivity) {
                            final String exportPath = exportPathEdit.getText().toString();
                            DConnectService service = ((SettingActivity) activity).getManagerService();
                            if (service != null) {
                                service.exportCertificate(exportPath);
                            }
                        }
                    }
                })
                .setNegativeButton(R.string.activity_settings_export_server_certificate_dialog_button_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialogInterface, final int i) {
                        // NOP.
                    }
                });

        return builder.create();
    }

}