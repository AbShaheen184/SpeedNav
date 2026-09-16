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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class LocationManager(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var lastBearing: Float = 0f
    private var lastSpeedKmh: Float = 0f

    // Simulated drive state
    private val _isSimulating = MutableStateFlow(false)
    val isSimulating = _isSimulating.asStateFlow()

    private val _simulationLocation = MutableStateFlow<LocationPoint?>(null)
    val simulationLocation = _simulationLocation.asStateFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

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
                    if (!_isSimulating.value) {
                        trySend(point)
                    }
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && !_isSimulating.value) {
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

    /**
     * Start a simulation drive along the given route waypoints or around an area.
     */
    fun startSimulation(
        waypoints: List<Pair<Double, Double>>,
        targetSpeedKmh: Float = 85f,
        speedVary: Boolean = true
    ) {
        if (waypoints.isEmpty()) return
        stopSimulation()

        _isSimulating.value = true
        simulationJob = scope.launch {
            var currentIndex = 0
            var currentSpeed = 0f

            while (isActive && _isSimulating.value && currentIndex < waypoints.size) {
                val currentPt = waypoints[currentIndex]
                val nextPt = if (currentIndex + 1 < waypoints.size) waypoints[currentIndex + 1] else currentPt

                // Calculate bearing towards next point
                val bearing = calculateBearing(
                    currentPt.first, currentPt.second,
                    nextPt.first, nextPt.second
                )

                // Accelerate or vary speed naturally
                val speedObjective = if (speedVary) {
                    val variation = kotlin.math.sin(currentIndex * 0.4) * 20f
                    (targetSpeedKmh + variation).toFloat().coerceIn(35f, 135f)
                } else {
                    targetSpeedKmh
                }

                currentSpeed += (speedObjective - currentSpeed) * 0.25f

                val point = LocationPoint(
                    latitude = currentPt.first,
                    longitude = currentPt.second,
                    speedKmh = currentSpeed,
                    bearing = bearing,
                    accuracy = 4.0f,
                    altitude = 45.0,
                    timestamp = System.currentTimeMillis(),
                    hasSpeed = true
                )

                _simulationLocation.value = point
                currentIndex++
                delay(1000L)
            }
            // Loop or finish
            stopSimulation()
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _isSimulating.value = false
        _simulationLocation.value = null
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
