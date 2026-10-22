package com.iranamap.real.data

import com.iranamap.real.core.GeoMath
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.ManeuverText
import com.iranamap.real.nav.ActiveRoute
import com.iranamap.real.nav.NavStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** ساخت مسیر از سرویس OSRM و تبدیل آن به مدل داخلی. */
class RouteRepository(private val api: OsrmApi = ApiClient.osrm) {

    suspend fun fetchRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String? = null,
        waypoints: List<GeoPoint> = emptyList()
    ): Result<ActiveRoute> = withContext(Dispatchers.IO) {
        val points = buildList {
            add(origin)
            addAll(waypoints)
            add(destination)
        }
        val coordString = points.joinToString(";") {
            String.format(Locale.US, "%.6f,%.6f", it.lng, it.lat)
        }
        try {
            val response = runCatching { api.route(coordString) }
                .recoverCatching { ApiClient.osrmBackup.route(coordString) }
                .getOrElse { throw it }
            val route = response.routes?.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("مسیری پیدا نشد (${response.code})"))
            val path = route.geometry?.coordinates
                ?.mapNotNull { c ->
                    if (c.size >= 2) GeoPoint(c[1], c[0]) else null
                }
                ?.takeIf { it.size >= 2 }
                ?: return@withContext Result.failure(IllegalStateException("هندسه‌ی مسیر نامعتبر است"))

            val steps = buildSteps(route, path)
            Result.success(
                ActiveRoute(
                    points = path,
                    steps = steps,
                    totalMeters = route.distance,
                    totalSeconds = route.duration,
                    destinationName = destinationName
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * چند مسیر جایگزین برای انتخاب کاربر (معادل «مسیرهای جایگزین» در آمپ).
     * مسیر اول همیشه سریع‌ترین است؛ بقیه بر اساس زمان مرتب می‌شوند.
     */
    suspend fun fetchRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String? = null,
        alternatives: Boolean = true
    ): Result<List<ActiveRoute>> = withContext(Dispatchers.IO) {
        val coordString = listOf(origin, destination).joinToString(";") {
            String.format(Locale.US, "%.6f,%.6f", it.lng, it.lat)
        }
        try {
            val response = runCatching { api.route(coordString, alternatives = alternatives) }
                .recoverCatching { ApiClient.osrmBackup.route(coordString, alternatives = alternatives) }
                .getOrElse { throw it }
            val raw = response.routes.orEmpty()
            if (raw.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("مسیری پیدا نشد (${response.code})"))
            }
            val routes = raw.mapNotNull { route ->
                val path = route.geometry?.coordinates
                    ?.mapNotNull { c -> if (c.size >= 2) GeoPoint(c[1], c[0]) else null }
                    ?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                ActiveRoute(
                    points = path,
                    steps = buildSteps(route, path),
                    totalMeters = route.distance,
                    totalSeconds = route.duration,
                    destinationName = destinationName
                )
            }.sortedBy { it.totalSeconds }
            if (routes.isEmpty()) {
                Result.failure(IllegalStateException("هندسه‌ی مسیر نامعتبر است"))
            } else {
                Result.success(routes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** تخمین کلاس راه از نوع مانور/نام راه (OSRM کلاس راه را برنمی‌گرداند). */
    private fun roadClassOf(step: OsrmStep): String? {
        val ref = step.ref.orEmpty().uppercase()
        val name = step.name.orEmpty()
        return when {
            ref.startsWith("آزادراه") || ref.contains("MOTORWAY") -> "motorway"
            ref.startsWith("بزرگراه") -> "trunk"
            name.contains("آزادراه") -> "motorway"
            name.contains("بزرگراه") -> "trunk"
            name.contains("خیابان") || name.contains("بلوار") -> "secondary"
            name.contains("کوچه") -> "residential"
            else -> null
        }
    }

    private fun buildSteps(route: OsrmRoute, path: List<GeoPoint>): List<NavStep> {
        val flat = route.legs?.flatMap { it.steps ?: emptyList() } ?: emptyList()
        if (flat.isEmpty()) return emptyList()

        var along = 0.0
        val out = ArrayList<NavStep>(flat.size)
        for ((index, raw) in flat.withIndex()) {
            val kind = ManeuverText.kindOf(raw.maneuver?.type, raw.maneuver?.modifier)
            val location = raw.maneuver?.location?.let { if (it.size >= 2) GeoPoint(it[1], it[0]) else null }
            val lanes = raw.intersections?.firstOrNull()?.lanes
            val name = raw.name?.takeIf { it.isNotBlank() } ?: raw.ref?.takeIf { it.isNotBlank() }
            out.add(
                NavStep(
                    index = index,
                    kind = kind,
                    streetName = name,
                    exitNumber = raw.maneuver?.exit,
                    distanceMeters = raw.distance,
                    durationSeconds = raw.duration,
                    location = location,
                    laneIndications = lanes?.map { it.indications ?: emptyList() } ?: emptyList(),
                    laneValid = lanes?.map { it.valid ?: true } ?: emptyList(),
                    startAlongMeters = along,
                    maxspeed = null,
                    roadClass = roadClassOf(raw)
                )
            )
            along += raw.distance
        }

        // تصحیح مسافت‌های انباشته با طول واقعی هندسه‌ی مسیر
        val totalGeometry = GeoMath.cumulativeDistances(path).lastOrNull() ?: along
        if (along > 1.0 && totalGeometry > 1.0) {
            val scale = totalGeometry / along
            var running = 0.0
            for (i in out.indices) {
                val fixed = out[i].copy(startAlongMeters = running)
                out[i] = fixed
                running += out[i].distanceMeters * scale
            }
        }
        return out
    }
}
