package com.speedcam.nav.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.speedcam.nav.data.model.LocationPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class LocationManager(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var lastBearing: Float = 0f
    private var lastSpeedKmh: Float = 0f

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationPoint? = suspendCancellableCoroutine { continuation ->
        val cts = CancellationTokenSource()
        continuation.invokeOnCancellation {
            cts.cancel()
        }
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        continuation.resume(processRawLocation(loc))
                    } else {
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                            continuation.resume(lastLoc?.let { processRawLocation(it) })
                        }.addOnFailureListener {
                            continuation.resume(null)
                        }
                    }
                }
                .addOnFailureListener {
                    fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                        continuation.resume(lastLoc?.let { processRawLocation(it) })
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                }
        } catch (e: SecurityException) {
            continuation.resume(null)
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 1000L): Flow<LocationPoint> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(0.5f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val point = processRawLocation(loc)
                    trySend(point)
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    trySend(processRawLocation(loc))
                }
            }
        } catch (e: SecurityException) {
            // Handled when permission is requested in UI
        }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    private fun processRawLocation(loc: Location): LocationPoint {
        val rawSpeed = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        // Speed smoothing & stationary jitter filtering
        val speedKmh = if (rawSpeed < 1.8f) {
            0f
        } else {
            // Low-pass filter for smooth gauge animations
            lastSpeedKmh * 0.3f + rawSpeed * 0.7f
        }
        lastSpeedKmh = speedKmh

        if (loc.hasBearing() && speedKmh > 2.5f) {
            lastBearing = loc.bearing
        }

        return LocationPoint(
            latitude = loc.latitude,
            longitude = loc.longitude,
            speedKmh = speedKmh,
            bearing = lastBearing,
            accuracy = loc.accuracy,
            altitude = loc.altitude,
            timestamp = loc.time,
            hasSpeed = loc.hasSpeed()
        )
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }
}
