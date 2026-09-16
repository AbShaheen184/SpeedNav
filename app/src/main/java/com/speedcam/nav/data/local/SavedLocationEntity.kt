package com.speedcam.nav.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val subtitle: String = "",
    val latitude: Double,
    val longitude: Double,
    val category: String = "FAVORITE", // HOME, WORK, FAVORITE, RECENT
    val timestamp: Long = System.currentTimeMillis()
)
