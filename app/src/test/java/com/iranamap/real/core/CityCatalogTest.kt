package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityCatalogTest {

    @Test
    fun catalogHasAllProvincesAndManyCities() {
        assertEquals(31, CityCatalog.provinces.size)
        assertTrue("cities=${CityCatalog.all.size}", CityCatalog.all.size >= 250)
    }

    @Test
    fun everyCityHasUniqueIdAndValidCoordinates() {
        val ids = CityCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (c in CityCatalog.all) {
            assertTrue(c.name, GeoMath.isPlausibleIranPoint(GeoPoint(c.lat, c.lng)))
            assertTrue(c.name, c.radiusKm in 3.0..60.0)
            assertTrue(c.name, c.province in CityCatalog.provinces)
        }
    }

    @Test
    fun majorCitiesArePresent() {
        for (name in listOf("تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "اهواز", "رشت", "کرمان", "یزد")) {
            assertNotNull(name, CityCatalog.search(name).firstOrNull())
        }
    }

    @Test
    fun searchIgnoresSpacingAndArabicLetters() {
        assertTrue(CityCatalog.search("تهران").isNotEmpty())
        assertTrue(CityCatalog.search("خرم اباد").isNotEmpty())
        assertTrue(CityCatalog.search("خرم‌آباد").isNotEmpty())
    }

    @Test
    fun filterByProvinceWorks() {
        val qom = CityCatalog.byProvince("قم")
        assertTrue(qom.isNotEmpty())
        assertTrue(qom.all { it.province == "قم" })
        assertFalse(CityCatalog.byProvince(null).isEmpty())
    }

    @Test
    fun boundsContainTheCityCenter() {
        val c = CityCatalog.search("اصفهان").first()
        assertTrue(c.bounds.contains(GeoPoint(c.lat, c.lng)))
        assertTrue(c.bounds.maxLat > c.bounds.minLat)
    }

    @Test
    fun largestReturnsSortedBySize() {
        val top = CityCatalog.largest(5)
        assertEquals(5, top.size)
        assertTrue(top[0].radiusKm >= top[4].radiusKm)
        assertEquals("تهران", top[0].name)
    }

    @Test
    fun eachCityProducesAValidDownloadPlan() {
        for (c in CityCatalog.all) {
            val plan = OfflinePlanner.planForCity(c)
            assertTrue(c.name, plan.packs.isNotEmpty())
            assertTrue(c.name, plan.totalTiles > 0)
            assertTrue(c.name, plan.packs.all { it.minZoom in 9..14 })
        }
    }
}
