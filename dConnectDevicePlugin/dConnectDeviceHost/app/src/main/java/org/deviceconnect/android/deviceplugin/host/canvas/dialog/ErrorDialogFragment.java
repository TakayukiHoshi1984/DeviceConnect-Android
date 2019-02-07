/*
 ErrorDialogFragment.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.canvas.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;

/**
 * Canvas機能に対するエラーダイログ.
 * @author NTT DOCOMO, INC.
 */
public class ErrorDialogFragment extends DialogFragment {
    /**
     * タグのキーを定義します.
     */
    protected static final String KEY_TAG = "tag";

    /**
     * タイトルのキーを定義します.
     */
    protected static final String KEY_TITLE = "title";

    /**
     * メッセージのキーを定義します.
     */
    protected static final String KEY_MESSAGE = "message";

    /**
     * Positiveボタンのキーを定義します.
     */
    protected static final String KEY_POSITIVE = "yes";

    /**
     * Negativeボタンのキーを定義します.
     */
    protected static final String KEY_NEGATIVE = "no";

    /**
     * デフォルトのチェックボックスのメッセージ.
     */
    protected static final String KEY_DEFAULT_CHECK_MESSAGE = "check_message";

    /**
     * チェック後のチェックボックスのメッセージ.
     */
    protected static final String KEY_CHECKED_MESSAGE = "checked_message";

    /**
     * Dialogに対するリスナーを返す.
     */
    public interface OnWarningDialogListener {
        /**
         * OKが押された場合.
         */
        void onOK();

        /**
         * Cancelが押された場合.
         */
        void onCancel();
    }
    /** Warningリスナー. */
    protected static OnWarningDialogListener mListener;

    /**
     * ボタン無しでAlertDialogを作成します.
     * @param tag タグ
     * @param title タイトル
     * @param message メッセージ
     * @return AlertDialogFragmentのインスタンス
     */
    public static ErrorDialogFragment create(final String tag, final String title, final String message) {
        return create(tag, title, message, null, null, null);
    }

    /**
     * PositiveボタンのみでAlertDialogを作成します.
     * @param tag タグ
     * @param title タイトル
     * @param message メッセージ
     * @param positive positiveボタン名
     * @return AlertDialogFragmentのインスタンス
     */
    public static ErrorDialogFragment create(final String tag, final String title, final String message, final String positive, final OnWarningDialogListener l) {
        return create(tag, title, message, positive, null, l);
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
    private static ErrorDialogFragment create(final String tag, final String title, final String message,
                                              final String positive, final String negative, final OnWarningDialogListener listener) {
        mListener = listener;
        Bundle args = getArguments(tag, title, message, positive, negative);

        ErrorDialogFragment dialog = new ErrorDialogFragment();
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    protected static Bundle getArguments(String tag, String title, String message, String positive, String negative) {
        Bundle args = new Bundle();
        args.putString(KEY_TAG, tag);
        args.putString(KEY_TITLE, title);
        args.putString(KEY_MESSAGE, message);
        if (positive != null) {
            args.putString(KEY_POSITIVE, positive);
        }
        if (negative != null) {
            args.putString(KEY_NEGATIVE, negative);
        }
        return args;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getArguments().getString(KEY_TITLE));
        builder.setMessage(getArguments().getString(KEY_MESSAGE));
        if (getArguments().getString(KEY_POSITIVE) != null) {

            builder.setPositiveButton(getArguments().getString(KEY_POSITIVE), (dialog, which) -> {
                if (mListener != null) {
                    mListener.onOK();
                }
            });
        }
        if (getArguments().getString(KEY_NEGATIVE) != null) {
            builder.setNegativeButton(getArguments().getString(KEY_NEGATIVE), (dialog, which) -> {
                if (mListener != null) {
                    mListener.onCancel();
                }
            });
        }
        return builder.create();
    }

    @Override
    public void onCancel(final DialogInterface dialog) {

    }
}
