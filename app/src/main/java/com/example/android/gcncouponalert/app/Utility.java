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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.NetworkOnMainThreadException;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;

//import org.apache.http.util.ByteArrayBuffer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Utility {
    public static final String LOG_TAG = Utility.class.getSimpleName();

    public static void largeLog(String tag, String content) {
        if (content.length() > 4000) {
            Log.d(tag, "("+content.length()+") " + content.substring(0, 4000));
            largeLog(tag, content.substring(4000));
        } else {
            Log.d(tag, "("+content.length()+") " + content);
        }
    }

    public static String getPreferredLocation(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(context.getString(R.string.pref_location_key),context.getString(R.string.pref_location_default));
        //return prefs.getString(context.getString(R.string.pref_location_key),getDefaultLocation(context));
    }

    /*
    private static String getDefaultLocation(Context context) {
        String default_zip = null;
        if (MainActivity.mLastLocation != null) {
            try {
                default_zip = getAndSetZip(MainActivity.mLastLocation.getLatitude(), MainActivity.mLastLocation.getLongitude());
            } catch (NetworkOnMainThreadException e) {
                Log.d(LOG_TAG, "We can only call getAndSetZip from the SyncAdapter - rats!");
            }
        }
        if (default_zip == null) {
            default_zip = context.getString(R.string.pref_location_default);
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(context.getString(R.string.pref_location_key), default_zip);
        editor.commit();
        return default_zip;
    }

    private static String getAndSetZip (double lat, double lon) throws NetworkOnMainThreadException {
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
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(1000);
            urlConnection.connect();
            Log.d(LOG_TAG, " Got back: " +urlConnection.getResponseCode()+" "+urlConnection.getResponseMessage());
            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return null;
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
                return "";
            }
            String mapJsonStr = buffer.toString();
            Log.d(LOG_TAG, "API returned this: " + mapJsonStr);
            //found_data = getCouponDataFromJson(couponJsonStr, locationQuery);
        } catch (Exception e) {
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
        return null;
    }
    */

    public static boolean isMetric(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(context.getString(R.string.pref_units_key),
                context.getString(R.string.pref_units_metric))
                .equals(context.getString(R.string.pref_units_metric));
    }

    public static String formatTemperature(Context context, double temperature) {
        // Data stored in Celsius by default.  If user prefers to see in Fahrenheit, convert
        // the values here.
        String suffix = "\u00B0";
        if (!isMetric(context)) {
            temperature = (temperature * 1.8) + 32;
        }

        // For presentation, assume the user doesn't care about tenths of a degree.
        return String.format(context.getString(R.string.format_temperature), temperature);
    }

    static String formatDate(long dateInMilliseconds) {
        Date date = new Date(dateInMilliseconds);
        return DateFormat.getDateInstance().format(date);
    }

    // Format used for storing dates in the database.  ALso used for converting those strings
    // back into date objects for comparison/processing.
    public static final String DATE_FORMAT = "yyyyMMdd";

    /**
     * Helper method to convert the database representation of the date into something to display
     * to users.  As classy and polished a user experience as "20140102" is, we can do better.
     *
     * @param context Context to use for resource localization
     * @param dateInMillis The date in milliseconds
     * @return a user-friendly representation of the date.
     */
    public static String getFriendlyDayString(Context context, long dateInMillis) {
        // The day string for forecast uses the following logic:
        // For today: "Today, June 8"
        // For tomorrow:  "Tomorrow"
        // For the next 5 days: "Wednesday" (just the day name)
        // For all days after that: "Mon Jun 8"

        Time time = new Time();
        time.setToNow();
        long currentTime = System.currentTimeMillis();
        int julianDay = Time.getJulianDay(dateInMillis, time.gmtoff);
        int currentJulianDay = Time.getJulianDay(currentTime, time.gmtoff);

        // If the date we're building the String for is today's date, the format
        // is "Today, June 24"
        if (julianDay == currentJulianDay) {
            String today = context.getString(R.string.today);
            int formatId = R.string.format_full_friendly_date;
            return String.format(context.getString(
                    formatId,
                    today,
                    getFormattedMonthDay(context, dateInMillis)));
        } else if ( julianDay < currentJulianDay + 7 ) {
            // If the input date is less than a week in the future, just return the day name.
            return getDayName(context, dateInMillis);
        } else {
            // Otherwise, use the form "Mon Jun 3"
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(dateInMillis);
        }
    }

    /**
     * Given a day, returns just the name to use for that day.
     * E.g "today", "tomorrow", "wednesday".
     *
     * @param context Context to use for resource localization
     * @param dateInMillis The date in milliseconds
     * @return
     */
    public static String getDayName(Context context, long dateInMillis) {
        // If the date is today, return the localized version of "Today" instead of the actual
        // day name.

        Time t = new Time();
        t.setToNow();
        int julianDay = Time.getJulianDay(dateInMillis, t.gmtoff);
        int currentJulianDay = Time.getJulianDay(System.currentTimeMillis(), t.gmtoff);
        if (julianDay == currentJulianDay) {
            return context.getString(R.string.today);
        } else if ( julianDay == currentJulianDay +1 ) {
            return context.getString(R.string.tomorrow);
        } else {
            Time time = new Time();
            time.setToNow();
            // Otherwise, the format is just the day of the week (e.g "Wednesday".
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE");
            return dayFormat.format(dateInMillis);
        }
    }

    /**
     * Converts db date format to the format "Month day", e.g "June 24".
     * @param context Context to use for resource localization
     * @param dateInMillis The db formatted date string, expected to be of the form specified
     *                in Utility.DATE_FORMAT
     * @return The day in the form of a string formatted "December 6"
     */
    public static String getFormattedMonthDay(Context context, long dateInMillis ) {
        Time time = new Time();
        time.setToNow();
        SimpleDateFormat dbDateFormat = new SimpleDateFormat(Utility.DATE_FORMAT);
        SimpleDateFormat monthDayFormat = new SimpleDateFormat("MMMM dd");
        String monthDayString = monthDayFormat.format(dateInMillis);
        return monthDayString;
    }

    public static String getFormattedWind(Context context, float windSpeed, float degrees) {
        int windFormat;
        if (Utility.isMetric(context)) {
            windFormat = R.string.format_wind_kmh;
        } else {
            windFormat = R.string.format_wind_mph;
            windSpeed = .621371192237334f * windSpeed;
        }

        // From wind direction in degrees, determine compass direction as a string (e.g NW)
        // You know what's fun, writing really long if/else statements with tons of possible
        // conditions.  Seriously, try it!
        String direction = "Unknown";
        if (degrees >= 337.5 || degrees < 22.5) {
            direction = "N";
        } else if (degrees >= 22.5 && degrees < 67.5) {
            direction = "NE";
        } else if (degrees >= 67.5 && degrees < 112.5) {
            direction = "E";
        } else if (degrees >= 112.5 && degrees < 157.5) {
            direction = "SE";
        } else if (degrees >= 157.5 && degrees < 202.5) {
            direction = "S";
        } else if (degrees >= 202.5 && degrees < 247.5) {
            direction = "SW";
        } else if (degrees >= 247.5 && degrees < 292.5) {
            direction = "W";
        } else if (degrees >= 292.5 && degrees < 337.5) {
            direction = "NW";
        }
        return String.format(context.getString(windFormat), windSpeed, direction);
    }



    public static int getIconResourceForCoupon(int coupon_code) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        return R.drawable.ic_storm;
        /*
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        }
        return -1;
        */
    }

    /*
    private void saveImageToLocalStore(Bitmap finalBitmap) {
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/temp");
        myDir.mkdirs();
        String fname = "Profile_Image.png";
        File file = new File (myDir, fname);
        if (file.exists()) file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
*/

/*
    private void loadImageFromLocalStore(String imageURI) {
        try {
            Uri uri = Uri.parse("file://"+Environment.getExternalStorageDirectory().toString() + "/temp/" + imageURI);
            Bitmap bitmap = BitmapFactory.decodeStream(getActivity().getContentResolver().openInputStream(uri));
            profileImage.setImageBitmap(bitmap);
            profileImage.setTag("Other");
            select_image_button.setText(R.string.button_remove_profile_picture);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
*/
    public static Bitmap loadImageFromLocalStore(String imageURL, String image_extension) {
        try {
            String fileName = imageURL + "." + image_extension;

            String root = Environment.getExternalStorageDirectory().toString();
            File myDir = new File(root + "/temp");
            myDir.mkdirs();

            File file = new File(myDir, fileName);

            if (file.exists()) {
                FileInputStream is = new FileInputStream(file);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();
                //Log.d("loadImageFromLocalStore", "Found image: " + imageURL + "." + image_extension);
                return bitmap;

            } else {
                Log.d("loadImageFromLocalStore", "Could not find image: "+imageURL+"."+image_extension);
                return null;
            }
        } catch(IOException e) {
            Log.d("loadImageFromLocalStore", "Error: " + e);
            return null;
        }
    }
    public static void downloader(String imageURL, String image_extension) {
        try {
            String fileName = imageURL+"."+image_extension;

            String root = Environment.getExternalStorageDirectory().toString();
            File myDir = new File(root + "/temp");
            myDir.mkdirs();

            File file = new File(myDir, fileName);

            if (file.exists()) {
                //FileInputStream is = new FileInputStream(file);
                //Bitmap bitmap = BitmapFactory.decodeStream(is);
                //is.close();

                //return bitmap;
                //Log.d("Downloader", "Already have image: "+imageURL+"."+image_extension);

            } else {
                //String image_url_80x100 = R.string.coupon_image_url_prefix+couponInfo.getString(OWM_COUPON_URL_PATH)+"."+couponInfo.getString(OWM_COUPON_IMAGE_EXTENSION);
                String full_imageURL = "http://img6.grocerycouponnetwork.com/images/coupons/img-"+fileName;
                URL url = new URL(full_imageURL);
                URLConnection con = url.openConnection();

                InputStream is = con.getInputStream();
                BufferedInputStream bis = new BufferedInputStream(is);

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                //We create an array of bytes
                byte[] data = new byte[50];
                int current = 0;

                while((current = bis.read(data,0,data.length)) != -1){
                    buffer.write(data,0,current);
                }

                /*
                ByteArrayBuffer baf = new ByteArrayBuffer(50);
                int current = 0;
                while ((current = bis.read()) != -1) {
                    baf.append((byte) current);
                }
                */

                FileOutputStream fos = new FileOutputStream(file);
                //fos.write(baf.toByteArray());
                fos.write(buffer.toByteArray());
                fos.close();
                Log.d("Downloader", "Got image: " + full_imageURL);
                //return BitmapFactory.decodeByteArray(baf.toByteArray(), 0, baf.toByteArray().length);
            }

        }catch(IOException e){
            Log.d("Downloader", "Error: " + e);
            //return null;
        }

    }


    public static int getArtResourceForCoupon(int coupon_code) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        return R.drawable.art_storm;
        /*
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.art_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.art_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.art_rain;
        } else if (weatherId == 511) {
            return R.drawable.art_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.art_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.art_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.art_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.art_storm;
        } else if (weatherId == 800) {
            return R.drawable.art_clear;
        } else if (weatherId == 801) {
            return R.drawable.art_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.art_clouds;
        }
        return -1;
        */
    }


}