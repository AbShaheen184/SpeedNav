package com.speedcam.nav.data.remote

import com.speedcam.nav.data.model.NominatimReverseResult
import com.speedcam.nav.data.model.NominatimSearchResult
import com.speedcam.nav.data.model.OsrmRouteResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RoutingApiService {
    @GET("route/v1/driving/{coordinates}")
    suspend fun getRoute(
        @Path(value = "coordinates", encoded = true) coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: String = "true"
    ): OsrmRouteResponse
}

interface GeocodingApiService {
    @GET("search")
    suspend fun searchLocations(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 8,
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("viewbox") viewBox: String? = null,
        @Query("bounded") bounded: Int? = null,
        @Query("countrycodes") countryCodes: String? = null
    ): List<NominatimSearchResult>

    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1
    ): NominatimReverseResult
}

