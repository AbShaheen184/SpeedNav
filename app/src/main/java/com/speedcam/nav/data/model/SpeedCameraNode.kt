package com.speedcam.nav.data.model

enum class CameraType(val label: String, val description: String) {
    SPEED("Speed Camera", "Fixed radar enforcing maximum speed"),
    RED_LIGHT("Traffic Light Camera", "Enforcing red-light stop compliance"),
    SEATBELT_PHONE("AI Seatbelt & Phone", "Surveillance for seatbelt and mobile device usage"),
    AVERAGE_SPEED("Average Speed Trap", "Section control measuring speed across a stretch"),
    POLICE_MOBILE("Mobile Radar Trap", "Reported mobile police patrol checkpoint")
}

data class SpeedCameraNode(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val cameraType: CameraType,
    val speedLimit: Int? = null,
    val direction: String? = null,
    val distanceMeters: Int = 0,
    val roadName: String? = null
)
