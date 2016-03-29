/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.gcncouponalert.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.android.gcncouponalert.app.data.CouponsContract.LocationEntry;
import com.example.android.gcncouponalert.app.data.CouponsContract.CouponEntry;

/**
 * Manages a local database for weather data.
 */
public class CouponsDbHelper extends SQLiteOpenHelper {

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 11;

    static final String DATABASE_NAME = "gcn_coupon.db";

    public CouponsDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        // Create a table to hold locations.  A location consists of the string supplied in the
        // location setting, the city name, and the latitude and longitude
        final String SQL_CREATE_LOCATION_TABLE = "CREATE TABLE " + LocationEntry.TABLE_NAME + " (" +
                LocationEntry._ID + " INTEGER PRIMARY KEY," +
                LocationEntry.COLUMN_LOCATION_SETTING + " TEXT UNIQUE NOT NULL, " +
                LocationEntry.COLUMN_CITY_NAME + " TEXT NOT NULL, " +
                LocationEntry.COLUMN_COORD_LAT + " REAL NOT NULL, " +
                LocationEntry.COLUMN_COORD_LONG + " REAL NOT NULL " +
                " );";

        final String SQL_CREATE_COUPON_TABLE = "CREATE TABLE " + CouponEntry.TABLE_NAME + " (" +
                CouponEntry._ID + " INTEGER PRIMARY KEY," +
                CouponEntry.COLUMN_LOC_KEY + " INTEGER NOT NULL, " +
                CouponEntry.COLUMN_COUPON_CODE + " TEXT UNIQUE ON CONFLICT IGNORE NOT NULL, " +
                CouponEntry.COLUMN_COUPON_NAME + " TEXT NOT NULL, " +
                CouponEntry.COLUMN_LAST_ACTIVE_DATE + " DATETIME NOT NULL, " +
                CouponEntry.COLUMN_DATE_CREATED + " DATETIME NOT NULL, " +
                CouponEntry.COLUMN_NOTIFIED + " INTEGER DEFAULT 0 NOT NULL, " +
                CouponEntry.COLUMN_COUPON_IMAGE_URL_80x100 + " TEXT, " +
                CouponEntry.COLUMN_COUPON_IMAGE_EXT_80x100 + " TEXT, " +
                CouponEntry.COLUMN_COUPON_REMOTE_ID + " INTEGER DEFAULT 0, " +
                CouponEntry.COLUMN_COUPON_SLOT_INFO + " INTEGER DEFAULT 0, " +
                CouponEntry.COLUMN_COUPON_BRAND_CODE + " INTEGER, " +
                CouponEntry.COLUMN_COUPON_CATEGORY_CODE + " INTEGER, " +
                CouponEntry.COLUMN_EXPIRATION_DATE + " DATETIME NOT NULL, " +
                CouponEntry.COLUMN_BRAND_NAME + " TEXT, " +
                CouponEntry.COLUMN_ADDITIONAL_TEXT + " TEXT, " +
                CouponEntry.COLUMN_SUMMARY_TEXT + " TEXT, " +
                // Set up the location column as a foreign key to location table.
                " FOREIGN KEY (" + CouponEntry.COLUMN_LOC_KEY + ") REFERENCES " +
                LocationEntry.TABLE_NAME + " (" + LocationEntry._ID + ") " +
                " );";



        sqLiteDatabase.execSQL(SQL_CREATE_LOCATION_TABLE);
        sqLiteDatabase.execSQL(SQL_CREATE_COUPON_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        // Note that this only fires if you change the version number for your database.
        // It does NOT depend on the version number for your application.
        // If you want to update the schema without wiping data, commenting out the next 2 lines
        // should be your top priority before modifying this method.
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + LocationEntry.TABLE_NAME);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + CouponEntry.TABLE_NAME);
        onCreate(sqLiteDatabase);
    }
}
