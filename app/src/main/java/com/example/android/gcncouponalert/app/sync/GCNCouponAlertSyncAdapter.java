package com.example.android.gcncouponalert.app.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Notification;
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
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
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
import com.example.android.gcncouponalert.app.CouponsFragment;
import com.example.android.gcncouponalert.app.MainActivity;
import com.example.android.gcncouponalert.app.R;
import com.example.android.gcncouponalert.app.Utility;
import com.example.android.gcncouponalert.app.data.CouponsContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

public class GCNCouponAlertSyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String LOG_TAG = GCNCouponAlertSyncAdapter.class.getSimpleName();
    // Interval at which to sync with the weather, in seconds.
    // 60 seconds (1 minute) * 180 = 3 hours
    //public static final int SYNC_INTERVAL = 60 * 180;
    //public static final int SYNC_FLEXTIME = SYNC_INTERVAL/3;

    public static final int SYNC_INTERVAL = 60 * 180;
    public static final int SYNC_FLEXTIME = SYNC_INTERVAL/3;

    //private static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;
    private static final long NOTIFICATION_COOLDOWN_MILLIS = 1000 * 60;
    private static final long ONE_MINUTE_IN_MILLIS = 1000 * 60;
    //private static final int WEATHER_NOTIFICATION_ID = 3004;
    private static final int COUPON_NOTIFICATION_ID = 3005;

    private static final String[] NOTIFY_NEW_COUPONS = new String[] {
            CouponsContract.CouponEntry.TABLE_NAME+"."+ CouponsContract.CouponEntry._ID,
            CouponsContract.CouponEntry.COLUMN_COUPON_CODE,
            CouponsContract.CouponEntry.COLUMN_COUPON_NAME
    };

    // these indices must match the projection
    private static final int INDEX_COUPON_ID = 0;
    private static final int INDEX_COUPON_CODE = 1;
    private static final int INDEX_COUPON_NAME = 2;

    //private GoogleApiClient mGoogleApiClient;
    //private Location mLastLocation;

    public GCNCouponAlertSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    private String getAndSetZip (double lat, double lon) {
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        String zip_code = "";

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
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(1000);
            urlConnection.setRequestProperty("User-Agent", "GCN Coupon Alert 0.1");
            urlConnection.connect();
            Log.d(LOG_TAG, " Got back: " +urlConnection.getResponseCode()+" "+urlConnection.getResponseMessage());
            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return zip_code;
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
                return zip_code;
            }
            String mapJsonStr = buffer.toString();
            Log.d(LOG_TAG, "API returned this: " + mapJsonStr);

            JSONObject mapJson = new JSONObject(mapJsonStr);
            JSONObject addressJson = mapJson.getJSONObject("address");
            zip_code = addressJson.getString("postcode");

            return zip_code;
            //found_data = getCouponDataFromJson(couponJsonStr, locationQuery);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            //e.printStackTrace();
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
            return zip_code;
        }
    }

    private String getZipCodeFromLocation(Location location) {
        Address addr = getAddressFromLocation(location);
        return addr.getPostalCode() == null ? "" : addr.getPostalCode();
    }

    private Address getAddressFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(getContext());
        Address address = new Address(Locale.getDefault());
        try {
            List<Address> addr = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addr.size() > 0) {
                address = addr.get(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return address;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.d(LOG_TAG, "Starting sync");

        //if (MainActivity.mLastLocation != null) {
            //getAndSetZip(MainActivity.mLastLocation.getLatitude(), MainActivity.mLastLocation.getLongitude());
        //}
        // if zip_code is not set, get it from the user's phone
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        String zip_code = prefs.getString(getContext().getString(R.string.pref_location_key),null);
        Log.d(LOG_TAG, "Zip: " + zip_code);
        if (zip_code == null) {
            cSyncLocation oSyncLocation = new cSyncLocation();
            // first get lat/lon from google location services
            Location oLocation = oSyncLocation.GetLocationBlocking(getContext());
            if (oLocation != null) {
                // now get zip from openstreetmap.org
                zip_code = getAndSetZip(oLocation.getLatitude(),oLocation.getLongitude());
                Log.d(LOG_TAG, "Lat: " + oLocation.getLatitude() + "; Lon: " + oLocation.getLongitude() + "; Zip: " + zip_code);
                if (!zip_code.equals("")) {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(getContext().getString(R.string.pref_location_key), zip_code);
                    editor.commit();
                }
            }
        }
        String locationQuery;
        if (!zip_code.equals("")) {
            locationQuery = zip_code;
        } else {
            locationQuery = Utility.getPreferredLocation(getContext());
        }

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // setup GCN API authorization stuff
        long dateInMillis = System.currentTimeMillis();
        SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        shortenedDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String xAuthDate = shortenedDateFormat.format(dateInMillis);
        String api_key = "b84ed8cb132cba185d6214af7fcd31f3c115b0e40046c512d4c2872c7edffe7cf7719193fdf8edffad62284c503a5f1beaca15916c84316e5e9c2b0a6c30f820";
        String xAuthorization_token = "gcn-android:"+api_key+"@"+xAuthDate;

        String xAuthorization_string = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(xAuthorization_token.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for(int i=0; i< bytes.length ;i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            xAuthorization_string = "gcn-android:"+sb.toString();
        } catch (Exception e) {
            xAuthorization_string = "";
            e.printStackTrace();
        }


        // Will contain the raw JSON response as a string.
        String couponJsonStr = null;

        //final String COUPON_BASE_URL = "http://tools.grocerycouponnetwork.com/api1/coupon/?";
        final String COUPON_BASE_URL = "http://www.grocerycouponnetwork.com/api/coupons/get_active_coupons/?";
        final String LOCATION_PARAM = "zip";

        boolean found_data = false;

        try {
            Uri[] builtUri = new Uri[1];
            builtUri[0] = Uri.parse(COUPON_BASE_URL).buildUpon()
                    .appendQueryParameter(LOCATION_PARAM, locationQuery)
                    .build();

            for (int i = 0; i < builtUri.length; i++) {
                URL url = new URL(builtUri[i].toString());
                Log.d(LOG_TAG, "Calling API URL: " + builtUri[i].toString()+"; "+xAuthDate+"; "+xAuthorization_string);

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setRequestProperty("X-Auth-Date", xAuthDate);
                urlConnection.setRequestProperty("X-Authorization",xAuthorization_string);
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
                couponJsonStr = buffer.toString();
                //Utility.largeLog(LOG_TAG, "API returned this: " + couponJsonStr);
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
            getContext().getContentResolver().notifyChange(CouponsContract.CouponEntry.CONTENT_URI, null);
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

        //final String OWM_STATUS = "status";
        //final String OWM_ERROR_MESSAGE = "error_message";
        //final String OWM_DATA = "data";
        final String OWM_COUPONS = "coupons";
        final String OWM_CATEGORIES = "categories";

        //final String OWM_RESULTS = "results";

        final String OWM_COUPON_CODE = "coupon_code";
        final String OWM_COUPON_NAME = "coupon_name";
        final String OWM_COUPON_LAST_ACTIVE_DATE = "last_active";
        final String OWM_COUPON_DATE_CREATED = "date_created";
        final String OWM_COUPON_URL_PATH = "url_path";
        final String OWM_COUPON_IMAGE_EXTENSION = "image_extension";
        final String OWM_COUPON_REMOTE_ID = "remote_id";
        final String OWM_COUPON_SLOT_INFO = "slot_info";
        final String OWM_COUPON_CATEGORY_CODE = "category_code";
        final String OWM_COUPON_BRAND_CODE = "brand_code";
        final String OWM_COUPON_EXPIRATION_DATE = "expiration_date";
        final String OWM_COUPON_BRAND_NAME = "brand_name";
        final String OWM_COUPON_ADDITIONAL_TEXT = "additional_text";
        final String OWM_COUPON_SUMMARY_TEXT = "summary_text";

        final String OWM_CATEGORY_CODE = "code";
        final String OWM_CATEGORY_NAME = "name";
        final String OWM_CATEGORY_COUNT = "count";

        boolean found_data = false;

        try {
            JSONObject couponJson = new JSONObject(forecastJsonStr);
            JSONArray categoryArray = couponJson.getJSONArray(OWM_CATEGORIES);

            Vector<ContentValues> cVVector_categories = new Vector<ContentValues>(categoryArray.length());

            for(int i = 0; i < categoryArray.length(); i++) {
                JSONObject categoryInfo = categoryArray.getJSONObject(i);
                String category_code = categoryInfo.getString(OWM_CATEGORY_CODE);
                String category_name = categoryInfo.getString(OWM_CATEGORY_NAME);
                String category_count = categoryInfo.getString(OWM_CATEGORY_COUNT);

                ContentValues categoryValues = new ContentValues();
                categoryValues.put(CouponsContract.CategoryEntry.COLUMN_CATEGORY_CODE, category_code);
                categoryValues.put(CouponsContract.CategoryEntry.COLUMN_CATEGORY_NAME, category_name);
                categoryValues.put(CouponsContract.CategoryEntry.COLUMN_CATEGORY_COUNT, category_count);

                cVVector_categories.add(categoryValues);
            }
            int inserted = 0;
            // add to database
            if ( cVVector_categories.size() > 0 ) {
                ContentValues[] cvArray = new ContentValues[cVVector_categories.size()];
                cVVector_categories.toArray(cvArray);
                inserted = getContext().getContentResolver().bulkInsert(CouponsContract.CategoryEntry.CONTENT_URI, cvArray);
            }

            Log.d(LOG_TAG, "getCouponDataFromJson (categories): " + cVVector_categories.size() + " categories found; "+inserted+" new categories inserted.");

            JSONArray couponArray = couponJson.getJSONArray(OWM_COUPONS);

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
                String slot_info = couponInfo.getString(OWM_COUPON_SLOT_INFO);
                String category_code = couponInfo.getString(OWM_COUPON_CATEGORY_CODE);
                String brand_code = couponInfo.getString(OWM_COUPON_BRAND_CODE);
                String expiration_date = couponInfo.getString(OWM_COUPON_EXPIRATION_DATE);
                String brand_name = couponInfo.getString(OWM_COUPON_BRAND_NAME);
                String additional_text = couponInfo.getString(OWM_COUPON_ADDITIONAL_TEXT);
                String summary_text = couponInfo.getString(OWM_COUPON_SUMMARY_TEXT);

                long brandId = addBrand(brand_code, brand_name);
                long categoryId = getCategoryId(category_code);

                ContentValues couponValues = new ContentValues();

                couponValues.put(CouponsContract.CouponEntry.COLUMN_COUPON_NAME, coupon_name);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_COUPON_CODE, coupon_code);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_LAST_ACTIVE_DATE, last_active_date);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_DATE_CREATED, date_created);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_COUPON_IMAGE_URL_80x100, image_url_80x100);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_COUPON_IMAGE_EXT_80x100, image_ext_80x100);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_COUPON_REMOTE_ID, remote_id);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_COUPON_SLOT_INFO, slot_info);
                //couponValues.put(CouponsContract.CouponEntry.COLUMN_COUPON_CATEGORY_CODE, category_code);
                //couponValues.put(CouponsContract.CouponEntry.COLUMN_COUPON_BRAND_CODE, brand_code);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_BRAND_KEY, brandId);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_CATEGORY_KEY, categoryId);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_EXPIRATION_DATE, expiration_date);
                //couponValues.put(CouponsContract.CouponEntry.COLUMN_BRAND_NAME, brand_name);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_ADDITIONAL_TEXT, additional_text);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_SUMMARY_TEXT, summary_text);
                couponValues.put(CouponsContract.CouponEntry.COLUMN_LOC_KEY, locationId);

                cVVector.add(couponValues);

                Utility.downloader(image_url_80x100, image_ext_80x100);
            }

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            inserted = 0;
            // add to database
            if ( cVVector.size() > 0 ) {
                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                inserted = getContext().getContentResolver().bulkInsert(CouponsContract.CouponEntry.CONTENT_URI, cvArray);

                // delete old data so we don't build up an endless history
                //getContext().getContentResolver().delete(CouponsContract.CouponEntry.CONTENT_URI,CouponsContract.CouponEntry.COLUMN_LAST_ACTIVE_DATE + " <= ?",new String[]{"NOW()"});

                //notifyCoupon();
                if (inserted > 0) {
                    found_data = true;
                }

            }

            Log.d(LOG_TAG, "getCouponDataFromJson (coupons): " + cVVector.size() + " coupons found; "+inserted+" new coupons inserted.");

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

            if (System.currentTimeMillis() - lastSync >= NOTIFICATION_COOLDOWN_MILLIS) {
                // Last sync was more than 1 day ago, let's send a notification with the weather.
                String locationQuery = Utility.getPreferredLocation(context);

                //Uri weatherUri = CouponsContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());
                Uri couponUri = CouponsContract.CouponEntry.buildCouponLocationNotNotified(locationQuery);

                Log.d(LOG_TAG,"notifyCoupon() calling Uri: "+couponUri.toString());

                // we'll query our contentProvider, as always
                Cursor cursor = context.getContentResolver().query(couponUri, NOTIFY_NEW_COUPONS, null, null, CouponsContract.CouponEntry.COLUMN_DATE_CREATED + " DESC");

                if (cursor.moveToFirst()) {
                    long coupon_id = cursor.getLong(INDEX_COUPON_ID);
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
                    String contentText = String.format(context.getString(R.string.format_notification), coupon_name);

                    // NotificationCompatBuilder is a very convenient way to build backward-compatible
                    // notifications.  Just throw in some data.
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(getContext())
                                    .setColor(resources.getColor(R.color.gcncouponalert_light_green))
                                    .setSmallIcon(iconId)
                                    .setLargeIcon(largeIcon)
                                    .setContentTitle(title)
                                    .setAutoCancel(true)
                                    .setContentText(contentText);

                    // Make something interesting happen when the user clicks on the notification.
                    // In this case, opening the com.example.android.gcncouponalert.app is sufficient.
                    Intent resultIntent = new Intent(context, MainActivity.class);
                    resultIntent.putExtra("coupon_id",coupon_id);

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
                    Notification noti = mBuilder.build();
                    mNotificationManager.notify(COUPON_NOTIFICATION_ID, noti);

                    //refreshing last sync
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(lastNotificationKey, System.currentTimeMillis());
                    editor.commit();

                    // update notified field so we don't keep notifying people with same coupons
                    ContentValues notified_flag = new ContentValues();
                    notified_flag.put(CouponsContract.CouponEntry.COLUMN_NOTIFIED, 1);
                    getContext().getContentResolver().update(CouponsContract.CouponEntry.CONTENT_URI, notified_flag, CouponsContract.CouponEntry.COLUMN_NOTIFIED + " = 0", null);
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
                CouponsContract.LocationEntry.CONTENT_URI,
                new String[]{CouponsContract.LocationEntry._ID},
                CouponsContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                new String[]{locationSetting},
                null);

        if (locationCursor.moveToFirst()) {
            int locationIdIndex = locationCursor.getColumnIndex(CouponsContract.LocationEntry._ID);
            locationId = locationCursor.getLong(locationIdIndex);
        } else {
            // Now that the content provider is set up, inserting rows of data is pretty simple.
            // First create a ContentValues object to hold the data you want to insert.
            ContentValues locationValues = new ContentValues();

            // Then add the data, along with the corresponding name of the data type,
            // so the content provider knows what kind of value is being inserted.
            //locationValues.put(CouponsContract.LocationEntry.COLUMN_CITY_NAME, cityName);
            locationValues.put(CouponsContract.LocationEntry.COLUMN_CITY_NAME, "unknown");
            locationValues.put(CouponsContract.LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);
            locationValues.put(CouponsContract.LocationEntry.COLUMN_COORD_LAT, "unknown");
            locationValues.put(CouponsContract.LocationEntry.COLUMN_COORD_LONG, "unknown");

            // Finally, insert location data into the database.
            Uri insertedUri = getContext().getContentResolver().insert(
                    CouponsContract.LocationEntry.CONTENT_URI,
                    locationValues
            );

            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
            locationId = ContentUris.parseId(insertedUri);
        }

        locationCursor.close();
        // Wait, that worked?  Yes!
        return locationId;
    }

    long getCategoryId (String category_code) {
        long categoryId = 0;

        // First, check if the location with this city name exists in the db
        Cursor categoryCursor = getContext().getContentResolver().query(
                CouponsContract.CategoryEntry.CONTENT_URI,
                new String[]{CouponsContract.CategoryEntry._ID},
                CouponsContract.CategoryEntry.COLUMN_CATEGORY_CODE + " = ?",
                new String[]{category_code},
                null);

        if (categoryCursor.moveToFirst()) {
            int categoryIdIndex = categoryCursor.getColumnIndex(CouponsContract.CategoryEntry._ID);
            categoryId = categoryCursor.getLong(categoryIdIndex);
        }

        categoryCursor.close();
        // Wait, that worked?  Yes!
        return categoryId;
    }

    long addBrand (String brand_code, String brand_name) {
        long brandId;

        // First, check if the location with this city name exists in the db
        Cursor brandCursor = getContext().getContentResolver().query(
                CouponsContract.BrandEntry.CONTENT_URI,
                new String[]{CouponsContract.BrandEntry._ID},
                CouponsContract.BrandEntry.COLUMN_BRAND_CODE + " = ?",
                new String[]{brand_code},
                null);

        if (brandCursor.moveToFirst()) {
            int brandIdIndex = brandCursor.getColumnIndex(CouponsContract.BrandEntry._ID);
            brandId = brandCursor.getLong(brandIdIndex);
        } else {
            // Now that the content provider is set up, inserting rows of data is pretty simple.
            // First create a ContentValues object to hold the data you want to insert.
            ContentValues brandValues = new ContentValues();

            // Then add the data, along with the corresponding name of the data type,
            // so the content provider knows what kind of value is being inserted.
            //locationValues.put(CouponsContract.LocationEntry.COLUMN_CITY_NAME, cityName);
            brandValues.put(CouponsContract.BrandEntry.COLUMN_BRAND_CODE, brand_code);
            brandValues.put(CouponsContract.BrandEntry.COLUMN_BRAND_NAME, brand_name);

            // Finally, insert location data into the database.
            Uri insertedUri = getContext().getContentResolver().insert(
                    CouponsContract.BrandEntry.CONTENT_URI,
                    brandValues
            );

            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
            brandId = ContentUris.parseId(insertedUri);
        }

        brandCursor.close();
        // Wait, that worked?  Yes!
        return brandId;
    }




    /**
     * Helper method to schedule the sync adapter periodic execution
     */
    public static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Log.d(LOG_TAG, "configurePeriodicSync");
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            SyncRequest request = new SyncRequest.Builder().syncPeriodic(syncInterval, flexTime).setSyncAdapter(account, authority).setExtras(new Bundle()).build();
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
        Log.d(LOG_TAG, "syncImmediately");
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
        Log.d(LOG_TAG, "  getSyncAccount");
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
        Log.d(LOG_TAG, "initializeSyncAdapter");
        getSyncAccount(context);
    }
}