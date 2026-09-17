package com.speedcam.nav.data.remote

import com.speedcam.nav.data.model.ResolvedMapLink
import com.speedcam.nav.data.model.SearchLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Pattern

object GoogleMapsLinkResolver {

    // Regex patterns for detecting Google Maps URLs and geo schemes
    private val URL_REGEX = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE)
    private val GMAPS_HOST_REGEX = Pattern.compile(
        "^(https?://)?([a-zA-Z0-9.-]+\\.)?(maps\\.google\\.[a-z.]+|goo\\.gl/maps|maps\\.app\\.goo\\.gl|google\\.[a-z.]+/maps)",
        Pattern.CASE_INSENSITIVE
    )
    private val RAW_COORDINATES_REGEX = Pattern.compile(
        "^\\s*(-?\\d{1,2}\\.\\d+)[,\\s]+(-?\\d{1,3}\\.\\d+)\\s*$"
    )

    /**
     * Checks if the given text contains a Google Maps link, geo URI, or raw coordinates.
     */
    fun isMapLinkOrCoordinates(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.startsWith("geo:", ignoreCase = true)) return true
        if (RAW_COORDINATES_REGEX.matcher(trimmed).matches()) return true

        val matcher = URL_REGEX.matcher(trimmed)
        while (matcher.find()) {
            val url = matcher.group()
            if (GMAPS_HOST_REGEX.matcher(url).find() || url.contains("google.com/maps") || url.contains("maps.app.goo.gl")) {
                return true
            }
        }
        return false
    }

    /**
     * Resolves an incoming text/URL/intent into a clean ResolvedMapLink with exact coordinates,
     * address, and title.
     */
    suspend fun resolve(
        input: String,
        userLat: Double? = null,
        userLon: Double? = null,
        geocodeSearch: suspend (query: String) -> SearchLocation? = { null },
        reverseGeocode: suspend (lat: Double, lon: Double) -> String? = { _, _ -> null }
    ): ResolvedMapLink? = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return@withContext null

        // 1. Check for raw coordinates: e.g. "51.5074, -0.1278"
        val rawCoordsMatcher = RAW_COORDINATES_REGEX.matcher(trimmed)
        if (rawCoordsMatcher.matches()) {
            val lat = rawCoordsMatcher.group(1)?.toDoubleOrNull()
            val lon = rawCoordsMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return@withContext buildResolvedLink(
                    original = trimmed,
                    lat = lat,
                    lon = lon,
                    extractedTitle = "Pinned Coordinates",
                    userLat = userLat,
                    userLon = userLon,
                    reverseGeocode = reverseGeocode
                )
            }
        }

        // 2. Check for geo: URI: e.g. "geo:48.8584,2.2923?q=Eiffel+Tower" or "geo:0,0?q=48.8584,2.2923(Label)"
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            val resolved = parseGeoUri(trimmed, userLat, userLon, geocodeSearch, reverseGeocode)
            if (resolved != null) return@withContext resolved
        }

        // 3. Extract URL from text (e.g. if text is "Check this place: https://maps.app.goo.gl/...")
        val urlMatcher = URL_REGEX.matcher(trimmed)
        val extractedUrl = if (urlMatcher.find()) urlMatcher.group() else null

        val targetUrl = extractedUrl ?: if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else null
        if (targetUrl == null) {
            // Not a URL or geo URI, try fallback geocoding if it looks like an address/place
            return@withContext null
        }

        // 4. If it's a short URL (maps.app.goo.gl or goo.gl/maps), follow redirects to find canonical URL
        val canonicalUrl = expandShortUrlIfNeeded(targetUrl)

        // 5. Parse coordinates and place details from canonical URL
        parseGoogleMapsUrl(
            originalInput = trimmed,
            url = canonicalUrl,
            userLat = userLat,
            userLon = userLon,
            geocodeSearch = geocodeSearch,
            reverseGeocode = reverseGeocode
        )
    }

    /**
     * Follows redirects for short URLs like maps.app.goo.gl
     */
    private fun expandShortUrlIfNeeded(url: String): String {
        if (!url.contains("maps.app.goo.gl") && !url.contains("goo.gl/maps")) {
            return url
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                .build()

            NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl.isNotBlank() && finalUrl != url) {
                    finalUrl
                } else {
                    response.header("Location") ?: url
                }
            }
        } catch (e: Exception) {
            url
        }
    }

    /**
     * Parses expanded Google Maps URL into coordinates and title
     */
    private suspend fun parseGoogleMapsUrl(
        originalInput: String,
        url: String,
        userLat: Double?,
        userLon: Double?,
        geocodeSearch: suspend (query: String) -> SearchLocation?,
        reverseGeocode: suspend (lat: Double, lon: Double) -> String?
    ): ResolvedMapLink? {
        var lat: Double? = null
        var lon: Double? = null
        var title: String? = null

        // Pattern A: /@lat,lon,zoom (e.g. /@48.8583701,2.2922926,17z)
        val atPattern = Pattern.compile("/@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        val atMatcher = atPattern.matcher(url)
        if (atMatcher.find()) {
            lat = atMatcher.group(1)?.toDoubleOrNull()
            lon = atMatcher.group(2)?.toDoubleOrNull()
        }

        // Pattern B: Query parameters: ?q=lat,lon or ?query=lat,lon or ?daddr=lat,lon or ?ll=lat,lon
        val queryCoordsPattern = Pattern.compile("[?&](?:q|query|daddr|ll|destination)=(-?\\d+\\.\\d+)(?:,|%2C)(-?\\d+\\.\\d+)")
        val queryCoordsMatcher = queryCoordsPattern.matcher(url)
        if (queryCoordsMatcher.find()) {
            // Prioritize specific query coordinates if present
            lat = queryCoordsMatcher.group(1)?.toDoubleOrNull() ?: lat
            lon = queryCoordsMatcher.group(2)?.toDoubleOrNull() ?: lon
        }

        // Extract title from place path: /place/Place+Name/
        val placePathPattern = Pattern.compile("/place/([^/@?#]+)")
        val placePathMatcher = placePathPattern.matcher(url)
        if (placePathMatcher.find()) {
            val rawName = placePathMatcher.group(1)
            if (!rawName.isNullOrBlank()) {
                title = decodeUrlParam(rawName).replace('+', ' ')
            }
        }

        // If no title from place path, check ?q= query param
        if (title.isNullOrBlank()) {
            val qParamPattern = Pattern.compile("[?&]q=([^&#]+)")
            val qMatcher = qParamPattern.matcher(url)
            if (qMatcher.find()) {
                val qVal = qMatcher.group(1)
                if (!qVal.isNullOrBlank() && !qVal.matches(Regex("^-?\\d+\\.\\d+.*"))) {
                    title = decodeUrlParam(qVal).replace('+', ' ')
                }
            }
        }

        // If coordinates found directly
        if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
            return buildResolvedLink(
                original = originalInput,
                canonicalUrl = url,
                lat = lat,
                lon = lon,
                extractedTitle = title,
                userLat = userLat,
                userLon = userLon,
                reverseGeocode = reverseGeocode
            )
        }

        // If coordinates not in URL, but title / place name is present, forward-geocode it!
        if (!title.isNullOrBlank()) {
            val searchResult = geocodeSearch(title)
            if (searchResult != null) {
                val dist = if (userLat != null && userLon != null) {
                    calculateDistanceMeters(userLat, userLon, searchResult.latitude, searchResult.longitude)
                } else {
                    searchResult.distanceMeters
                }
                return ResolvedMapLink(
                    originalInput = originalInput,
                    canonicalUrl = url,
                    latitude = searchResult.latitude,
                    longitude = searchResult.longitude,
                    title = searchResult.title,
                    subtitle = searchResult.subtitle,
                    distanceMeters = dist
                )
            }
        }

        return null
    }

    /**
     * Parses geo: URI scheme
     */
    private suspend fun parseGeoUri(
        geoUri: String,
        userLat: Double?,
        userLon: Double?,
        geocodeSearch: suspend (query: String) -> SearchLocation?,
        reverseGeocode: suspend (lat: Double, lon: Double) -> String?
    ): ResolvedMapLink? {
        val ssp = geoUri.substringAfter("geo:", "").trim()
        if (ssp.isEmpty()) return null

        // Format 1: geo:lat,lon or geo:lat,lon?q=...
        val parts = ssp.split("?", limit = 2)
        val coordPart = parts[0]
        val queryPart = if (parts.size > 1) parts[1] else null

        var lat: Double? = null
        var lon: Double? = null
        var label: String? = null

        val coordSplit = coordPart.split(",")
        if (coordSplit.size == 2) {
            lat = coordSplit[0].toDoubleOrNull()
            lon = coordSplit[1].toDoubleOrNull()
        }

        if (queryPart != null) {
            val qIndex = queryPart.indexOf("q=")
            if (qIndex != -1) {
                var qVal = queryPart.substring(qIndex + 2)
                val ampIndex = qVal.indexOf('&')
                if (ampIndex != -1) qVal = qVal.substring(0, ampIndex)

                val labelPattern = Pattern.compile("(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)(?:\\(([^)]+)\\))?")
                val labelMatcher = labelPattern.matcher(qVal)
                if (labelMatcher.find()) {
                    lat = labelMatcher.group(1)?.toDoubleOrNull() ?: lat
                    lon = labelMatcher.group(2)?.toDoubleOrNull() ?: lon
                    label = labelMatcher.group(3)?.let { decodeUrlParam(it) }
                } else if (!qVal.contains(",")) {
                    label = decodeUrlParam(qVal)
                }
            }
        }

        if (lat != null && lon != null && (lat != 0.0 || lon != 0.0) && isValidCoordinate(lat, lon)) {
            return buildResolvedLink(
                original = geoUri,
                canonicalUrl = geoUri,
                lat = lat,
                lon = lon,
                extractedTitle = label,
                userLat = userLat,
                userLon = userLon,
                reverseGeocode = reverseGeocode
            )
        }

        if (!label.isNullOrBlank()) {
            val found = geocodeSearch(label)
            if (found != null) {
                val dist = if (userLat != null && userLon != null) {
                    calculateDistanceMeters(userLat, userLon, found.latitude, found.longitude)
                } else {
                    found.distanceMeters
                }
                return ResolvedMapLink(
                    originalInput = geoUri,
                    canonicalUrl = geoUri,
                    latitude = found.latitude,
                    longitude = found.longitude,
                    title = found.title,
                    subtitle = found.subtitle,
                    distanceMeters = dist
                )
            }
        }

        return null
    }

    private suspend fun buildResolvedLink(
        original: String,
        canonicalUrl: String? = null,
        lat: Double,
        lon: Double,
        extractedTitle: String?,
        userLat: Double?,
        userLon: Double?,
        reverseGeocode: suspend (lat: Double, lon: Double) -> String?
    ): ResolvedMapLink {
        val distMeters = if (userLat != null && userLon != null) {
            calculateDistanceMeters(userLat, userLon, lat, lon)
        } else {
            null
        }

        val address = try {
            reverseGeocode(lat, lon)
        } catch (e: Exception) {
            null
        }

        val finalTitle = when {
            !extractedTitle.isNullOrBlank() -> extractedTitle
            !address.isNullOrBlank() -> address.split(",").firstOrNull()?.trim() ?: "Shared Location"
            else -> "Shared Location"
        }

        val finalSubtitle = when {
            !address.isNullOrBlank() -> address
            else -> String.format(Locale.US, "%.5f, %.5f", lat, lon)
        }

        return ResolvedMapLink(
            originalInput = original,
            canonicalUrl = canonicalUrl,
            latitude = lat,
            longitude = lon,
            title = finalTitle,
            subtitle = finalSubtitle,
            distanceMeters = distMeters
        )
    }

    private fun decodeUrlParam(param: String): String {
        return try {
            URLDecoder.decode(param, "UTF-8")
        } catch (e: Exception) {
            param
        }
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (earthRadius * c).toInt()
    }
}
