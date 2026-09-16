package com.speedcam.nav.data.model

import com.google.gson.annotations.SerializedName

data class OverpassResponse(
    @SerializedName("elements")
    val elements: List<OverpassElement> = emptyList()
)

data class OverpassElement(
    @SerializedName("type")
    val type: String = "",
    @SerializedName("id")
    val id: Long = 0L,
    @SerializedName("lat")
    val lat: Double? = null,
    @SerializedName("lon")
    val lon: Double? = null,
    @SerializedName("tags")
    val tags: Map<String, String>? = null
)
