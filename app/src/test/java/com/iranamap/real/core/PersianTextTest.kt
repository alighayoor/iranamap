package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianTextTest {

    @Test
    fun convertsDigitsToPersianAndBack() {
        assertEquals("۱۴:۲۷", PersianText.toPersianDigits("14:27"))
        assertEquals("12.5", PersianText.toEnglishDigits("۱۲٫۵"))
        assertEquals("2024", PersianText.toEnglishDigits("٢٠٢٤"))
    }

    @Test
    fun normalizesArabicCharactersAndSpacing() {
        val input = "علي   كريمي\u0640\u064B"
        val out = PersianText.normalize(input)
        assertTrue(out.startsWith("علی"))
        assertTrue(out.contains("کریمی"))
        assertFalse(out.contains("  "))
        assertFalse(out.contains("\u0640"))
    }

    @Test
    fun numberToWordsHandlesAllGroups() {
        assertEquals("صفر", PersianText.numberToWords(0))
        assertEquals("هفت", PersianText.numberToWords(7))
        assertEquals("پانزده", PersianText.numberToWords(15))
        assertEquals("بیست و یک", PersianText.numberToWords(21))
        assertEquals("صد", PersianText.numberToWords(100))
        assertEquals("صد و بیست و سه", PersianText.numberToWords(123))
        assertEquals("یک هزار", PersianText.numberToWords(1000))
        assertEquals("دو هزار و پانصد", PersianText.numberToWords(2500))
        assertEquals("یک میلیون و دویست هزار", PersianText.numberToWords(1_200_000))
        assertEquals("منفی پنج", PersianText.numberToWords(-5))
    }

    @Test
    fun formatsDistancesInPersian() {
        assertEquals("۳۵۰ متر", PersianText.formatDistance(352.0))
        assertEquals("۲٫۴ کیلومتر", PersianText.formatDistance(2380.0))
        assertEquals("۱۵ کیلومتر", PersianText.formatDistance(15_200.0))
    }

    @Test
    fun formatsDistanceForSpeech() {
        assertEquals("سیصد و پنجاه متر", PersianText.formatDistanceSpeech(352.0))
        assertEquals("دو کیلومتر", PersianText.formatDistanceSpeech(2000.0))
        assertEquals("دو و نیم کیلومتر", PersianText.formatDistanceSpeech(2450.0))
        assertEquals("یک و نیم کیلومتر", PersianText.formatDistanceSpeech(1500.0))
    }

    @Test
    fun formatsDurations() {
        assertEquals("۴۵ ثانیه", PersianText.formatDuration(45))
        assertEquals("۲ دقیقه", PersianText.formatDuration(120))
        assertEquals("۱ ساعت و ۵ دقیقه", PersianText.formatDuration(3900))
    }

    @Test
    fun expandsAbbreviationsAndDigitsForSpeech() {
        val out = PersianText.forSpeech("خ. ولیعصر / خیابان ۱۵ خرداد")
        assertTrue(out.contains("خیابان"))
        assertTrue(out.contains("پانزده"))
        assertFalse(out.contains("۱۵"))
        assertFalse(out.contains("/"))
    }

    @Test
    fun clockStringUsesPersianDigits() {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 14)
        cal.set(java.util.Calendar.MINUTE, 27)
        assertEquals("۱۴:۲۷", PersianText.clockString(cal.timeInMillis))
    }

    @Test
    fun fileSizeIsLocalized() {
        assertTrue(PersianText.fileSize(20_666_906L).contains("مگابایت"))
        assertTrue(PersianText.fileSize(2048).contains("کیلوبایت"))
    }

    @Test
    fun percentUsesPersianDigits() {
        assertEquals("۴۵٪", PersianText.percent(45))
    }
}
