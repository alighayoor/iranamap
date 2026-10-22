package com.iranamap.real.nav

import com.iranamap.real.core.GeoMath
import com.iranamap.real.core.GeoPoint
import kotlin.math.abs

/**
 * ردیاب مسیر: موقعیت خام GPS را روی مسیر منطبق می‌کند، پیشرفت، مانور بعدی و
 * خروج از مسیر را محاسبه می‌کند. کاملاً مستقل از اندروید و قابل تست واحد.
 */
class RouteTracker(
    private val route: ActiveRoute,
    private val offRouteThresholdMeters: Double = 45.0,
    private val offRouteConfirmations: Int = 3,
    private val arrivalRadiusMeters: Double = 25.0
) {
    private val cumulative: DoubleArray = GeoMath.cumulativeDistances(route.points)
    private val totalMeters: Double = if (cumulative.isEmpty()) 0.0 else cumulative.last()
    private var lastMatchedIndex = 0
    private var offRouteStreak = 0
    private var bearing = 0.0
    private val averageSpeed: Double =
        if (route.totalSeconds > 0) route.totalMeters / route.totalSeconds else 10.0

    fun reset() {
        lastMatchedIndex = 0
        offRouteStreak = 0
        bearing = 0.0
    }

    /** پیشرفت را با موقعیت فعلی (و سرعت اختیاری) به‌روزرسانی می‌کند. */
    fun update(point: GeoPoint, speedMps: Double? = null): TrackerState {
        val match = GeoMath.matchOnPath(point, route.points, lastMatchedIndex)
        var along = 0.0
        var snapped = point
        var offDistance = 0.0
        if (match != null) {
            along = match.distanceAlongMeters
            snapped = match.point
            offDistance = match.distanceMeters
            // پنجره‌ی جست‌وجو فقط با تطبیق معتبر جلو می‌رود تا پس از بازگشت به مسیر هم بازیابی شود
            if (offDistance <= offRouteThresholdMeters) {
                lastMatchedIndex = (match.segmentIndex - 2).coerceAtLeast(0)
            }
        }

        val previousSnapped = GeoMath.interpolateAlong(route.points, along - 5.0)
        if (previousSnapped != null && GeoMath.distanceMeters(previousSnapped, snapped) > 1.0) {
            bearing = GeoMath.bearingDegrees(previousSnapped, snapped)
        }

        val isOffRoute = offDistance > offRouteThresholdMeters
        if (isOffRoute) offRouteStreak++ else offRouteStreak = 0
        val confirmedOffRoute = offRouteStreak >= offRouteConfirmations

        val stepIndex = route.stepAt(along).coerceAtMost((route.steps.size - 1).coerceAtLeast(0))
        val step = route.steps.getOrNull(stepIndex)
        val nextStep = route.steps.getOrNull(stepIndex + 1) ?: step
        val nextEnd = if (stepIndex + 1 < route.steps.size) nextStep?.startAlongMeters ?: step?.endAlongMeters ?: totalMeters
        else totalMeters
        val distanceToManeuver = (nextEnd - along).coerceAtLeast(0.0)

        val remaining = (totalMeters - along).coerceAtLeast(0.0)
        val speed = speedMps?.takeIf { it > 1.0 } ?: averageSpeed
        val remainingSeconds = (remaining / speed.coerceAtLeast(2.0)).toLong()

        val atDestination = route.destination?.let { GeoMath.distanceMeters(it, point) } ?: Double.MAX_VALUE
        val arrived = atDestination < arrivalRadiusMeters || (remaining < 15.0 && atDestination < 60.0)

        return TrackerState(
            distanceAlong = along,
            remainingMeters = remaining,
            remainingSeconds = remainingSeconds,
            stepIndex = stepIndex,
            distanceToManeuverMeters = distanceToManeuver,
            stepLengthMeters = step?.distanceMeters ?: 0.0,
            offRoute = confirmedOffRoute,
            offRouteDistanceMeters = offDistance,
            arrived = arrived,
            snappedPoint = snapped,
            bearingDegrees = if (bearing.isNaN()) 0.0 else bearing,
            currentStreetName = step?.streetName
        )
    }

    /** زاویه‌ی مسیر در مسافت مشخص (برای چرخاندن دوربین). */
    fun bearingAt(meters: Double): Double {
        val a = GeoMath.interpolateAlong(route.points, (meters - 8.0).coerceAtLeast(0.0)) ?: return 0.0
        val b = GeoMath.interpolateAlong(route.points, meters + 8.0) ?: return 0.0
        if (GeoMath.distanceMeters(a, b) < 1.0) return 0.0
        return GeoMath.bearingDegrees(a, b)
    }

    /**
     * چراغ‌های راهنمایی که روی ادامه‌ی مسیر (جلوی خودرو) قرار دارند،
     * به همراه فاصله‌ی مسیر تا هر چراغ. چراغ‌های پشت سر حذف می‌شوند.
     */
    fun lightsAheadOnRoute(
        lights: List<Pair<Long, GeoPoint>>,
        fromAlongMeters: Double,
        maxAheadMeters: Double = 3000.0
    ): List<Triple<Long, GeoPoint, Double>> {
        if (route.points.size < 2) return emptyList()
        val out = ArrayList<Triple<Long, GeoPoint, Double>>()
        for ((id, p) in lights) {
            val m = GeoMath.matchOnPath(p, route.points, 0) ?: continue
            if (m.distanceMeters > 60.0) continue
            val ahead = m.distanceAlongMeters - fromAlongMeters
            if (ahead in 5.0..maxAheadMeters) out.add(Triple(id, p, ahead))
        }
        return out.sortedBy { it.third }
    }
}
