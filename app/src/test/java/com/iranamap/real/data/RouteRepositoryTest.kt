package com.iranamap.real.data

import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.ManeuverKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRepositoryTest {
    private val origin = GeoPoint(29.620, 52.520)
    private val destination = GeoPoint(29.630, 52.530)

    private class FakeApi(private val answer: suspend () -> OsrmResponse) : OsrmApi {
        var calls = 0
        var coordinates = ""
        var requestedAlternatives = false

        override suspend fun route(
            coords: String,
            overview: String,
            geometries: String,
            steps: Boolean,
            alternatives: Boolean,
            annotations: String
        ): OsrmResponse {
            calls++
            coordinates = coords
            requestedAlternatives = alternatives
            return answer()
        }

        override suspend fun nearest(coords: String, number: Int): OsrmResponse = answer()
    }

    private fun route(duration: Double, valid: Boolean = true) = OsrmRoute(
        distance = 1500.0,
        duration = duration,
        geometry = OsrmGeometry(coordinates = if (valid) {
            listOf(listOf(origin.lng, origin.lat), listOf(destination.lng, destination.lat))
        } else listOf(listOf(origin.lng, origin.lat))),
        legs = listOf(OsrmLeg(steps = listOf(
            OsrmStep(distance = 1000.0, name = "خیابان اول", maneuver = OsrmManeuver(type = "depart")),
            OsrmStep(distance = 500.0, name = "خیابان دوم", maneuver = OsrmManeuver(type = "turn", modifier = "right"))
        )))
    )

    @Test
    fun primaryFailureUsesBackupAndPreservesCoordinates() = runBlocking {
        val primary = FakeApi { throw IllegalStateException("offline") }
        val backup = FakeApi { OsrmResponse(code = "Ok", routes = listOf(route(200.0))) }
        val result = RouteRepository(primary, backup).fetchRoute(origin, destination, "مقصد")
        assertTrue(result.isSuccess)
        assertEquals(1, primary.calls)
        assertEquals(1, backup.calls)
        assertEquals("52.520000,29.620000;52.530000,29.630000", backup.coordinates)
        val resolved = result.getOrThrow()
        assertEquals("مقصد", resolved.destinationName)
        assertEquals(destination, resolved.points.last())
        assertEquals(ManeuverKind.TURN_RIGHT, resolved.steps[1].kind)
    }

    @Test
    fun alternativesAreSortedAndInvalidGeometryIsDiscarded() = runBlocking {
        val primary = FakeApi { OsrmResponse(code = "Ok", routes = listOf(route(400.0), route(1.0, false), route(200.0))) }
        val backup = FakeApi { error("backup should not run") }
        val result = RouteRepository(primary, backup).fetchRoutes(origin, destination).getOrThrow()
        assertEquals(listOf(200.0, 400.0), result.map { it.totalSeconds })
        assertTrue(primary.requestedAlternatives)
        assertEquals(0, backup.calls)
    }

    @Test
    fun cancelledRequestDoesNotStartBackupOrReturnFailure() = runBlocking {
        val primary = FakeApi { throw CancellationException("cancelled") }
        val backup = FakeApi { error("backup should not run after cancellation") }
        try {
            RouteRepository(primary, backup).fetchRoutes(origin, destination)
            throw AssertionError("CancellationException should propagate")
        } catch (_: CancellationException) {
            assertEquals(0, backup.calls)
        }
    }

    @Test
    fun invalidGeometryIsReportedWithoutCrashing() = runBlocking {
        val primary = FakeApi { OsrmResponse(code = "Ok", routes = listOf(route(100.0, false))) }
        val backup = FakeApi { error("backup should not run") }
        val result = RouteRepository(primary, backup).fetchRoute(origin, destination)
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }
}
