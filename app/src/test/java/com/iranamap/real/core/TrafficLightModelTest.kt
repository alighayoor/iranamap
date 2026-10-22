package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficLightModelTest {

    private val timing = LightTiming(greenSec = 20, yellowSec = 3, redSec = 30, offsetSec = 0)

    @Test
    fun cycleCoversAllPhases() {
        assertEquals(53, timing.cycleSec)
        assertEquals(LightColor.GREEN, TrafficLightModel.stateAt(timing, 0).color)
        assertEquals(LightColor.GREEN, TrafficLightModel.stateAt(timing, 19).color)
        assertEquals(LightColor.YELLOW, TrafficLightModel.stateAt(timing, 21).color)
        assertEquals(LightColor.RED, TrafficLightModel.stateAt(timing, 30).color)
        assertEquals(LightColor.GREEN, TrafficLightModel.stateAt(timing, 53).color)
    }

    @Test
    fun remainingSecondsCountsDownMonotonically() {
        var last = 100
        for (s in 0L..50L) {
            val f = TrafficLightModel.stateAt(timing, s)
            assertTrue(f.remainingSec in 1..timing.cycleSec)
            if (f.color == LightColor.RED) {
                assertTrue("at $s remaining ${f.remainingSec}", f.remainingSec <= 30)
            }
            last = f.remainingSec
        }
        assertTrue(last > 0)
    }

    @Test
    fun offsetShiftsThePhase() {
        val shifted = timing.copy(offsetSec = 25)
        assertEquals(LightColor.RED, TrafficLightModel.stateAt(shifted, 0).color)
    }

    @Test
    fun forecastAtArrivalDependsOnTravelTime() {
        val greenNow = TrafficLightModel.stateAt(timing, 0)
        assertEquals(LightColor.GREEN, greenNow.color)
        val arrives = TrafficLightModel.forecastAtArrival(timing, 0, 45.0)
        assertEquals(LightColor.RED, arrives.color)
        assertTrue(TrafficLightModel.willBeRedOnArrival(timing, 0, 45.0))
        assertFalse(TrafficLightModel.willBeRedOnArrival(timing, 0, 5.0))
    }

    @Test
    fun speedAdviceFindsAGreenWindow() {
        val kmh = TrafficLightModel.speedForGreen(timing, 0, 600.0, minKmh = 20, maxKmh = 80, currentKmh = 50)
        assertTrue(kmh in 20..80)
        val travel = 600.0 / (kmh / 3.6)
        assertEquals(LightColor.GREEN, TrafficLightModel.forecastAtArrival(timing, 0, travel).color)
    }

    @Test
    fun greenRatioIsBetweenZeroAndOne() {
        val lights = listOf(
            timing to 100.0,
            timing.copy(offsetSec = 10) to 300.0,
            timing.copy(offsetSec = 20) to 500.0
        )
        val r = TrafficLightModel.greenRatioAtSpeed(lights, 0, 50.0)
        assertTrue(r in 0.0..1.0)
    }

    @Test
    fun seedsAreStableAndDifferByLocation() {
        val a = TrafficLightModel.seedFor(35.7, 51.4, 1001)
        val b = TrafficLightModel.seedFor(35.7, 51.4, 1001)
        val c = TrafficLightModel.seedFor(35.7001, 51.4001, 1002)
        assertEquals(a, b)
        assertTrue(a != c)
        assertTrue(a >= 0)
    }

    @Test
    fun timingsStayInReasonableRange() {
        for (seed in 0..200) {
            val t = TrafficLightModel.timingFor(seed, seed % 2 == 0)
            assertTrue(t.greenSec in 15..60)
            assertTrue(t.yellowSec in 2..8)
            assertTrue(t.redSec in 20..70)
            assertTrue(t.offsetSec < t.cycleSec)
        }
    }

    @Test
    fun shouldStopOnlyWhenCloseAndMoving() {
        val red = TrafficLightModel.stateAt(timing, 30)
        assertTrue(TrafficLightModel.shouldStop(red, 50.0, 40.0))
        assertFalse(TrafficLightModel.shouldStop(red, 50.0, 800.0))
        val green = TrafficLightModel.stateAt(timing, 5)
        assertFalse(TrafficLightModel.shouldStop(green, 50.0, 40.0))
    }

    @Test
    fun learnerInfersCycleFromObservations() {
        val cycle = 90
        val obs = (0 until 60).map { i ->
            val t = i * 3
            val phase = t % cycle
            LightObservation(t, if (phase < 40) LightColor.GREEN else if (phase < 45) LightColor.YELLOW else LightColor.RED)
        }
        val inferred = TrafficLightLearner.inferCycleSeconds(obs, minCycle = 40, maxCycle = 140)
        assertNotNull(inferred)
        assertTrue("inferred $inferred", inferred!! in 60..120)
        val learned = TrafficLightLearner.inferTiming(obs, seed = 1)
        assertTrue(learned.cycleSec > 30)
    }
}
