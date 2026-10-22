package com.iranamap.real.data

import com.iranamap.real.core.Categories
import com.iranamap.real.core.GeoMath
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.PoiCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** یک نقطه‌ی مورد علاقه‌ی یافته‌شده در نزدیکی (پمپ بنزین، داروخانه، …). */
data class PoiResult(
    val id: String,
    val name: String,
    val categoryId: String,
    val categoryTitle: String,
    val glyph: String,
    val point: GeoPoint,
    val address: String?,
    val distanceMeters: Double
)

/**
 * جست‌وجوی دسته‌بندی‌شده (معادل «附近 / دسته‌بندی» در آمپ) با Overpass،
 * و در صورت نبود نتیجه، جست‌وجوی متنی با Nominatim.
 */
class PoiRepository(
    private val overpass: OverpassApi = ApiClient.overpass,
    private val nominatim: NominatimApi = ApiClient.nominatim
) {

    private val cache = HashMap<String, Pair<Long, List<PoiResult>>>()

    suspend fun byCategory(
        category: PoiCategory,
        center: GeoPoint,
        radiusMeters: Int = 2000,
        limit: Int = 50
    ): List<PoiResult> = withContext(Dispatchers.IO) {
        val key = "${category.id}-${"%.3f".format(java.util.Locale.US, center.lat)}-" +
            "${"%.3f".format(java.util.Locale.US, center.lng)}-$radiusMeters"
        cache[key]?.let { (stamp, list) ->
            if (System.currentTimeMillis() - stamp < 5 * 60 * 1000L) return@withContext list
        }

        val query = Categories.overpassQuery(category, center, radiusMeters, limit)
        val elements = runCatching { overpass.query(query).elements }.getOrNull().orEmpty()
        var results = elements.mapNotNull { element ->
            val lat = element.lat ?: return@mapNotNull null
            val lon = element.lon ?: return@mapNotNull null
            val point = GeoPoint(lat, lon)
            val tags = element.tags.orEmpty()
            val name = tags["name:fa"] ?: tags["name"] ?: tags["name:en"] ?: category.titleFa
            PoiResult(
                id = "${element.type.orEmpty()}/${element.id ?: 0L}",
                name = name,
                categoryId = category.id,
                categoryTitle = category.titleFa,
                glyph = category.glyph,
                point = point,
                address = listOfNotNull(tags["addr:street"], tags["addr:city"]).joinToString("، ")
                    .ifBlank { null },
                distanceMeters = GeoMath.distanceMeters(center, point)
            )
        }.sortedBy { it.distanceMeters }

        if (results.isEmpty()) {
            // جایگزین: جست‌وجوی متنی فارسی
            results = runCatching {
                nominatim.search(query = "${category.textQuery} ${"%.3f".format(java.util.Locale.US, center.lat)},${"%.3f".format(java.util.Locale.US, center.lng)}")
            }.getOrNull().orEmpty().mapNotNull { place ->
                if (place.latitude == 0.0 && place.longitude == 0.0) return@mapNotNull null
                val p = GeoPoint(place.latitude, place.longitude)
                PoiResult(
                    id = "n/${place.place_id ?: 0L}",
                    name = place.title,
                    categoryId = category.id,
                    categoryTitle = category.titleFa,
                    glyph = category.glyph,
                    point = p,
                    address = place.subtitle,
                    distanceMeters = GeoMath.distanceMeters(center, p)
                )
            }.filter { it.distanceMeters <= radiusMeters * 1.5 }
        }

        cache[key] = System.currentTimeMillis() to results
        results
    }

    fun clearCache() = cache.clear()
}
