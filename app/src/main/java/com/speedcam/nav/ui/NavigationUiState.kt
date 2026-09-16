package com.speedcam.nav.ui

import com.speedcam.nav.data.local.SavedLocationEntity
import com.speedcam.nav.data.model.LocationPoint
import com.speedcam.nav.data.model.NavigationRoute
import com.speedcam.nav.data.model.NavigationStep
import com.speedcam.nav.data.model.SearchLocation
import com.speedcam.nav.data.model.SpeedCameraNode

data class NavigationUiState(
    val currentLocation: LocationPoint? = null,
    val currentSpeedKmh: Int = 0,
    val speedLimitKmh: Int? = 80, // Dynamic, null if unmapped
    val isOverspeed: Boolean = false,
    val overspeedDelta: Int = 0,
    val nearbyCameras: List<SpeedCameraNode> = emptyList(),
    val activeCameraAlert: SpeedCameraNode? = null,
    val currentRoute: NavigationRoute? = null,
    val isNavigating: Boolean = false,
    val isFollowMode: Boolean = true,
    val isDarkMapTheme: Boolean = true,
    val isSimulating: Boolean = false,
    val originText: String = "Current Location",
    val destinationText: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<SearchLocation> = emptyList(),
    val isMuted: Boolean = false,
    val isRouteLoading: Boolean = false,
    val statusMessage: String? = null,
    // Saved locations
    val savedLocations: List<SavedLocationEntity> = emptyList(),
    val showSavedLocationsSheet: Boolean = false,
    val isDestinationSaved: Boolean = false,
    // Navigation & Turn-by-Turn state
    val selectedDestination: SearchLocation? = null,
    val isRoutePreviewShowing: Boolean = false,
    val currentStepIndex: Int = 0,
    val currentStep: NavigationStep? = null,
    val nextStep: NavigationStep? = null,
    val distanceToNextManeuverMeters: Double? = null,
    val isApproachingTurnOrExit: Boolean = false,
    val remainingDistanceMeters: Double = 0.0,
    val remainingDurationSeconds: Double = 0.0,
    val etaFormatted: String = ""
)
