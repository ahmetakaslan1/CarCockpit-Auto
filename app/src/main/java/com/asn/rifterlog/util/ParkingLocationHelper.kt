package com.asn.rifterlog.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

object ParkingLocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): Pair<Location, String>? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null

        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        // Önce taze bir konum almaya çalış (3 saniye zaman aşımı)
        var bestLocation: Location? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (provider in providers) {
                if (! runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)) continue
                val fresh = withTimeoutOrNull(3000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val cancelSignal = CancellationSignal()
                        cont.invokeOnCancellation { cancelSignal.cancel() }
                        runCatching {
                            lm.getCurrentLocation(
                                provider,
                                cancelSignal,
                                ContextCompat.getMainExecutor(context)
                            ) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        }.onFailure {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (fresh != null) {
                    bestLocation = fresh
                    break
                }
            }
        }

        // Taze alınamadıysa bilinen son konumlardan en yenisini seç
        if (bestLocation == null) {
            for (provider in providers) {
                val last = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
                if (last != null) {
                    if (bestLocation == null || last.time > bestLocation!!.time) {
                        bestLocation = last
                    }
                }
            }
        }

        val loc = bestLocation ?: return@withContext null
        val address = resolveAddress(context, loc.latitude, loc.longitude)
        Pair(loc, address)
    }

    @SuppressLint("MissingPermission")
    fun getBestLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        var best: Location? = null
        for (provider in providers) {
            val last = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            if (last != null) {
                if (best == null || last.time > best!!.time) {
                    best = last
                }
            }
        }
        return best
    }

    @Suppress("DEPRECATION")
    fun resolveAddress(context: Context, lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale("tr", "TR"))
            val list = geocoder.getFromLocation(lat, lng, 1)
            val first = list?.firstOrNull()
            if (first != null) {
                val line = first.getAddressLine(0)
                if (!line.isNullOrBlank()) line
                else listOfNotNull(first.thoroughfare, first.subLocality, first.subAdminArea, first.adminArea)
                    .joinToString(", ")
                    .ifBlank { "%.5f, %.5f".format(Locale.US, lat, lng) }
            } else {
                "%.5f, %.5f".format(Locale.US, lat, lng)
            }
        } catch (_: Exception) {
            "%.5f, %.5f".format(Locale.US, lat, lng)
        }
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return ""
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
        return sdf.format(Date(millis))
    }

    fun formatTimestampWithDay(millis: Long): String {
        if (millis <= 0L) return ""
        val sdf = SimpleDateFormat("EEEE, dd.MM.yyyy · HH:mm", Locale("tr", "TR"))
        return sdf.format(Date(millis)).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("tr", "TR")) else it.toString()
        }
    }

    fun openWalkingDirections(context: Context, lat: Double, lng: Double) {
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(fallbackIntent)
        }
    }
}

