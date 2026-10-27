package com.iranamap.real.data

import com.iranamap.real.core.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class TrafficLight(
    val id: Long,
    val point: GeoPoint,
    val name: String?
)

/** موقعیت چراغ‌ها از OpenStreetMap است؛ OSM رنگ یا زمان‌بندی زنده ارائه نمی‌کند. */
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
                TrafficLight(
                    id = el.id ?: 0L,
                    point = GeoPoint(lat, lon),
                    name = el.tags?.get("name")
                )
            }
            cache[key] = now to lights
            if (cache.size > 24) cache.remove(cache.keys.first())
            lights
        }
}
