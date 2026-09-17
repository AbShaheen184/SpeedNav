package com.speedcam.nav.data.remote

import com.speedcam.nav.data.model.PhotonResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface PhotonApiService {
    @GET("api")
    suspend fun search(
        @Query("q") query: String,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("limit") limit: Int = 10,
        @Query("lang") lang: String = "en"
    ): PhotonResponse
}
