package com.example.android.gcncouponalert.app.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class GCNCouponAlertSyncService extends Service {
    private static final Object sSyncAdapterLock = new Object();
    private static GCNCouponAlertSyncAdapter sGCNCouponAlertSyncAdapter = null;

    @Override
    public void onCreate() {
        Log.d("GCNCoupAlertSyncService", "onCreate - GCNCouponAlertSyncService");
        synchronized (sSyncAdapterLock) {
            if (sGCNCouponAlertSyncAdapter == null) {
                sGCNCouponAlertSyncAdapter = new GCNCouponAlertSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sGCNCouponAlertSyncAdapter.getSyncAdapterBinder();
    }
}