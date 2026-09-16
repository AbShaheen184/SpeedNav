package com.speedcam.nav.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speedcam.nav.data.local.AppDatabase
import com.speedcam.nav.data.location.LocationManager
import com.speedcam.nav.data.model.LocationPoint
import com.speedcam.nav.data.model.ManeuverType
import com.speedcam.nav.data.model.NavigationRoute
import com.speedcam.nav.data.model.SearchLocation
import com.speedcam.nav.data.model.SpeedCameraNode
import com.speedcam.nav.data.repository.SavedLocationRepository
import com.speedcam.nav.data.repository.SpeedNavRepository
import com.speedcam.nav.ui.util.AlertSoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpeedNavRepository()
    private val locationManager = LocationManager(application)
    private val alertSoundManager = AlertSoundManager(application)
    private val savedLocationRepository = SavedLocationRepository(
        AppDatabase.getDatabase(application).savedLocationDao()
    )

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var searchJob: Job? = null

    // Track previously alerted camera IDs to avoid repetitive beeping for the same camera
    private val alertedCameraIds = mutableSetOf<Long>()
    private var lastOverspeedAlertTime = 0L

    init {
        startLocationUpdates()
        observeSimulation()
        observeSavedLocations()
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationManager.getLocationUpdates().collect { point ->
                if (!_uiState.value.isSimulating) {
                    handleNewLocation(point)
                }
            }
        }
    }

    private fun observeSimulation() {
        viewModelScope.launch {
            locationManager.simulationLocation.collect { simPoint ->
                if (_uiState.value.isSimulating && simPoint != null) {
                    handleNewLocation(simPoint)
                }
            }
        }
    }

    private fun observeSavedLocations() {
        viewModelScope.launch {
            savedLocationRepository.savedLocations.collect { list ->
                _uiState.update { it.copy(savedLocations = list) }
            }
        }
    }

    private fun handleNewLocation(point: LocationPoint) {
        val speedKmh = point.speedKmh.toInt()
        val currentLimit = _uiState.value.speedLimitKmh
        val isOverspeed = currentLimit != null && speedKmh > currentLimit
        val overspeedDelta = if (isOverspeed) speedKmh - currentLimit!! else 0

        _uiState.update { current ->
            current.copy(
                currentLocation = point,
                currentSpeedKmh = speedKmh,
                isOverspeed = isOverspeed,
                overspeedDelta = overspeedDelta
            )
        }

        // Check and pre-populate default sample saved locations once we have user's coordinate
        viewModelScope.launch {
            savedLocationRepository.ensureDefaultLocations(point.latitude, point.longitude)
        }

        // Update Turn-by-Turn tracking if active navigation is on
        if (_uiState.value.isNavigating && _uiState.value.currentRoute != null) {
            updateNavigationProgress(point)
        }

        // Query speed limit and cameras (debounced in repository)
        queryRoadData(point.latitude, point.longitude)

        // Check for speed cameras within 500 meters
        checkCameraProximity(point.latitude, point.longitude)

        // Overspeed audible reminder (interval: 10s)
        if (isOverspeed && !_uiState.value.isMuted) {
            val now = System.currentTimeMillis()
            if (now - lastOverspeedAlertTime > 10_000L) {
                lastOverspeedAlertTime = now
                alertSoundManager.playOverspeedWarningTone()
            }
        }
    }

    private fun updateNavigationProgress(point: LocationPoint) {
        val route = _uiState.value.currentRoute ?: return
        val steps = route.steps
        if (steps.isEmpty()) return

        var stepIdx = _uiState.value.currentStepIndex.coerceIn(0, steps.size - 1)
        val currentStep = steps[stepIdx]

        // Calculate distance to current maneuver
        val results = FloatArray(1)
        Location.distanceBetween(
            point.latitude, point.longitude,
            currentStep.latitude, currentStep.longitude,
            results
        )
        val distToManeuver = results[0].toDouble()

        // Advance to next step if user reached the maneuver (within 35 meters)
        if (distToManeuver < 35.0 && stepIdx < steps.size - 1) {
            stepIdx++
        }

        val activeStep = steps[stepIdx]
        val upcomingNextStep = if (stepIdx + 1 < steps.size) steps[stepIdx + 1] else null

        // Recalculate distance to the active step
        Location.distanceBetween(
            point.latitude, point.longitude,
            activeStep.latitude, activeStep.longitude,
            results
        )
        val finalDistToManeuver = results[0].toDouble()

        // Check if approaching a turn or exit (e.g. within 220 meters for smoother lead-in)
        val isTurnOrExit = activeStep.maneuverType != ManeuverType.STRAIGHT &&
                activeStep.maneuverType != ManeuverType.DEPART

        // Also check if we just passed a maneuver recently (within 100 meters) 
        // to keep the zoom-in state through the turn/junction
        val prevStep = if (stepIdx > 0) steps[stepIdx - 1] else null
        var isRecentManeuver = false
        if (prevStep != null) {
            Location.distanceBetween(
                point.latitude, point.longitude,
                prevStep.latitude, prevStep.longitude,
                results
            )
            val distFromPrev = results[0].toDouble()
            isRecentManeuver = distFromPrev < 100.0 && 
                              prevStep.maneuverType != ManeuverType.STRAIGHT &&
                              prevStep.maneuverType != ManeuverType.DEPART
        }

        val isClose = finalDistToManeuver <= 220.0
        val isApproaching = (isTurnOrExit && isClose) || isRecentManeuver

        // Calculate remaining route distance and duration from current location to destination
        val destPt = route.waypoints.lastOrNull()
        var remainingMeters = route.totalDistanceMeters
        if (destPt != null) {
            Location.distanceBetween(
                point.latitude, point.longitude,
                destPt.first, destPt.second,
                results
            )
            remainingMeters = results[0].toDouble().coerceAtLeast(0.0)
        }

        val speedMs = (point.speedKmh / 3.6).coerceAtLeast(8.0) // fallback ~30km/h
        val remainingSecs = remainingMeters / speedMs
        val etaTimeMs = System.currentTimeMillis() + (remainingSecs * 1000).toLong()
        val etaFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(etaTimeMs))

        _uiState.update {
            it.copy(
                currentStepIndex = stepIdx,
                currentStep = activeStep,
                nextStep = upcomingNextStep,
                distanceToNextManeuverMeters = finalDistToManeuver,
                isApproachingTurnOrExit = isApproaching,
                remainingDistanceMeters = remainingMeters,
                remainingDurationSeconds = remainingSecs,
                etaFormatted = etaFormatted
            )
        }
    }

    private fun queryRoadData(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val limit = repository.getSpeedLimit(lat, lon)
                if (limit != null) {
                    _uiState.update {
                        val currentSpeed = it.currentSpeedKmh
                        val isOver = currentSpeed > limit
                        it.copy(
                            speedLimitKmh = limit,
                            isOverspeed = isOver,
                            overspeedDelta = if (isOver) currentSpeed - limit else 0
                        )
                    }
                }

                val cameras = repository.getNearbySpeedCameras(lat, lon, 3500)
                _uiState.update { it.copy(nearbyCameras = cameras) }
                checkCameraProximity(lat, lon)
            } catch (e: Exception) {
                // Graceful fallback
            }
        }
    }

    private fun checkCameraProximity(userLat: Double, userLon: Double) {
        val cameras = _uiState.value.nearbyCameras
        val criticalCamera = cameras.firstOrNull { it.distanceMeters in 1..500 }

        if (criticalCamera != null) {
            _uiState.update { it.copy(activeCameraAlert = criticalCamera) }

            if (!alertedCameraIds.contains(criticalCamera.id)) {
                alertedCameraIds.add(criticalCamera.id)
                if (!_uiState.value.isMuted) {
                    alertSoundManager.playCameraAlertBeep()
                }
            }
        } else {
            if (_uiState.value.activeCameraAlert != null) {
                _uiState.update { it.copy(activeCameraAlert = null) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(destinationText = query, isSearching = query.isNotBlank()) }
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                delay(350L) // Debounce typing
                val results = repository.searchPlaces(query)
                _uiState.update { it.copy(searchResults = results) }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    /**
     * Called when a destination is chosen either from search results or from saved locations.
     * Calculates the route and shows the Route Preview Card with "Start Navigation" button.
     */
    fun selectDestination(location: SearchLocation) {
        _uiState.update {
            it.copy(
                selectedDestination = location,
                destinationText = location.title,
                isSearching = false,
                searchResults = emptyList(),
                isRoutePreviewShowing = true,
                isNavigating = false
            )
        }
        checkIfDestinationSaved(location.latitude, location.longitude)
        calculateRoutePreview(location.latitude, location.longitude, location.title)
    }

    private fun checkIfDestinationSaved(lat: Double, lon: Double) {
        viewModelScope.launch {
            val isSaved = savedLocationRepository.isLocationSaved(lat, lon)
            _uiState.update { it.copy(isDestinationSaved = isSaved) }
        }
    }

    fun saveCurrentDestination(customTitle: String? = null, category: String = "FAVORITE") {
        val dest = _uiState.value.selectedDestination ?: return
        viewModelScope.launch {
            val title = customTitle?.ifBlank { null } ?: dest.title
            savedLocationRepository.saveLocation(
                title = title,
                subtitle = dest.subtitle,
                latitude = dest.latitude,
                longitude = dest.longitude,
                category = category
            )
            _uiState.update {
                it.copy(
                    isDestinationSaved = true,
                    statusMessage = "Saved '$title' to your locations"
                )
            }
        }
    }

    fun saveLocation(title: String, subtitle: String, lat: Double, lon: Double, category: String = "FAVORITE") {
        viewModelScope.launch {
            savedLocationRepository.saveLocation(
                title = title,
                subtitle = subtitle,
                latitude = lat,
                longitude = lon,
                category = category
            )
            _uiState.update {
                it.copy(statusMessage = "Saved '$title' to your locations")
            }
        }
    }

    fun deleteSavedLocation(id: Long) {
        viewModelScope.launch {
            savedLocationRepository.deleteLocation(id)
            val dest = _uiState.value.selectedDestination
            if (dest != null) {
                checkIfDestinationSaved(dest.latitude, dest.longitude)
            }
        }
    }

    fun updateSavedLocation(id: Long, title: String, subtitle: String, category: String) {
        viewModelScope.launch {
            savedLocationRepository.updateLocation(id, title, subtitle, category)
            _uiState.update {
                it.copy(statusMessage = "Updated '$title'")
            }
        }
    }

    fun setShowSavedLocationsSheet(show: Boolean) {
        _uiState.update { it.copy(showSavedLocationsSheet = show) }
    }

    private fun calculateRoutePreview(destLat: Double, destLon: Double, destName: String) {
        val currentLoc = _uiState.value.currentLocation
        val startLat = currentLoc?.latitude ?: 51.5074
        val startLon = currentLoc?.longitude ?: -0.1278

        viewModelScope.launch {
            _uiState.update { it.copy(isRouteLoading = true, statusMessage = "Finding fastest route to $destName...") }
            try {
                val route = repository.getRoute(startLat, startLon, destLat, destLon)
                if (route != null) {
                    val enrichedRoute = route.copy(destinationName = destName)
                    val firstStep = enrichedRoute.steps.firstOrNull()
                    val secondStep = if (enrichedRoute.steps.size > 1) enrichedRoute.steps[1] else null
                    val etaMs = System.currentTimeMillis() + (enrichedRoute.durationSeconds * 1000).toLong()
                    val etaFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(etaMs))

                    _uiState.update {
                        it.copy(
                            currentRoute = enrichedRoute,
                            isRoutePreviewShowing = true,
                            isNavigating = false,
                            isRouteLoading = false,
                            currentStepIndex = 0,
                            currentStep = firstStep,
                            nextStep = secondStep,
                            distanceToNextManeuverMeters = firstStep?.distanceMeters,
                            remainingDistanceMeters = enrichedRoute.totalDistanceMeters,
                            remainingDurationSeconds = enrichedRoute.durationSeconds,
                            etaFormatted = etaFormatted,
                            statusMessage = "Route ready: ${enrichedRoute.summary}"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isRouteLoading = false,
                            statusMessage = "Could not compute route to $destName"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRouteLoading = false,
                        statusMessage = "Route error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * User clicks "Start Navigation" button:
     * Enters full Google Maps style active navigation mode!
     */
    fun startNavigation() {
        val route = _uiState.value.currentRoute
        if (route == null) return

        _uiState.update {
            it.copy(
                isNavigating = true,
                isRoutePreviewShowing = false,
                isFollowMode = true,
                statusMessage = "Navigation started. Follow route."
            )
        }

        // Start drive simulation along route so user immediately sees real-time progress and zoom transitions
        startSimulation()
    }

    /**
     * Stop active navigation and return to normal map overview
     */
    fun exitNavigation() {
        stopSimulation()
        _uiState.update {
            it.copy(
                isNavigating = false,
                isRoutePreviewShowing = false,
                statusMessage = "Navigation ended"
            )
        }
    }

    fun clearRoute() {
        stopSimulation()
        _uiState.update {
            it.copy(
                currentRoute = null,
                selectedDestination = null,
                isNavigating = false,
                isRoutePreviewShowing = false,
                destinationText = "",
                statusMessage = null,
                currentStep = null,
                nextStep = null,
                distanceToNextManeuverMeters = null,
                isApproachingTurnOrExit = false
            )
        }
    }

    fun toggleSimulation() {
        if (_uiState.value.isSimulating) {
            stopSimulation()
        } else {
            startSimulation()
        }
    }

    private fun startSimulation() {
        val route = _uiState.value.currentRoute
        val currentLoc = _uiState.value.currentLocation
        val baseLat = currentLoc?.latitude ?: 51.5074
        val baseLon = currentLoc?.longitude ?: -0.1278

        val waypoints = if (route != null && route.waypoints.size >= 2) {
            route.waypoints
        } else {
            listOf(
                Pair(baseLat, baseLon),
                Pair(baseLat + 0.0015, baseLon + 0.0010),
                Pair(baseLat + 0.0030, baseLon + 0.0025),
                Pair(baseLat + 0.0045, baseLon + 0.0038),
                Pair(baseLat + 0.0060, baseLon + 0.0050),
                Pair(baseLat + 0.0072, baseLon + 0.0060),
                Pair(baseLat + 0.0085, baseLon + 0.0070),
                Pair(baseLat + 0.0105, baseLon + 0.0085),
                Pair(baseLat + 0.0125, baseLon + 0.0075),
                Pair(baseLat + 0.0135, baseLon + 0.0050),
                Pair(baseLat + 0.0120, baseLon + 0.0020),
                Pair(baseLat + 0.0090, baseLon),
                Pair(baseLat + 0.0050, baseLon - 0.0010),
                Pair(baseLat + 0.0020, baseLon - 0.0005),
                Pair(baseLat, baseLon)
            )
        }

        _uiState.update { it.copy(isSimulating = true, isFollowMode = true) }
        locationManager.startSimulation(waypoints, targetSpeedKmh = 80f, speedVary = true)
    }

    fun stopSimulation() {
        locationManager.stopSimulation()
        _uiState.update { it.copy(isSimulating = false) }
    }

    fun toggleFollowMode() {
        _uiState.update { it.copy(isFollowMode = !it.isFollowMode) }
    }

    fun toggleDarkMapTheme() {
        _uiState.update { it.copy(isDarkMapTheme = !it.isDarkMapTheme) }
    }

    fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun dismissCameraAlert() {
        _uiState.update { it.copy(activeCameraAlert = null) }
    }

    fun setSpeedLimit(limit: Int?) {
        _uiState.update {
            val currentSpeed = it.currentSpeedKmh
            val isOver = limit != null && currentSpeed > limit
            it.copy(
                speedLimitKmh = limit,
                isOverspeed = isOver,
                overspeedDelta = if (isOver) currentSpeed - limit else 0
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationManager.stopSimulation()
        alertSoundManager.release()
    }
}
