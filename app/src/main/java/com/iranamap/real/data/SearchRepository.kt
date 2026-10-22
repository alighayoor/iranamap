package com.iranamap.real.data

import com.iranamap.real.core.GeoMath
import com.iranamap.real.core.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class SearchResult(
    val title: String,
    val subtitle: String,
    val point: GeoPoint,
    val isCity: Boolean
)

/** جست‌وجوی مکان و آدرس با اولویت ایران و زبان فارسی. */
class SearchRepository(private val api: NominatimApi = ApiClient.nominatim) {

    suspend fun search(query: String, near: GeoPoint? = null): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            val viewBox = near?.let {
                val b = GeoMath.boundsAround(it, 60.0)
                String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", b.minLng, b.maxLat, b.maxLng, b.minLat)
            }
            val places = runCatching { api.search(query = q, viewBox = viewBox) }.getOrElse { emptyList() }
            places.mapNotNull { place ->
                val p = GeoPoint(place.latitude, place.longitude)
                if (!GeoMath.isPlausibleIranPoint(p)) return@mapNotNull null
                SearchResult(
                    title = place.title,
                    subtitle = place.subtitle,
                    point = p,
                    isCity = place.address?.containsKey("city") == true && place.name == place.address?.get("city")
                )
            }.distinctBy { "${it.title}|${"%.4f".format(Locale.US, it.point.lat)}|${"%.4f".format(Locale.US, it.point.lng)}" }
        }

    suspend fun streetNameAt(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        runCatching {
            val place = api.reverse(point.lat, point.lng)
            place?.address?.get("road") ?: place?.name
        }.getOrNull()
    }
}
