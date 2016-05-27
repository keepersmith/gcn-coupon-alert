package com.example.android.gcncouponalert.app;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * {@link CouponsAdapter} exposes a list of weather forecasts
 * from a {@link Cursor} to a {@link android.widget.ListView}.
 */
public class CouponsAdapter extends CursorAdapter {
    public static final String LOG_TAG = CouponsAdapter.class.getSimpleName();

    private static final int VIEW_TYPE_COUNT = 2;
    private static final int VIEW_TYPE_TODAY = 0;
    private static final int VIEW_TYPE_FUTURE_DAY = 1;

    // Flag to determine if we want to use a separate view for "today".
    private boolean mUseTodayLayout = true;

    /**
     * Cache of the children views for a forecast list item.
     */
    public static class ViewHolder {
        public final ImageView iconView;
        public final TextView dateView;
        //public final TextView descriptionView;
        //public final TextView highTempView;
        //public final TextView lowTempView;

        public ViewHolder(View view) {
            iconView = (ImageView) view.findViewById(R.id.list_item_icon);
            dateView = (TextView) view.findViewById(R.id.list_item_date_textview);
            //descriptionView = (TextView) view.findViewById(R.id.list_item_forecast_textview);
            //highTempView = (TextView) view.findViewById(R.id.list_item_high_textview);
            //lowTempView = (TextView) view.findViewById(R.id.list_item_low_textview);
        }
    }

    public CouponsAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        // Choose the layout type
        int viewType = getItemViewType(cursor.getPosition());
        int layoutId = -1;
        switch (viewType) {
            case VIEW_TYPE_TODAY: {
                //layoutId = R.layout.list_item_forecast_today;
                layoutId = R.layout.list_item_forecast;
                break;
            }
            case VIEW_TYPE_FUTURE_DAY: {
                layoutId = R.layout.list_item_forecast;
                break;
            }
        }

        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);

        ViewHolder viewHolder = new ViewHolder(view);
        view.setTag(viewHolder);

        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        ViewHolder viewHolder = (ViewHolder) view.getTag();

        int viewType = getItemViewType(cursor.getPosition());
        switch (viewType) {
            case VIEW_TYPE_TODAY: {
                // Get weather icon
                /*
                viewHolder.iconView.setImageResource(Utility.getArtResourceForWeatherCondition(
                        cursor.getInt(CouponsFragment.COL_WEATHER_CONDITION_ID)));
                        */
                break;
            }
            case VIEW_TYPE_FUTURE_DAY: {
                // Get weather icon
                /*
                viewHolder.iconView.setImageResource(Utility.getIconResourceForWeatherCondition(
                        cursor.getInt(CouponsFragment.COL_WEATHER_CONDITION_ID)));
                        */
                break;
            }
        }

        viewHolder.iconView.setImageBitmap(Utility.loadImageFromLocalStore(cursor.getString(CouponsFragment.COL_IMAGE_URL_80x100), cursor.getString(CouponsFragment.COL_IMAGE_EXT_80x100)));

        // Read date from cursor
        //long dateInMillis = cursor.getLong(CouponsFragment.COL_WEATHER_DATE);
        // Find TextView and set formatted date on it
        //viewHolder.dateView.setText(Utility.getFriendlyDayString(context, dateInMillis));
        //viewHolder.dateView.setText(cursor.getString(CouponsFragment.COL_COUPON_CODE));
        viewHolder.dateView.setText(cursor.getString(CouponsFragment.COL_COUPON_NAME));

        // Read weather forecast from cursor
        //String description = cursor.getString(CouponsFragment.COL_WEATHER_DESC);
        // Find TextView and set weather forecast on it
        //viewHolder.descriptionView.setText(description);
        //viewHolder.descriptionView.setText(cursor.getString(CouponsFragment.COL_COUPON_NAME));

        // For accessibility, add a content description to the icon field
        //viewHolder.iconView.setContentDescription(description);

        // Read user preference for metric or imperial temperature units
        //boolean isMetric = Utility.isMetric(context);

        // Read high temperature from cursor
        //double high = cursor.getDouble(CouponsFragment.COL_WEATHER_MAX_TEMP);
        //viewHolder.highTempView.setText(Utility.formatTemperature(context, high));

        // Read low temperature from cursor
        //double low = cursor.getDouble(CouponsFragment.COL_WEATHER_MIN_TEMP);
        //viewHolder.lowTempView.setText(Utility.formatTemperature(context, low));
        Log.d(LOG_TAG, "bindView: "+cursor.getString(CouponsFragment.COL_COUPON_NAME));
    }

    public void setUseTodayLayout(boolean useTodayLayout) {
        mUseTodayLayout = useTodayLayout;
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0 && mUseTodayLayout) ? VIEW_TYPE_TODAY : VIEW_TYPE_FUTURE_DAY;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }
}