package com.devkorea1m.weatherwake.util

import android.content.Context
import android.content.SharedPreferences
import com.devkorea1m.weatherwake.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

data class LatLon(val lat: Double, val lon: Double, val label: String)

object LocationHelper {

    private const val PREFS = "location_prefs"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_LABEL = "label"
    private const val KEY_USE_GPS = "use_gps"

    // ── GPS 현재 위치 조회 ──────────────────────────
    suspend fun getCurrentLocation(context: Context): LatLon? = runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val label = context.getString(R.string.label_current_location_gps)

        // 1차: 캐시된 마지막 위치 (빠르고 권한 문제 없음)
        val last = client.lastLocation.await()
        if (last != null) return@runCatching LatLon(last.latitude, last.longitude, label)

        // 2차: 캐시 없을 때 새로 요청 (COARSE로도 동작)
        val cts = CancellationTokenSource()
        val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
        loc?.let { LatLon(it.latitude, it.longitude, label) }
    }.getOrNull()

    // ── 저장된 위치 ─────────────────────────────────
    fun getSavedLocation(context: Context): LatLon? {
        val prefs = prefs(context)
        val lat = prefs.getFloat(KEY_LAT, Float.MIN_VALUE).toDouble()
        val lon = prefs.getFloat(KEY_LON, Float.MIN_VALUE).toDouble()
        val label = prefs.getString(KEY_LABEL, "") ?: ""
        return if (lat == Float.MIN_VALUE.toDouble()) null else LatLon(lat, lon, label)
    }

    fun saveLocation(context: Context, latLon: LatLon) {
        prefs(context).edit()
            .putFloat(KEY_LAT, latLon.lat.toFloat())
            .putFloat(KEY_LON, latLon.lon.toFloat())
            .putString(KEY_LABEL, latLon.label)
            .apply()
    }

    fun isUseGps(context: Context) = prefs(context).getBoolean(KEY_USE_GPS, true)
    fun setUseGps(context: Context, useGps: Boolean) =
        prefs(context).edit().putBoolean(KEY_USE_GPS, useGps).apply()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
