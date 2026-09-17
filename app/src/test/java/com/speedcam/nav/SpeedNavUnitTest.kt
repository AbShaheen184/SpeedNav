package com.speedcam.nav

import com.speedcam.nav.data.model.DroppedPinLocation
import org.junit.Assert.*
import org.junit.Test

class SpeedNavUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun droppedPinLocation_formatsCoordinatesCorrectly() {
        val pin = DroppedPinLocation(
            latitude = 51.507412,
            longitude = -0.127812,
            title = "Trafalgar Square"
        )
        val formatted = pin.formattedCoordinates()
        assertEquals("51.507412, -0.127812", formatted)
    }

    @Test
    fun droppedPinLocation_convertsToSearchLocation() {
        val pin = DroppedPinLocation(
            latitude = 51.507412,
            longitude = -0.127812,
            title = "Trafalgar Square",
            subtitle = "London, UK",
            distanceMeters = 350
        )
        val searchLoc = pin.toSearchLocation()
        assertEquals("Trafalgar Square", searchLoc.title)
        assertEquals("London, UK", searchLoc.subtitle)
        assertEquals(51.507412, searchLoc.latitude, 0.000001)
        assertEquals(-0.127812, searchLoc.longitude, 0.000001)
        assertEquals(350, searchLoc.distanceMeters)
    }
}
