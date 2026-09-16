package com.speedcam.nav.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import android.preference.PreferenceManager
import com.speedcam.nav.data.model.CameraType
import com.speedcam.nav.data.model.LocationPoint
import com.speedcam.nav.data.model.NavigationRoute
import com.speedcam.nav.data.model.SpeedCameraNode
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapViewContainer(
    currentLocation: LocationPoint?,
    nearbyCameras: List<SpeedCameraNode>,
    currentRoute: NavigationRoute?,
    isNavigating: Boolean,
    isApproachingTurnOrExit: Boolean,
    distanceToNextManeuverMeters: Double?,
    isFollowMode: Boolean,
    isDarkMapTheme: Boolean,
    onMapTouched: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Initialize osmdroid configuration once
    remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.5)

            // Default initial location: London Trafalgar / or any major road junction
            val defaultGeo = GeoPoint(51.5074, -0.1278)
            controller.setCenter(defaultGeo)

            setOnTouchListener { _, _ ->
                onMapTouched()
                false
            }
        }
    }

    // Apply Dark/Light theme tiles color filter
    LaunchedEffect(isDarkMapTheme) {
        if (isDarkMapTheme) {
            val darkMatrix = ColorMatrix(
                floatArrayOf(
                    -0.85f, 0f, 0f, 0f, 235f,
                    0f, -0.85f, 0f, 0f, 235f,
                    0f, 0f, -0.80f, 0f, 245f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(darkMatrix))
        } else {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
        mapView.invalidate()
    }

    // Dynamic Navigation Zoom & Map Orientation Tracking
    LaunchedEffect(isNavigating, isApproachingTurnOrExit, currentLocation, isFollowMode) {
        if (currentLocation == null) return@LaunchedEffect
        val vehicleGeo = GeoPoint(currentLocation.latitude, currentLocation.longitude)

        if (isNavigating) {
            // Determine target zoom based on turn/exit proximity
            // Normal navigation close area: 18.2
            // Approaching turn/exit: zoom in more (19.8) to clarify the turn/ramp/junction
            val targetZoom = if (isApproachingTurnOrExit) {
                19.8
            } else {
                18.2
            }

            if (mapView.zoomLevelDouble != targetZoom) {
                mapView.controller.setZoom(targetZoom)
            }

            if (isFollowMode) {
                // Heads-up perspective: orient map so user travels forward (towards top of screen)
                if (currentLocation.speedKmh > 3f) {
                    mapView.mapOrientation = -currentLocation.bearing
                }
                mapView.setExpectedCenter(vehicleGeo)
            }
        } else {
            // Browsing / overview mode: North-up orientation
            mapView.mapOrientation = 0f
            if (isFollowMode) {
                mapView.setExpectedCenter(vehicleGeo)
            }
        }
        mapView.invalidate()
    }

    // Update vehicle marker
    LaunchedEffect(currentLocation) {
        if (currentLocation == null) return@LaunchedEffect
        val vehicleGeo = GeoPoint(currentLocation.latitude, currentLocation.longitude)

        // Remove old vehicle markers
        val existingVehicleMarkers = mapView.overlays.filterIsInstance<Marker>()
            .filter { it.id == "VEHICLE_MARKER" }
        mapView.overlays.removeAll(existingVehicleMarkers)

        // Create sleek vehicle marker with directional arrow
        val vehicleMarker = Marker(mapView).apply {
            id = "VEHICLE_MARKER"
            position = vehicleGeo
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            rotation = currentLocation.bearing
            icon = createVehicleIcon(context, currentLocation.speedKmh > 2f)
            title = "Current Position: ${currentLocation.speedKmh.toInt()} km/h"
        }
        mapView.overlays.add(vehicleMarker)
        mapView.invalidate()
    }

    // Update Route polyline
    LaunchedEffect(currentRoute) {
        // Remove old route polylines
        val existingPolylines = mapView.overlays.filterIsInstance<Polyline>()
        mapView.overlays.removeAll(existingPolylines)

        if (currentRoute != null && currentRoute.waypoints.isNotEmpty()) {
            val geoPoints = currentRoute.waypoints.map { GeoPoint(it.first, it.second) }

            // Shadow/Glow outline polyline
            val glowPolyline = Polyline(mapView).apply {
                outlinePaint.color = android.graphics.Color.argb(120, 0, 180, 255)
                outlinePaint.strokeWidth = 22f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.isAntiAlias = true
                setPoints(geoPoints)
            }

            // Main electric cyan-blue route polyline
            val mainPolyline = Polyline(mapView).apply {
                outlinePaint.color = android.graphics.Color.rgb(0, 229, 255)
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.isAntiAlias = true
                setPoints(geoPoints)
            }

            mapView.overlays.add(glowPolyline)
            mapView.overlays.add(mainPolyline)
        }
        mapView.invalidate()
    }

    // Update Speed Camera markers
    LaunchedEffect(nearbyCameras) {
        val existingCameraMarkers = mapView.overlays.filterIsInstance<Marker>()
            .filter { it.id?.startsWith("CAMERA_") == true }
        mapView.overlays.removeAll(existingCameraMarkers)

        for (cam in nearbyCameras) {
            val marker = Marker(mapView).apply {
                id = "CAMERA_${cam.id}"
                position = GeoPoint(cam.latitude, cam.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = createCameraIcon(context, cam.cameraType, cam.speedLimit)
                title = "${cam.cameraType.label} (${cam.distanceMeters}m)"
                snippet = cam.roadName ?: cam.cameraType.description
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .fillMaxSize()
            .testTag("map_view_canvas")
    )
}

/**
 * Creates custom crisp vehicle icon pointing forward with glowing halo
 */
private fun createVehicleIcon(context: Context, isMoving: Boolean): Drawable {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer radar wave circle
    paint.color = android.graphics.Color.argb(70, 0, 229, 255)
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)

    // Inner bright ring
    paint.color = android.graphics.Color.argb(220, 0, 229, 255)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)

    // Directional chevron arrow pointing up (0 degrees)
    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    val path = android.graphics.Path().apply {
        moveTo(size / 2f, size * 0.22f) // Tip
        lineTo(size * 0.72f, size * 0.72f) // Right corner
        lineTo(size / 2f, size * 0.58f) // Inner notch
        lineTo(size * 0.28f, size * 0.72f) // Left corner
        close()
    }
    canvas.drawPath(path, paint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Creates custom pin icon for Speed Cameras
 */
private fun createCameraIcon(context: Context, type: CameraType, speedLimit: Int?): Drawable {
    val width = 84
    val height = 94
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val pinColor = when (type) {
        CameraType.SPEED -> android.graphics.Color.rgb(255, 42, 77)
        CameraType.RED_LIGHT -> android.graphics.Color.rgb(255, 68, 68)
        CameraType.SEATBELT_PHONE -> android.graphics.Color.rgb(255, 149, 0)
        CameraType.AVERAGE_SPEED -> android.graphics.Color.rgb(255, 180, 0)
        CameraType.POLICE_MOBILE -> android.graphics.Color.rgb(255, 42, 77)
    }

    // Outer Pin Circle
    paint.style = Paint.Style.FILL
    paint.color = pinColor
    canvas.drawCircle(width / 2f, 38f, 34f, paint)

    // Bottom pointer triangle
    val pointer = android.graphics.Path().apply {
        moveTo(width / 2f - 16f, 48f)
        lineTo(width / 2f, height - 6f)
        lineTo(width / 2f + 16f, 48f)
        close()
    }
    canvas.drawPath(pointer, paint)

    // Inner White Disc
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(width / 2f, 38f, 26f, paint)

    // Inner Text or Speed Limit
    paint.color = android.graphics.Color.BLACK
    paint.textAlign = Paint.Align.CENTER
    if (speedLimit != null) {
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("$speedLimit", width / 2f, 46f, paint)
    } else {
        paint.textSize = 18f
        paint.isFakeBoldText = true
        val label = when (type) {
            CameraType.RED_LIGHT -> "RL"
            CameraType.SEATBELT_PHONE -> "AI"
            else -> "CAM"
        }
        canvas.drawText(label, width / 2f, 45f, paint)
    }

    return BitmapDrawable(context.resources, bitmap)
}
