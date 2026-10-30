package pl.trailpocket.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RouteTest {
    @Test fun decodesBackendContractAndPreservesGeoJsonCoordinateOrder() {
        val route = Json.decodeFromString<Route>("""{
            "id":"route-1","name":null,"profile":"bike",
            "points":[{"lat":52.2,"lon":21.0,"name":"A"},{"lat":52.3,"lon":21.1,"name":"B"}],
            "geometry":{"type":"LineString","coordinates":[[21.0,52.2],[21.1,52.3]]},
            "distance_m":12500.0,"duration_s":3900.0,"created_at":"2026-10-30T12:00:00Z"
        }""")
        assertEquals(Profile.BIKE, route.profile)
        assertEquals(21.0, route.geometry.coordinates.first()[0], 0.0)
        assertEquals("12,5 km", route.distanceLabel())
        assertEquals("1 h 5 min", route.durationLabel())
    }
}
