package com.iranamap.real.core

/**
 * مدل سرعت، محدودیت مجاز، دوربین‌های کنترل سرعت و تخمین ترافیک.
 * همه‌چیز خالص و آزمایش‌پذیر است (بدون اندروید).
 */
object SpeedModel {

    /** حد مجاز پیش‌فرض ایران بر اساس نوع راه. */
    private val IRAN_DEFAULTS = mapOf(
        "motorway" to 120,
        "trunk" to 100,
        "primary" to 95,
        "secondary" to 85,
        "tertiary" to 60,
        "residential" to 50,
        "living_street" to 30,
        "service" to 30,
        "unclassified" to 50
    )

    /**
     * تبدیل برچسب `maxspeed` عثمان (OSM) به عدد.
     * پشتیبانی: "50"، "50 km/h"، "30 mph"، "IR:urban"، "walk"، "none".
     */
    fun parseMaxspeed(raw: String?, fallbackRoadClass: String? = null): Int? {
        val text = raw?.trim()?.lowercase()
        if (text.isNullOrBlank() || text == "none" || text == "signals" || text == "variable") {
            return fallbackRoadClass?.let { IRAN_DEFAULTS[it] }
        }
        // مقادیر کشوری
        when (text) {
            "walk" -> return 5
            "ir:urban", "ir:urban_road" -> return 50
            "ir:rural", "ir:rural_road" -> return 85
            "ir:motorway", "ir:freeway" -> return 120
            "ir:trunk" -> return 100
        }
        // عدد + واحد
        val number = Regex("""(\d{1,3})(?:\s*[-–]\s*(\d{1,3}))?""").find(text) ?: run {
            return fallbackRoadClass?.let { IRAN_DEFAULTS[it] }
        }
        val value = (number.groupValues[2].ifBlank { number.groupValues[1] }).toIntOrNull() ?: return null
        val mph = text.contains("mph")
        val kmh = if (mph) (value * 1.60934).toInt() else value
        return kmh.takeIf { it in 5..160 }
    }

    fun defaultForRoadClass(roadClass: String?): Int? = roadClass?.let { IRAN_DEFAULTS[it] }

    /** آیا سرعت فعلی از حد مجاز گذشته است؟ */
    fun isOverLimit(speedKmh: Double, limitKmh: Int, toleranceKmh: Int = 5): Boolean =
        limitKmh > 0 && speedKmh > limitKmh + toleranceKmh

    /**
     * فاصله‌ی مناسب برای هشدار دوربین: هرچه سریع‌تر، زودتر هشدار.
     * ۵۰km/h → ۳۰۰متر، ۱۲۰km/h → ۷۲۰متر (حداقل ۲۵۰ و حداکثر ۹۰۰).
     */
    fun cameraWarningDistanceMeters(speedKmh: Double): Double =
        (speedKmh.coerceIn(20.0, 150.0) * 6.0).coerceIn(250.0, 900.0)

    /** آیا دوربین در محدوده‌ی هشدار است؟ */
    fun shouldWarnCamera(distanceMeters: Double, speedKmh: Double): Boolean =
        distanceMeters in 0.0..cameraWarningDistanceMeters(speedKmh)

    /**
     * ضریب ترافیک تخمینی بر اساس ساعت روز و نوع راه (الگوی شهری ایران).
     * این یک تخمین است، نه داده‌ی زنده — و همین را به کاربر هم می‌گوییم.
     */
    fun trafficFactor(roadClass: String?, secondsOfDay: Long): Double {
        val hour = ((secondsOfDay % 86_400) / 3600).toInt()
        val base = when (roadClass) {
            "motorway", "trunk" -> 1.0
            "primary" -> 1.12
            "secondary" -> 1.08
            else -> 1.05
        }
        val rush = when (hour) {
            in 6..8 -> 1.35      // صبح‌های کاری
            in 9..11 -> 1.15
            in 12..14 -> 1.20
            in 16..19 -> 1.45    // عصرهای شلوغ
            in 20..22 -> 1.20
            in 23..23 -> 0.85
            in 0..5 -> 0.85
            else -> 1.0
        }
        val weekend = false // در تقویم شمسی جمعه‌ها تعطیل‌اند؛ اینجا ساده نگه داشته می‌شود
        val adjusted = if (weekend) rush * 0.9 else rush
        return (base * adjusted).coerceIn(0.8, 1.8)
    }

    /** زمان رسیدن با در نظر گرفتن ترافیک تخمینی. */
    fun trafficAwareSeconds(baseSeconds: Double, roadClass: String?, secondsOfDay: Long): Double =
        baseSeconds * trafficFactor(roadClass, secondsOfDay)

    /** متن فارسی محدودیت سرعت برای نمایش و گفتار. */
    fun limitLabel(kmh: Int?): String = when {
        kmh == null || kmh <= 0 -> "—"
        else -> PersianText.toPersianDigits(kmh.toString())
    }

    /** پیام صوتی هشدار دوربین. */
    fun cameraSpeech(distanceMeters: Double, speedLimitKmh: Int?): String {
        val dist = PersianText.formatDistanceSpeech(distanceMeters)
        val limit = if (speedLimitKmh != null && speedLimitKmh > 0) {
            "، محدودیت ${PersianText.numberToWords(speedLimitKmh.toLong())} کیلومتر بر ساعت"
        } else ""
        return "دوربین سرعت در $dist$limit"
    }
}

/** نوع دوربین کنترل سرعت. */
enum class CameraKind(val fa: String) {
    SPEED("دوربین سرعت"),
    RED_LIGHT("دوربین چراغ قرمز"),
    AVERAGE_SPEED("دوربین سرعت میانگین")
}
