package com.speedcam.nav.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.speedcam.nav.ui.components.CameraAlertCard
import com.speedcam.nav.ui.components.MapPointOptionsSheet
import com.speedcam.nav.ui.components.MapViewContainer
import com.speedcam.nav.ui.components.NavigationHudControls
import com.speedcam.nav.ui.components.RoutePreviewCard
import com.speedcam.nav.ui.components.SavedLocationsSheet
import com.speedcam.nav.ui.components.SearchRouteBar
import com.speedcam.nav.ui.components.SharedMapLinkSheet
import com.speedcam.nav.ui.components.SpeedometerHUD
import com.speedcam.nav.ui.components.TurnByTurnHeader

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    incomingSharedLink: String? = null,
    onSharedLinkConsumed: () -> Unit = {},
    viewModel: NavigationViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(incomingSharedLink) {
        if (!incomingSharedLink.isNullOrBlank()) {
            viewModel.handleSharedMapInput(incomingSharedLink)
            onSharedLinkConsumed()
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fineGranted || coarseGranted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    var showSpeedLimitPicker by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("home_screen")
    ) {
        // 1. Edge-to-Edge Fullscreen Map Canvas with Dynamic Google Maps Navigation Zoom
        MapViewContainer(
            currentLocation = uiState.currentLocation,
            nearbyCameras = uiState.nearbyCameras,
            currentRoute = uiState.currentRoute,
            droppedPin = uiState.droppedPin,
            isNavigating = uiState.isNavigating,
            isApproachingTurnOrExit = uiState.isApproachingTurnOrExit,
            distanceToNextManeuverMeters = uiState.distanceToNextManeuverMeters,
            isFollowMode = uiState.isFollowMode,
            recenterTrigger = uiState.recenterTrigger,
            isDarkMapTheme = uiState.isDarkMapTheme,
            onMapTouched = {
                viewModel.onMapPanned()
            },
            onMapLongPress = { lat, lon ->
                viewModel.onMapLongPress(lat, lon)
            },
            onDroppedPinClick = {
                viewModel.openDroppedPinSheet()
            }
        )

        // 2. Top Header Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Google Maps-style Turn-By-Turn HUD Card during active navigation
            TurnByTurnHeader(
                isNavigating = uiState.isNavigating,
                currentStep = uiState.currentStep,
                nextStep = uiState.nextStep,
                distanceToManeuverMeters = uiState.distanceToNextManeuverMeters,
                isApproachingTurnOrExit = uiState.isApproachingTurnOrExit,
                onExitNavigation = { viewModel.exitNavigation() },
                onRecalculateRoute = { viewModel.recalculateRoute() },
                isUpdatingRoute = uiState.isRouteLoading
            )

            // Search Bar & Saved Places when browsing or route previewing
            if (!uiState.isNavigating) {
                SearchRouteBar(
                    originText = uiState.originText,
                    destinationText = uiState.destinationText,
                    onDestinationChange = { viewModel.onSearchQueryChanged(it) },
                    searchResults = uiState.searchResults,
                    onSelectDestination = { viewModel.selectDestination(it) },
                    savedLocations = uiState.savedLocations,
                    onOpenSavedLocations = { viewModel.setShowSavedLocationsSheet(true) },
                    currentCity = uiState.currentCity,
                    currentCountry = uiState.currentCountry,
                    isNearMeFilterEnabled = uiState.isNearMeFilterEnabled,
                    onToggleNearMeFilter = { viewModel.toggleNearMeFilter() },
                    currentRoute = uiState.currentRoute,
                    isNavigating = uiState.isNavigating,
                    isLoadingRoute = uiState.isRouteLoading,
                    onClearRoute = { viewModel.clearRoute() }
                )
            }

            // Speed Camera Warning HUD Banner (<500m)
            CameraAlertCard(
                camera = uiState.activeCameraAlert,
                onDismiss = { viewModel.dismissCameraAlert() }
            )

            // Permission Request Banner (if GPS not granted)
            if (!hasLocationPermission) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xF21E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location access needed for live GPS",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grant permission for turn-by-turn navigation.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Enable", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Floating HUD Controls (Right-aligned)
        NavigationHudControls(
            isFollowMode = uiState.isFollowMode,
            onToggleFollowMode = { viewModel.onMyLocationClicked() },
            isDarkMapTheme = uiState.isDarkMapTheme,
            onToggleDarkMapTheme = { viewModel.toggleDarkMapTheme() },
            isMuted = uiState.isMuted,
            onToggleMute = { viewModel.toggleMute() },
            onCycleSpeedLimit = { showSpeedLimitPicker = true },
            onOpenSavedLocations = { viewModel.setShowSavedLocationsSheet(true) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        )

        // 4. Bottom Area: Speedometer HUD and Navigation Status / Route Preview Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
        ) {
            // Route Preview Card with "Start Navigation" button
            if (uiState.isRoutePreviewShowing && !uiState.isNavigating) {
                RoutePreviewCard(
                    isVisible = uiState.isRoutePreviewShowing,
                    route = uiState.currentRoute,
                    destination = uiState.selectedDestination,
                    isDestinationSaved = uiState.isDestinationSaved,
                    onStartNavigation = { viewModel.startNavigation() },
                    onToggleSaveLocation = { viewModel.saveCurrentDestination() },
                    onDismiss = { viewModel.clearRoute() }
                )
            }

            // Bottom Navigation Dashboard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Speedometer HUD
                SpeedometerHUD(
                    currentSpeedKmh = uiState.currentSpeedKmh,
                    speedLimitKmh = uiState.speedLimitKmh,
                    isOverspeed = uiState.isOverspeed,
                    overspeedDelta = uiState.overspeedDelta,
                    onSpeedLimitClick = { showSpeedLimitPicker = true }
                )

                // Active Navigation Trip Stats Dock (Google Maps style ETA / Distance / Exit)
                if (uiState.isNavigating) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xEE111827),
                        modifier = Modifier
                            .shadow(12.dp, RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0x2BFFFFFF), RoundedCornerShape(20.dp))
                            .testTag("active_navigation_trip_dock")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val remainingMin = (uiState.remainingDurationSeconds / 60.0).toInt().coerceAtLeast(1)
                                Text(
                                    text = "$remainingMin min",
                                    color = Color(0xFF00C853),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black
                                )
                                val remainingKm = uiState.remainingDistanceMeters / 1000.0
                                Text(
                                    text = "${String.format("%.1f", remainingKm)} km · ${uiState.etaFormatted}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Red Exit Navigation Button
                            IconButton(
                                onClick = { viewModel.exitNavigation() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF334B).copy(alpha = 0.2f))
                                    .testTag("stop_nav_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Exit Navigation",
                                    tint = Color(0xFFFF334B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Saved Locations Bottom Sheet
        SavedLocationsSheet(
            isOpen = uiState.showSavedLocationsSheet,
            savedLocations = uiState.savedLocations,
            onSelectLocation = { location ->
                viewModel.selectDestination(location)
            },
            onDeleteLocation = { id ->
                viewModel.deleteSavedLocation(id)
            },
            onAddCurrentLocation = { name, category ->
                val curr = uiState.currentLocation
                val lat = curr?.latitude ?: 51.5074
                val lon = curr?.longitude ?: -0.1278
                viewModel.saveLocation(
                    title = name,
                    subtitle = "Saved Coordinate (${String.format("%.3f", lat)}, ${String.format("%.3f", lon)})",
                    lat = lat,
                    lon = lon,
                    category = category
                )
            },
            onUpdateLocation = { id, title, subtitle, lat, lon, category ->
                viewModel.updateSavedLocation(id, title, subtitle, lat, lon, category)
            },
            onDismiss = { viewModel.setShowSavedLocationsSheet(false) }
        )
    }

    // Touch & Hold Dropped Pin Options Bottom Sheet (Directions, Save, Copy Lat/Lon, Share)
    MapPointOptionsSheet(
        isOpen = uiState.showDroppedPinSheet,
        droppedPin = uiState.droppedPin,
        onDismiss = { viewModel.dismissDroppedPinSheet() },
        onDirections = { viewModel.requestDirectionsToDroppedPin() },
        onSaveLocation = { name, category ->
            viewModel.saveDroppedPinLocation(name, category)
        }
    )

    // Google Maps Shared Link Bottom Sheet (Navigate or Save)
    SharedMapLinkSheet(
        isOpen = uiState.showSharedLinkSheet,
        resolvedLink = uiState.sharedMapLink,
        isLoading = uiState.isResolvingSharedLink,
        errorMessage = uiState.sharedLinkError,
        onDismiss = { viewModel.dismissSharedLinkSheet() },
        onNavigate = { link -> viewModel.startRouteToResolvedLink(link) },
        onSaveLocation = { link, name, category ->
            viewModel.saveResolvedLocation(link, name, category)
        },
        onShowOnMap = { link ->
            viewModel.showResolvedOnMap(link)
        }
    )

    // Speed Limit Quick Tester / Selector Dialog
    if (showSpeedLimitPicker) {
        Dialog(onDismissRequest = { showSpeedLimitPicker = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Road Speed Limit Test",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select a regional speed limit to test HUD badges and overspeed alerts:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val limits = listOf(30, 50, 60, 80, 100, 120, 130)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        limits.chunked(3).forEach { rowLimits ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowLimits.forEach { limit ->
                                    Button(
                                        onClick = {
                                            viewModel.setSpeedLimit(limit)
                                            showSpeedLimitPicker = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (uiState.speedLimitKmh == limit) Color(0xFF00E5FF) else Color(0xFF334155)
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.width(80.dp)
                                    ) {
                                        Text(
                                            text = "$limit",
                                            fontWeight = FontWeight.Bold,
                                            color = if (uiState.speedLimitKmh == limit) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.setSpeedLimit(null)
                                showSpeedLimitPicker = false
                            }
                        ) {
                            Text("Unmapped (--)", color = Color(0xFF94A3B8))
                        }

                        TextButton(
                            onClick = { showSpeedLimitPicker = false }
                        ) {
                            Text("Done", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
