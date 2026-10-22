package com.iranamap.real.data

/** پاسخ مسیریابی OSRM */
data class OsrmResponse(
    val code: String? = null,
    val routes: List<OsrmRoute>? = null
)

data class OsrmRoute(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val geometry: OsrmGeometry? = null,
    val legs: List<OsrmLeg>? = null
)

data class OsrmLeg(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val steps: List<OsrmStep>? = null
)

data class OsrmStep(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val name: String? = null,
    val ref: String? = null,
    val maneuver: OsrmManeuver? = null,
    val geometry: OsrmGeometry? = null,
    val intersections: List<OsrmIntersection>? = null
)

data class OsrmManeuver(
    val type: String? = null,
    val modifier: String? = null,
    val exit: Int? = null,
    val location: List<Double>? = null,
    val bearing_before: Int? = null,
    val bearing_after: Int? = null
)

data class OsrmIntersection(
    val lanes: List<OsrmLane>? = null,
    val classes: List<String>? = null
)

data class OsrmLane(
    val indications: List<String>? = null,
    val valid: Boolean? = null
)

data class OsrmGeometry(
    val type: String? = null,
    val coordinates: List<List<Double>>? = null
)

/** نتیجه‌ی جست‌وجوی Nominatim */
data class NominatimPlace(
    val place_id: Long? = null,
    val display_name: String? = null,
    val name: String? = null,
    val lat: String? = null,
    val lon: String? = null,
    val type: String? = null,
    val category: String? = null,
    val address: Map<String, String>? = null
) {
    val latitude: Double get() = lat?.toDoubleOrNull() ?: 0.0
    val longitude: Double get() = lon?.toDoubleOrNull() ?: 0.0
    val title: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: address?.get("road")
            ?: display_name?.substringBefore(",")
            ?: "نقطه‌ی بدون نام"
    val subtitle: String
        get() {
            val parts = buildList {
                address?.get("city")?.let { add(it) }
                address?.get("state")?.let { add(it) }
                if (isEmpty()) display_name?.split(",")?.drop(1)?.take(2)?.joinToString("، ")?.trim()?.let { add(it) }
            }
            return parts.joinToString("، ")
        }
}

/** پاسخ Overpass برای چراغ‌های راهنمایی */
data class OverpassResponse(
    val elements: List<OverpassElement>? = null
)

data class OverpassElement(
    val id: Long? = null,
    val type: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val tags: Map<String, String>? = null
)
