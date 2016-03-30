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

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

public class CouponsProvider extends ContentProvider {
    public static final String LOG_TAG = CouponsProvider.class.getSimpleName();

    // The URI Matcher used by this content provider.
    private static final UriMatcher sUriMatcher = buildUriMatcher();
    private CouponsDbHelper mOpenHelper;

    static final int LOCATION = 300;
    static final int COUPON = 400; // this seems like a generic deal
    static final int COUPON_WITH_LOCATION = 401;
    static final int COUPON_WITH_ID = 402;
    static final int COUPON_WITH_LOCATION_NOT_NOTIFIED = 403;
    static final int BRAND = 500;

    private static final SQLiteQueryBuilder sCouponByLocationSettingQueryBuilder;

    static{
        sCouponByLocationSettingQueryBuilder = new SQLiteQueryBuilder();

        //This is an inner join which looks like
        //weather INNER JOIN location ON weather.location_id = location._id
        sCouponByLocationSettingQueryBuilder.setTables(
                CouponsContract.CouponEntry.TABLE_NAME +
                        " INNER JOIN " +
                        CouponsContract.LocationEntry.TABLE_NAME +
                        " ON " + CouponsContract.CouponEntry.TABLE_NAME +
                        "." + CouponsContract.CouponEntry.COLUMN_LOC_KEY +
                        " = " + CouponsContract.LocationEntry.TABLE_NAME +
                        "." + CouponsContract.LocationEntry._ID +
                        " INNER JOIN " +
                        CouponsContract.BrandEntry.TABLE_NAME +
                        " ON " + CouponsContract.CouponEntry.TABLE_NAME +
                        "." + CouponsContract.CouponEntry.COLUMN_BRAND_KEY +
                        " = " + CouponsContract.BrandEntry.TABLE_NAME +
                        "." + CouponsContract.BrandEntry._ID);
    }

    //location.location_setting = ?
    private static final String sLocationSettingSelection =
            CouponsContract.LocationEntry.TABLE_NAME+
                    "." + CouponsContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ? ";

    private static final String sLocationSettingNotNotifiedSelection =
            CouponsContract.LocationEntry.TABLE_NAME +
                    "." + CouponsContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ? AND " +
                    CouponsContract.CouponEntry.TABLE_NAME+"."+ CouponsContract.CouponEntry.COLUMN_NOTIFIED + " = 0 ";


    private Cursor getCouponByLocationSettingNotNotified(Uri uri, String[] projection, String sortOrder) {
        String locationSetting = CouponsContract.CouponEntry.getLocationSettingFromUri(uri);

        String selection = sLocationSettingNotNotifiedSelection;
        String[] selectionArgs = new String[]{locationSetting};
        /*
        String queryString = sCouponByLocationSettingQueryBuilder.buildQuery(
                projection,
                selection,
                null,
                null,
                sortOrder,
                null
        );
        Log.d(LOG_TAG,"getCouponByLocationSettingNotNotified query:"+queryString);
        */
        return sCouponByLocationSettingQueryBuilder.query(mOpenHelper.getReadableDatabase(),
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
        );
    }

    private Cursor getCouponByID(Uri uri, String[] projection, String sortOrder) {
        //String locationSetting = CouponsContract.CouponEntry.getLocationSettingFromUri(uri);
        String couponID = CouponsContract.CouponEntry.getCouponIDFromUri(uri);

        String selection = CouponsContract.CouponEntry.TABLE_NAME+
                "."+CouponsContract.CouponEntry._ID + " = ? ";

        String[] selectionArgs = new String[]{couponID};

        return sCouponByLocationSettingQueryBuilder.query(mOpenHelper.getReadableDatabase(),
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
        );
    }

    private Cursor getCouponByLocationSetting(Uri uri, String[] projection, String sortOrder) {
        String locationSetting = CouponsContract.CouponEntry.getLocationSettingFromUri(uri);

        String selection = sLocationSettingSelection;
        String[] selectionArgs = new String[]{locationSetting};

        return sCouponByLocationSettingQueryBuilder.query(mOpenHelper.getReadableDatabase(),
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
        );
    }




    /*
        Students: Here is where you need to create the UriMatcher. This UriMatcher will
        match each URI to the WEATHER, WEATHER_WITH_LOCATION, WEATHER_WITH_LOCATION_AND_DATE,
        and LOCATION integer constants defined above.  You can test this by uncommenting the
        testUriMatcher test within TestUriMatcher.
     */
    static UriMatcher buildUriMatcher() {
        // I know what you're thinking.  Why create a UriMatcher when you can use regular
        // expressions instead?  Because you're not crazy, that's why.

        // All paths added to the UriMatcher have a corresponding code to return when a match is
        // found.  The code passed into the constructor represents the code to return for the root
        // URI.  It's common to use NO_MATCH as the code for this case.
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = CouponsContract.CONTENT_AUTHORITY;

        // For each type of URI you want to add, create a corresponding code.


        matcher.addURI(authority, CouponsContract.PATH_LOCATION, LOCATION);
        matcher.addURI(authority, CouponsContract.PATH_BRAND, BRAND);

        matcher.addURI(authority, CouponsContract.PATH_COUPON, COUPON); // note to udacity: this does not really work like you think it does.
        matcher.addURI(authority, CouponsContract.PATH_COUPON + "/#", COUPON_WITH_LOCATION);
        matcher.addURI(authority, CouponsContract.PATH_COUPON + "/id/#", COUPON_WITH_ID);
        matcher.addURI(authority, CouponsContract.PATH_COUPON + "/#/not-notified", COUPON_WITH_LOCATION_NOT_NOTIFIED);
        return matcher;
    }

    /*
        Students: We've coded this for you.  We just create a new CouponsDbHelper for later use
        here.
     */
    @Override
    public boolean onCreate() {
        mOpenHelper = new CouponsDbHelper(getContext());
        return true;
    }

    /*
        Students: Here's where you'll code the getType function that uses the UriMatcher.  You can
        test this by uncommenting testGetType in TestProvider.

     */
    @Override
    public String getType(Uri uri) {

        // Use the Uri Matcher to determine what kind of URI this is.
        final int match = sUriMatcher.match(uri);

        switch (match) {
            // Student: Uncomment and fill out these two cases
            case LOCATION:
                return CouponsContract.LocationEntry.CONTENT_TYPE;
            case BRAND:
                return CouponsContract.BrandEntry.CONTENT_TYPE;
            case COUPON:
                return CouponsContract.CouponEntry.CONTENT_TYPE;
            case COUPON_WITH_LOCATION:
                return CouponsContract.CouponEntry.CONTENT_TYPE;
            case COUPON_WITH_ID:
                return CouponsContract.CouponEntry.CONTENT_ITEM_TYPE;
            case COUPON_WITH_LOCATION_NOT_NOTIFIED:
                return CouponsContract.CouponEntry.CONTENT_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // Here's the switch statement that, given a URI, will determine what kind of request it is,
        // and query the database accordingly.
        Cursor retCursor;
        switch (sUriMatcher.match(uri)) {
            // "location"
            case LOCATION: {
                retCursor = mOpenHelper.getReadableDatabase().query(
                        CouponsContract.LocationEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            }

            case BRAND: {
                retCursor = mOpenHelper.getReadableDatabase().query(
                        CouponsContract.BrandEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            }

            case COUPON: {
                retCursor = mOpenHelper.getReadableDatabase().query(
                        CouponsContract.CouponEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                //Log.d(LOG_TAG,"COUPON: "+selection);
                break;
            }

            case COUPON_WITH_ID: {
                /*
                retCursor = mOpenHelper.getReadableDatabase().query(
                        CouponsContract.CouponEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                */
                //Log.d(LOG_TAG,"COUPON_WITH_ID: "+selection);
                retCursor = getCouponByID(uri, projection, sortOrder);
                break;
            }

            case COUPON_WITH_LOCATION: {
                //Log.d(LOG_TAG,"COUPON_WITH_LOCATION: "+selection);
                retCursor = getCouponByLocationSetting(uri, projection, sortOrder);
                break;

            }

            case COUPON_WITH_LOCATION_NOT_NOTIFIED: {
                retCursor = getCouponByLocationSettingNotNotified(uri, projection, sortOrder);
                break;

            }

            /*
            case COUPON_WITH_LOCATION_AND_DATE: {
                retCursor = getCouponByLocationSettingAndDate(uri, projection, sortOrder);
                break;

            }
            */

            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        //Log.d(LOG_TAG,retCursor.toString());
        retCursor.setNotificationUri(getContext().getContentResolver(), uri);
        return retCursor;
    }

    /*
        Student: Add the ability to insert Locations to the implementation of this function.
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        Uri returnUri;

        switch (match) {
            case LOCATION: {
                long _id = db.insert(CouponsContract.LocationEntry.TABLE_NAME, null, values);
                if ( _id > 0 )
                    returnUri = CouponsContract.LocationEntry.buildLocationUri(_id);
                else
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                break;
            }
            case BRAND: {
                long _id = db.insert(CouponsContract.BrandEntry.TABLE_NAME, null, values);
                if ( _id > 0 )
                    returnUri = CouponsContract.BrandEntry.buildBrandUri(_id);
                else
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                break;
            }
            case COUPON: {
                long _id = db.insert(CouponsContract.CouponEntry.TABLE_NAME, null, values);
                if ( _id > 0 )
                    returnUri = CouponsContract.CouponEntry.buildCouponUri(_id);
                else
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                break;
            }
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return returnUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        int rowsDeleted;
        // this makes delete all rows return the number of rows deleted
        if ( null == selection ) selection = "1";
        switch (match) {
            case LOCATION:
                rowsDeleted = db.delete(
                        CouponsContract.LocationEntry.TABLE_NAME, selection, selectionArgs);
                break;
            case BRAND:
                rowsDeleted = db.delete(
                        CouponsContract.BrandEntry.TABLE_NAME, selection, selectionArgs);
                break;
            case COUPON:
                rowsDeleted = db.delete(
                        CouponsContract.CouponEntry.TABLE_NAME, selection, selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        // Because a null deletes all rows
        if (rowsDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsDeleted;
    }

    /*
    private void normalizeDate(ContentValues values) {
        // normalize the date value
        if (values.containsKey(CouponsContract.WeatherEntry.COLUMN_DATE)) {
            long dateValue = values.getAsLong(CouponsContract.WeatherEntry.COLUMN_DATE);
            values.put(CouponsContract.WeatherEntry.COLUMN_DATE, CouponsContract.normalizeDate(dateValue));
        }
    }
    */

    @Override
    public int update(
            Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        int rowsUpdated;

        switch (match) {
            case LOCATION:
                rowsUpdated = db.update(CouponsContract.LocationEntry.TABLE_NAME, values, selection,
                        selectionArgs);
                break;
            case BRAND:
                rowsUpdated = db.update(CouponsContract.BrandEntry.TABLE_NAME, values, selection,
                        selectionArgs);
                break;
            case COUPON:
                rowsUpdated = db.update(CouponsContract.CouponEntry.TABLE_NAME, values, selection,
                        selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsUpdated;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case COUPON: {
                db.beginTransaction();
                int returnCount = 0;
                int rowsUpdated = 0;
                try {
                    for (ContentValues value : values) {
                        //normalizeDate(value);
                        long _id = db.insert(CouponsContract.CouponEntry.TABLE_NAME, null, value);
                        if (_id != -1) {
                            returnCount++;
                        } else {
                            // update the last_active_date and location
                            ContentValues update_stuff = new ContentValues();
                            update_stuff.put(CouponsContract.CouponEntry.COLUMN_LAST_ACTIVE_DATE, (String) value.get(CouponsContract.CouponEntry.COLUMN_COUPON_CODE));
                            update_stuff.put(CouponsContract.CouponEntry.COLUMN_LOC_KEY, (Long) value.get(CouponsContract.CouponEntry.COLUMN_LOC_KEY));
                            rowsUpdated += db.update(CouponsContract.CouponEntry.TABLE_NAME, update_stuff, CouponsContract.CouponEntry.COLUMN_COUPON_CODE+" = "+value.get(CouponsContract.CouponEntry.COLUMN_COUPON_CODE), null);
                            //getContext().getContentResolver().update(CouponsContract.CouponEntry.CONTENT_URI, notified_flag, CouponsContract.CouponEntry.COLUMN_NOTIFIED + " = 0", null);
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                if (returnCount > 0) {
                    //getContext().getContentResolver().notifyChange(uri, null, false);
                }
                Log.d(LOG_TAG, "returnCount: " + returnCount + "; rowsUpdated: " + rowsUpdated);
                return (returnCount+rowsUpdated);
            }
            default:
                return super.bulkInsert(uri, values);
        }
    }

    // You do not need to call this method. This is a method specifically to assist the testing
    // framework in running smoothly. You can read more at:
    // http://developer.android.com/reference/android/content/ContentProvider.html#shutdown()
    @Override
    @TargetApi(11)
    public void shutdown() {
        mOpenHelper.close();
        super.shutdown();
    }
}