package com.speedcam.nav.data.repository

import com.speedcam.nav.data.local.SavedLocationDao
import com.speedcam.nav.data.local.SavedLocationEntity
import kotlinx.coroutines.flow.Flow

class SavedLocationRepository(private val dao: SavedLocationDao) {

    val savedLocations: Flow<List<SavedLocationEntity>> = dao.getAllSavedLocations()

    suspend fun saveLocation(
        title: String,
        subtitle: String,
        latitude: Double,
        longitude: Double,
        category: String = "FAVORITE"
    ): Long {
        val entity = SavedLocationEntity(
            title = title,
            subtitle = subtitle,
            latitude = latitude,
            longitude = longitude,
            category = category,
            timestamp = System.currentTimeMillis()
        )
        return dao.insert(entity)
    }

    suspend fun deleteLocation(id: Long) {
        dao.deleteById(id)
    }

    suspend fun isLocationSaved(lat: Double, lon: Double): Boolean {
        return dao.findByCoordinates(lat, lon) != null
    }

    suspend fun updateLocation(id: Long, title: String, subtitle: String, category: String) {
        dao.updateLocation(id, title, subtitle, category)
    }

    suspend fun ensureDefaultLocations(currentLat: Double, currentLon: Double) {
        if (dao.count() == 0) {
            dao.insert(
                SavedLocationEntity(
                    title = "Home",
                    subtitle = "Main Residence",
                    latitude = currentLat + 0.0125,
                    longitude = currentLon + 0.0085,
                    category = "HOME"
                )
            )
            dao.insert(
                SavedLocationEntity(
                    title = "Work Office",
                    subtitle = "Innovation Tech Park",
                    latitude = currentLat + 0.0240,
                    longitude = currentLon - 0.0120,
                    category = "WORK"
                )
            )
            dao.insert(
                SavedLocationEntity(
                    title = "International Airport",
                    subtitle = "Terminal 2 Departure",
                    latitude = currentLat - 0.0350,
                    longitude = currentLon + 0.0280,
                    category = "FAVORITE"
                )
            )
        }
    }
}
