package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPlannerTest {

    private fun state(
        stepIndex: Int = 1,
        maneuver: ManeuverKind = ManeuverKind.TURN_RIGHT,
        street: String? = "ولیعصر",
        distance: Double = 400.0,
        stepLength: Double = 800.0,
        speed: Double = 40.0,
        remaining: Double = 2000.0,
        remainingSec: Long = 300,
        offRoute: Boolean = false,
        arrived: Boolean = false,
        nowMillis: Long = 1_700_000_000_000L
    ) = NavState(
        stepIndex, maneuver, street, null, distance, stepLength, speed, remaining, remainingSec,
        offRoute, arrived, secondsOfDay = 39000, nowMillis = nowMillis
    )

    @Test
    fun routeStartAnnouncesDistanceAndLights() {
        val planner = SpeechPlanner()
        val out = planner.onRouteStart(RouteBrief("میدان آزادی", 5200.0, 900, 4, null))
        assertEquals(1, out.size)
        assertTrue(out[0].text, out[0].text.contains("پنج کیلومتر"))
        assertTrue(out[0].text, out[0].text.contains("چهار چراغ"))
        // بار دوم تکرار نمی‌شود
        assertTrue(planner.onRouteStart(RouteBrief("میدان آزادی", 5200.0, 900, 4, null)).isEmpty())
    }

    @Test
    fun maneuverAnnouncedAtEachThresholdOnce() {
        val planner = SpeechPlanner(AnnounceOptions(verbosity = Verbosity.HIGH))
        assertTrue(planner.onNavigation(state(distance = 850.0)).any { it.kind == AnnounceKind.MANEUVER_FAR })
        assertTrue(planner.onNavigation(state(distance = 850.0)).isEmpty())
        assertTrue(planner.onNavigation(state(distance = 380.0)).any { it.kind == AnnounceKind.MANEUVER_MID })
        assertTrue(planner.onNavigation(state(distance = 160.0)).any { it.kind == AnnounceKind.MANEUVER_NEAR })
        assertTrue(planner.onNavigation(state(distance = 20.0)).any { it.kind == AnnounceKind.MANEUVER_NOW })
    }

    @Test
    fun lowVerbositySkipsFarAnnouncements() {
        val planner = SpeechPlanner(AnnounceOptions(verbosity = Verbosity.LOW))
        assertTrue(planner.onNavigation(state(distance = 850.0)).isEmpty())
        assertTrue(planner.onNavigation(state(distance = 380.0)).isEmpty())
        assertTrue(planner.onNavigation(state(distance = 150.0)).any { it.kind == AnnounceKind.MANEUVER_NEAR })
    }

    @Test
    fun streetNameIsOmittedWhenDisabled() {
        val planner = SpeechPlanner(AnnounceOptions(verbosity = Verbosity.NORMAL, streetNames = false))
        val out = planner.onNavigation(state(distance = 380.0))
        assertTrue(out.isNotEmpty())
        assertFalse(out[0].text.contains("ولیعصر"))
    }

    @Test
    fun arrivalAnnouncedOnce() {
        val planner = SpeechPlanner()
        assertEquals(AnnounceKind.ARRIVE, planner.onNavigation(state(arrived = true)).first().kind)
        assertTrue(planner.onNavigation(state(arrived = true)).isEmpty())
    }

    @Test
    fun offRouteAnnouncedOnceUntilBackOnRoute() {
        val planner = SpeechPlanner()
        assertEquals(AnnounceKind.OFF_ROUTE, planner.onNavigation(state(offRoute = true)).first().kind)
        assertTrue(planner.onNavigation(state(offRoute = true)).isEmpty())
        planner.onNavigation(state(offRoute = false, distance = 5000.0))
        assertEquals(AnnounceKind.OFF_ROUTE, planner.onNavigation(state(offRoute = true)).first().kind)
    }

    @Test
    fun rerouteRemovesManeuverMemory() {
        val planner = SpeechPlanner(AnnounceOptions(verbosity = Verbosity.NORMAL))
        planner.onNavigation(state(distance = 380.0))
        assertTrue(planner.onNavigation(state(distance = 380.0)).isEmpty())
        planner.onRerouted(1200.0)
        assertTrue(planner.onNavigation(state(distance = 380.0)).isNotEmpty())
    }

    @Test
    fun trafficLightAnnouncementRespectsCooldown() {
        val planner = SpeechPlanner()
        val f = TrafficLightModel.stateAt(LightTiming(20, 3, 30, 0), 39000)
        val ahead = listOf(LightAhead(7, 150.0, f))
        val first = planner.onLights(ahead, 40.0, 1_000_000L, 39000)
        assertTrue(first.isNotEmpty())
        val second = planner.onLights(ahead, 40.0, 1_005_000L, 39005)
        assertTrue(second.isEmpty())
        val third = planner.onLights(ahead, 40.0, 1_040_000L, 39400)
        assertTrue(third.isNotEmpty())
    }

    @Test
    fun lightAnnouncementsCanBeDisabled() {
        val planner = SpeechPlanner(AnnounceOptions(lights = false))
        val f = TrafficLightModel.stateAt(LightTiming(20, 3, 30, 0), 39000)
        assertTrue(planner.onLights(listOf(LightAhead(1, 100.0, f)), 40.0, 0L, 0L).isEmpty())
    }

    @Test
    fun redLightTextMentionsCountdown() {
        val planner = SpeechPlanner()
        val red = TrafficLightModel.stateAt(LightTiming(20, 3, 30, 0), 35)
        assertEquals(LightColor.RED, red.color)
        val out = planner.onLights(listOf(LightAhead(3, 180.0, red)), 30.0, 0L, 35)
        assertTrue(out.first().text.contains("قرمز"))
        assertTrue(out.first().text.contains("ثانیه"))
    }

    @Test
    fun greenWaveSuggestedForManyGreenLights() {
        val planner = SpeechPlanner(AnnounceOptions(lightCooldownMillis = 0))
        val f = TrafficLightModel.stateAt(LightTiming(60, 3, 10, 0), 0)
        val ahead = listOf(
            LightAhead(1, 100.0, f),
            LightAhead(2, 200.0, f),
            LightAhead(3, 300.0, f),
            LightAhead(4, 400.0, f)
        )
        val out = planner.onLights(ahead, 45.0, 0L, 0L)
        assertTrue(out.isNotEmpty())
    }

    @Test
    fun speedWarningThrottled() {
        val planner = SpeechPlanner(AnnounceOptions(speedWarningKmh = 90))
        val first = planner.onNavigation(state(speed = 120.0))
        assertTrue(first.any { it.kind == AnnounceKind.SPEED_WARNING })
        val second = planner.onNavigation(state(speed = 120.0, nowMillis = 1_700_000_030_000L))
        assertFalse(second.any { it.kind == AnnounceKind.SPEED_WARNING })
        val third = planner.onNavigation(state(speed = 120.0, nowMillis = 1_700_000_200_000L))
        assertTrue(third.any { it.kind == AnnounceKind.SPEED_WARNING })
    }

    @Test
    fun resetClearsState() {
        val planner = SpeechPlanner()
        planner.onNavigation(state(distance = 380.0))
        planner.reset()
        assertTrue(planner.onNavigation(state(distance = 380.0)).isNotEmpty())
    }

    @Test
    fun voicePhrasesAreNaturalPersian() {
        assertTrue(VoicePhrases.arrival("میدان آزادی").contains("رسیدید"))
        assertTrue(VoicePhrases.offRoute().contains("خارج شدید"))
        assertTrue(VoicePhrases.trafficLight(LightColor.RED, 12, 200.0).contains("دوازده"))
        assertTrue(VoicePhrases.speedWarning(130).contains("صد و سی"))
        assertTrue(VoicePhrases.greenWave(45, 3).contains("موج سبز"))
    }
}
