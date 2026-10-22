package com.iranamap.real.core

import kotlin.math.abs
import kotlin.math.roundToInt

enum class LightColor {
    RED, YELLOW, GREEN;

    val fa: String
        get() = when (this) {
            RED -> "قرمز"
            YELLOW -> "زرد"
            GREEN -> "سبز"
        }
}

/** زمان‌بندی تخمینی یک چراغ: سبز → زرد → قرمز. */
data class LightTiming(
    val greenSec: Int,
    val yellowSec: Int,
    val redSec: Int,
    val offsetSec: Int
) {
    val cycleSec: Int get() = (greenSec + yellowSec + redSec).coerceAtLeast(1)

    fun normalized(secondsOfDay: Long): Int {
        val c = cycleSec
        val v = (secondsOfDay + offsetSec.toLong()) % c
        return if (v < 0) (v + c).toInt() else v.toInt()
    }

    companion object {
        /** چراغ معمولی چهارراه شهری. */
        fun urban(seed: Int): LightTiming {
            val s = abs(seed)
            val green = 18 + (s % 14)
            val yellow = 3 + (s / 7 % 2)
            val red = 26 + (s / 13 % 26)
            val offset = s % (green + yellow + red)
            return LightTiming(green, yellow, red, offset)
        }

        /** چراغ تقاطع بزرگراهی/شریانی با زمان سبز بیشتر. */
        fun arterial(seed: Int): LightTiming {
            val s = abs(seed)
            val green = 32 + (s % 20)
            val yellow = 4
            val red = 30 + (s / 11 % 30)
            val offset = s % (green + yellow + red)
            return LightTiming(green, yellow, red, offset)
        }
    }
}

data class LightForecast(
    val color: LightColor,
    val remainingSec: Int,
    val timing: LightTiming
) {
    val nextColor: LightColor
        get() = when (color) {
            LightColor.GREEN -> LightColor.YELLOW
            LightColor.YELLOW -> LightColor.RED
            LightColor.RED -> LightColor.GREEN
        }
}

/** یک مشاهده‌ی کاربر از وضعیت چراغ؛ برای یادگیری زمان‌بندی واقعی. */
data class LightObservation(val secondsOfDay: Int, val color: LightColor)

object TrafficLightModel {

    /** عدد شبه‌تصادفی پایدار برای هر چراغ، بر پایه‌ی مکان و شناسه‌ی OSM. */
    fun seedFor(lat: Double, lng: Double, osmId: Long? = null): Int {
        var h = 2166136261L
        val latBits = (lat * 100000).roundToInt().toLong()
        val lngBits = (lng * 100000).roundToInt().toLong()
        val idBits = osmId ?: (latBits * 31 + lngBits * 17)
        for (b in longArrayOf(latBits, lngBits, idBits)) {
            var v = b
            for (i in 0 until 8) {
                h = h xor (v and 0xFF)
                h *= 16777619L
                v = v shr 8
            }
        }
        return ((h and 0x7FFFFFFF) % 1_000_003).toInt()
    }

    fun timingFor(seed: Int, arterial: Boolean): LightTiming =
        if (arterial) LightTiming.arterial(seed) else LightTiming.urban(seed)

    /** وضعیت لحظه‌ای چراغ در ثانیه‌ی مشخصی از شبانه‌روز. */
    fun stateAt(timing: LightTiming, secondsOfDay: Long): LightForecast {
        val t = timing.normalized(secondsOfDay)
        val g = timing.greenSec
        val y = timing.yellowSec
        return when {
            t < g -> LightForecast(LightColor.GREEN, (g - t).coerceAtLeast(1), timing)
            t < g + y -> LightForecast(LightColor.YELLOW, (g + y - t).coerceAtLeast(1), timing)
            else -> LightForecast(LightColor.RED, (timing.cycleSec - t).coerceAtLeast(1), timing)
        }
    }

    /** وضعیت چراغ در لحظه‌ای که خودرو به آن می‌رسد. */
    fun forecastAtArrival(timing: LightTiming, secondsOfDay: Long, travelSeconds: Double): LightForecast =
        stateAt(timing, secondsOfDay + travelSeconds.roundToInt().toLong())

    /** آیا در مسیر رسیدن، چراغ قرمز می‌شود؟ */
    fun willBeRedOnArrival(timing: LightTiming, secondsOfDay: Long, travelSeconds: Double): Boolean =
        forecastAtArrival(timing, secondsOfDay, travelSeconds).color != LightColor.GREEN

    /**
     * بهترین سرعت پیشنهادی (km/h) برای رسیدن به چراغ در وضعیت سبز، با فرض اینکه
     * محدوده‌ی سرعت بین minKmh و maxKmh باشد. اگر هیچ سرعتی به سبز نرسد، سرعت فعلی برگردانده می‌شود.
     */
    fun speedForGreen(timing: LightTiming, secondsOfDay: Long, distanceMeters: Double, minKmh: Int = 20, maxKmh: Int = 80, currentKmh: Int = 45): Int {
        var best: Int = currentKmh
        var bestScore = -1.0
        var kmh = minKmh
        while (kmh <= maxKmh) {
            val travelSec = distanceMeters / (kmh / 3.6)
            val f = forecastAtArrival(timing, secondsOfDay, travelSec)
            val score = when (f.color) {
                LightColor.GREEN -> 100.0 - abs(kmh - currentKmh)
                LightColor.YELLOW -> 30.0 - abs(kmh - currentKmh)
                LightColor.RED -> 10.0 - abs(kmh - currentKmh)
            }
            if (score > bestScore) {
                bestScore = score
                best = kmh
            }
            kmh += 5
        }
        return best
    }

    /** نسبت چراغ‌هایی که با سرعت مشخص، سبک خواهند بود. */
    fun greenRatioAtSpeed(lights: List<Pair<LightTiming, Double>>, secondsOfDay: Long, speedKmh: Double): Double {
        if (lights.isEmpty()) return 0.0
        val mps = speedKmh / 3.6
        var green = 0
        for ((timing, dist) in lights) {
            val travel = dist / mps.coerceAtLeast(1.0)
            if (forecastAtArrival(timing, secondsOfDay, travel).color == LightColor.GREEN) green++
        }
        return green.toDouble() / lights.size
    }

    /** آیا باید برای سرعت فعلی توصیه‌ی «توقف» داد؟ */
    fun shouldStop(forecast: LightForecast, speedKmh: Double, distanceMeters: Double): Boolean {
        if (forecast.color == LightColor.GREEN) return false
        val stoppingDistance = (speedKmh / 10.0) * (speedKmh / 10.0) * 0.6 + speedKmh / 3.6 * 1.2
        return distanceMeters < stoppingDistance * 2 && distanceMeters > 5
    }
}

/**
 * یادگیری زمان‌بندی واقعی چراغ از گزارش‌های کاربران (بدون سرور، فقط روی دستگاه).
 * چرخه‌ای را انتخاب می‌کند که بیشترین سازگاری را با مشاهدات دارد.
 */
object TrafficLightLearner {

    fun inferCycleSeconds(observations: List<LightObservation>, minCycle: Int = 30, maxCycle: Int = 180): Int? {
        if (observations.size < 6) return null
        var bestCycle: Int? = null
        var bestScore = -1
        for (cycle in minCycle..maxCycle) {
            var consistent = 0
            val buckets = HashMap<Int, MutableList<LightColor>>()
            for (o in observations) {
                val key = ((o.secondsOfDay % cycle) + cycle) % cycle
                buckets.getOrPut(key / 5) { mutableListOf() }.add(o.color)
            }
            for ((_, colors) in buckets) {
                val majority = colors.groupingBy { it }.eachCount().maxByOrNull { it.value }?.value ?: 0
                consistent += majority
            }
            val score = consistent * 1000 / observations.size - kotlin.math.abs(cycle - 90) / 10
            if (score > bestScore) {
                bestScore = score
                bestCycle = cycle
            }
        }
        return bestCycle
    }

    /** برآورد طول سبز و قرمز برای یک چراغ از مشاهدات. */
    fun inferTiming(observations: List<LightObservation>, seed: Int): LightTiming {
        if (observations.isEmpty()) return LightTiming.urban(seed)
        val cycle = inferCycleSeconds(observations) ?: return LightTiming.urban(seed)
        val counts = observations.groupingBy { it.color }.eachCount()
        val total = observations.size.toDouble()
        val green = ((counts[LightColor.GREEN] ?: 0) / total * cycle).roundToInt().coerceAtLeast(8)
        val yellow = ((counts[LightColor.YELLOW] ?: 0) / total * cycle).roundToInt().coerceIn(2, 8)
        val red = (cycle - green - yellow).coerceAtLeast(8)
        val firstGreen = observations.firstOrNull { it.color == LightColor.GREEN }?.secondsOfDay ?: 0
        val offset = ((-firstGreen) % (green + yellow + red) + (green + yellow + red)) % (green + yellow + red)
        return LightTiming(green, yellow, red, offset)
    }
}
