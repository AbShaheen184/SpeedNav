package com.speedcam.nav.data.model

import com.google.gson.annotations.SerializedName

data class OsrmRouteResponse(
    @SerializedName("code")
    val code: String? = null,
    @SerializedName("routes")
    val routes: List<OsrmRoute>? = null
)

data class OsrmRoute(
    @SerializedName("distance")
    val distance: Double = 0.0,
    @SerializedName("duration")
    val duration: Double = 0.0,
    @SerializedName("geometry")
    val geometry: OsrmGeometry? = null,
    @SerializedName("legs")
    val legs: List<OsrmLeg>? = null
)

data class OsrmGeometry(
    @SerializedName("coordinates")
    val coordinates: List<List<Double>> = emptyList(),
    @SerializedName("type")
    val type: String = ""
)

data class OsrmLeg(
    @SerializedName("distance")
    val distance: Double = 0.0,
    @SerializedName("duration")
    val duration: Double = 0.0,
    @SerializedName("summary")
    val summary: String = "",
    @SerializedName("steps")
    val steps: List<OsrmStep>? = null
)

data class OsrmStep(
    @SerializedName("distance")
    val distance: Double = 0.0,
    @SerializedName("duration")
    val duration: Double = 0.0,
    @SerializedName("name")
    val name: String = "",
    @SerializedName("maneuver")
    val maneuver: OsrmManeuver? = null
)

data class OsrmManeuver(
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("modifier")
    val modifier: String? = null,
    @SerializedName("location")
    val location: List<Double>? = null
)

data class NominatimSearchResult(
    @SerializedName("place_id")
    val placeId: Long = 0L,
    @SerializedName("display_name")
    val displayName: String = "",
    @SerializedName("lat")
    val lat: String = "0",
    @SerializedName("lon")
    val lon: String = "0",
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("address")
    val address: NominatimAddress? = null
)

data class NominatimReverseResult(
    @SerializedName("place_id")
    val placeId: Long = 0L,
    @SerializedName("display_name")
    val displayName: String = "",
    @SerializedName("lat")
    val lat: String = "0",
    @SerializedName("lon")
    val lon: String = "0",
    @SerializedName("address")
    val address: NominatimAddress? = null
)

data class NominatimAddress(
    @SerializedName("road")
    val road: String? = null,
    @SerializedName("neighbourhood")
    val neighbourhood: String? = null,
    @SerializedName("suburb")
    val suburb: String? = null,
    @SerializedName("city")
    val city: String? = null,
    @SerializedName("town")
    val town: String? = null,
    @SerializedName("village")
    val village: String? = null,
    @SerializedName("municipality")
    val municipality: String? = null,
    @SerializedName("county")
    val county: String? = null,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("country")
    val country: String? = null,
    @SerializedName("country_code")
    val countryCode: String? = null
)

data class SearchLocation(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int? = null,
    val category: String? = null
)

data class DroppedPinLocation(
    val latitude: Double,
    val longitude: Double,
    val title: String = "Dropped Pin",
    val subtitle: String = "",
    val distanceMeters: Int? = null,
    val isResolvingAddress: Boolean = true
) {
    fun toSearchLocation(): SearchLocation {
        return SearchLocation(
            title = title,
            subtitle = subtitle.ifBlank { String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude) },
            latitude = latitude,
            longitude = longitude,
            distanceMeters = distanceMeters
        )
    }

    fun formattedCoordinates(): String {
        return String.format(java.util.Locale.US, "%.6f, %.6f", latitude, longitude)
    }
}

enum class ManeuverType(val label: String) {
    DEPART("Head out"),
    STRAIGHT("Continue straight"),
    TURN_LEFT("Turn left"),
    TURN_RIGHT("Turn right"),
    SLIGHT_LEFT("Keep left"),
    SLIGHT_RIGHT("Keep right"),
    SHARP_LEFT("Sharp left"),
    SHARP_RIGHT("Sharp right"),
    EXIT("Take exit"),
    ROUNDABOUT("At roundabout"),
    U_TURN("Make a U-turn"),
    ARRIVE("Arrive at destination")
}

data class NavigationStep(
    val maneuverType: ManeuverType,
    val instruction: String,
    val streetName: String,
    val distanceMeters: Double,
    val latitude: Double,
    val longitude: Double
)

data class NavigationRoute(
    val waypoints: List<Pair<Double, Double>>, // Lat, Lon
    val steps: List<NavigationStep> = emptyList(),
    val totalDistanceMeters: Double,
    val durationSeconds: Double,
    val summary: String = "",
    val destinationName: String = ""
)
