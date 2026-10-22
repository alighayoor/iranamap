package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    private val tehran = GeoPoint(35.6892, 51.3890)

    @Test
    fun catalogIsLargeAndUnique() {
        assertTrue(Categories.all.size >= 25)
        assertEquals(Categories.all.size, Categories.all.map { it.id }.distinct().size)
        assertTrue(Categories.all.all { it.titleFa.isNotBlank() })
        assertTrue(Categories.all.all { it.glyph.isNotBlank() })
    }

    @Test
    fun quickListIsSubsetOfAll() {
        assertTrue(Categories.quick.isNotEmpty())
        assertTrue(Categories.quick.all { c -> Categories.all.any { it.id == c.id } })
        assertNotNull(Categories.find("fuel"))
        assertNull(Categories.find("nope"))
    }

    @Test
    fun overpassQueryIsWellFormed() {
        val fuel = Categories.find("fuel")!!
        val q = Categories.overpassQuery(fuel, tehran, 1500)
        assertTrue(q.contains("[out:json]"))
        assertTrue(q.contains("around:1500,35.68920,51.38900"))
        assertTrue(q.contains("amenity\"=\"fuel"))
        assertTrue(q.endsWith("out center 60;"))
    }

    @Test
    fun overpassQueryClampsRadius() {
        val parking = Categories.find("parking")!!
        val q = Categories.overpassQuery(parking, tehran, 999_999)
        assertTrue(q.contains("around:20000,"))
    }

    @Test
    fun multiValueFiltersAreSplit() {
        val attraction = Categories.find("attraction")!!
        val q = Categories.overpassQuery(attraction, tehran, 1200)
        assertTrue(q.contains("nwr[\"tourism\"~\"attraction|museum|viewpoint\"]"))
    }

    @Test
    fun areaCategoriesAreMarked() {
        assertTrue(Categories.find("parking")!!.isArea)
        assertTrue(Categories.find("hospital")!!.isArea)
        assertTrue(!Categories.find("fuel")!!.isArea)
    }
}
