package org.deviceconnect.android.deviceplugin.host.canvas.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.deviceconnect.android.deviceplugin.host.R;

/**
 * CanvasActivityが外部リソースにアクセスする場合に警告を出すダイアログ.
 */
public class ExternalNetworkWarningDialogFragment extends ErrorDialogFragment {
    public static final String EXTERNAL_SHOW_CANVAS_WARNING_TAG = "EXTERNAL_SHOW_CANVAS_WARNING_TAG";


    private static OnWarningDialogListener mListener;
    public static ExternalNetworkWarningDialogFragment createDialog(final Context context,
                                                                final OnWarningDialogListener l) {
        mListener = l;
        return ExternalNetworkWarningDialogFragment.create(EXTERNAL_SHOW_CANVAS_WARNING_TAG,
                context.getString(R.string.host_canvas_warning_dialog_title),
                context.getString(R.string.host_canvas_external_resource_warning_dialog_message),
                context.getString(R.string.host_ok),
                context.getString(R.string.host_cancel)
        );
    }
    /**
     * ボタン有りでAlertDialogを作成します.
     * @param tag タグ
     * @param title タイトル
     * @param message メッセージ
     * @param positive positiveボタン名
     * @param negative negativeボタン名
     * @return AlertDialogFragmentのインスタンス
     */
    private static ExternalNetworkWarningDialogFragment create(final String tag,
                                                    final String title,
                                                    final String message,
                                                    final String positive,
                                                    final String negative) {
        Bundle args = getArguments(tag, title, message, positive, negative);
        ExternalNetworkWarningDialogFragment dialog = new ExternalNetworkWarningDialogFragment();
        dialog.setArguments(args);
        return dialog;
    }
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getArguments().getString(KEY_TITLE));

        if (getArguments().getString(KEY_NEGATIVE) != null) {
            builder.setNegativeButton(getArguments().getString(KEY_NEGATIVE), null);
        }
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.dialog_warning, null);
        LinearLayout checkArea = v.findViewById(R.id.checkArea);
        TextView messageView = v.findViewById(R.id.message);
        messageView.setText(getArguments().getString(KEY_MESSAGE));
        checkArea.setVisibility(View.GONE);
        if (getArguments().getString(KEY_POSITIVE) != null) {
            builder.setPositiveButton(getArguments().getString(KEY_POSITIVE), (dialog, which) -> {
                if (mListener != null) {
                    mListener.onOK();
                }
            });
        }
        if (getArguments().getString(KEY_NEGATIVE) != null) {
            builder.setNegativeButton(getArguments().getString(KEY_NEGATIVE) , (dialog, which) -> {
                if (mListener != null) {
                    mListener.onCancel();
                }
            });
        }

        builder.setView(v);
        return builder.create();
    }

}
