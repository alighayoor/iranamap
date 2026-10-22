package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** بررسی اعلام دوربین سرعت و هشدار عبور از محدودیت. */
class SpeechCamerasTest {

    private val options = AnnounceOptions(speedWarnings = true, verbosity = Verbosity.NORMAL)

    @Test
    fun cameraIsAnnouncedOncePerMinute() {
        val planner = SpeechPlanner(options)
        val first = planner.onSpeedAndCameras(
            speedKmh = 60.0, speedLimitKmh = 60,
            cameraId = 42L, cameraDistanceMeters = 300.0, cameraLimitKmh = 60,
            nowMillis = 1_000_000L
        )
        assertTrue(first.any { it.kind == AnnounceKind.SPEED_WARNING })
        assertTrue(first.first().text.contains("دوربین سرعت"))

        val repeated = planner.onSpeedAndCameras(
            speedKmh = 60.0, speedLimitKmh = 60,
            cameraId = 42L, cameraDistanceMeters = 250.0, cameraLimitKmh = 60,
            nowMillis = 1_020_000L
        )
        assertTrue(repeated.none { it.kind == AnnounceKind.SPEED_WARNING })

        val later = planner.onSpeedAndCameras(
            speedKmh = 60.0, speedLimitKmh = 60,
            cameraId = 42L, cameraDistanceMeters = 200.0, cameraLimitKmh = 60,
            nowMillis = 1_100_000L
        )
        assertTrue(later.any { it.kind == AnnounceKind.SPEED_WARNING })
    }

    @Test
    fun overSpeedWarnsWithCooldown() {
        val planner = SpeechPlanner(options)
        val warn = planner.onSpeedAndCameras(
            speedKmh = 95.0, speedLimitKmh = 60,
            cameraId = null, cameraDistanceMeters = 0.0, cameraLimitKmh = null,
            nowMillis = 5_000L
        )
        assertTrue(warn.any { it.text.contains("سرعت") })

        val tooSoon = planner.onSpeedAndCameras(
            speedKmh = 95.0, speedLimitKmh = 60,
            cameraId = null, cameraDistanceMeters = 0.0, cameraLimitKmh = null,
            nowMillis = 20_000L
        )
        assertTrue(tooSoon.isEmpty())

        val afterCooldown = planner.onSpeedAndCameras(
            speedKmh = 95.0, speedLimitKmh = 60,
            cameraId = null, cameraDistanceMeters = 0.0, cameraLimitKmh = null,
            nowMillis = 40_000L
        )
        assertTrue(afterCooldown.isNotEmpty())
    }

    @Test
    fun disabledOptionSilencesEverything() {
        val planner = SpeechPlanner(AnnounceOptions(speedWarnings = false))
        val out = planner.onSpeedAndCameras(
            speedKmh = 120.0, speedLimitKmh = 60,
            cameraId = 7L, cameraDistanceMeters = 200.0, cameraLimitKmh = 60,
            nowMillis = 1L
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun withinLimitDoesNotWarn() {
        val planner = SpeechPlanner(options)
        val out = planner.onSpeedAndCameras(
            speedKmh = 58.0, speedLimitKmh = 60,
            cameraId = null, cameraDistanceMeters = 0.0, cameraLimitKmh = null,
            nowMillis = 1L
        )
        assertEquals(0, out.size)
    }
}
