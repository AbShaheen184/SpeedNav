package com.speedcam.nav.data.model

import com.google.gson.annotations.SerializedName

data class PhotonResponse(
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("features")
    val features: List<PhotonFeature> = emptyList()
)

data class PhotonFeature(
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("geometry")
    val geometry: PhotonGeometry? = null,
    @SerializedName("properties")
    val properties: PhotonProperties? = null
)

data class PhotonGeometry(
    @SerializedName("type")
    val type: String? = null,
    // [lon, lat]
    @SerializedName("coordinates")
    val coordinates: List<Double> = emptyList()
)

data class PhotonProperties(
    @SerializedName("osm_id")
    val osmId: Long? = null,
    @SerializedName("osm_type")
    val osmType: String? = null,
    @SerializedName("osm_key")
    val osmKey: String? = null,
    @SerializedName("osm_value")
    val osmValue: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("street")
    val street: String? = null,
    @SerializedName("housenumber")
    val housenumber: String? = null,
    @SerializedName("postcode")
    val postcode: String? = null,
    @SerializedName("city")
    val city: String? = null,
    @SerializedName("district")
    val district: String? = null,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("country")
    val country: String? = null,
    @SerializedName("countrycode")
    val countryCode: String? = null
)
