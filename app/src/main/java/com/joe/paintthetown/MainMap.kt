package com.joe.paintthetown

import android.annotation.SuppressLint
import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.*

import com.google.android.gms.tasks.OnSuccessListener
import android.Manifest.permission
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.Manifest
import android.os.Build
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.location.LocationSettingsStatusCodes
import android.content.IntentSender
import android.graphics.Color
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ApiException
import android.support.annotation.NonNull
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.location.LocationSettingsResponse
import android.location.Location

class MainMap : AppCompatActivity(), OnMapReadyCallback {

    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var mMap: GoogleMap
    private val points: ArrayList<LatLng> = ArrayList<LatLng>()
    private var path: Polyline? = null
    private var canDrawRect: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_map)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission();
        }

        mFusedLocationClient?.getLastLocation()
                ?.addOnSuccessListener(this) { location ->
                    // Got last known location. In some rare situations this can be null.
                    if (location != null) {
                        val currentPosition = LatLng(location.latitude, location.longitude)
                        mMap.addMarker(MarkerOptions().position(currentPosition).title("CurrentPosition"))
                        val zoomLevel = 16.0f //This goes up to 21
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, zoomLevel));

                        mMap.addPolygon(PolygonOptions()
                                .add(calculateOffsetPosition(currentPosition, 1.0, 1.0),
                                        calculateOffsetPosition(currentPosition, 1.0, -1.0),
                                        calculateOffsetPosition(currentPosition, -1.0, -1.0),
                                        calculateOffsetPosition(currentPosition, -1.0, 1.0)
                                ))


                    }
                }

        val mLocationRequest = createLocationRequest();
        val builder = LocationSettingsRequest.Builder().addLocationRequest(mLocationRequest)

        val client = LocationServices.getSettingsClient(this);
        val task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this) {
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
        }

        task.addOnFailureListener(this) { e ->
            val statusCode = (e as ApiException).statusCode
            when (statusCode) {
                CommonStatusCodes.RESOLUTION_REQUIRED ->
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        val resolvable = e as ResolvableApiException
                        // resolvable.startResolutionForResult(this,REQUEST_CHECK_SETTINGS)
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }

                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                }
            }// Location settings are not satisfied. However, we have no way
            // to fix the settings so we won't show the dialog.
        }

        mFusedLocationClient?.requestLocationUpdates(mLocationRequest,
                object : LocationCallback() {

                    override fun onLocationResult(locationResult: LocationResult) {
                        for (location in locationResult.locations) {
                            points.add(LatLng(location.latitude, location.longitude))
                        }

                        val distance = DistanceBetween(points.first(),points.last ())

                        if (distance > 50 && !canDrawRect) {
                            canDrawRect = true;
                        } else if (distance < 20 && canDrawRect) {
                            path?.remove()

                            mMap.addPolygon(PolygonOptions()
                                    .addAll(points)
                                    .fillColor(Color.BLUE))

                            points.clear()
                        }

                        path?.remove()
                        path = mMap.addPolyline(PolylineOptions()
                                .addAll(points))
                    }
                },
                null);


    }

    fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {//Can add more as per requirement

            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    123)
        }
    }

    fun calculateOffsetPosition(currenPostion: LatLng, dx: Double, dy: Double): LatLng {
        val r_earth = 6378;
        val new_latitude = currenPostion.latitude + (dy / r_earth) * (180 / Math.PI)
        val new_longitude = currenPostion.longitude + (dx / r_earth) * (180 / Math.PI / Math.cos(currenPostion.latitude * Math.PI / 180))
        return LatLng(new_latitude, new_longitude)
    }

    fun createLocationRequest(): LocationRequest {
        val mLocationRequest = LocationRequest()
        mLocationRequest.interval = 10000
        mLocationRequest.fastestInterval = 500
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        return mLocationRequest
    }

    fun DistanceBetween(position1: LatLng, position2: LatLng): Float {
        val loc1 = Location("")
        loc1.setLatitude(position1.latitude)
        loc1.setLongitude(position1.longitude)

        val loc2 = Location("")
        loc2.setLatitude(position2.latitude)
        loc2.setLongitude(position2.longitude)

        return loc1.distanceTo(loc2)
    }
}
