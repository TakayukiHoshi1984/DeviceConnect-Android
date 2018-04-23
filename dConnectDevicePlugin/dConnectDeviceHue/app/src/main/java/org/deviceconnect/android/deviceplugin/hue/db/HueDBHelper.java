/*
HueDBHelper
Copyright (c) 2017 NTT DOCOMO,INC.
Released under the MIT license
http://opensource.org/licenses/mit-license.php
*/
package org.deviceconnect.android.deviceplugin.hue.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;


import org.deviceconnect.android.deviceplugin.hue.service.HueService;

import java.util.ArrayList;
import java.util.List;

/**
 * Hueアクセスポイント情報を格納するDBヘルパークラス.
 * @author NTT DOCOMO, INC.
 */
public class HueDBHelper {

    /**
     * データベース名.
     */
    private static final String DB_NAME = "hue_bridge.db";

    /**
     * データベースのバージョン.
     */
    private static final int DB_VERSION = 2;

    /**
     * アクセスポイントの情報を格納するテーブル名.
     */
    private static final String TBL_NAME = "access_point_tbl";

    /**
     * ユーザ名を格納するカラム名.
     */
    private static final String COL_USER_NAME = "user_name";

    /**
     * IPアドレスを格納するカラム名.
     */
    private static final String COL_IP_ADDRESS = "ip_address";

    /**
     * Macアドレスを格納するカラム.
     */
    private static final String COL_MAC_ADDRESS = "mac_address";
    /**
     * ブリッジがオンラインであるかオフラインであるかを表すフラグ.
     */
    private static final String COL_REGISTER_FLAG = "register_flag";


    /**
     * DB管理ヘルパー.
     */
    private DBHelper mDBHelper;

    HueDBHelper(final Context context) {
        mDBHelper = new DBHelper(context);
    }

    /**
     * アクセスポイントを追加します.
     * @param service 追加するアクセスポイント
     * @return 追加した行番号
     */
    synchronized long addHueBridgeService(final HueService service) {
        ContentValues values = new ContentValues();
        values.put(COL_USER_NAME, service.getName());
        values.put(COL_IP_ADDRESS, service.getId());
        values.put(COL_MAC_ADDRESS, service.getName());
        values.put(COL_REGISTER_FLAG, service.isOnline()? 1 : 0);

        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        try {
            return db.insert(TBL_NAME, null, values);
        } finally {
            db.close();
        }
    }

    /**
     * 指定されたIPアドレスと同じアクセスポイントを削除します.
     * @param ipAddress 削除するアクセスポイントのIPアドレス
     * @return 削除した個数
     */
    synchronized int removeBridgeByIpAddress(final String ipAddress) {
        String whereClause = COL_IP_ADDRESS + "=?";
        String[] whereArgs = {
                ipAddress
        };

        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        try {
            return db.delete(TBL_NAME, whereClause, whereArgs);
        } finally {
            db.close();
        }
    }
    /**
     * ブリッジ一覧を取得します.
     * @return ブリッジ
     */
    synchronized List<HueService> getBridgeServices() {
        String sql = "SELECT * FROM " + TBL_NAME;
        String[] selectionArgs = {};

        SQLiteDatabase db = mDBHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(sql, selectionArgs);
        try {
            List<HueService> results = new ArrayList<HueService>();
            boolean next = cursor.moveToFirst();
            while (next) {
                HueService result = new HueService(
                        cursor.getString(cursor.getColumnIndex(COL_IP_ADDRESS)),
                        cursor.getString(cursor.getColumnIndex(COL_MAC_ADDRESS)));
                result.setOnline(cursor.getInt(cursor.getColumnIndex(COL_REGISTER_FLAG)) == 1);
                results.add(result);
                next = cursor.moveToNext();
            }
            return results;
        } finally {
            cursor.close();
        }
    }
    /**
     * アクセスポイント情報を更新します.
     * @param service 更新するアクセスポイント情報
     * @return 更新したアクセスポイントの個数
     */
    synchronized long updateBridge(final HueService service) {
        ContentValues values = new ContentValues();
        values.put(COL_USER_NAME, service.getName());
        values.put(COL_IP_ADDRESS, service.getId());
        values.put(COL_MAC_ADDRESS, service.getName());
        values.put(COL_REGISTER_FLAG, service.isOnline()? 1 : 0);

        String whereClause = COL_IP_ADDRESS + "=?";
        String[] whereArgs = {
                service.getId()
        };

        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        try {
            return db.update(TBL_NAME, values, whereClause, whereArgs);
        } finally {
            db.close();
        }
    }

    /**
     * 指定したIPのブリッジの情報を返す.
     * @param ip ブリッジのIPアドレス
     * @return ブリッジの情報
     */
    synchronized HueService getBridgeServiceByIpAddress(final String ip) {
        String sql = "SELECT * FROM " + TBL_NAME + " WHERE " + COL_IP_ADDRESS + "=?";
        String[] whereArgs = {
                ip
        };

        SQLiteDatabase db = mDBHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(sql, whereArgs);
        try {
            if (cursor.moveToFirst()) {
                HueService result = new HueService(
                        cursor.getString(cursor.getColumnIndex(COL_IP_ADDRESS)),
                        cursor.getString(cursor.getColumnIndex(COL_MAC_ADDRESS)));
                result.setOnline(cursor.getInt(cursor.getColumnIndex(COL_REGISTER_FLAG)) == 1);
                return result;
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }
    /**
     * 指定されたIPのブリッジが格納されているかを確認します.
     * @param ip 存在確認をするブリッジ
     * @return 存在する場合にはtrue、それ以外はfalse
     */
    synchronized boolean hasBridgeService(final String ip) {
        String sql = "SELECT * FROM " + TBL_NAME + " WHERE " + COL_IP_ADDRESS + "=?";
        String[] whereArgs = {
                ip
        };

        SQLiteDatabase db = mDBHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(sql, whereArgs);
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private static class DBHelper extends SQLiteOpenHelper {
        DBHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            createDB(db);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TBL_NAME);
            createDB(db);
        }

        private void createDB(final SQLiteDatabase db) {
            String sql = "CREATE TABLE " + TBL_NAME + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY, "
                    + COL_USER_NAME + " TEXT NOT NULL, "
                    + COL_IP_ADDRESS + " TEXT NOT NULL, "
                    + COL_MAC_ADDRESS + " TEXT NOT NULL, "
                    + COL_REGISTER_FLAG + " INTEGER"
                    + ");";
            db.execSQL(sql);
        }
    }
}
