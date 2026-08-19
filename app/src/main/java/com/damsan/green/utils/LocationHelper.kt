package com.damsan.green.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Kiểm tra quyền truy cập vị trí
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Lấy vị trí hiện tại (suspend function - dùng với coroutine)
     * Thử lastKnownLocation trước, nếu null thì request fresh location
     */
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) return null

        return try {
            // Thử lấy location gần nhất trước (nhanh hơn)
            val lastLocation = getLastKnownLocation()
            if (lastLocation != null && isLocationFresh(lastLocation)) {
                lastLocation
            } else {
                // Request location mới
                requestFreshLocation()
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    @Suppress("MissingPermission")
    private suspend fun requestFreshLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000L
            ).apply {
                setMinUpdateIntervalMillis(2000L)
                setMaxUpdates(1)
                setWaitForAccurateLocation(true)
            }.build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    cont.resume(result.lastLocation)
                }
            }

            fusedLocationClient.requestLocationUpdates(request, callback, context.mainLooper)

            cont.invokeOnCancellation {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        }

    // Location được coi là "mới" nếu dưới 2 phút tuổi
    private fun isLocationFresh(location: Location): Boolean {
        val twoMinutes = 2 * 60 * 1000L
        return System.currentTimeMillis() - location.time < twoMinutes
    }

    /**
     * Chuyển tọa độ thành chuỗi địa chỉ đẹp cho hiển thị
     */
    fun formatCoordinates(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        return "%.5f°%s, %.5f°%s".format(Math.abs(lat), latDir, Math.abs(lon), lonDir)
    }
}
