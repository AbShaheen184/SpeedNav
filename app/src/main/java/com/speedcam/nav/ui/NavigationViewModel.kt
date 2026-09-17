package com.speedcam.nav.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speedcam.nav.data.local.AppDatabase
import com.speedcam.nav.data.location.LocationManager
import com.speedcam.nav.data.model.DroppedPinLocation
import com.speedcam.nav.data.model.LocationPoint
import com.speedcam.nav.data.model.ManeuverType
import com.speedcam.nav.data.model.NavigationRoute
import com.speedcam.nav.data.model.NominatimAddress
import com.speedcam.nav.data.model.ResolvedMapLink
import com.speedcam.nav.data.model.SearchLocation
import com.speedcam.nav.data.model.SpeedCameraNode
import com.speedcam.nav.data.remote.GoogleMapsLinkResolver
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
    private var reverseGeocodePinJob: Job? = null

    // Track previously alerted camera IDs to avoid repetitive beeping for the same camera
    private val alertedCameraIds = mutableSetOf<Long>()
    private var lastOverspeedAlertTime = 0L
    private var lastRerouteTime = 0L
    private var isRerouting = false
    private var lastReverseGeocodeTime = 0L

    init {
        startLocationUpdates()
        observeSavedLocations()
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationManager.getLocationUpdates().collect { point ->
                handleNewLocation(point)
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

        // Resolve current city and country code for search location bias (every 60 seconds)
        val nowTime = System.currentTimeMillis()
        if (nowTime - lastReverseGeocodeTime > 60_000L || _uiState.value.currentCity == null) {
            lastReverseGeocodeTime = nowTime
            viewModelScope.launch {
                val addr = repository.reverseGeocode(point.latitude, point.longitude)
                if (addr != null) {
                    val city = addr.city ?: addr.town ?: addr.village ?: addr.municipality ?: addr.suburb
                    val country = addr.country
                    val countryCode = addr.countryCode
                    _uiState.update {
                        it.copy(
                            currentCity = city,
                            currentCountry = country,
                            currentCountryCode = countryCode
                        )
                    }
                }
            }
        }

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

        // While navigating: automatically get fresh location and update directions if off-route
        val now = System.currentTimeMillis()
        if (!isRerouting && now - lastRerouteTime > 15_000L) {
            val isOffRoute = isUserOffRoute(point, route.waypoints)
            if (isOffRoute) {
                recalculateRouteFromCurrentLocation(point)
            }
        }
    }

    private fun isUserOffRoute(point: LocationPoint, waypoints: List<Pair<Double, Double>>): Boolean {
        if (waypoints.isEmpty()) return false
        val results = FloatArray(1)
        var minDistance = Float.MAX_VALUE
        for (wp in waypoints) {
            Location.distanceBetween(point.latitude, point.longitude, wp.first, wp.second, results)
            if (results[0] < minDistance) {
                minDistance = results[0]
            }
            if (minDistance < 45f) return false
        }
        return minDistance > 65f
    }

    fun recalculateRoute() {
        val currentLoc = _uiState.value.currentLocation ?: return
        recalculateRouteFromCurrentLocation(currentLoc)
    }

    private fun recalculateRouteFromCurrentLocation(point: LocationPoint) {
        val dest = _uiState.value.selectedDestination ?: _uiState.value.currentRoute?.let { route ->
            val last = route.waypoints.lastOrNull()
            if (last != null) {
                SearchLocation(
                    title = route.destinationName ?: "Destination",
                    subtitle = "",
                    latitude = last.first,
                    longitude = last.second
                )
            } else null
        } ?: return

        isRerouting = true
        lastRerouteTime = System.currentTimeMillis()
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Updating directions from current location...") }
            try {
                val newRoute = repository.getRoute(point.latitude, point.longitude, dest.latitude, dest.longitude)
                if (newRoute != null) {
                    val enriched = newRoute.copy(destinationName = dest.title)
                    val firstStep = enriched.steps.firstOrNull()
                    val secondStep = if (enriched.steps.size > 1) enriched.steps[1] else null
                    val etaMs = System.currentTimeMillis() + (enriched.durationSeconds * 1000).toLong()
                    val etaFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(etaMs))

                    _uiState.update {
                        it.copy(
                            currentRoute = enriched,
                            currentStepIndex = 0,
                            currentStep = firstStep,
                            nextStep = secondStep,
                            distanceToNextManeuverMeters = firstStep?.distanceMeters,
                            remainingDistanceMeters = enriched.totalDistanceMeters,
                            remainingDurationSeconds = enriched.durationSeconds,
                            etaFormatted = etaFormatted,
                            statusMessage = "Updated navigation path"
                        )
                    }
                }
            } catch (e: Exception) {
                // Graceful fallback
            } finally {
                isRerouting = false
            }
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

        // Automatically detect if user pasted a Google Maps link or decimal coordinates into the search bar
        if (GoogleMapsLinkResolver.isMapLinkOrCoordinates(query)) {
            handleSharedMapInput(query)
            return
        }

        if (query.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                delay(280L) // Responsive debounce
                val userLoc = _uiState.value.currentLocation
                val filterNearMe = _uiState.value.isNearMeFilterEnabled
                val userLat = if (filterNearMe) userLoc?.latitude else null
                val userLon = if (filterNearMe) userLoc?.longitude else null
                val countryCode = if (filterNearMe) _uiState.value.currentCountryCode else null

                val results = repository.searchPlaces(
                    query = query,
                    userLat = userLat,
                    userLon = userLon,
                    countryCode = countryCode
                )
                _uiState.update { it.copy(searchResults = results) }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun toggleNearMeFilter() {
        val newEnabled = !_uiState.value.isNearMeFilterEnabled
        _uiState.update { it.copy(isNearMeFilterEnabled = newEnabled) }
        val currentQuery = _uiState.value.destinationText
        if (currentQuery.length >= 2) {
            onSearchQueryChanged(currentQuery)
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

    fun updateSavedLocation(id: Long, title: String, subtitle: String, lat: Double, lon: Double, category: String) {
        viewModelScope.launch {
            savedLocationRepository.updateLocation(id, title, subtitle, lat, lon, category)
            _uiState.update {
                it.copy(statusMessage = "Updated '$title'")
            }
        }
    }

    fun setShowSavedLocationsSheet(show: Boolean) {
        _uiState.update { it.copy(showSavedLocationsSheet = show) }
    }

    /**
     * Called when the user touches and holds (long-presses) any point on the map.
     * Drops a pin, reverse-geocodes the coordinates in background, and displays the options sheet.
     */
    fun onMapLongPress(lat: Double, lon: Double) {
        val curr = _uiState.value.currentLocation
        val dist = if (curr != null) {
            repository.distanceBetween(curr.latitude, curr.longitude, lat, lon).toInt()
        } else null

        val initialPin = DroppedPinLocation(
            latitude = lat,
            longitude = lon,
            title = "Dropped Pin",
            subtitle = String.format(Locale.US, "%.5f, %.5f", lat, lon),
            distanceMeters = dist,
            isResolvingAddress = true
        )

        _uiState.update {
            it.copy(
                droppedPin = initialPin,
                showDroppedPinSheet = true
            )
        }

        reverseGeocodePinJob?.cancel()
        reverseGeocodePinJob = viewModelScope.launch {
            val address = repository.reverseGeocode(lat, lon)
            val title = address?.road
                ?: address?.neighbourhood
                ?: address?.suburb
                ?: address?.city
                ?: address?.town
                ?: address?.village
                ?: "Dropped Pin"

            val subtitleParts = mutableListOf<String>()
            address?.road?.let { if (it != title) subtitleParts.add(it) }
            address?.neighbourhood?.let { if (it != title && !subtitleParts.contains(it)) subtitleParts.add(it) }
            address?.suburb?.let { if (it != title && !subtitleParts.contains(it)) subtitleParts.add(it) }
            address?.city?.let { if (it != title && !subtitleParts.contains(it)) subtitleParts.add(it) }
            address?.town?.let { if (it != title && !subtitleParts.contains(it)) subtitleParts.add(it) }
            address?.state?.let { if (!subtitleParts.contains(it)) subtitleParts.add(it) }
            address?.country?.let { if (!subtitleParts.contains(it)) subtitleParts.add(it) }

            val subtitle = if (subtitleParts.isNotEmpty()) {
                subtitleParts.joinToString(", ")
            } else {
                String.format(Locale.US, "%.5f, %.5f", lat, lon)
            }

            _uiState.update { state ->
                if (state.droppedPin?.latitude == lat && state.droppedPin?.longitude == lon) {
                    state.copy(
                        droppedPin = state.droppedPin.copy(
                            title = title,
                            subtitle = subtitle,
                            isResolvingAddress = false
                        )
                    )
                } else state
            }
        }
    }

    fun dismissDroppedPinSheet() {
        _uiState.update { it.copy(showDroppedPinSheet = false) }
    }

    fun clearDroppedPin() {
        reverseGeocodePinJob?.cancel()
        _uiState.update { it.copy(droppedPin = null, showDroppedPinSheet = false) }
    }

    fun openDroppedPinSheet() {
        if (_uiState.value.droppedPin != null) {
            _uiState.update { it.copy(showDroppedPinSheet = true) }
        }
    }

    fun requestDirectionsToDroppedPin() {
        val pin = _uiState.value.droppedPin ?: return
        val searchLoc = pin.toSearchLocation()
        _uiState.update { it.copy(showDroppedPinSheet = false) }
        selectDestination(searchLoc)
    }

    fun saveDroppedPinLocation(customTitle: String? = null, category: String = "FAVORITE") {
        val pin = _uiState.value.droppedPin ?: return
        val title = customTitle?.ifBlank { null } ?: pin.title
        viewModelScope.launch {
            savedLocationRepository.saveLocation(
                title = title,
                subtitle = pin.subtitle,
                latitude = pin.latitude,
                longitude = pin.longitude,
                category = category
            )
            _uiState.update {
                it.copy(
                    statusMessage = "Saved '$title' to your locations"
                )
            }
        }
    }

    // Google Maps Shared Links handling
    private var resolveSharedLinkJob: Job? = null

    fun handleSharedMapInput(input: String) {
        if (input.isBlank()) return
        resolveSharedLinkJob?.cancel()

        _uiState.update {
            it.copy(
                isResolvingSharedLink = true,
                showSharedLinkSheet = true,
                sharedLinkError = null,
                sharedMapLink = null
            )
        }

        resolveSharedLinkJob = viewModelScope.launch {
            val userLat = _uiState.value.currentLocation?.latitude
            val userLon = _uiState.value.currentLocation?.longitude

            try {
                val resolved = GoogleMapsLinkResolver.resolve(
                    input = input,
                    userLat = userLat,
                    userLon = userLon,
                    geocodeSearch = { query ->
                        repository.searchPlaces(query, userLat, userLon, null).firstOrNull()
                    },
                    reverseGeocode = { lat, lon ->
                        val addr = repository.reverseGeocode(lat, lon)
                        val parts = listOfNotNull(
                            addr?.road ?: addr?.neighbourhood,
                            addr?.city ?: addr?.town ?: addr?.village ?: addr?.suburb,
                            addr?.state,
                            addr?.country
                        )
                        if (parts.isNotEmpty()) parts.joinToString(", ") else null
                    }
                )

                if (resolved != null) {
                    _uiState.update {
                        it.copy(
                            sharedMapLink = resolved,
                            isResolvingSharedLink = false,
                            sharedLinkError = null,
                            destinationText = resolved.title,
                            // Immediately position a dropped pin for instant visual feedback on the map
                            droppedPin = DroppedPinLocation(
                                latitude = resolved.latitude,
                                longitude = resolved.longitude,
                                title = resolved.title,
                                subtitle = resolved.subtitle,
                                distanceMeters = resolved.distanceMeters,
                                isResolvingAddress = false
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isResolvingSharedLink = false,
                            sharedLinkError = "Could not find coordinates or place in this link."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isResolvingSharedLink = false,
                        sharedLinkError = "Error parsing link: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun dismissSharedLinkSheet() {
        _uiState.update { it.copy(showSharedLinkSheet = false) }
    }

    fun startRouteToResolvedLink(link: ResolvedMapLink) {
        _uiState.update { it.copy(showSharedLinkSheet = false) }
        selectDestination(link.toSearchLocation())
    }

    fun saveResolvedLocation(link: ResolvedMapLink, customName: String? = null, category: String = "FAVORITE") {
        val name = customName?.ifBlank { null } ?: link.title
        viewModelScope.launch {
            savedLocationRepository.saveLocation(
                title = name,
                subtitle = link.subtitle,
                latitude = link.latitude,
                longitude = link.longitude,
                category = category
            )
            _uiState.update {
                it.copy(
                    showSharedLinkSheet = false,
                    statusMessage = "Saved '$name' to your locations"
                )
            }
        }
    }

    fun showResolvedOnMap(link: ResolvedMapLink) {
        _uiState.update {
            it.copy(
                showSharedLinkSheet = false,
                droppedPin = DroppedPinLocation(
                    latitude = link.latitude,
                    longitude = link.longitude,
                    title = link.title,
                    subtitle = link.subtitle,
                    distanceMeters = link.distanceMeters,
                    isResolvingAddress = false
                ),
                showDroppedPinSheet = true
            )
        }
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
     * 1. Obtains the most accurate current location first.
     * 2. Re-queries directions from the current location to destination.
     * 3. Sets the updated navigation path and begins active turn-by-turn guidance.
     */
    fun startNavigation() {
        val dest = _uiState.value.selectedDestination ?: _uiState.value.currentRoute?.let { route ->
            val last = route.waypoints.lastOrNull()
            if (last != null) {
                SearchLocation(
                    title = route.destinationName ?: "Destination",
                    subtitle = "",
                    latitude = last.first,
                    longitude = last.second
                )
            } else null
        } ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isNavigating = true,
                    isRoutePreviewShowing = false,
                    isFollowMode = true,
                    isRouteLoading = true,
                    recenterTrigger = System.currentTimeMillis(),
                    statusMessage = "Locating GPS & calculating fresh route..."
                )
            }

            // 1. Get current location first
            val freshLocation = locationManager.getCurrentLocation() ?: _uiState.value.currentLocation
            if (freshLocation != null) {
                _uiState.update { it.copy(currentLocation = freshLocation) }
            }

            val startLat = freshLocation?.latitude ?: _uiState.value.currentLocation?.latitude ?: 51.5074
            val startLon = freshLocation?.longitude ?: _uiState.value.currentLocation?.longitude ?: -0.1278

            // 2. Again get directions to destination from this current location and use updated navigation path
            try {
                val freshRoute = repository.getRoute(startLat, startLon, dest.latitude, dest.longitude)
                if (freshRoute != null) {
                    val enriched = freshRoute.copy(destinationName = dest.title)
                    val firstStep = enriched.steps.firstOrNull()
                    val secondStep = if (enriched.steps.size > 1) enriched.steps[1] else null
                    val etaMs = System.currentTimeMillis() + (enriched.durationSeconds * 1000).toLong()
                    val etaFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(etaMs))

                    _uiState.update {
                        it.copy(
                            currentRoute = enriched,
                            currentStepIndex = 0,
                            currentStep = firstStep,
                            nextStep = secondStep,
                            distanceToNextManeuverMeters = firstStep?.distanceMeters,
                            remainingDistanceMeters = enriched.totalDistanceMeters,
                            remainingDurationSeconds = enriched.durationSeconds,
                            etaFormatted = etaFormatted,
                            isRouteLoading = false,
                            statusMessage = "Navigating to ${dest.title}"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isRouteLoading = false,
                            statusMessage = "Navigation started. Following route."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRouteLoading = false,
                        statusMessage = "Navigation active."
                    )
                }
            }
        }
    }

    /**
     * Stop active navigation and return to normal map overview
     */
    fun exitNavigation() {
        _uiState.update {
            it.copy(
                isNavigating = false,
                isRoutePreviewShowing = false,
                statusMessage = "Navigation ended"
            )
        }
    }

    fun clearRoute() {
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

    /**
     * Google Maps-style My Location action:
     * Enables follow mode, triggers smooth camera gliding animation to current location,
     * and refreshes GPS location instantly.
     */
    fun onMyLocationClicked() {
        _uiState.update {
            it.copy(
                isFollowMode = true,
                recenterTrigger = System.currentTimeMillis(),
                statusMessage = "Centering on current location"
            )
        }
        viewModelScope.launch {
            val freshLoc = locationManager.getCurrentLocation()
            if (freshLoc != null) {
                _uiState.update {
                    it.copy(
                        currentLocation = freshLoc,
                        recenterTrigger = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    /**
     * Called when user touches and drags/pans the map.
     * Pauses follow mode so user can freely explore, like in Google Maps.
     */
    fun onMapPanned() {
        if (_uiState.value.isFollowMode) {
            _uiState.update { it.copy(isFollowMode = false) }
        }
    }

    fun toggleFollowMode() {
        onMyLocationClicked()
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
        alertSoundManager.release()
    }
}
