package com.example.android.gcncouponalert.app.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.text.format.Time;
import android.util.Log;

import com.example.android.gcncouponalert.app.BuildConfig;
import com.example.android.gcncouponalert.app.MainActivity;
import com.example.android.gcncouponalert.app.R;
import com.example.android.gcncouponalert.app.Utility;
import com.example.android.gcncouponalert.app.data.WeatherContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Vector;

public class GCNCouponAlertSyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String LOG_TAG = GCNCouponAlertSyncAdapter.class.getSimpleName();
    // Interval at which to sync with the weather, in seconds.
    // 60 seconds (1 minute) * 180 = 3 hours
    //public static final int SYNC_INTERVAL = 60 * 180;
    //public static final int SYNC_FLEXTIME = SYNC_INTERVAL/3;

    public static final int SYNC_INTERVAL = 60;
    public static final int SYNC_FLEXTIME = SYNC_INTERVAL/3;

    //private static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;
    private static final long ONE_MINUTE_IN_MILLIS = 1000 * 60;
    private static final int WEATHER_NOTIFICATION_ID = 3004;
    private static final int COUPON_NOTIFICATION_ID = 3005;

    private static final String[] NOTIFY_NEW_COUPONS = new String[] {
            WeatherContract.CouponEntry.COLUMN_COUPON_CODE,
            WeatherContract.CouponEntry.COLUMN_COUPON_NAME
    };

    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static final int INDEX_SHORT_DESC = 3;

    private static final int INDEX_COUPON_CODE = 0;
    private static final int INDEX_COUPON_NAME = 1;

    public GCNCouponAlertSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.d(LOG_TAG, "Starting sync");
        String locationQuery = Utility.getPreferredLocation(getContext());

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String couponJsonStr = null;

        String format = "json";
        //String units = "metric";
        //int numDays = 14;

        boolean found_data = false;

        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are avaiable at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast
            final String FORECAST_BASE_URL =
                    "http://tools.grocerycouponnetwork.com/api1/coupon/?";
            final String AUTH_KEY_PARAM = "auth_key";
            final String LOCATION_PARAM = "zip_code";

            // do this twice - once for national coupons, once for local coupons
            Uri[] builtUri = new Uri[2];
            builtUri[0] = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(LOCATION_PARAM, locationQuery)
                    .appendQueryParameter(AUTH_KEY_PARAM, BuildConfig.GCN_COUPON_API_KEY)
                    .build();
            builtUri[1] = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(AUTH_KEY_PARAM, BuildConfig.GCN_COUPON_API_KEY)
                    .build();

            for (int i = 0; i < 2; i++) {
                URL url = new URL(builtUri[i].toString());
                Log.d(LOG_TAG,"Calling API URL: "+builtUri[i].toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

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
                couponJsonStr = buffer.toString();
                Log.d(LOG_TAG, "API returned this: " + couponJsonStr);
                found_data = getCouponDataFromJson(couponJsonStr, locationQuery);

            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            // If the code didn't successfully get the weather data, there's no point in attempting
            // to parse it.
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }
        if (found_data) {
            notifyCoupon();
        }
        return;
    }

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     *
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private boolean getCouponDataFromJson(String forecastJsonStr, String locationSetting)
            throws JSONException {

        // Now we have a String representing the complete forecast in JSON Format.
        // Fortunately parsing is easy:  constructor takes the JSON string and converts it
        // into an Object hierarchy for us.

        // These are the names of the JSON objects that need to be extracted.

        final String OWM_STATUS = "status";
        final String OWM_ERROR_MESSAGE = "error_message";
        final String OWM_DATA = "data";

        final String OWM_RESULTS = "results";

        final String OWM_COUPON_CODE = "coupon_code";
        final String OWM_COUPON_NAME = "coupon_name";
        final String OWM_COUPON_LAST_ACTIVE_DATE = "last_active";
        final String OWM_COUPON_DATE_CREATED = "date_created";
        final String OWM_COUPON_URL_PATH = "url_path";
        final String OWM_COUPON_IMAGE_EXTENSION = "image_extension";
        final String OWM_COUPON_REMOTE_ID = "remote_id";

        boolean found_data = false;

        try {
            JSONObject couponJson = new JSONObject(forecastJsonStr);
            JSONObject couponData = couponJson.getJSONObject(OWM_DATA);
            JSONArray couponArray = couponData.getJSONArray(OWM_RESULTS);

            long locationId = addLocation(locationSetting);

            Vector<ContentValues> cVVector = new Vector<ContentValues>(couponArray.length());

            for(int i = 0; i < couponArray.length(); i++) {
                JSONObject couponInfo = couponArray.getJSONObject(i);
                String coupon_code = couponInfo.getString(OWM_COUPON_CODE);
                String coupon_name = couponInfo.getString(OWM_COUPON_NAME);
                coupon_name = Html.fromHtml(coupon_name).toString();
                String last_active_date = couponInfo.getString(OWM_COUPON_LAST_ACTIVE_DATE);
                String date_created = couponInfo.getString(OWM_COUPON_DATE_CREATED);
                String image_url_80x100 = couponInfo.getString(OWM_COUPON_URL_PATH);
                String image_ext_80x100 = couponInfo.getString(OWM_COUPON_IMAGE_EXTENSION);
                String remote_id = couponInfo.getString(OWM_COUPON_REMOTE_ID);

                ContentValues couponValues = new ContentValues();

                couponValues.put(WeatherContract.CouponEntry.COLUMN_COUPON_NAME, coupon_name);
                couponValues.put(WeatherContract.CouponEntry.COLUMN_COUPON_CODE, coupon_code);
                couponValues.put(WeatherContract.CouponEntry.COLUMN_LAST_ACTIVE_DATE, last_active_date);
                couponValues.put(WeatherContract.CouponEntry.COLUMN_DATE_CREATED, date_created);
                couponValues.put(WeatherContract.CouponEntry.COLUMN_COUPON_IMAGE_URL_80x100, image_url_80x100);
                couponValues.put(WeatherContract.CouponEntry.COLUMN_COUPON_IMAGE_EXT_80x100, image_ext_80x100);
                couponValues.put(WeatherContract.CouponEntry.COLUMN_COUPON_REMOTE_ID, remote_id);
                couponValues.put(WeatherContract.CouponEntry.COLUMN_LOC_KEY, locationId);

                cVVector.add(couponValues);

                Utility.downloader(image_url_80x100, image_ext_80x100);
            }

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            int inserted = 0;
            // add to database
            if ( cVVector.size() > 0 ) {
                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                getContext().getContentResolver().bulkInsert(WeatherContract.CouponEntry.CONTENT_URI, cvArray);

                // delete old data so we don't build up an endless history
                //getContext().getContentResolver().delete(WeatherContract.CouponEntry.CONTENT_URI,WeatherContract.CouponEntry.COLUMN_LAST_ACTIVE_DATE + " <= ?",new String[]{"NOW()"});

                //notifyCoupon();
                found_data = true;

            }

            Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }
        return found_data;
    }

    private void notifyCoupon() {
        Log.d(LOG_TAG, "notifyCoupon()");
        Context context = getContext();
        //checking the last update and notify if it' the first of the day
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String displayNotificationsKey = context.getString(R.string.pref_enable_notifications_key);
        boolean displayNotifications = prefs.getBoolean(displayNotificationsKey,
                Boolean.parseBoolean(context.getString(R.string.pref_enable_notifications_default)));

        if ( displayNotifications ) {

            String lastNotificationKey = context.getString(R.string.pref_last_notification);
            long lastSync = prefs.getLong(lastNotificationKey, 0);

            if (System.currentTimeMillis() - lastSync >= ONE_MINUTE_IN_MILLIS) {
                // Last sync was more than 1 day ago, let's send a notification with the weather.
                String locationQuery = Utility.getPreferredLocation(context);

                //Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());
                Uri couponUri = WeatherContract.CouponEntry.buildCouponLocationNotNotified(locationQuery);

                Log.d(LOG_TAG,"notifyCoupon() calling Uri: "+couponUri.toString());

                // we'll query our contentProvider, as always
                Cursor cursor = context.getContentResolver().query(couponUri, NOTIFY_NEW_COUPONS, null, null, WeatherContract.CouponEntry.COLUMN_DATE_CREATED + " DESC");

                if (cursor.moveToFirst()) {
                    int coupon_code = cursor.getInt(INDEX_COUPON_CODE);
                    String coupon_name = cursor.getString(INDEX_COUPON_NAME);
                    //int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                    //double high = cursor.getDouble(INDEX_MAX_TEMP);
                    //double low = cursor.getDouble(INDEX_MIN_TEMP);
                    //String desc = cursor.getString(INDEX_SHORT_DESC);

                    //int iconId = Utility.getIconResourceForWeatherCondition(weatherId);
                    int iconId = Utility.getIconResourceForCoupon(coupon_code);
                    Resources resources = context.getResources();
                    Bitmap largeIcon = BitmapFactory.decodeResource(resources, Utility.getArtResourceForCoupon(coupon_code));
                    //Bitmap largeIcon = Utility.downloader();
                    String title = context.getString(R.string.app_name);

                    // Define the text of the forecast.
                    String contentText = String.format(context.getString(R.string.format_notification),coupon_name);

                    // NotificationCompatBuilder is a very convenient way to build backward-compatible
                    // notifications.  Just throw in some data.
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(getContext())
                                    .setColor(resources.getColor(R.color.gcncouponalert_light_green))
                                    .setSmallIcon(iconId)
                                    .setLargeIcon(largeIcon)
                                    .setContentTitle(title)
                                    .setContentText(contentText);

                    // Make something interesting happen when the user clicks on the notification.
                    // In this case, opening the com.example.android.gcncouponalert.app is sufficient.
                    Intent resultIntent = new Intent(context, MainActivity.class);

                    // The stack builder object will contain an artificial back stack for the
                    // started Activity.
                    // This ensures that navigating backward from the Activity leads out of
                    // your application to the Home screen.
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                    stackBuilder.addNextIntent(resultIntent);
                    PendingIntent resultPendingIntent =
                            stackBuilder.getPendingIntent(
                                    0,
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            );
                    mBuilder.setContentIntent(resultPendingIntent);

                    NotificationManager mNotificationManager =
                            (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                    // WEATHER_NOTIFICATION_ID allows you to update the notification later on.
                    mNotificationManager.notify(COUPON_NOTIFICATION_ID, mBuilder.build());

                    //refreshing last sync
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(lastNotificationKey, System.currentTimeMillis());
                    editor.commit();

                    // update notified field so we don't keep notifying people with same coupons
                    ContentValues notified_flag = new ContentValues();
                    notified_flag.put(WeatherContract.CouponEntry.COLUMN_NOTIFIED, 1);
                    getContext().getContentResolver().update(WeatherContract.CouponEntry.CONTENT_URI, notified_flag, WeatherContract.CouponEntry.COLUMN_NOTIFIED + " = 0", null);
                }
                cursor.close();
            }
        }
    }
    private void notifyWeather() {
        Context context = getContext();
        //checking the last update and notify if it' the first of the day
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String displayNotificationsKey = context.getString(R.string.pref_enable_notifications_key);
        boolean displayNotifications = prefs.getBoolean(displayNotificationsKey,
                Boolean.parseBoolean(context.getString(R.string.pref_enable_notifications_default)));

        if ( displayNotifications ) {

            String lastNotificationKey = context.getString(R.string.pref_last_notification);
            long lastSync = prefs.getLong(lastNotificationKey, 0);

            if (System.currentTimeMillis() - lastSync >= ONE_MINUTE_IN_MILLIS) {
                // Last sync was more than 1 day ago, let's send a notification with the weather.
                String locationQuery = Utility.getPreferredLocation(context);

                Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

                // we'll query our contentProvider, as always
                Cursor cursor = context.getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

                if (cursor.moveToFirst()) {
                    int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                    double high = cursor.getDouble(INDEX_MAX_TEMP);
                    double low = cursor.getDouble(INDEX_MIN_TEMP);
                    String desc = cursor.getString(INDEX_SHORT_DESC);

                    int iconId = Utility.getIconResourceForWeatherCondition(weatherId);
                    Resources resources = context.getResources();
                    Bitmap largeIcon = BitmapFactory.decodeResource(resources,
                            Utility.getArtResourceForWeatherCondition(weatherId));
                    String title = context.getString(R.string.app_name);

                    // Define the text of the forecast.
                    String contentText = String.format(context.getString(R.string.format_notification_weather),
                            desc,
                            Utility.formatTemperature(context, high),
                            Utility.formatTemperature(context, low));

                    // NotificationCompatBuilder is a very convenient way to build backward-compatible
                    // notifications.  Just throw in some data.
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(getContext())
                                    .setColor(resources.getColor(R.color.gcncouponalert_light_blue))
                                    .setSmallIcon(iconId)
                                    .setLargeIcon(largeIcon)
                                    .setContentTitle(title)
                                    .setContentText(contentText);

                    // Make something interesting happen when the user clicks on the notification.
                    // In this case, opening the com.example.android.gcncouponalert.app is sufficient.
                    Intent resultIntent = new Intent(context, MainActivity.class);

                    // The stack builder object will contain an artificial back stack for the
                    // started Activity.
                    // This ensures that navigating backward from the Activity leads out of
                    // your application to the Home screen.
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                    stackBuilder.addNextIntent(resultIntent);
                    PendingIntent resultPendingIntent =
                            stackBuilder.getPendingIntent(
                                    0,
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            );
                    mBuilder.setContentIntent(resultPendingIntent);

                    NotificationManager mNotificationManager =
                            (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                    // WEATHER_NOTIFICATION_ID allows you to update the notification later on.
                    mNotificationManager.notify(WEATHER_NOTIFICATION_ID, mBuilder.build());

                    //refreshing last sync
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(lastNotificationKey, System.currentTimeMillis());
                    editor.commit();
                }
                cursor.close();
            }
        }
    }

    /**
     * Helper method to handle insertion of a new location in the weather database.
     *
     * @param locationSetting The location string used to request updates from the server.
     * @param //cityName A human-readable city name, e.g "Mountain View"
     * @param //lat the latitude of the city
     * @param //lon the longitude of the city
     * @return the row ID of the added location.
     */
    long addLocation(String locationSetting) {
        long locationId;

        // First, check if the location with this city name exists in the db
        Cursor locationCursor = getContext().getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry._ID},
                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                new String[]{locationSetting},
                null);

        if (locationCursor.moveToFirst()) {
            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry._ID);
            locationId = locationCursor.getLong(locationIdIndex);
        } else {
            // Now that the content provider is set up, inserting rows of data is pretty simple.
            // First create a ContentValues object to hold the data you want to insert.
            ContentValues locationValues = new ContentValues();

            // Then add the data, along with the corresponding name of the data type,
            // so the content provider knows what kind of value is being inserted.
            //locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, cityName);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, "unknown");
            locationValues.put(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LAT, "unknown");
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LONG, "unknown");

            // Finally, insert location data into the database.
            Uri insertedUri = getContext().getContentResolver().insert(
                    WeatherContract.LocationEntry.CONTENT_URI,
                    locationValues
            );

            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
            locationId = ContentUris.parseId(insertedUri);
        }

        locationCursor.close();
        // Wait, that worked?  Yes!
        return locationId;
    }

    /**
     * Helper method to schedule the sync adapter periodic execution
     */
    public static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority).
                    setExtras(new Bundle()).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account,
                    authority, new Bundle(), syncInterval);
        }
    }

    /**
     * Helper method to have the sync adapter sync immediately
     * @param context The context used to access the account service
     */
    public static void syncImmediately(Context context) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(getSyncAccount(context),
                context.getString(R.string.content_authority), bundle);
    }

    /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The context used to access the account service
     * @return a fake account.
     */
    public static Account getSyncAccount(Context context) {
        Log.d(LOG_TAG, "getSyncAccount");
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist
        if ( null == accountManager.getPassword(newAccount) ) {

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call ContentResolver.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */

            onAccountCreated(newAccount, context);
        }
        return newAccount;
    }

    private static void onAccountCreated(Account newAccount, Context context) {
        Log.d(LOG_TAG, "onAccountCreated");
        /*
         * Since we've created an account
         */
        GCNCouponAlertSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);

        /*
         * Without calling setSyncAutomatically, our periodic sync will not be enabled.
         */
        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.content_authority), true);

        /*
         * Finally, let's do a sync to get things started
         */
        syncImmediately(context);

    }

    public static void initializeSyncAdapter(Context context) {
        getSyncAccount(context);
    }
}