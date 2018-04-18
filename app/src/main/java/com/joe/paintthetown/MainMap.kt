package com.joe.paintthetown

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.*

import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.Manifest
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationSettingsStatusCodes
import android.content.IntentSender
import android.graphics.Color

import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ApiException
import android.location.Location
import android.os.StrictMode
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.cocoahero.android.geojson.*
import com.cocoahero.android.geojson.Polygon
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.drive.Drive
import com.google.android.gms.games.Games
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.maps.android.geojson.GeoJsonLayer
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class MainMap : AppCompatActivity(), OnMapReadyCallback {

    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var mMap: GoogleMap
    private val points: ArrayList<LatLng> = ArrayList<LatLng>()
    private var jsonRing: Ring = Ring()
    private var path: Polyline? = null
    private var canDrawRect: Boolean = false
    private var mAuth = FirebaseAuth.getInstance()
    private var token: String? = null

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main_map, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.refresh -> return (true)
            R.id.user -> startActivity(Intent(this, UserInformation::class.java))
        }
        return (super.onOptionsItemSelected(item));
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_map)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()


        signIn()
        //  LoadSurroundingAreas()
        /*   if (mAuth.currentUser != null) {
            mAuth.currentUser!!.getIdToken(true).addOnSuccessListener({ result ->
                 token = result.token
                LoadSurroundingAreas()
            })

        } else {


            signIn()
        } // not signed in*/
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission();
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
                            jsonRing.addPosition(Position(location))
                        }

                        val distance = DistanceBetween(points.first(), points.last())

                        if (distance > 50 && !canDrawRect) {
                            canDrawRect = true;

                        } else if (distance < 20 && canDrawRect) {
                            path?.remove()

                            mMap.addPolygon(PolygonOptions()
                                    .addAll(points)
                                    .fillColor(Color.BLUE))


                            var retrofit = Retrofit.Builder().baseUrl("http://paintthetownserver.azurewebsites.net/api/").addConverterFactory(GsonConverterFactory.create()).build();
                            var service = retrofit.create(PTTService::class.java)
                            jsonRing.close();
                            var x = service.SavePolygon(Polygon(jsonRing).toJSON().toString(), "token $token").execute()

                            jsonRing = Ring()

                            points.clear()
                        }

                        path?.remove()
                        path = mMap.addPolyline(PolylineOptions()
                                .addAll(points))
                    }
                },
                null);


    }

    private fun LoadSurroundingAreas() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission();
        }
        mFusedLocationClient?.getLastLocation()
                ?.addOnSuccessListener(this) { location ->
                    // Got last known location. In some rare situations this can be null.
                    if (location != null) {
                        val currentPosition = LatLng(location.latitude, location.longitude)
                        //mMap.addMarker(MarkerOptions().position(currentPosition).title("CurrentPosition"))
                        val zoomLevel = 14.0f //This goes up to 21
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, zoomLevel));

                        var retrofit = Retrofit.Builder().baseUrl("http://paintthetownserver.azurewebsites.net/api/").addConverterFactory(GsonConverterFactory.create()).build();
                        var service = retrofit.create(PTTService::class.java)

                        val point = Point(location.latitude, location.longitude)
                        var x = service.getAllinArea(point.toJSON().toString(), "token $token").execute()

                        var jsonObj = x.body()?.getAsJsonObject();
                        var strObj = x.body()?.toString();
                        var jo = JSONObject(strObj);


                        var layer = GeoJsonLayer(mMap, jo);

                        layer.addLayerToMap();
                    }
                }
    }

    fun checkPermission() {
        var SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            var policy = StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
            //your codes here

        }

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
        mLocationRequest.interval = 1000
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

    fun signIn() {

        /*  var signInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);

        signInClient.silentSignIn().addOnCompleteListener{task ->
            if (task.isSuccessful()) {
                // The signed in account is stored in the task's result.
                var signedInAccount = task.getResult();
            } else {
                // Player will need to sign-in explicitly using via UI
            }
        }*/


        val RC_SIGN_IN = 123;
        var gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("300353354116-bigm93gijg4013dro7qnkg4je67curtg.apps.googleusercontent.com")
                .requestServerAuthCode("300353354116-bigm93gijg4013dro7qnkg4je67curtg.apps.googleusercontent.com")

                .requestEmail()
                .build();

        var mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);

        var signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data);
        val RC_SIGN_IN = 123;
        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (resultCode != RESULT_CANCELED) {
            if (requestCode == RC_SIGN_IN) {
                var task = GoogleSignIn.getSignedInAccountFromIntent(data);
                try {
                    // Google Sign In was successful, authenticate with Firebase
                    var account = task.getResult()
                   // firebaseAuthWithGoogle(account)
                    var authCode = account.serverAuthCode
                    token = account.idToken
                    firebaseAuthWithGoogle(account);
                    var retrofit = Retrofit.Builder().baseUrl("http://paintthetownserver.azurewebsites.net/api/").addConverterFactory(GsonConverterFactory.create()).build();
                    var service = retrofit.create(PTTService::class.java)
                    service.Auhtorize(authCode.toString(), "token $token").execute()

                } catch (e: ApiException) {
                    // Google Sign In failed, update UI appropriately
                    //Log.w(TAG, "Google sign in failed", e);
                    // ...
                }
            }
        }}

         fun firebaseAuthWithGoogle(acct: GoogleSignInAccount?) {
            //Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());
           // token = acct?.getIdToken()
            var credential = GoogleAuthProvider.getCredential(token, null);
            mAuth.signInWithCredential(credential)
                    .addOnCompleteListener(this, OnCompleteListener<AuthResult>() {
                        @Override
                        fun onComplete(task: Task<AuthResult>) {
                            if (task.isSuccessful()) {
                                // Sign in success, update UI with the signed-in user's information
                                //Log.d(TAG, "signInWithCredential:success");
                                var user = mAuth.getCurrentUser();

                                LoadSurroundingAreas()
                                //updateUI(user);
                            } else {
                                // If sign in fails, display a message to the user.
                                //Log.w(TAG, "signInWithCredential:failure", task.getException());
                                //Snackbar.make(findViewById(R.id.main_layout), "Authentication Failed.", Snackbar.LENGTH_SHORT).show();
                                //updateUI(null);
                            }

                            // ...
                        }
                    });
        }
    }



