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
package com.example.android.gcncouponalert.app;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.example.android.gcncouponalert.app.data.CouponsContract;
import com.example.android.gcncouponalert.app.data.CouponsContract.CouponEntry;

/**
 * A placeholder fragment containing a simple view.
 */
public class DetailFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String LOG_TAG = DetailFragment.class.getSimpleName();
    static final String DETAIL_URI = "URI";

    private static final String FORECAST_SHARE_HASHTAG = " #GCNCouponAlertApp";

    private ShareActionProvider mShareActionProvider;
    private String mForecast;
    private Uri mUri;

    private static final int DETAIL_LOADER = 0;

    private static final String[] DETAIL_COLUMNS = {
            CouponEntry.TABLE_NAME + "." + CouponEntry._ID,
            CouponEntry.COLUMN_COUPON_CODE,
            CouponEntry.COLUMN_COUPON_NAME,
            CouponEntry.COLUMN_SUMMARY_TEXT,
            CouponsContract.BrandEntry.COLUMN_BRAND_NAME,
            CouponEntry.COLUMN_ADDITIONAL_TEXT,
            CouponsContract.CouponEntry.COLUMN_COUPON_IMAGE_URL_80x100,
            CouponsContract.CouponEntry.COLUMN_COUPON_IMAGE_EXT_80x100,
            CouponsContract.CouponEntry.COLUMN_COUPON_REMOTE_ID,
            CouponsContract.CouponEntry.COLUMN_BRAND_KEY,
            CouponsContract.BrandEntry.COLUMN_NOTIFICATION_FLAG

    };

    // These indices are tied to DETAIL_COLUMNS.  If DETAIL_COLUMNS changes, these
    // must change.
    public static final int COL_COUPON_ID = 0;
    public static final int COL_COUPON_CODE = 1;
    public static final int COL_COUPON_NAME = 2;
    public static final int COL_SUMMARY_TEXT = 3;
    public static final int COL_BRAND_NAME = 4;
    public static final int COL_ADDITIONAL_TEXT = 5;
    public static final int COL_IMAGE_URL_80x100 = 6;
    public static final int COL_IMAGE_EXT_80x100 = 7;
    public static final int COL_REMOTE_ID = 8;
    public static final int COL_BRAND_KEY = 9;
    public static final int COL_BRAND_NOTIFICATION_FLAG = 10;


    private ImageView mIconView;
    private TextView mSummaryView;
    private TextView mAdditionalView;
    private TextView mBrandNameView;
    private TextView mNotificationView;
    private Switch mNotificationToggle;

    /*
    private TextView mFriendlyDateView;
    private TextView mDateView;
    private TextView mDescriptionView;
    private TextView mHighTempView;
    private TextView mLowTempView;
    private TextView mHumidityView;
    private TextView mWindView;
    private TextView mPressureView;
    */

    public DetailFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Bundle arguments = getArguments();
        if (arguments != null) {
            mUri = arguments.getParcelable(DetailFragment.DETAIL_URI);
        }

        View rootView = inflater.inflate(R.layout.fragment_detail, container, false);
        mIconView = (ImageView) rootView.findViewById(R.id.detail_icon);
        mSummaryView = (TextView) rootView.findViewById(R.id.detail_summary_text);
        mAdditionalView = (TextView) rootView.findViewById(R.id.detail_additional_text);
        mBrandNameView = (TextView) rootView.findViewById(R.id.detail_brand_name);
        mNotificationView = (TextView) rootView.findViewById(R.id.detail_notification_text);
        mNotificationToggle = (Switch) rootView.findViewById(R.id.detail_brand_notification_toggle);

        //mNotificationToggle.setVisibility(View.INVISIBLE);

        /*
        mDateView = (TextView) rootView.findViewById(R.id.detail_date_textview);
        mFriendlyDateView = (TextView) rootView.findViewById(R.id.detail_day_textview);
        mDescriptionView = (TextView) rootView.findViewById(R.id.detail_forecast_textview);
        mHighTempView = (TextView) rootView.findViewById(R.id.detail_high_textview);
        mLowTempView = (TextView) rootView.findViewById(R.id.detail_low_textview);
        mHumidityView = (TextView) rootView.findViewById(R.id.detail_humidity_textview);
        mWindView = (TextView) rootView.findViewById(R.id.detail_wind_textview);
        mPressureView = (TextView) rootView.findViewById(R.id.detail_pressure_textview);
        */
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.detailfragment, menu);

        // Retrieve the share menu item
        MenuItem menuItem = menu.findItem(R.id.action_share);

        // Get the provider and hold onto it to set/change the share intent.
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(menuItem);

        // If onLoadFinished happens before this, we can go ahead and set the share intent now.
        if (mForecast != null) {
            mShareActionProvider.setShareIntent(createShareForecastIntent());
        }
    }

    private Intent createShareForecastIntent() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, mForecast + FORECAST_SHARE_HASHTAG);
        return shareIntent;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getLoaderManager().initLoader(DETAIL_LOADER, null, this);
        super.onActivityCreated(savedInstanceState);
    }

        /*
    void onLocationChanged( String newLocation ) {
        // replace the uri, since the location has changed
        Uri uri = mUri;
        if (null != uri) {
            Uri updatedUri = CouponsContract.CouponEntry.buildCouponLocation(newLocation);
            mUri = updatedUri;
            getLoaderManager().restartLoader(DETAIL_LOADER, null, this);
        }
    }
    */

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if ( null != mUri ) {
            // Now create and return a CursorLoader that will take care of
            // creating a Cursor for the data being displayed.
            return new CursorLoader(
                    getActivity(),
                    //getContext(),
                    mUri,
                    DETAIL_COLUMNS,
                    null,
                    null,
                    null
            );
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, final Cursor data) {
        Log.d(LOG_TAG,"any data? "+mUri);
        if (data != null && data.moveToFirst()) {
            //Log.d(LOG_TAG, "with data");
            mIconView.setImageBitmap(Utility.loadImageFromLocalStore(data.getString(DetailFragment.COL_IMAGE_URL_80x100), data.getString(DetailFragment.COL_IMAGE_EXT_80x100)));
            mIconView.setContentDescription(data.getString(COL_COUPON_NAME));
            mSummaryView.setText(data.getString(COL_SUMMARY_TEXT));
            mAdditionalView.setText(data.getString(COL_ADDITIONAL_TEXT));
            mBrandNameView.setText(data.getString(COL_BRAND_NAME));
            mNotificationView.setText("Get New Coupon Alerts for " + data.getString(COL_BRAND_NAME) + "?");

            boolean notification_checked = false;
            if (data.getInt(COL_BRAND_NOTIFICATION_FLAG) == 1) {
                notification_checked = true;
            }
            mNotificationToggle.setChecked(notification_checked);
            mNotificationToggle.jumpDrawablesToCurrentState();

            mNotificationToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    int checked_value;
                    if (isChecked) {
                        // The toggle is enabled
                        Log.d(LOG_TAG, "Toggle enabled!");
                        checked_value = 1;
                    } else {
                        // The toggle is disabled
                        Log.d(LOG_TAG, "Toggle disabled!");
                        checked_value = 0;
                    }
                    ContentValues notification_flag = new ContentValues();
                    notification_flag.put(CouponsContract.BrandEntry.COLUMN_NOTIFICATION_FLAG, checked_value);
                    String[] brandId = new String[]{data.getString(COL_BRAND_KEY)};
                    getActivity().getContentResolver().update(CouponsContract.BrandEntry.CONTENT_URI, notification_flag, CouponsContract.BrandEntry._ID + " = ?", brandId);
                    //getContext().getContentResolver().update(CouponsContract.BrandEntry.CONTENT_URI, notification_flag, CouponsContract.BrandEntry._ID + " = ?", brandId);
                }
            });

            // If onCreateOptionsMenu has already happened, we need to update the share intent now.
            if (mShareActionProvider != null) {
                mShareActionProvider.setShareIntent(createShareForecastIntent());
            }

            mNotificationToggle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) { }
}