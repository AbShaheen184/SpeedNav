package com.speedcam.nav.data.remote

import com.speedcam.nav.data.model.OverpassResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OverpassApiService {
    @FormUrlEncoded
    @POST("interpreter")
    suspend fun queryOverpass(
        @Field("data") query: String
    ): OverpassResponse

    @GET("interpreter")
    suspend fun queryOverpassGet(
        @Query("data") query: String
    ): OverpassResponse
}
