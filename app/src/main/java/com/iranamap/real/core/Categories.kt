package com.iranamap.real.core

/**
 * دسته‌بندی‌های جست‌وجوی مکانی (معادل «附近» در آمپ).
 * هر دسته به تگ‌های OpenStreetMap و یک کوئری Overpass نگاشت می‌شود.
 * این فایل کاملاً خالص است تا بدون اندروید هم آزمایش شود.
 */
data class PoiCategory(
    val id: String,
    val titleFa: String,
    /** بخش شرط کوئری Overpass؛ برای چند حالت، با «|» جدا می‌شود. */
    val overpassFilter: String,
    /** عبارت جست‌وجوی متنی جایگزین (Nominatim) وقتی Overpass چیزی برنگرداند. */
    val textQuery: String,
    val glyph: String
) {
    /** آیا این دسته از نوع «محوطه/ساختمان» است (به‌جای نقطه)؟ */
    val isArea: Boolean get() = id in AREA_IDS

    companion object {
        private val AREA_IDS = setOf(
            "parking", "hospital", "hotel", "attraction", "park", "airport", "mosque"
        )
    }
}

object Categories {

    val all: List<PoiCategory> = listOf(
        PoiCategory("fuel", "پمپ بنزین", "nwr[\"amenity\"=\"fuel\"]", "پمپ بنزین", "⛽"),
        PoiCategory("fuel_lpg", "پمپ CNG", "nwr[\"amenity\"=\"fuel\"][\"fuel:lpg\"=\"yes\"]", "پمپ گاز", "🛢"),
        PoiCategory("parking", "پارکینگ", "nwr[\"amenity\"=\"parking\"]", "پارکینگ", "🅿"),
        PoiCategory("charging", "شارژ خودروی برقی", "nwr[\"amenity\"=\"charging_station\"]", "شارژ خودرو برقی", "🔌"),
        PoiCategory("repair", "تعمیرگاه خودرو", "nwr[\"shop\"=\"car_repair\"]", "تعمیرگاه خودرو", "🔧"),
        PoiCategory("car_wash", "کارواش", "nwr[\"shop\"=\"car_wash\"]", "کارواش", "🚿"),
        PoiCategory("hospital", "بیمارستان", "nwr[\"amenity\"=\"hospital\"]", "بیمارستان", "🏥"),
        PoiCategory("clinic", "درمانگاه", "nwr[\"amenity\"=\"clinic\"]", "درمانگاه", "🩺"),
        PoiCategory("pharmacy", "داروخانه", "nwr[\"amenity\"=\"pharmacy\"]", "داروخانه", "💊"),
        PoiCategory("restaurant", "رستوران", "nwr[\"amenity\"=\"restaurant\"]", "رستوران", "🍽"),
        PoiCategory("cafe", "کافه", "nwr[\"amenity\"=\"cafe\"]", "کافه", "☕"),
        PoiCategory("fast_food", "فست‌فود", "nwr[\"amenity\"=\"fast_food\"]", "فست فود", "🍔"),
        PoiCategory("hotel", "هتل", "nwr[\"tourism\"=\"hotel\"]", "هتل", "🏨"),
        PoiCategory("attraction", "جاذبه‌ی گردشگری", "nwr[\"tourism\"~\"attraction|museum|viewpoint\"]", "جاذبه گردشگری", "🏛"),
        PoiCategory("bank", "بانک", "nwr[\"amenity\"=\"bank\"]", "بانک", "🏦"),
        PoiCategory("atm", "خودپرداز", "nwr[\"amenity\"=\"atm\"]", "خودپرداز", "💳"),
        PoiCategory("supermarket", "سوپرمارکت", "nwr[\"shop\"=\"supermarket\"]", "سوپرمارکت", "🛒"),
        PoiCategory("bakery", "نانوایی", "nwr[\"shop\"=\"bakery\"]", "نانوایی", "🥖"),
        PoiCategory("mosque", "مسجد", "nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]", "مسجد", "🕌"),
        PoiCategory("metro", "ایستگاه مترو", "nwr[\"railway\"=\"station\"][\"station\"=\"subway\"]", "ایستگاه مترو", "🚇"),
        PoiCategory("bus", "ایستگاه اتوبوس", "nwr[\"highway\"=\"bus_stop\"]", "ایستگاه اتوبوس", "🚌"),
        PoiCategory("taxi", "تاکسی", "nwr[\"amenity\"=\"taxi\"]", "ایستگاه تاکسی", "🚕"),
        PoiCategory("airport", "فرودگاه", "nwr[\"aeroway\"=\"aerodrome\"]", "فرودگاه", "✈"),
        PoiCategory("train", "ایستگاه قطار", "nwr[\"railway\"=\"station\"][\"station\"!=\"subway\"]", "ایستگاه قطار", "🚆"),
        PoiCategory("police", "کلانتری", "nwr[\"amenity\"=\"police\"]", "کلانتری", "🚓"),
        PoiCategory("park", "پارک", "nwr[\"leisure\"=\"park\"]", "پارک", "🌳"),
        PoiCategory("toilet", "سرویس بهداشتی", "nwr[\"amenity\"=\"toilets\"]", "سرویس بهداشتی", "🚻"),
        PoiCategory("post", "اداره پست", "nwr[\"amenity\"=\"post_office\"]", "اداره پست", "📮")
    )

    /** دسته‌های پرمصرف که در نوار افقی صفحه‌ی جست‌وجو نشان داده می‌شوند. */
    val quick: List<PoiCategory> = listOf(
        "fuel", "parking", "charging", "hospital", "pharmacy", "restaurant",
        "cafe", "hotel", "attraction", "bank", "atm", "supermarket", "mosque",
        "metro", "bus", "park"
    ).mapNotNull { find(it) }

    fun find(id: String?): PoiCategory? = all.firstOrNull { it.id == id }

    fun quickByIds(ids: List<String>): List<PoiCategory> = ids.mapNotNull { find(it) }

    /**
     * ساخت کوئری Overpass برای یافتن دسته در شعاع مشخص.
     * خروجی: `nwr[...](around:رادیومتر,lat,lng);out center 60;`
     */
    fun overpassQuery(category: PoiCategory, center: GeoPoint, radiusMeters: Int, limit: Int = 60): String {
        val r = radiusMeters.coerceIn(100, 20_000)
        val lat = format(center.lat)
        val lng = format(center.lng)
        val filters = category.overpassFilter.split(";").filter { it.isNotBlank() }
        val body = filters.joinToString("\n  ") { f ->
            val (kind, tags) = splitFilter(f)
            "$kind$tags(around:$r,$lat,$lng);"
        }
        return "[out:json][timeout:25];\n(\n  $body\n);\nout center $limit;"
    }

    /** شمارش دسته‌ها برای آمار/آزمون. */
    val size: Int get() = all.size

    private fun splitFilter(filter: String): Pair<String, String> {
        val idx = filter.indexOf('[')
        return if (idx <= 0) filter to "" else filter.substring(0, idx) to filter.substring(idx)
    }

    private fun format(v: Double): String = String.format(java.util.Locale.US, "%.5f", v)
}
