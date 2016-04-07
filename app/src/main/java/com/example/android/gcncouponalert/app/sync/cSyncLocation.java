package com.example.android.gcncouponalert.app.sync;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationServices;

/**
 * Created by john on 4/6/2016.
 */

public class cSyncLocation  implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener
{

    // =======================================================
    // private vars
    // =======================================================
    private GoogleApiClient moGoogleApiClient;
    private LocationRequest moLocationRequest;
    private Location moCurrentLocation;
    private static final int kTIMEOUT_MILLISECONDS = 2500;

    // =======================================================
    // public static vars
    // =======================================================

    // =======================================================
    // public methods
    // =======================================================

    public void Start(Context oContext)
    {
        if (moGoogleApiClient == null)
        {
            moGoogleApiClient = new GoogleApiClient.Builder(oContext)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        if (moLocationRequest == null)
        {
            moLocationRequest = new LocationRequest();

            moLocationRequest.setInterval(1);
            moLocationRequest.setFastestInterval(1);
            moLocationRequest.setInterval(1);
            moLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }
        // Start the connection
        if (moGoogleApiClient != null)
        {
            if (!moGoogleApiClient.isConnecting() && !moGoogleApiClient.isConnected())
                moGoogleApiClient.connect();
            else if (moCurrentLocation == null)
                LocationServices.FusedLocationApi.requestLocationUpdates(moGoogleApiClient, moLocationRequest, this);

        }
    }

    public void Stop()
    {
        if (moGoogleApiClient != null && moGoogleApiClient.isConnected())
            LocationServices.FusedLocationApi.removeLocationUpdates(moGoogleApiClient, this);
        if (moGoogleApiClient != null)
            moGoogleApiClient.disconnect();
    }

    public Location GetLocationBlocking(Context oContext)
    {
        if (moCurrentLocation == null)
        {
            int intTimeout = kTIMEOUT_MILLISECONDS;
            Start(oContext);
            //while(intTimeout > 0 && aFrmLocationActivity.IsLastLocationExpired(oContext))
            while(intTimeout > 0 && moCurrentLocation == null)
            {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    //...
                }
                intTimeout -= 100;
            }
            Stop();
        }
        return moCurrentLocation;
    }

    // =======================================================
    // Location API Events
    // =======================================================

    @Override
    public void onLocationChanged(Location oLocation)
    {
        if (oLocation != null)
        {
            moCurrentLocation = oLocation;
        }
    }

    // =======================================================
    // Google API Connection Events
    // =======================================================


    @Override
    public void onConnected(Bundle connectionHint)
    {
        // Connected to Google Play services! The good stuff goes here.
        if (moGoogleApiClient != null)
        {
            Location oLocation = LocationServices.FusedLocationApi.getLastLocation(moGoogleApiClient);
            if (oLocation != null)
                moCurrentLocation = oLocation;
            else
                LocationServices.FusedLocationApi.requestLocationUpdates(moGoogleApiClient, moLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int cause)
    {
        //...
    }

    @Override
    public void onConnectionFailed(ConnectionResult result)
    {
        //...
    }


}