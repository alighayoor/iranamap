package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    private val tehran = GeoPoint(35.6892, 51.3890)
    private val tajrish = GeoPoint(35.8044, 51.4245)

    @Test
    fun haversineMatchesKnownDistance() {
        val d = GeoMath.distanceMeters(tehran, tajrish)
        assertTrue("distance was $d", d in 12_000.0..15_000.0)
    }

    @Test
    fun bearingPointsNorthEast() {
        val b = GeoMath.bearingDegrees(tehran, tajrish)
        assertTrue("bearing $b", b in 5.0..35.0)
    }

    @Test
    fun angleDeltaWrapsCorrectly() {
        assertEquals(10.0, GeoMath.angleDelta(350.0, 0.0), 0.001)
        assertEquals(-10.0, GeoMath.angleDelta(0.0, 350.0), 0.001)
        assertEquals(180.0, GeoMath.angleDelta(0.0, 180.0), 0.001)
    }

    @Test
    fun projectAndDistanceAreConsistent() {
        val p = GeoMath.project(tehran, 1000.0, 90.0)
        val d = GeoMath.distanceMeters(tehran, p)
        assertEquals(1000.0, d, 5.0)
        assertTrue(p.lng > tehran.lng)
    }

    @Test
    fun projectsPointOnSegment() {
        val a = GeoPoint(35.0, 51.0)
        val b = GeoPoint(35.0, 51.01)
        val p = GeoPoint(35.001, 51.005)
        val (dist, t, snapped) = GeoMath.projectOnSegment(p, a, b)
        assertTrue(dist in 100.0..120.0)
        assertTrue(t in 0.45..0.55)
        assertEquals(35.0, snapped.lat, 1e-6)
    }

    @Test
    fun matchOnPathFindsNearestSegment() {
        val path = listOf(
            GeoPoint(35.700, 51.400),
            GeoPoint(35.705, 51.400),
            GeoPoint(35.710, 51.400)
        )
        val m = GeoMath.matchOnPath(GeoPoint(35.7075, 51.4002), path)
        assertNotNull(m)
        assertEquals(1, m!!.segmentIndex)
        assertTrue(m.distanceMeters < 50)
    }

    @Test
    fun remainingDistanceDecreasesAlongPath() {
        val path = listOf(
            GeoPoint(35.700, 51.400),
            GeoPoint(35.710, 51.400),
            GeoPoint(35.720, 51.400)
        )
        val atStart = GeoMath.remainingDistance(path, 0, path[0])
        val atMiddle = GeoMath.remainingDistance(path, 0, path[1])
        assertTrue(atStart > atMiddle)
        assertEquals(2224.0, atStart, 200.0)
    }

    @Test
    fun boundsAndTilesAreComputed() {
        val b = GeoMath.boundsAround(tehran, 10.0)
        assertTrue(b.contains(tehran))
        assertTrue(GeoMath.tileCount(b, 12) > 0)
        assertTrue(GeoMath.tileCount(b, 14) > GeoMath.tileCount(b, 12))
    }

    @Test
    fun iranPlausibilityCheck() {
        assertTrue(GeoMath.isPlausibleIranPoint(tehran))
        assertFalse(GeoMath.isPlausibleIranPoint(GeoPoint(52.52, 13.40)))
    }

    @Test
    fun interpolationWalksAlongPath() {
        val path = listOf(GeoPoint(35.0, 51.0), GeoPoint(35.0, 51.01))
        val mid = GeoMath.interpolateAlong(path, 500.0)!!
        assertEquals(51.005, mid.lng, 0.0015)
    }
}
