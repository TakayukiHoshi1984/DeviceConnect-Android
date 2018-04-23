/*
HueFragment01
Copyright (c) 2014 NTT DOCOMO,INC.
Released under the MIT license
http://opensource.org/licenses/mit-license.php
 */

package org.deviceconnect.android.deviceplugin.hue.activity.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.philips.lighting.hue.sdk.wrapper.discovery.BridgeDiscoveryResult;

import org.deviceconnect.android.deviceplugin.hue.db.HueManager;
import org.deviceconnect.android.deviceplugin.hue.R;
import org.deviceconnect.android.deviceplugin.hue.service.HueService;

import java.util.ArrayList;
import java.util.List;

/**
 * Hue設定画面(1)フラグメント.
 */
public class HueFragment01 extends Fragment implements OnClickListener, OnItemClickListener {

    /** ListViewのAdapter. */
    private CustomAdapter mAdapter;

    /** ProgressZone. */
    private View mProgressView;

    /** 再検索ボタン. */
    private Button mSearchButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HueManager.INSTANCE.init(getContext());
    }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateView(final LayoutInflater inflater,
            final ViewGroup container, final Bundle savedInstanceState) {


        View rootView = inflater.inflate(R.layout.hue_fragment_01, container, false);
        if (rootView != null) {
            mSearchButton = (Button) rootView.findViewById(R.id.btnRefresh);
            mSearchButton.setOnClickListener(this);

            mProgressView = rootView.findViewById(R.id.progress_zone);
            mProgressView.setVisibility(View.VISIBLE);

            mAdapter = new CustomAdapter(getActivity().getBaseContext());

            ListView listView = (ListView) rootView.findViewById(R.id.bridge_list2);
            listView.setOnItemClickListener(this);
            View headerView = inflater.inflate(R.layout.hue_fragment_01_header, null, false);
            listView.addHeaderView(headerView, null, false);
            listView.setAdapter(mAdapter);
        }

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isWifiEnabled()) {
            // ローカルBridgeのUPNP Searchを開始する.
            doBridgeSearch();
        } else {
            mProgressView.setVisibility(View.GONE);
            mSearchButton.setVisibility(View.VISIBLE);
            showWifiNotConnected();
        }
    }

    @Override
    public void onPause() {
        HueManager.INSTANCE.stopBridgeDiscovery();
        super.onPause();
    }

    @Override
    public void onClick(final View v) {
        // 検索処理を再度実行.
        if (isWifiEnabled()) {
            doBridgeSearch();
        } else {
            showWifiNotConnected();
        }
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        moveNextFragment((BridgeDiscoveryResult) mAdapter.getItem(position));
    }

    private void runOnUiThread(final Runnable run) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(run);
        }
    }

    /**
     * ローカルBridgeのUPNP Searchを開始する.
     */
    private void doBridgeSearch() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressView.setVisibility(View.VISIBLE);
                mSearchButton.setVisibility(View.GONE);
            }
        });
        HueManager.INSTANCE.startBridgeDiscovery(new HueManager.HueBridgeDiscoveryListener() {
            @Override
            public void onFoundedBridge(final List<BridgeDiscoveryResult> results) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.updateData(results);
                        mProgressView.setVisibility(View.GONE);
                        mSearchButton.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    /**
     * Wi-Fi接続が無効になっている場合のエラーダイアログを表示します.
     */
    private void showWifiNotConnected() {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.hue_dialog_network_error)
                            .setMessage(R.string.hue_dialog_not_connect_wifi)
                            .setPositiveButton(R.string.hue_dialog_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialog, final int which) {
                                }
                            })
                            .setCancelable(false)
                            .show();
                }
            });
        }
    }

    /**
     * 指定されたアクセスポイントを指定して、次のフラグメントを開く.
     * @param bridge アクセスポイント
     */
    private void moveNextFragment(final BridgeDiscoveryResult bridge) {
        FragmentManager manager = getFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.setCustomAnimations(R.anim.fragment_slide_right_enter, R.anim.fragment_slide_left_exit,
                R.anim.fragment_slide_left_enter, R.anim.fragment_slide_right_exit);
        transaction.replace(R.id.fragment_frame, HueFragment02.newInstance(bridge));
        transaction.commit();
    }

    /**
     * Wi-Fi接続設定の状態を取得します.
     * @return trueの場合は有効、それ以外の場合は無効
     */
    private boolean isWifiEnabled() {
        WifiManager mgr = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return mgr.isWifiEnabled();
    }

    /**
     * カスタムAdapter.
     */
    private class CustomAdapter extends BaseAdapter {
        /** コンテキスト. */
        private final Context mContext;

        /** Access Point. */
        private List<BridgeDiscoveryResult> mBridges;

        /**
         * コンストラクタ.
         * 
         * @param context コンテキスト
         */
        CustomAdapter(final Context context) {
            mContext = context;
            mBridges = new ArrayList<>();
        }

        /**
         * Access Pointリストのアップデートを行う.
         * 
         * @param bridges Access Point
         */
        private void updateData(final List<BridgeDiscoveryResult> bridges) {
            mBridges = bridges;
            notifyDataSetChanged();
        }

        @SuppressLint("ViewHolder")
        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView = inflater.inflate(R.layout.hue_list, parent, false);

            TextView mTextView = (TextView) rowView.findViewById(R.id.row_textview1);

            String listTitle = mBridges.get(position).getUniqueID() + "("
                    + mBridges.get(position).getIP() + ")";
            mTextView.setText(listTitle);
            HueService service = new HueService(mBridges.get(position).getIP(), mBridges.get(position).getUniqueID());
            HueManager.INSTANCE.saveBridgeForDB(service);
            return rowView;
        }

        @Override
        public int getCount() {
            return mBridges.size();
        }

        @Override
        public Object getItem(final int position) {
            return mBridges.get(position - 1);
        }

        @Override
        public long getItemId(final int position) {
            return 0;
        }
    }
}
