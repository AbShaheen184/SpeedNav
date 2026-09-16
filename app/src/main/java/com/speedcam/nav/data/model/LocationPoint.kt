package com.speedcam.nav.data.model

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float = 0f,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val hasSpeed: Boolean = false
)
