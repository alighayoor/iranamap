package com.iranamap.real.nav

import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.ManeuverKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerTest {

    // مسیری مستقیم به سمت شمال: ۳ گام، هر کدام ۵۰۰ متر
    private val path = (0..60).map { GeoPoint(35.7000 + it * 0.000226, 51.4000) } // حدود ۱۵۰۰ متر

    private fun step(i: Int, along: Double, name: String) = NavStep(
        index = i,
        kind = if (i == 2) ManeuverKind.ARRIVE else ManeuverKind.TURN_RIGHT,
        streetName = name,
        exitNumber = null,
        distanceMeters = 500.0,
        durationSeconds = 60.0,
        location = null,
        startAlongMeters = along
    )

    private val route = ActiveRoute(
        points = path,
        steps = listOf(step(0, 0.0, "خیابان اول"), step(1, 500.0, "خیابان دوم"), step(2, 1000.0, "خیابان سوم")),
        totalMeters = 1500.0,
        totalSeconds = 180.0,
        destinationName = "مقصد آزمایشی"
    )

    @Test
    fun startsAtBeginningOfRoute() {
        val tracker = RouteTracker(route)
        val state = tracker.update(path.first())
        assertTrue(state.distanceAlong < 5)
        assertTrue(state.remainingMeters > 1000)
        assertEquals(0, state.stepIndex)
        assertFalse(state.offRoute)
    }

    @Test
    fun detectsOffRouteAfterRepeatedDeviations() {
        val tracker = RouteTracker(route)
        val far = GeoPoint(35.7100, 51.4200)
        assertFalse(tracker.update(far).offRoute)
        assertFalse(tracker.update(far).offRoute)
        assertTrue(tracker.update(far).offRoute)
    }

    @Test
    fun offRouteClearsWhenBackOnTrack() {
        val tracker = RouteTracker(route)
        val far = GeoPoint(35.7100, 51.4200)
        repeat(3) { tracker.update(far) }
        val back = tracker.update(path[10])
        assertFalse(back.offRoute)
        assertTrue(back.offRouteDistanceMeters < 20)
    }

    @Test
    fun advancesStepsAlongRoute() {
        val tracker = RouteTracker(route)
        val s1 = tracker.update(path[10])
        val s2 = tracker.update(path[45])
        assertTrue(s2.stepIndex >= s1.stepIndex)
        assertTrue(s2.remainingMeters < s1.remainingMeters)
    }

    @Test
    fun reportsArrivalAtDestination() {
        val tracker = RouteTracker(route)
        val state = tracker.update(path.last())
        assertTrue(state.arrived)
    }

    @Test
    fun bearingFollowsTravelDirection() {
        val tracker = RouteTracker(route)
        tracker.update(path[5])
        val state = tracker.update(path[15])
        assertTrue("bearing=${state.bearingDegrees}", state.bearingDegrees in 340.0..360.0 || state.bearingDegrees in 0.0..20.0)
    }

    @Test
    fun remainingTimeUsesSpeed() {
        val tracker = RouteTracker(route)
        val state = tracker.update(path[20], speedMps = 10.0)
        assertTrue(state.remainingSeconds > 0)
        assertTrue(state.remainingSeconds < 1000)
    }

    @Test
    fun resetRestoresInitialState() {
        val tracker = RouteTracker(route)
        tracker.update(path[40])
        tracker.reset()
        val state = tracker.update(path[0])
        assertTrue(state.distanceAlong < 10)
    }
}
