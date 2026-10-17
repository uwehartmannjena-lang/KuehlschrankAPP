package com.example.myapplication.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class LocationResult(
    val zip: String,
    val city: String,
    val isGps: Boolean
)

object LocationHelper {
    const val DEFAULT_ZIP = "07743"
    const val DEFAULT_CITY = "Jena"

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    suspend fun detectLocation(context: Context): LocationResult = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context) || !isGpsEnabled(context)) {
            Log.d("LocationHelper", "Keine Standort-Berechtigung oder GPS inaktiv. Nutze Standard-PLZ.")
            return@withContext LocationResult(DEFAULT_ZIP, DEFAULT_CITY, isGps = false)
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val location: Location? = suspendCancellableCoroutine { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }

            if (location != null) {
                val geocoder = Geocoder(context, Locale.GERMANY)
                var zip: String? = null
                var city: String? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val addresses = suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }
                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                    val addr = addresses?.firstOrNull()
                    zip = addr?.postalCode
                    city = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val addr = addresses?.firstOrNull()
                    zip = addr?.postalCode
                    city = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
                }

                if (!zip.isNullOrBlank()) {
                    return@withContext LocationResult(
                        zip = zip.trim(),
                        city = (city ?: DEFAULT_CITY).trim(),
                        isGps = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("LocationHelper", "Fehler bei der Standortermittlung: ${e.message}", e)
        }

        LocationResult(DEFAULT_ZIP, DEFAULT_CITY, isGps = false)
    }
}
