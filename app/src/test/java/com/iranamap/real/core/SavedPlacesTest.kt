package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlacesTest {

    @Test
    fun placeKeepsStableIdFromCoordinates() {
        val id = SavedPlaceIds.from(GeoPoint(35.6892, 51.3890))
        assertEquals("35.68920_51.38900", id)
        assertEquals(id, SavedPlaceIds.from(GeoPoint(35.6892, 51.3890)))
    }

    @Test
    fun shortLabelPrefersLabelThenAddress() {
        val place = SavedPlace("1", "خانه", "خیابان ولیعصر", GeoPoint(35.7, 51.4))
        assertEquals("خانه", place.shortLabel)
        val blank = place.copy(label = "")
        assertEquals("خیابان ولیعصر", blank.shortLabel)
        val empty = place.copy(label = "", address = "")
        assertEquals("مکان ذخیره‌شده", empty.shortLabel)
    }

    @Test
    fun presetsAreHomeAndWork() {
        assertTrue(SavedLabels.presets.contains("خانه"))
        assertTrue(SavedLabels.presets.contains("محل کار"))
    }
}
