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

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.example.android.gcncouponalert.app.data.CouponsContract;
import com.example.android.gcncouponalert.app.sync.GCNCouponAlertSyncAdapter;

/**
 * Encapsulates fetching the forecast and displaying it as a {@link ListView} layout.
 */
public class CouponsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    public static final String LOG_TAG = CouponsFragment.class.getSimpleName();
    private CouponsAdapter mCouponsAdapter;

    private ListView mListView;
    private int mPosition = ListView.INVALID_POSITION;
    private boolean mUseTodayLayout;

    private static final String SELECTED_KEY = "selected_position";

    private static final int FORECAST_LOADER = 0;

    private static final int COUPONS_LOADER = 0;
    // For the forecast view we're showing only a small subset of the stored data.
    // Specify the columns we need.

    private static final String[] COUPONS_COLUMNS = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            CouponsContract.CouponEntry.TABLE_NAME + "." + CouponsContract.CouponEntry._ID,
            CouponsContract.CouponEntry.COLUMN_COUPON_CODE,
            CouponsContract.CouponEntry.COLUMN_COUPON_NAME,
            CouponsContract.CouponEntry.COLUMN_LAST_ACTIVE_DATE,
            CouponsContract.LocationEntry.COLUMN_LOCATION_SETTING,
            CouponsContract.LocationEntry.COLUMN_COORD_LAT,
            CouponsContract.LocationEntry.COLUMN_COORD_LONG,
            CouponsContract.CouponEntry.COLUMN_COUPON_IMAGE_URL_80x100,
            CouponsContract.CouponEntry.COLUMN_COUPON_IMAGE_EXT_80x100,
            CouponsContract.CouponEntry.COLUMN_COUPON_REMOTE_ID

    };

    static final int COL_COUPON_ID = 0;
    static final int COL_COUPON_CODE = 1;
    static final int COL_COUPON_NAME = 2;
    static final int COL_LAST_ACTIVE_DATE = 3;
    static final int COL_LOCATION_SETTING = 4;
    static final int COL_COORD_LAT = 5;
    static final int COL_COORD_LONG = 6;
    static final int COL_IMAGE_URL_80x100 = 7;
    static final int COL_IMAGE_EXT_80x100 = 8;
    static final int COL_REMOTE_ID = 9;

    // These indices are tied to FORECAST_COLUMNS.  If FORECAST_COLUMNS changes, these
    // must change.
    /*
    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_MAX_TEMP = 3;
    static final int COL_WEATHER_MIN_TEMP = 4;
    static final int COL_LOCATION_SETTING = 5;
    static final int COL_WEATHER_CONDITION_ID = 6;
    static final int COL_COORD_LAT = 7;
    static final int COL_COORD_LONG = 8;
    */

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callback {
        /**
         * DetailFragmentCallback for when an item has been selected.
         */
        public void onItemSelected(Uri dateUri);
    }

    public CouponsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        // Add this line in order for this fragment to handle menu events.
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        //int id = item.getItemId();
        /*
        if (id == R.id.action_refresh) {
            updateCoupons();
            return true;
        }
        */
        /*
        if (id == R.id.action_map) {
            openPreferredLocationInMap();
            return true;
        }
        */

        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Log.d(LOG_TAG, "onCreateView");
        // The CouponsAdapter will take data from a source and
        // use it to populate the ListView it's attached to.
        mCouponsAdapter = new CouponsAdapter(getActivity(), null, 0);
        //mCouponsAdapter = new CouponsAdapter(getContext(), null, 0);

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get a reference to the ListView, and attach this adapter to it.
        mListView = (ListView) rootView.findViewById(R.id.listview_forecast);
        mListView.setAdapter(mCouponsAdapter);
        // We'll call our MainActivity

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // CursorAdapter returns a cursor at the correct position for getItem(), or null
                // if it cannot seek to that position.
                Cursor cursor = (Cursor) adapterView.getItemAtPosition(position);
                if (cursor != null) {
                    ((Callback) getActivity()).onItemSelected(CouponsContract.CouponEntry.buildCouponUri(cursor.getLong(COL_COUPON_ID)));

                    /*
                    String remote_id = cursor.getString(COL_REMOTE_ID);
                    Uri uriUrl = Uri.parse("http://www.grocerycouponnetwork.com/coupons/?").buildUpon().appendQueryParameter("cid", remote_id).build();
                    Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
                    startActivity(launchBrowser);
                    Log.d(LOG_TAG,"Launched: " + uriUrl.toString());
                    */
                }
                mPosition = position;
            }
        });


        // If there's instance state, mine it for useful information.
        // The end-goal here is that the user never knows that turning their device sideways
        // does crazy lifecycle related things.  It should feel like some stuff stretched out,
        // or magically appeared to take advantage of room, but data or place in the com.example.android.gcncouponalert.app was never
        // actually *lost*.
        if (savedInstanceState != null && savedInstanceState.containsKey(SELECTED_KEY)) {
            // The listview probably hasn't even been populated yet.  Actually perform the
            // swapout in onLoadFinished.
            mPosition = savedInstanceState.getInt(SELECTED_KEY);
        }
        mCouponsAdapter.setUseTodayLayout(mUseTodayLayout);

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onActivityCreated");
        getLoaderManager().initLoader(COUPONS_LOADER, null, this);
        super.onActivityCreated(savedInstanceState);
    }

    // since we read the location when we create the loader, all we need to do is restart things
    void onLocationChanged( ) {
        updateCoupons();
        getLoaderManager().restartLoader(COUPONS_LOADER, null, this);
    }

    private void updateCoupons() {
        //GCNCouponAlertSyncAdapter.syncImmediately(getActivity());
        GCNCouponAlertSyncAdapter.syncImmediately(getContext());
    }

    void onSpinnerChanged(int spinner_pos) {
        Log.d(LOG_TAG, "onSpinnerChanged pos: "+spinner_pos);
        Bundle bundle = new Bundle();
        bundle.putInt("spinner_pos", spinner_pos);
        getLoaderManager().restartLoader(COUPONS_LOADER, bundle, this);
    }

    /*
    private void openPreferredLocationInMap() {
        // Using the URI scheme for showing a location found on a map.  This super-handy
        // intent can is detailed in the "Common Intents" page of Android's developer site:
        // http://developer.android.com/guide/components/intents-common.html#Maps
        if ( null != mCouponsAdapter) {
            Cursor c = mCouponsAdapter.getCursor();
            if ( null != c ) {
                c.moveToPosition(0);
                String posLat = c.getString(COL_COORD_LAT);
                String posLong = c.getString(COL_COORD_LONG);
                Uri geoLocation = Uri.parse("geo:" + posLat + "," + posLong);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(geoLocation);

                if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Log.d(LOG_TAG, "Couldn't call " + geoLocation.toString() + ", no receiving apps installed!");
                }
            }

        }
    }
    */

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // When tablets rotate, the currently selected list item needs to be saved.
        // When no item is selected, mPosition will be set to Listview.INVALID_POSITION,
        // so check for that before storing.
        if (mPosition != ListView.INVALID_POSITION) {
            outState.putInt(SELECTED_KEY, mPosition);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        Log.d(LOG_TAG, "onCreateLoader");
        // This is called when a new Loader needs to be created.  This
        // fragment only uses one loader, so we don't care about checking the id.

        // To only show current and future dates, filter the query to return weather only for
        // dates after or including today.

        /*
        // Sort order:  Ascending, by date.
        String sortOrder = CouponsContract.WeatherEntry.COLUMN_DATE + " ASC";

        String locationSetting = Utility.getPreferredLocation(getActivity());
        Uri weatherForLocationUri = CouponsContract.WeatherEntry.buildWeatherLocationWithStartDate(
                locationSetting, System.currentTimeMillis());

        return new CursorLoader(getActivity(),
                weatherForLocationUri,
                FORECAST_COLUMNS,
                null,
                null,
                sortOrder);
        */

        //String locationSetting = Utility.getPreferredLocation(getActivity());
        String locationSetting = Utility.getPreferredLocation(getContext());
        int spinner_pos = 0;
        if (bundle != null) {
            spinner_pos = bundle.getInt("spinner_pos");
        }
        //String brand_code = "1";
        Uri couponUri;
        if (spinner_pos == 0) {
            couponUri = CouponsContract.CouponEntry.buildCouponLocation(locationSetting);
        } else {
            couponUri = CouponsContract.CouponEntry.buildCouponLocationBrand(locationSetting);
        }

        Log.d(LOG_TAG, "locationSetting: "+locationSetting+"; spinner_pos: "+spinner_pos);

        return new CursorLoader(
                //getActivity(),
                getContext(),
                couponUri,
                COUPONS_COLUMNS,
                null,
                null,
                CouponsContract.CouponEntry.COLUMN_COUPON_SLOT_INFO);
        //CouponsContract.CouponEntry.COLUMN_DATE_CREATED + " DESC");

    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mCouponsAdapter.swapCursor(data);

        //Bundle extras = getActivity().getIntent().getExtras();
        Bundle extras = this.getArguments();
        if(extras != null) {
            if(extras.containsKey("coupon_id")) {
                long coupon_id = extras.getLong("coupon_id");
                Log.d(LOG_TAG,"Intent! coupon_id: "+coupon_id);
                if (null != mCouponsAdapter) {
                    //Log.d(LOG_TAG,"Intent! mCouponsAdapter OK");
                    Cursor c = mCouponsAdapter.getCursor();
                    if (null != c) {
                        //Log.d(LOG_TAG,"Intent! Cursor c OK");
                        if (c.moveToFirst()) {
                            //Log.d(LOG_TAG,"Intent! Cursor c has data OK");
                            while (!c.isAfterLast()) {
                                //Log.d(LOG_TAG,"Intent! is "+c.getLong(COL_COUPON_ID)+" == "+coupon_id+" ?");
                                if (c.getLong(COL_COUPON_ID) == coupon_id) {
                                    mPosition = c.getPosition();
                                    break;
                                }
                                c.moveToNext();
                            }
                        }
                    }
                }
                //getActivity().getIntent().removeExtra("coupon_id");
                extras.remove("coupon_id");
            }

        }
        //Log.d(LOG_TAG,"Intent! mPosition: "+mPosition);
        if (mPosition != ListView.INVALID_POSITION) {
            // If we don't need to restart the loader, and there's a desired position to restore
            // to, do so now.
            int offset = 0;
            //mListView.smoothScrollToPositionFromTop(mPosition, offset, 100);
            mListView.setSelection(mPosition);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCouponsAdapter.swapCursor(null);
    }

    public void setUseTodayLayout(boolean useTodayLayout) {
        mUseTodayLayout = useTodayLayout;
        if (mCouponsAdapter != null) {
            mCouponsAdapter.setUseTodayLayout(mUseTodayLayout);
        }
    }
}