package com.iranamap.real.data

import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.LightTiming
import com.iranamap.real.core.TrafficLightModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class TrafficLight(
    val id: Long,
    val point: GeoPoint,
    val name: String?,
    val timing: LightTiming
)

/**
 * چراغ‌های راهنمایی را از OpenStreetMap (Overpass) می‌گیرد و برای هر چراغ
 * یک زمان‌بندی پایدار (قابل یادگیری) در نظر می‌گیرد.
 */
class TrafficLightRepository(private val api: OverpassApi = ApiClient.overpass) {

    private val cache = LinkedHashMap<String, Pair<Long, List<TrafficLight>>>()

    suspend fun lightsAround(center: GeoPoint, radiusMeters: Int = 2500): List<TrafficLight> =
        withContext(Dispatchers.IO) {
            val key = String.format(Locale.US, "%.3f,%.3f,%d", center.lat, center.lng, radiusMeters / 1000)
            val cached = cache[key]
            val now = System.currentTimeMillis()
            if (cached != null && now - cached.first < 10 * 60 * 1000L) return@withContext cached.second

            val query = """
                [out:json][timeout:20];
                node["highway"="traffic_signals"](around:$radiusMeters,${center.lat},${center.lng});
                out body;
            """.trimIndent()

            val elements = runCatching { api.query(query).elements }.getOrNull().orEmpty()
            val lights = elements.mapNotNull { el ->
                val lat = el.lat ?: return@mapNotNull null
                val lon = el.lon ?: return@mapNotNull null
                val point = GeoPoint(lat, lon)
                val seed = TrafficLightModel.seedFor(lat, lon, el.id)
                val arterial = (seed % 3 == 0)
                TrafficLight(
                    id = el.id ?: 0L,
                    point = point,
                    name = el.tags?.get("name"),
                    timing = TrafficLightModel.timingFor(seed, arterial)
                )
            }
            cache[key] = now to lights
            if (cache.size > 24) cache.remove(cache.keys.first())
            lights
        }
}
