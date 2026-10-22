package com.iranamap.real.nav

import com.iranamap.real.core.GeoBounds
import com.iranamap.real.core.GeoMath
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.ManeuverKind

/** یک گام (مانور) از مسیر؛ مستقل از اندروید تا قابل تست باشد. */
data class NavStep(
    val index: Int,
    val kind: ManeuverKind,
    val streetName: String?,
    val exitNumber: Int?,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val location: GeoPoint?,
    val laneIndications: List<List<String>> = emptyList(),
    val laneValid: List<Boolean> = emptyList(),
    /** مسافت انباشته روی مسیر تا شروع این گام. */
    val startAlongMeters: Double = 0.0,
    /** برچسب maxspeed خام OSM برای این گام (اگر موجود باشد). */
    val maxspeed: String? = null,
    /** کلاس راه (motorway/primary/residential/…) برای تخمین حد مجاز و ترافیک. */
    val roadClass: String? = null
) {
    val endAlongMeters: Double get() = startAlongMeters + distanceMeters
}

/** مسیر فعال برای ناوبری. */
data class ActiveRoute(
    val points: List<GeoPoint>,
    val steps: List<NavStep>,
    val totalMeters: Double,
    val totalSeconds: Double,
    val destinationName: String?,
    val trafficLightCount: Int = 0
) {
    val bounds: GeoBounds? get() = GeoMath.boundsOf(points)
    val destination: GeoPoint? get() = points.lastOrNull()

    fun stepAt(distanceAlong: Double): Int {
        if (steps.isEmpty()) return 0
        val i = steps.indexOfLast { distanceAlong >= it.startAlongMeters }
        return i.coerceAtLeast(0)
    }
}

/** خروجی محاسبه‌ی پیشرفت ناوبری برای هر موقعیت جدید. */
data class TrackerState(
    val distanceAlong: Double,
    val remainingMeters: Double,
    val remainingSeconds: Long,
    val stepIndex: Int,
    val distanceToManeuverMeters: Double,
    val stepLengthMeters: Double,
    val offRoute: Boolean,
    val offRouteDistanceMeters: Double,
    val arrived: Boolean,
    val snappedPoint: GeoPoint,
    val bearingDegrees: Double,
    val currentStreetName: String?
)
