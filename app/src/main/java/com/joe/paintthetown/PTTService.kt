package com.joe.paintthetown

import com.cocoahero.android.geojson.FeatureCollection
import com.cocoahero.android.geojson.Point
import com.google.gson.JsonElement
import org.json.JSONObject
import retrofit2.Call
import retrofit2.http.*


interface PTTService {
    @POST("map/GetPolysInArea")

    fun getAllinArea(@Body currentPostion:String, @Header("Authorization") authHeader:String ): Call<JsonElement>

    @POST("map/SaveArea")

    fun SavePolygon(@Body currentPostion:String, @Header("Authorization") authHeader:String) :Call<Void>

    @POST("account/Auhtorize")

    fun Auhtorize(@Body authCode:String, @Header("Authorization") authHeader:String) :Call<Void>
}