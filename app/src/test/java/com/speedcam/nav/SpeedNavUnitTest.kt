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

    @Test
    fun googleMapsLinkResolver_detectsMapLinksAndCoordinates() {
        assertTrue(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("https://maps.google.com/?q=51.5007,-0.1246"))
        assertTrue(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("https://maps.app.goo.gl/xyz123"))
        assertTrue(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("https://goo.gl/maps/abc456"))
        assertTrue(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("https://www.google.com/maps/place/Eiffel+Tower/@48.8584,2.2923,17z"))
        assertTrue(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("geo:51.5074,-0.1278"))
        assertTrue(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("geo:0,0?q=48.8584,2.2923(Eiffel Tower)"))
        assertTrue(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("51.5074, -0.1278"))
        assertTrue(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("Check this place https://maps.app.goo.gl/abc in London"))

        assertFalse(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("Just a normal search query"))
        assertFalse(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("Baker Street London"))
        assertFalse(com.speedcam.nav.data.remote.GoogleMapsLinkResolver.isMapLinkOrCoordinates("https://example.com/notamap"))
    }

    @Test
    fun googleMapsLinkResolver_parsesQueryCoordinatesCorrectly() = kotlinx.coroutines.runBlocking {
        val url = "https://maps.google.com/?q=51.5007,-0.1246"
        val resolved = com.speedcam.nav.data.remote.GoogleMapsLinkResolver.resolve(
            input = url,
            userLat = 51.5000,
            userLon = -0.1200
        )
        assertNotNull(resolved)
        assertEquals(51.5007, resolved!!.latitude, 0.0001)
        assertEquals(-0.1246, resolved.longitude, 0.0001)
        assertNotNull(resolved.distanceMeters)
    }

    @Test
    fun googleMapsLinkResolver_parsesPlaceAtCoordinatesCorrectly() = kotlinx.coroutines.runBlocking {
        val url = "https://www.google.com/maps/place/The+Shard/@51.5045,-0.0865,17z"
        val resolved = com.speedcam.nav.data.remote.GoogleMapsLinkResolver.resolve(
            input = url
        )
        assertNotNull(resolved)
        assertEquals("The Shard", resolved!!.title)
        assertEquals(51.5045, resolved.latitude, 0.0001)
        assertEquals(-0.0865, resolved.longitude, 0.0001)
    }

    @Test
    fun googleMapsLinkResolver_parsesGeoUriCorrectly() = kotlinx.coroutines.runBlocking {
        val geo = "geo:0,0?q=48.8584,2.2923(Eiffel Tower)"
        val resolved = com.speedcam.nav.data.remote.GoogleMapsLinkResolver.resolve(input = geo)
        assertNotNull(resolved)
        assertEquals("Eiffel Tower", resolved!!.title)
        assertEquals(48.8584, resolved.latitude, 0.0001)
        assertEquals(2.2923, resolved.longitude, 0.0001)
    }

    @Test
    fun googleMapsLinkResolver_parsesRawCoordinatesCorrectly() = kotlinx.coroutines.runBlocking {
        val coords = "40.7128, -74.0060"
        val resolved = com.speedcam.nav.data.remote.GoogleMapsLinkResolver.resolve(input = coords)
        assertNotNull(resolved)
        assertEquals(40.7128, resolved!!.latitude, 0.0001)
        assertEquals(-74.0060, resolved.longitude, 0.0001)
    }

    @Test
    fun resolvedMapLink_convertsToSearchLocationAndFormats() {
        val link = com.speedcam.nav.data.model.ResolvedMapLink(
            originalInput = "https://maps.app.goo.gl/test",
            canonicalUrl = "https://www.google.com/maps/place/Louvre/@48.8606,2.3376",
            latitude = 48.860611,
            longitude = 2.337644,
            title = "Louvre Museum",
            subtitle = "Paris, France",
            distanceMeters = 2400
        )
        val searchLoc = link.toSearchLocation()
        assertEquals("Louvre Museum", searchLoc.title)
        assertEquals("Paris, France", searchLoc.subtitle)
        assertEquals(48.860611, searchLoc.latitude, 0.000001)
        assertEquals(2.337644, searchLoc.longitude, 0.000001)
        assertEquals(2400, searchLoc.distanceMeters)
        assertEquals("48.860611, 2.337644", link.formattedCoordinates())
    }
}
