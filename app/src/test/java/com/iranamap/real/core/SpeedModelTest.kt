package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedModelTest {

    @Test
    fun parsesPlainNumbers() {
        assertEquals(50, SpeedModel.parseMaxspeed("50"))
        assertEquals(120, SpeedModel.parseMaxspeed("120 km/h"))
        assertEquals(120, SpeedModel.parseMaxspeed("120km/h"))
    }

    @Test
    fun parsesMphAndCountryDefaults() {
        assertEquals(48, SpeedModel.parseMaxspeed("30 mph"))
        assertEquals(50, SpeedModel.parseMaxspeed("IR:urban"))
        assertEquals(120, SpeedModel.parseMaxspeed("IR:motorway"))
        assertEquals(5, SpeedModel.parseMaxspeed("walk"))
    }

    @Test
    fun fallsBackToRoadClass() {
        assertEquals(50, SpeedModel.parseMaxspeed(null, "residential"))
        assertEquals(120, SpeedModel.parseMaxspeed("none", "motorway"))
        assertNull(SpeedModel.parseMaxspeed(null, null))
    }

    @Test
    fun overLimitUsesTolerance() {
        assertFalse(SpeedModel.isOverLimit(54.0, 50))
        assertTrue(SpeedModel.isOverLimit(56.0, 50))
        assertFalse(SpeedModel.isOverLimit(200.0, 0))
    }

    @Test
    fun cameraWarningDistanceGrowsWithSpeed() {
        val slow = SpeedModel.cameraWarningDistanceMeters(30.0)
        val fast = SpeedModel.cameraWarningDistanceMeters(110.0)
        assertTrue(fast > slow)
        assertTrue(slow >= 250.0)
        assertTrue(fast <= 900.0)
        assertTrue(SpeedModel.shouldWarnCamera(200.0, 60.0))
        assertFalse(SpeedModel.shouldWarnCamera(5000.0, 60.0))
    }

    @Test
    fun trafficFactorPeaksAtRushHour() {
        val night = SpeedModel.trafficFactor("primary", 2 * 3600L)
        val rush = SpeedModel.trafficFactor("primary", 17 * 3600L)
        val midday = SpeedModel.trafficFactor("primary", 13 * 3600L)
        assertTrue(rush > midday)
        assertTrue(midday > night)
        assertTrue(rush <= 1.8)
    }

    @Test
    fun cameraSpeechIsPersian() {
        val text = SpeedModel.cameraSpeech(300.0, 60)
        assertTrue(text.contains("دوربین سرعت"))
        assertTrue(text.contains("کیلومتر بر ساعت"))
    }
}
