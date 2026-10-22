package com.iranamap.real.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * ابزارهای متن فارسی: ارقام، نرمال‌سازی، عدد به حروف و قالب‌بندی فاصله/زمان.
 * این کلاس کاملاً مستقل از اندروید است تا بتوان آن را با تست‌های JVM سنجید.
 */
object PersianText {

    const val ZWNJ = "\u200C"
    private val FA_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val AR_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    private val ONES = arrayOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    private val TEENS = arrayOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    private val TENS = arrayOf("", "ده", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val HUNDREDS = arrayOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    private val SCALES = arrayOf("", "هزار", "میلیون", "میلیارد", "تیلیون")

    /** کلمات کلیدی که هنگام گفتار باید باز شوند: خ. → خیابان */
    private val SPEECH_EXPANSIONS = mapOf(
        "خ." to "خیابان ",
        "خیاب." to "خیابان ",
        "بل." to "بلوار ",
        "بلو." to "بلوار ",
        "مید." to "میدان ",
        "م." to "میدان ",
        "بزرگر." to "بزرگراه ",
        "ک." to "کوچه ",
        "کوچ." to "کوچه ",
        "ب." to "بزرگراه ",
        "ج." to "جاده ",
        "نبش." to "نبش ",
        "پل." to "پل ",
        "تق." to "تقاطع ",
        "فاز" to "فاز"
    )

    /** جایگزینی نشانه‌ها با معادل گفتاری. */
    private val SPEECH_SYMBOLS = mapOf(
        "/" to " و ",
        "+" to " و ",
        "&" to " و ",
        "%" to " درصد ",
        "•" to " و ",
        "->" to " به ",
        "←" to " به ",
        "→" to " به "
    )

    fun toPersianDigits(input: String): String = buildString(input.length) {
        for (c in input) {
            append(
                when {
                    c in '0'..'9' -> FA_DIGITS[c - '0']
                    c == '.' -> '٫'
                    c == ',' -> '٬'
                    else -> c
                }
            )
        }
    }

    fun toEnglishDigits(input: String): String = buildString(input.length) {
        for (c in input) {
            when {
                c in FA_DIGITS -> append('0' + FA_DIGITS.indexOf(c))
                c in AR_DIGITS -> append('0' + AR_DIGITS.indexOf(c))
                c == '٫' -> append('.')
                c == '٬' -> append(',')
                else -> append(c)
            }
        }
    }

    /** اصلاح نویسه‌های عربی، حذف اعراب و فاصله‌های اضافی. */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (raw in input) {
            val c = when (raw) {
                '\u064A', '\u0649' -> '\u06CC' // ي/ى → ی
                '\u0643' -> '\u06A9'           // ك → ک
                '\u06C0' -> '\u0647'           // ۀ → ه
                '\u0629' -> '\u0647'           // ة → ه
                '\u0640' -> ' '                // کشیده
                in '\u064B'..'\u0652' -> ' '   // اعراب
                in '\u200B'..'\u200F' -> if (raw == '\u200C') raw else ' '
                '\u00A0' -> ' '
                else -> raw
            }
            sb.append(c)
        }
        var out = sb.toString()
        while (out.contains("  ")) out = out.replace("  ", " ")
        return out.trim()
    }

    /** متن نهایی که به موتور گفتار داده می‌شود: بازکردن خلاصه‌نویسی‌ها و خواندن اعداد به حروف. */
    fun forSpeech(input: String): String {
        var s = normalize(input)
        for ((k, v) in SPEECH_EXPANSIONS) s = s.replace(k, v)
        for ((k, v) in SPEECH_SYMBOLS) s = s.replace(k, v)
        s = s.replace(Regex("[\\uE000-\\uF8FF\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0F]"), " ")
        s = digitsToWords(s)
        while (s.contains("  ")) s = s.replace("  ", " ")
        return s.trim(' ', '.', '،', '-')
    }

    /** هر دنباله‌ی رقمی را به حروف فارسی تبدیل می‌کند؛ اعداد کسری مانند «۲٫۴» جداگانه خوانده می‌شوند. */
    fun digitsToWords(input: String): String {
        val english = toEnglishDigits(input)
        val sb = StringBuilder()
        var i = 0
        while (i < english.length) {
            val c = english[i]
            if (c.isDigit()) {
                var j = i
                while (j < english.length && english[j].isDigit()) j++
                val intPart = english.substring(i, j)
                var frac = ""
                var k = j
                if (k + 1 < english.length && (english[k] == '.' || english[k] == '٫') && english[k + 1].isDigit()) {
                    k++
                    val start = k
                    while (k < english.length && english[k].isDigit()) k++
                    frac = english.substring(start, k)
                }
                val value = intPart.toLongOrNull()
                if (value != null) {
                    sb.append(numberToWords(value))
                    if (frac.isNotEmpty()) {
                        sb.append(" ممیز ")
                        sb.append(numberToWords(frac.toLongOrNull() ?: 0L))
                    }
                } else {
                    sb.append(intPart)
                }
                i = k
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** تبدیل عدد صحیح به حروف فارسی. */
    fun numberToWords(value: Long): String {
        if (value == 0L) return "صفر"
        if (value < 0) return "منفی " + numberToWords(-value)
        val groups = ArrayList<Int>()
        var v = value
        while (v > 0) {
            groups.add((v % 1000L).toInt())
            v /= 1000L
        }
        val parts = ArrayList<String>()
        for (idx in groups.indices.reversed()) {
            val g = groups[idx]
            if (g == 0) continue
            val words = groupToWords(g)
            val scale = SCALES.getOrElse(idx) { "" }
            parts.add(if (scale.isEmpty()) words else "$words $scale".trim())
        }
        return parts.joinToString(" و ")
    }

    private fun groupToWords(n: Int): String {
        val parts = ArrayList<String>()
        val h = n / 100
        val rest = n % 100
        if (h > 0) parts.add(HUNDREDS[h])
        if (rest in 10..19) {
            parts.add(TEENS[rest - 10])
        } else {
            val t = rest / 10
            val o = rest % 10
            if (t > 0) parts.add(TENS[t])
            if (o > 0) parts.add(ONES[o])
        }
        return parts.joinToString(" و ")
    }

    /** فاصله‌ی متنی فارسی: «۳۵۰ متر» یا «۲٫۴ کیلومتر» */
    fun formatDistance(meters: Double): String {
        if (meters.isNaN()) return "نامشخص"
        val m = if (meters < 0) 0.0 else meters
        return if (m < 950) {
            toPersianDigits("${(m / 10).toInt() * 10}") + " متر"
        } else {
            val km = m / 1000.0
            val text = if (km < 10) String.format(Locale.US, "%.1f", km) else String.format(Locale.US, "%.0f", km)
            toPersianDigits(text) + " کیلومتر"
        }
    }

    /** فاصله برای گفتار: «سیصد و پنجاه متر»، «پنج کیلومتر»، «دو و نیم کیلومتر» */
    fun formatDistanceSpeech(meters: Double): String {
        val m = if (meters.isNaN() || meters < 0) 0.0 else meters
        if (m < 950.0) {
            val rounded = (m / 10).toInt() * 10
            return "${numberToWords(rounded.toLong())} متر"
        }
        val halves = Math.round(m / 500.0)
        val whole = (halves / 2).toInt()
        return if (halves % 2L == 0L) "${numberToWords(whole.toLong())} کیلومتر"
        else "${numberToWords(whole.toLong())} و نیم کیلومتر"
    }

    /** مدت‌زمان فارسی: «۱۲ دقیقه»، «۱ ساعت و ۵ دقیقه» */
    fun formatDuration(seconds: Long): String {
        val s = if (seconds < 0) 0 else seconds
        return when {
            s < 60 -> toPersianDigits("$s") + " ثانیه"
            s < 3600 -> {
                val minutes = ((s + 30) / 60)
                toPersianDigits("$minutes") + " دقیقه"
            }
            else -> {
                val hours = s / 3600
                val minutes = (s % 3600) / 60
                if (minutes == 0L) toPersianDigits("$hours") + " ساعت"
                else toPersianDigits("$hours") + " ساعت و " + toPersianDigits("$minutes") + " دقیقه"
            }
        }
    }

    fun formatDurationSpeech(seconds: Long): String {
        val s = if (seconds < 0) 0 else seconds
        return when {
            s < 60 -> "${numberToWords(s)} ثانیه"
            s < 3600 -> "${numberToWords((s + 30) / 60)} دقیقه"
            else -> {
                val h = s / 3600
                val m = (s % 3600) / 60
                if (m == 0L) "${numberToWords(h)} ساعت"
                else "${numberToWords(h)} ساعت و ${numberToWords(m)} دقیقه"
            }
        }
    }

    /** ساعت به شکل ۱۴:۲۷ با ارقام فارسی */
    fun clockString(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone, Locale.US)
        cal.timeInMillis = epochMillis
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        return toPersianDigits(String.format(Locale.US, "%02d:%02d", h, m))
    }

    /** تاریخ شمسی کوتاه: ۳۰ مهر */
    fun jalaliShort(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val j = JalaliDate.fromEpochMillis(epochMillis, timeZone)
        return toPersianDigits("${j.day}") + " " + JalaliDate.MONTHS[j.month - 1]
    }

    /** صفت ترتیبی فارسی: اول، دوم، سوم … */
    fun ordinalWords(value: Int): String = when (value) {
        1 -> "اول"
        2 -> "دوم"
        3 -> "سوم"
        4 -> "چهارم"
        5 -> "پنجم"
        6 -> "ششم"
        7 -> "هفتم"
        8 -> "هشتم"
        9 -> "نهم"
        10 -> "دهم"
        else -> numberToWords(value.toLong()) + "م"
    }

    /** کلید مقایسه‌ی بدون فاصله و بدون نیم‌فاصله (برای جست‌وجوی شهر و خیابان). */
    fun looseKey(input: String): String = normalize(input)
        .replace(" ", "")
        .replace(ZWNJ, "")
        .replace('آ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('ئ', 'ی')
        .replace('ؤ', 'و')

    fun percent(value: Int): String = toPersianDigits("$value") + "٪"

    fun fileSize(bytes: Long): String = when {
        bytes < 1024 -> toPersianDigits("$bytes") + " بایت"
        bytes < 1024 * 1024 -> toPersianDigits(String.format(Locale.US, "%.0f", bytes / 1024.0)) + " کیلوبایت"
        bytes < 1024L * 1024 * 1024 -> toPersianDigits(String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0)) + " مگابایت"
        else -> toPersianDigits(String.format(Locale.US, "%.2f", bytes / 1024.0 / 1024.0 / 1024.0)) + " گیگابایت"
    }
}
