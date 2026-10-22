package com.iranamap.real.core

enum class LaneDirection {
    LEFT, SLIGHT_LEFT, STRAIGHT, SLIGHT_RIGHT, RIGHT, UTURN;

    companion object {
        fun parse(raw: String?): LaneDirection? = when (raw?.lowercase()?.trim()) {
            "left" -> LEFT
            "slight left" -> SLIGHT_LEFT
            "straight", "through" -> STRAIGHT
            "slight right" -> SLIGHT_RIGHT
            "right" -> RIGHT
            "uturn", "u-turn", "reverse" -> UTURN
            else -> null
        }
    }
}

data class Lane(val directions: List<LaneDirection>, val valid: Boolean = true, val active: Boolean = false)

data class LanePlan(
    val lanes: List<Lane>,
    val activeIndices: List<Int>,
    val recommendedText: String?
)

object LanePlanner {

    /** لاین‌هایی که برای مانور بعدی مناسب‌اند. */
    fun plan(indications: List<List<String>>, validFlags: List<Boolean>? = null, maneuver: ManeuverKind): LanePlan {
        val lanes = indications.mapIndexed { index, raw ->
            val dirs = raw.mapNotNull { LaneDirection.parse(it) }.distinct()
            val valid = validFlags?.getOrNull(index) ?: true
            Lane(dirs, valid, false)
        }
        val wanted = wantedDirections(maneuver)
        val activeIndices = lanes.indices.filter { i ->
            lanes[i].valid && wants(lanes[i].directions, wanted)
        }
        val marked = lanes.mapIndexed { i, lane -> lane.copy(active = activeIndices.contains(i)) }
        return LanePlan(marked, activeIndices, recommendedText(activeIndices))
    }

    private fun wantedDirections(maneuver: ManeuverKind): Set<LaneDirection> = when (maneuver) {
        ManeuverKind.TURN_LEFT, ManeuverKind.SHARP_LEFT -> setOf(LaneDirection.LEFT, LaneDirection.SLIGHT_LEFT)
        ManeuverKind.SLIGHT_LEFT, ManeuverKind.FORK_LEFT -> setOf(LaneDirection.SLIGHT_LEFT, LaneDirection.LEFT)
        ManeuverKind.TURN_RIGHT, ManeuverKind.SHARP_RIGHT -> setOf(LaneDirection.RIGHT, LaneDirection.SLIGHT_RIGHT)
        ManeuverKind.SLIGHT_RIGHT, ManeuverKind.FORK_RIGHT, ManeuverKind.RAMP -> setOf(LaneDirection.SLIGHT_RIGHT, LaneDirection.RIGHT)
        ManeuverKind.UTURN -> setOf(LaneDirection.UTURN)
        ManeuverKind.STRAIGHT, ManeuverKind.CONTINUE, ManeuverKind.MERGE, ManeuverKind.ROUNDABOUT ->
            setOf(LaneDirection.STRAIGHT, LaneDirection.SLIGHT_LEFT, LaneDirection.SLIGHT_RIGHT)
        ManeuverKind.EXIT -> setOf(LaneDirection.SLIGHT_RIGHT, LaneDirection.RIGHT)
        else -> setOf(LaneDirection.STRAIGHT)
    }

    private fun wants(lanes: List<LaneDirection>, wanted: Set<LaneDirection>): Boolean =
        if (lanes.isEmpty()) false else lanes.any { wanted.contains(it) }

    fun recommendedText(activeIndices: List<Int>): String? {
        if (activeIndices.isEmpty()) return null
        val human = activeIndices.map { PersianText.numberToWords((it + 1).toLong()) }
        val joined = when (human.size) {
            1 -> human[0]
            2 -> "${human[0]} و ${human[1]}"
            else -> human.dropLast(1).joinToString("، ") + " و " + human.last()
        }
        return "لاین $joined"
    }

    /** ترجمه‌ی «چپ/راست» برای نمایش داخل هر لاین. */
    fun directionGlyph(dir: LaneDirection): String = when (dir) {
        LaneDirection.LEFT -> "⬅"
        LaneDirection.SLIGHT_LEFT -> "↖"
        LaneDirection.STRAIGHT -> "⬆"
        LaneDirection.SLIGHT_RIGHT -> "↗"
        LaneDirection.RIGHT -> "➡"
        LaneDirection.UTURN -> "⤶"
    }
}
