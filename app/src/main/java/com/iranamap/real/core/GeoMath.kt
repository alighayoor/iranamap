package com.iranamap.real.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lng: Double)

data class GeoBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
) {
    val centerLat: Double get() = (minLat + maxLat) / 2.0
    val centerLng: Double get() = (minLng + maxLng) / 2.0
    fun contains(p: GeoPoint): Boolean = p.lat in minLat..maxLat && p.lng in minLng..maxLng
    fun padded(factor: Double): GeoBounds {
        val dLat = (maxLat - minLat) * factor
        val dLng = (maxLng - minLng) * factor
        return GeoBounds(minLat - dLat, minLng - dLng, maxLat + dLat, maxLng + dLng)
    }
    override fun toString(): String = "%.5f,%.5f,%.5f,%.5f".format(minLng, minLat, maxLng, maxLat)
}

data class PathMatch(
    val point: GeoPoint,
    val distanceMeters: Double,
    val segmentIndex: Int,
    val distanceAlongMeters: Double
)

object GeoMath {

    const val EARTH_RADIUS_M = 6371008.8

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** زاویه‌ی ناوبری از a به b بر حسب درجه (۰ شمال، ۹۰ شرق). */
    fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** تفاضل زاویه‌ای علامت‌دار در بازه‌ی ‎-۱۸۰ تا ‎+۱۸۰. */
    fun angleDelta(from: Double, to: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    fun project(start: GeoPoint, distanceMeters: Double, bearingDeg: Double): GeoPoint {
        val br = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(start.lat)
        val lng1 = Math.toRadians(start.lng)
        val dr = distanceMeters / EARTH_RADIUS_M
        val lat2 = asin(sin(lat1) * cos(dr) + cos(lat1) * sin(dr) * cos(br))
        val lng2 = lng1 + atan2(sin(br) * sin(dr) * cos(lat1), cos(dr) - sin(lat1) * sin(lat2))
        return GeoPoint(Math.toDegrees(lat2), normalizeLng(Math.toDegrees(lng2)))
    }

    fun normalizeLng(lng: Double): Double {
        var l = lng
        while (l > 180.0) l -= 360.0
        while (l < -180.0) l += 360.0
        return l
    }

    /**
     * تصویر نقطه روی پاره‌خط a-b با تقریب مسطح محلی (دقت چند سانتی‌متر برای فواصل شهری).
     * t=0 یعنی روی a و t=1 یعنی روی b.
     */
    fun projectOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Triple<Double, Double, GeoPoint> {
        val latRef = Math.toRadians((a.lat + b.lat) / 2.0)
        val kx = 111320.0 * cos(latRef)
        val ky = 110540.0
        val ax = 0.0
        val ay = 0.0
        val bx = (b.lng - a.lng) * kx
        val by = (b.lat - a.lat) * ky
        val px = (p.lng - a.lng) * kx
        val py = (p.lat - a.lat) * ky
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq <= 1e-9) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        val snapped = GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
        return Triple(distanceMeters(p, snapped), t, snapped)
    }

    fun cumulativeDistances(path: List<GeoPoint>): DoubleArray {
        val out = DoubleArray(path.size)
        for (i in 1 until path.size) {
            out[i] = out[i - 1] + distanceMeters(path[i - 1], path[i])
        }
        return out
    }

    /** نزدیک‌ترین نقطه‌ی مسیر به موقعیت فعلی، همراه با فاصله‌ی انحراف و مسافت پیموده‌شده روی مسیر. */
    fun matchOnPath(p: GeoPoint, path: List<GeoPoint>, searchFromIndex: Int = 0): PathMatch? {
        if (path.size < 2) return null
        var best: PathMatch? = null
        val cum = cumulativeDistances(path)
        val start = searchFromIndex.coerceIn(0, path.size - 2)
        for (i in start until path.size - 1) {
            val (dist, t, snapped) = projectOnSegment(p, path[i], path[i + 1])
            if (best == null || dist < best!!.distanceMeters) {
                val along = cum[i] + (cum[i + 1] - cum[i]) * t
                best = PathMatch(snapped, dist, i, along)
            }
        }
        return best
    }

    fun remainingDistance(path: List<GeoPoint>, fromIndex: Int, fromPoint: GeoPoint): Double {
        if (path.isEmpty()) return 0.0
        if (path.size == 1) return distanceMeters(fromPoint, path[0])
        val idx = fromIndex.coerceIn(0, path.size - 2)
        val m = matchOnPath(fromPoint, path, idx) ?: return 0.0
        val total = cumulativeDistances(path).last()
        return (total - m.distanceAlongMeters).coerceAtLeast(0.0)
    }

    fun boundsOf(points: List<GeoPoint>): GeoBounds? {
        if (points.isEmpty()) return null
        var minLat = points[0].lat
        var maxLat = points[0].lat
        var minLng = points[0].lng
        var maxLng = points[0].lng
        for (p in points) {
            minLat = minOf(minLat, p.lat); maxLat = maxOf(maxLat, p.lat)
            minLng = minOf(minLng, p.lng); maxLng = maxOf(maxLng, p.lng)
        }
        return GeoBounds(minLat, minLng, maxLat, maxLng)
    }

    fun boundsAround(center: GeoPoint, radiusKm: Double): GeoBounds {
        val dLat = radiusKm / 110.574
        val dLng = radiusKm / (111.320 * cos(Math.toRadians(center.lat)).coerceAtLeast(0.01))
        return GeoBounds(center.lat - dLat, center.lng - dLng, center.lat + dLat, center.lng + dLng)
    }

    fun zoomForRadius(radiusKm: Double): Double = when {
        radiusKm < 3 -> 14.0
        radiusKm < 8 -> 13.0
        radiusKm < 18 -> 12.0
        radiusKm < 40 -> 11.0
        radiusKm < 90 -> 10.0
        radiusKm < 200 -> 9.0
        else -> 8.0
    }

    fun isPlausibleIranPoint(p: GeoPoint): Boolean =
        p.lat in 24.0..40.5 && p.lng in 43.5..64.5

    fun interpolateAlong(path: List<GeoPoint>, meters: Double): GeoPoint? {
        if (path.isEmpty()) return null
        if (path.size == 1) return path[0]
        val cum = cumulativeDistances(path)
        val target = meters.coerceIn(0.0, cum.last())
        var i = 1
        while (i < cum.size && cum[i] < target) i++
        if (i >= cum.size) return path.last()
        val segLen = cum[i] - cum[i - 1]
        val t = if (segLen <= 1e-6) 0.0 else (target - cum[i - 1]) / segLen
        return GeoPoint(
            path[i - 1].lat + (path[i].lat - path[i - 1].lat) * t,
            path[i - 1].lng + (path[i].lng - path[i - 1].lng) * t
        )
    }

    // ---------- ابزار کاشی (Web Mercator) برای برنامه‌ریزی دانلود آفلاین ----------
    fun lonToTileX(lng: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val y = (1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n - 1)
    }

    fun tileCount(b: GeoBounds, zoom: Int): Int {
        val x0 = lonToTileX(minOf(b.minLng, b.maxLng), zoom)
        val x1 = lonToTileX(maxOf(b.minLng, b.maxLng), zoom)
        val y0 = latToTileY(maxOf(b.minLat, b.maxLat), zoom)
        val y1 = latToTileY(minOf(b.minLat, b.maxLat), zoom)
        return (abs(x1 - x0) + 1) * (abs(y1 - y0) + 1)
    }
}
