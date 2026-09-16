package com.speedcam.nav.data.remote

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
        @Query("limit") limit: Int = 6,
        @Query("addressdetails") addressDetails: Int = 1
    ): List<NominatimSearchResult>
}
