package com.iranamap.real.data

import com.iranamap.real.core.CameraKind
import com.iranamap.real.core.GeoMath
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.SpeedModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** یک دوربین کنترل سرعت یا چراغ قرمز روی نقشه. */
data class SpeedCamera(
    val id: Long,
    val point: GeoPoint,
    val kind: CameraKind,
    val maxspeedKmh: Int?,
    val name: String?
)

/**
 * دوربین‌ها و محدودیت سرعت را از OpenStreetMap می‌گیرد.
 * منابع OSM: `highway=speed_camera`, `enforcement=maxspeed|red_light`, `maxspeed` روی راه‌ها.
 */
class CameraRepository(private val api: OverpassApi = ApiClient.overpass) {

    private val cache = LinkedHashMap<String, Pair<Long, List<SpeedCamera>>>()

    /** null یعنی خطای ارتباط؛ فهرست خالی یعنی پاسخ معتبرِ بدون دوربین. */
    suspend fun camerasAround(center: GeoPoint, radiusMeters: Int = 4000): List<SpeedCamera>? =
        withContext(Dispatchers.IO) {
            val key = String.format(Locale.US, "%.2f,%.2f,%d", center.lat, center.lng, radiusMeters / 1000)
            cache[key]?.let { (stamp, list) ->
                if (System.currentTimeMillis() - stamp < 10 * 60 * 1000L) return@withContext list
            }
            val query = """
                [out:json][timeout:20];
                (
                  node["highway"="speed_camera"](around:$radiusMeters,${center.lat},${center.lng});
                  node["enforcement"="maxspeed"](around:$radiusMeters,${center.lat},${center.lng});
                  node["enforcement"="red_light"](around:$radiusMeters,${center.lat},${center.lng});
                  node["camera:type"="speed_camera"](around:$radiusMeters,${center.lat},${center.lng});
                );
                out body 80;
            """.trimIndent()
            val elements = try {
                // نبودن elements یک پاسخ ناقص است، نه فهرست خالیِ معتبر.
                api.query(query).elements ?: return@withContext null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // خطای شبکه «فهرست خالیِ معتبر» نیست و نباید ده دقیقه کش شود.
                return@withContext null
            }
            val cameras = elements.mapNotNull { element ->
                val lat = element.lat ?: return@mapNotNull null
                val lon = element.lon ?: return@mapNotNull null
                val tags = element.tags.orEmpty()
                val kind = when {
                    tags["enforcement"] == "red_light" || tags["camera:type"] == "red_light" -> CameraKind.RED_LIGHT
                    tags["enforcement"] == "average" || tags["camera:type"] == "average_speed" -> CameraKind.AVERAGE_SPEED
                    else -> CameraKind.SPEED
                }
                SpeedCamera(
                    id = element.id ?: 0L,
                    point = GeoPoint(lat, lon),
                    kind = kind,
                    maxspeedKmh = SpeedModel.parseMaxspeed(tags["maxspeed"]),
                    name = tags["name"]
                )
            }
            cache[key] = System.currentTimeMillis() to cameras
            if (cache.size > 16) cache.remove(cache.keys.first())
            cameras
        }

    /** نزدیک‌ترین دوربین روی مسیر تا فاصله‌ی مشخص. */
    fun nearestAhead(
        cameras: List<SpeedCamera>,
        from: GeoPoint,
        maxMeters: Double = 1200.0
    ): Pair<SpeedCamera, Double>? = cameras
        .map { it to GeoMath.distanceMeters(from, it.point) }
        .filter { it.second <= maxMeters }
        .minByOrNull { it.second }
}
