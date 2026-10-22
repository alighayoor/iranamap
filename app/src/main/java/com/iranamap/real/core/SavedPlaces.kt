package com.iranamap.real.core

/** یک مکان ذخیره‌شده (خانه/کار/دلخواه). */
data class SavedPlace(
    val id: String,
    val label: String,
    val address: String,
    val point: GeoPoint,
    val icon: String = "⭐",
    val createdAtMillis: Long = 0L
) {
    val shortLabel: String get() = label.ifBlank { address.ifBlank { "مکان ذخیره‌شده" } }
}

/** یک ردیف تاریخچه‌ی مقصد. */
data class HistoryEntry(
    val id: String,
    val label: String,
    val address: String,
    val point: GeoPoint,
    val visitedAtMillis: Long
)

/** نشانگر تبدیل نام به شناسه‌ی پایدار برای ذخیره‌سازی. */
object SavedPlaceIds {
    fun from(point: GeoPoint): String =
        String.format(java.util.Locale.US, "%.5f_%.5f", point.lat, point.lng)
}

/**
 * منبع واحد مکان‌های پیش‌فرض (خانه/کار) برای استفاده در صفحه‌ی ذخیره‌ها.
 * مقدار واقعی از تنظیمات/دیتابیس می‌آید؛ اینجا فقط برچسب‌های فارسی نگه داشته می‌شود.
 */
object SavedLabels {
    const val HOME = "خانه"
    const val WORK = "محل کار"
    val presets: List<String> = listOf(HOME, WORK)
}
