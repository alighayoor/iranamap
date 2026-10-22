package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuversTest {

    @Test
    fun mapsOsrmTypesToKinds() {
        assertEquals(ManeuverKind.TURN_LEFT, ManeuverText.kindOf("turn", "left"))
        assertEquals(ManeuverKind.SHARP_RIGHT, ManeuverText.kindOf("turn", "sharp right"))
        assertEquals(ManeuverKind.SLIGHT_LEFT, ManeuverText.kindOf("turn", "slight left"))
        assertEquals(ManeuverKind.ROUNDABOUT, ManeuverText.kindOf("roundabout", null))
        assertEquals(ManeuverKind.MERGE, ManeuverText.kindOf("merge", null))
        assertEquals(ManeuverKind.FORK_RIGHT, ManeuverText.kindOf("fork", "right"))
        assertEquals(ManeuverKind.ARRIVE, ManeuverText.kindOf("arrive", null))
        assertEquals(ManeuverKind.DEPART, ManeuverText.kindOf("depart", null))
        assertEquals(ManeuverKind.UNKNOWN, ManeuverText.kindOf(null, null))
        assertEquals(ManeuverKind.STRAIGHT, ManeuverText.kindOf("turn", "straight"))
    }

    @Test
    fun addsRoadTypeWhenMissing() {
        assertEquals("خیابان ولیعصر", ManeuverText.withRoadType("ولیعصر"))
        assertEquals("بلوار کشاورز", ManeuverText.withRoadType("بلوار کشاورز"))
        assertEquals("میدان انقلاب", ManeuverText.withRoadType("میدان انقلاب"))
        assertEquals(null, ManeuverText.withRoadType("  "))
        assertEquals(null, ManeuverText.withRoadType(null))
    }

    @Test
    fun shortPhrasesArePersian() {
        assertEquals("به راست بپیچید", ManeuverText.shortPhrase(ManeuverKind.TURN_RIGHT))
        assertEquals("به چپ بپیچید", ManeuverText.shortPhrase(ManeuverKind.TURN_LEFT))
        assertTrue(ManeuverText.shortPhrase(ManeuverKind.ROUNDABOUT, 3).contains("سوم"))
    }

    @Test
    fun fullSentenceIncludesStreetAndDistance() {
        val s = ManeuverText.fullSentence(ManeuverKind.TURN_RIGHT, "ولیعصر", null, 300.0, false)
        assertTrue(s, s.contains("سیصد متر"))
        assertTrue(s, s.contains("به راست بپیچید"))
        assertTrue(s, s.contains("خیابان ولیعصر"))
    }

    @Test
    fun immediateManeuverUsesNowPhrase() {
        val s = ManeuverText.fullSentence(ManeuverKind.TURN_LEFT, null, null, 12.0, false)
        assertTrue(s.startsWith("همین حالا"))
    }

    @Test
    fun glyphIsProvidedForEveryKind() {
        for (k in ManeuverKind.values()) {
            assertTrue(ManeuverText.glyph(k).isNotBlank())
        }
    }
}
