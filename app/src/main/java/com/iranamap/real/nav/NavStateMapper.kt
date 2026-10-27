package com.iranamap.real.nav

import com.iranamap.real.core.ManeuverKind
import com.iranamap.real.core.NavState
import java.util.Calendar

/** تبدیل وضعیت ردیاب به وضعیت مورد نیاز سیاست اعلام صوتی. */
object NavStateMapper {

    /** فاصله‌ی ردیاب تا شروع گام بعدی است، پس جهت اعلامی نیز باید گام بعدی باشد. */
    fun upcomingStep(state: TrackerState, route: ActiveRoute?): NavStep? {
        val steps = route?.steps ?: return null
        return steps.getOrNull(state.stepIndex + 1) ?: steps.getOrNull(state.stepIndex)
    }

    /** [step] مانور پیشِ رو است، نه خیابانی که خودرو هم‌اکنون روی آن است. */
    fun toNavState(
        state: TrackerState,
        step: NavStep?,
        speedKmh: Double,
        nowMillis: Long,
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): NavState {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowMillis
        val secondsOfDay = (cal.get(Calendar.HOUR_OF_DAY) * 3600 +
            cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)).toLong()
        return NavState(
            stepIndex = state.stepIndex,
            maneuver = step?.kind ?: ManeuverKind.CONTINUE,
            streetName = step?.streetName?.takeIf { it.isNotBlank() } ?: state.currentStreetName,
            exitNumber = step?.exitNumber,
            distanceToManeuverMeters = state.distanceToManeuverMeters,
            stepLengthMeters = state.stepLengthMeters,
            speedKmh = speedKmh,
            remainingMeters = state.remainingMeters,
            remainingSeconds = state.remainingSeconds,
            offRoute = state.offRoute,
            arrived = state.arrived,
            secondsOfDay = secondsOfDay,
            nowMillis = nowMillis
        )
    }
}
