package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class JalaliDateTest {

    @Test
    fun convertsKnownGregorianDates() {
        assertEquals(JalaliDate(1403, 1, 1), JalaliDate.fromGregorian(2024, 3, 20))
        assertEquals(JalaliDate(1402, 12, 29), JalaliDate.fromGregorian(2024, 3, 19))
        assertEquals(JalaliDate(1403, 7, 30), JalaliDate.fromGregorian(2024, 10, 21))
        assertEquals(JalaliDate(1357, 11, 22), JalaliDate.fromGregorian(1979, 2, 11))
    }

    @Test
    fun roundTripsGregorianToJalali() {
        val samples = listOf(
            Triple(2024, 1, 1), Triple(2023, 6, 15), Triple(2025, 12, 31),
            Triple(1999, 3, 21), Triple(2026, 10, 22)
        )
        for ((y, m, d) in samples) {
            val j = JalaliDate.fromGregorian(y, m, d)
            assertEquals(Triple(y, m, d), JalaliDate.toGregorian(j.year, j.month, j.day))
        }
    }

    @Test
    fun monthNamesArePersian() {
        assertEquals("فروردین", JalaliDate.MONTHS[0])
        assertEquals("اسفند", JalaliDate.MONTHS[11])
        assertEquals("مهر", JalaliDate.monthName(7))
    }

    @Test
    fun leapYearsFollowTheThirtyThreeYearRule() {
        assertTrue(JalaliDate.isLeap(1403))
        assertFalse(JalaliDate.isLeap(1404))
    }

    @Test
    fun weekdayNameIsCorrectForKnownDate() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
        cal.set(2024, Calendar.MARCH, 20, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        assertEquals("چهارشنبه", JalaliDate.weekdayName(cal.timeInMillis, TimeZone.getTimeZone("Asia/Tehran")))
    }

    @Test
    fun shortPersianDateUsesJalaliMonths() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
        cal.set(2024, Calendar.OCTOBER, 22, 9, 0)
        val text = PersianText.jalaliShort(cal.timeInMillis, TimeZone.getTimeZone("Asia/Tehran"))
        assertEquals("۱ آبان", text)
    }
}
