/*
 ContinuousAccessConfirmDialogFragment.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.canvas.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;

/**
 * 連続でCanvasが起動された場合の警告ダイログ.
 * @author NTT DOCOMO, INC.
 */
public class ContinuousAccessConfirmDialogFragment extends ErrorDialogFragment {
    /** Multiple Show Warning Dialogのタグ名. */
    public static final String MULTIPLE_SHOW_CANVAS_WARNING_TAG = "MULTIPLE_SHOW_CANVAS_WARNING_TAG";
    /** ダイアログのリスナー. */
    private static OnWarningDialogListener mListener;

    /**
     * ダイアログの作成.
     * @param context コンテキスト
     * @param l リスナー
     * @return ダイアログのインスタンス
     */
    public static ContinuousAccessConfirmDialogFragment createDialog(final Context context,
                                                                     final OnWarningDialogListener l) {
        mListener = l;
        return ContinuousAccessConfirmDialogFragment.create(MULTIPLE_SHOW_CANVAS_WARNING_TAG,
                context.getString(R.string.host_canvas_warning_dialog_title),
                context.getString(R.string.host_canvas_multiple_warning_dialog_message),
                context.getString(R.string.host_canvas_warning_dialog_check),
                context.getString(R.string.host_canvas_warning_dialog_uncheck),
                context.getString(R.string.host_ok),
                null
                );
    }
    /**
     * ボタン有りでAlertDialogを作成します.
     * @param tag タグ
     * @param title タイトル
     * @param message メッセージ
     * @param checkMessage checkボックスのメッセージ
     * @param checkedMessage チェック後のメッセージ
     * @param positive positiveボタン名
     * @param negative negativeボタン名
     * @return AlertDialogFragmentのインスタンス
     */
    private static ContinuousAccessConfirmDialogFragment create(final String tag,
                                                                final String title,
                                                                final String message,
                                                                final String checkMessage,
                                                                final String checkedMessage,
                                                                final String positive,
                                                                final String negative) {
        Bundle args = getArguments(tag, title, message, positive, negative);
        args.putString(KEY_DEFAULT_CHECK_MESSAGE, checkMessage);
        args.putString(KEY_CHECKED_MESSAGE, checkedMessage);
        ContinuousAccessConfirmDialogFragment dialog = new ContinuousAccessConfirmDialogFragment();
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

        final HostCanvasSettings settings = new HostCanvasSettings(getActivity());
        final CheckBox checked = v.findViewById(R.id.checkWarn);
        checkArea.setVisibility(View.VISIBLE);
        final TextView checkMessage = v.findViewById(R.id.checkMessage);
        checkMessage.setText(getArguments().getString(KEY_DEFAULT_CHECK_MESSAGE));
        checkMessage.setOnClickListener((view) -> {
            checked.setChecked(!checked.isChecked());
        });
        checked.setChecked(!settings.isCanvasActivityNeverShowFlag());
        checked.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                checkMessage.setText(getArguments().getString(KEY_CHECKED_MESSAGE));
            } else {
                checkMessage.setText(getArguments().getString(KEY_DEFAULT_CHECK_MESSAGE));
            }
        });
        if (getArguments().getString(KEY_POSITIVE) != null) {
            builder.setPositiveButton(getArguments().getString(KEY_POSITIVE), (dialog, which) -> {
                boolean enabled = checked.isChecked();
                settings.setCanvasActivityNeverShowFlag(!enabled);
                if (enabled) {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.finish();
                    }
                } else {
                    if (mListener != null) {
                        mListener.onOK();
                    }
                }
            });
        }
        if (getArguments().getString(KEY_NEGATIVE) != null) {
            builder.setNegativeButton(getArguments().getString(KEY_NEGATIVE), null);
        }
        builder.setView(v);
        return builder.create();
    }
}
