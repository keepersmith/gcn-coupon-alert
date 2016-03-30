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
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.location.Location;

import com.example.android.gcncouponalert.app.data.CouponsContract;
import com.example.android.gcncouponalert.app.sync.GCNCouponAlertSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends ActionBarActivity implements
        CouponsFragment.Callback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String DETAILFRAGMENT_TAG = "DFTAG";

    private boolean mTwoPane;
    private String mLocation;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocation = Utility.getPreferredLocation(this);

        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        setContentView(R.layout.activity_main);

        /*
        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            if(extras.containsKey("coupon_id")) {
                int coupon_id = extras.getInt("coupon_id");
                Log.d(LOG_TAG,"Intent! coupon_id: "+coupon_id);
                //setContentView(R.layout.viewmain);
                // extract the extra-data in the Notification
                //String msg = extras.getString("NotificationMessage");
                //txtView = (TextView) findViewById(R.id.txtMessage);
                //txtView.setText(msg);
            }
        }
        */

        /*
        if (findViewById(R.id.weather_detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/layout-sw600dp). If this view is present, then the activity should be
            // in two-pane mode.
            mTwoPane = true;
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.weather_detail_container, new DetailFragment(), DETAILFRAGMENT_TAG)
                        .commit();
            }
        } else {
        */
            mTwoPane = false;
            getSupportActionBar().setElevation(0f);
        //}

        CouponsFragment couponsFragment =  ((CouponsFragment)getSupportFragmentManager()
                .findFragmentById(R.id.fragment_forecast));
        couponsFragment.setUseTodayLayout(!mTwoPane);

        GCNCouponAlertSyncAdapter.initializeSyncAdapter(this);

        //onNewIntent(getIntent());
    }

    private void getAndSetZip (double lat, double lon) {
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        final String OPENMAP_BASE_URL = "http://nominatim.openstreetmap.org/reverse?";
        Uri builtUri;
        builtUri = Uri.parse(OPENMAP_BASE_URL).buildUpon()
                .appendQueryParameter("format", "json")
                .appendQueryParameter("lat", Double.toString(lat))
                .appendQueryParameter("lon", Double.toString(lon))
                .appendQueryParameter("addressetails", "1")
                .build();
        try {
            URL url = new URL(builtUri.toString());
            Log.d(LOG_TAG, "Calling API URL: " + builtUri.toString());
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            Log.d(LOG_TAG, " Got back: " +urlConnection.getResponseCode()+" "+urlConnection.getResponseMessage());
            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }
            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                Log.d(LOG_TAG, "API returned nothing: " + builtUri.toString());
                return;
            }
            String mapJsonStr = buffer.toString();
            Log.d(LOG_TAG, "API returned this: " + mapJsonStr);
            //found_data = getCouponDataFromJson(couponJsonStr, locationQuery);
        } catch (Exception e) {

        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            Log.d(LOG_TAG,"Lat: "+mLastLocation.getLatitude()+"; Lon: "+mLastLocation.getLongitude());
            getAndSetZip(mLastLocation.getLatitude(),mLastLocation.getLongitude());
            //mLatitudeText.setText(String.valueOf(mLastLocation.getLatitude()));
            //mLongitudeText.setText(String.valueOf(mLastLocation.getLongitude()));
        }
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    /*
    @Override
    public void onNewIntent(Intent intent){
        Bundle extras = intent.getExtras();
        if(extras != null) {
            if(extras.containsKey("coupon_id")) {
                int coupon_id = extras.getInt("coupon_id");
                setContentView(R.layout.viewmain);
                // extract the extra-data in the Notification
                String msg = extras.getString("NotificationMessage");
                txtView = (TextView) findViewById(R.id.txtMessage);
                txtView.setText(msg);
            }
        }
    }
    */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
        String location = Utility.getPreferredLocation( this );
        // update the location in our second pane using the fragment manager
        if (location != null && !location.equals(mLocation)) {
            CouponsFragment ff = (CouponsFragment)getSupportFragmentManager().findFragmentById(R.id.fragment_forecast);
            if ( null != ff ) {
                ff.onLocationChanged();
            }
            DetailFragment df = (DetailFragment)getSupportFragmentManager().findFragmentByTag(DETAILFRAGMENT_TAG);
            if ( null != df ) {
                df.onLocationChanged(location);
            }
            mLocation = location;
        }
        */
    }

    @Override
    public void onItemSelected(Uri contentUri) {
        Log.d("onItemSelected()","Called with: "+contentUri.toString());

        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            Bundle args = new Bundle();
            args.putParcelable(DetailFragment.DETAIL_URI, contentUri);

            DetailFragment fragment = new DetailFragment();
            fragment.setArguments(args);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.coupon_detail_container, fragment, DETAILFRAGMENT_TAG)
                    .commit();
        } else {


            /*
            Cursor cursor = getContentResolver().query(contentUri, new String[]{CouponsContract.CouponEntry.COLUMN_COUPON_REMOTE_ID}, null, null, null);
            if (cursor.moveToFirst()) {

                String remote_id = cursor.getString(0);
                Uri uriUrl = Uri.parse("http://www.grocerycouponnetwork.com/coupons/?").buildUpon().appendQueryParameter("cid", remote_id).build();

                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
                startActivity(launchBrowser);
                Log.d("onItemSelected()", "Launched: " + uriUrl.toString());
            } else {
                Log.d("onItemSelected()", "Failed to Launch");
            }
            cursor.close();
        */


            Intent intent = new Intent(this, DetailActivity.class)
                    .setData(contentUri);
            startActivity(intent);

        }
    }
}
