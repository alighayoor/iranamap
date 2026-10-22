package com.iranamap.real.core

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** تاریخ هجری شمسی (جلالی) — پیاده‌سازی مستقل بر پایه‌ی الگوریتم مرجع جلالی. */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {

    fun isoLike(): String = "%04d-%02d-%02d".format(Locale.US, year, month, day)

    companion object {
        val MONTHS = arrayOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )
        private val WEEKDAYS = arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")
        private val BREAKS = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
        )

        private fun div(a: Int, b: Int): Int = a / b
        private fun mod(a: Int, b: Int): Int = a - div(a, b) * b

        fun fromEpochMillis(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): JalaliDate {
            val cal = GregorianCalendar(timeZone, Locale.US)
            cal.timeInMillis = epochMillis
            return fromGregorian(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun today(timeZone: TimeZone = TimeZone.getDefault()): JalaliDate =
            fromEpochMillis(System.currentTimeMillis(), timeZone)

        /** عدد روز جولیَن از تاریخ میلادی. */
        private fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Int {
            var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
                div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
            d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
            return d
        }

        private fun jdnToGregorian(jdn: Int): Triple<Int, Int, Int> {
            var j = 4 * jdn + 139361631
            j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
            val i = div(mod(j, 1461), 4) * 5 + 308
            val gd = div(mod(i, 153), 5) + 1
            val gm = mod(div(i, 153), 12) + 1
            val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
            return Triple(gy, gm, gd)
        }

        private data class JalCal(val leap: Int, val gy: Int, val march: Int)

        private fun jalCal(jy0: Int): JalCal {
            val jy = jy0.coerceIn(BREAKS.first(), BREAKS.last() - 1)
            val gy = jy + 621
            var leapJ = -14
            var jp = BREAKS[0]
            var jump = 0
            for (i in 1 until BREAKS.size) {
                val jm = BREAKS[i]
                jump = jm - jp
                if (jy < jm) break
                leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
                jp = jm
            }
            var n = jy - jp
            leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
            if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
            val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
            val march = 20 + leapJ - leapG
            if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
            var leap = mod(mod(n + 1, 33) - 1, 4)
            if (leap == -1) leap = 4
            return JalCal(leap, gy, march)
        }

        private fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Int {
            val r = jalCal(jy)
            return gregorianToJdn(r.gy, 3, r.march) + mod(jm - 1, 12) * 31 - div(jm, 7) * (jm - 7) + jd - 1
        }

        fun fromJdn(jdn: Int): JalaliDate {
            var gy = jdnToGregorian(jdn).first
            var jy = gy - 621
            val r = jalCal(jy)
            val jdn1f = gregorianToJdn(gy, 3, r.march)
            var k = jdn - jdn1f
            if (k >= 0) {
                if (k <= 185) {
                    return JalaliDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
                }
                k -= 186
            } else {
                jy -= 1
                k += 179
                if (r.leap == 1) k += 1
            }
            return JalaliDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
        }

        /** تبدیل میلادی به جلالی. */
        fun fromGregorian(gy: Int, gm: Int, gd: Int): JalaliDate =
            fromJdn(gregorianToJdn(gy, gm, gd))

        /** تبدیل جلالی به میلادی. */
        fun toGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> =
            jdnToGregorian(jalaliToJdn(jy, jm, jd))

        fun weekdayName(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
            val cal = GregorianCalendar(timeZone, Locale.US)
            cal.timeInMillis = epochMillis
            return WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
        }

        /** سال کبیسه بر اساس قاعده‌ی ۳۳ ساله. */
        fun isLeap(jy: Int): Boolean {
            val r = jy % 33
            return intArrayOf(1, 5, 9, 13, 17, 22, 26, 30).contains(if (r < 0) r + 33 else r)
        }

        fun monthName(month: Int): String = MONTHS[(month - 1).coerceIn(0, 11)]
    }
}
