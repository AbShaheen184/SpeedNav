package com.speedcam.nav.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY timestamp DESC")
    fun getAllSavedLocations(): Flow<List<SavedLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocationEntity): Long

    @Delete
    suspend fun delete(location: SavedLocationEntity)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM saved_locations")
    suspend fun count(): Int

    @Query("SELECT * FROM saved_locations WHERE latitude = :lat AND longitude = :lon LIMIT 1")
    suspend fun findByCoordinates(lat: Double, lon: Double): SavedLocationEntity?

    @Query("UPDATE saved_locations SET title = :title, subtitle = :subtitle, category = :category WHERE id = :id")
    suspend fun updateLocation(id: Long, title: String, subtitle: String, category: String)
}
