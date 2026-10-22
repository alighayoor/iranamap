package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneModelTest {

    @Test
    fun parsesDirections() {
        assertEquals(LaneDirection.LEFT, LaneDirection.parse("left"))
        assertEquals(LaneDirection.SLIGHT_RIGHT, LaneDirection.parse("slight right"))
        assertEquals(LaneDirection.STRAIGHT, LaneDirection.parse("through"))
        assertEquals(LaneDirection.UTURN, LaneDirection.parse("uturn"))
        assertNull(LaneDirection.parse("weird"))
    }

    @Test
    fun marksLanesForRightTurn() {
        val lanes = listOf(
            listOf("left", "straight"),
            listOf("straight"),
            listOf("right")
        )
        val plan = LanePlanner.plan(lanes, null, ManeuverKind.TURN_RIGHT)
        assertEquals(listOf(2), plan.activeIndices)
        assertTrue(plan.lanes[2].active)
        assertEquals("لاین سه", plan.recommendedText)
    }

    @Test
    fun marksMultipleLanesForStraight() {
        val lanes = listOf(
            listOf("left"),
            listOf("straight"),
            listOf("straight"),
            listOf("right")
        )
        val plan = LanePlanner.plan(lanes, null, ManeuverKind.CONTINUE)
        assertEquals(listOf(1, 2), plan.activeIndices)
        assertEquals("لاین دو و سه", plan.recommendedText)
    }

    @Test
    fun respectsInvalidLanes() {
        val lanes = listOf(listOf("right"), listOf("right"))
        val plan = LanePlanner.plan(lanes, listOf(true, false), ManeuverKind.TURN_RIGHT)
        assertEquals(listOf(0), plan.activeIndices)
    }

    @Test
    fun returnsNullWhenNoLaneMatches() {
        val plan = LanePlanner.plan(listOf(listOf("straight")), null, ManeuverKind.TURN_LEFT)
        assertTrue(plan.activeIndices.isEmpty())
        assertNull(plan.recommendedText)
    }

    @Test
    fun providesGlyphs() {
        for (d in LaneDirection.values()) {
            assertTrue(LanePlanner.directionGlyph(d).isNotBlank())
        }
    }
}
