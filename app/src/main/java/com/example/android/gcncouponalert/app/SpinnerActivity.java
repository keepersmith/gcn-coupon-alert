package com.example.android.gcncouponalert.app;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

/**
 * Created by John on 4/7/2016.
 */
public class SpinnerActivity extends Activity implements AdapterView.OnItemSelectedListener {

    private final String LOG_TAG = SpinnerActivity.class.getSimpleName();

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        Log.d(LOG_TAG, " onItemSelected");
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
        Log.d(LOG_TAG, " onNothingSelected");
    }
}
