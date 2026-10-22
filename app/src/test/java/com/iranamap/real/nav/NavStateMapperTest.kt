package com.iranamap.real.nav

import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.ManeuverKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NavStateMapperTest {

    private val tehran = TimeZone.getTimeZone("Asia/Tehran")

    private fun state() = TrackerState(
        distanceAlong = 500.0,
        remainingMeters = 1200.0,
        remainingSeconds = 180,
        stepIndex = 1,
        distanceToManeuverMeters = 240.0,
        stepLengthMeters = 700.0,
        offRoute = false,
        offRouteDistanceMeters = 3.0,
        arrived = false,
        snappedPoint = GeoPoint(35.7, 51.4),
        bearingDegrees = 92.0,
        currentStreetName = "خیابان ولیعصر"
    )

    private val step = NavStep(
        index = 1,
        kind = ManeuverKind.TURN_LEFT,
        streetName = "خیابان انقلاب",
        exitNumber = null,
        distanceMeters = 700.0,
        durationSeconds = 90.0,
        location = null
    )

    @Test
    fun mapsAllFields() {
        val cal = Calendar.getInstance(tehran)
        cal.set(2026, Calendar.OCTOBER, 22, 14, 27, 0)
        val nav = NavStateMapper.toNavState(state(), step, 48.0, cal.timeInMillis, tehran)
        assertEquals(1, nav.stepIndex)
        assertEquals(ManeuverKind.TURN_LEFT, nav.maneuver)
        assertEquals("خیابان ولیعصر", nav.streetName)
        assertEquals(240.0, nav.distanceToManeuverMeters, 0.001)
        assertEquals(700.0, nav.stepLengthMeters, 0.001)
        assertEquals(48.0, nav.speedKmh, 0.001)
        assertEquals(1200.0, nav.remainingMeters, 0.001)
        assertEquals(180, nav.remainingSeconds)
        assertFalse(nav.offRoute)
        assertFalse(nav.arrived)
        assertEquals(14 * 3600 + 27 * 60L, nav.secondsOfDay)
    }

    @Test
    fun fallsBackToStepStreetName() {
        val nav = NavStateMapper.toNavState(state().copy(currentStreetName = null), step, 30.0, 0L, tehran)
        assertEquals("خیابان انقلاب", nav.streetName)
    }

    @Test
    fun handlesMissingStep() {
        val nav = NavStateMapper.toNavState(state(), null, 10.0, 0L, tehran)
        assertEquals(ManeuverKind.CONTINUE, nav.maneuver)
        assertEquals("خیابان ولیعصر", nav.streetName)
        assertTrue(nav.nowMillis == 0L)
    }
}
