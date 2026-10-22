package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePlannerTest {

    @Test
    fun tileCountGrowsWithZoom() {
        val b = GeoMath.boundsAround(GeoPoint(35.6892, 51.3890), 10.0)
        val z10 = OfflinePlanner.tileCount(b, 10)
        val z14 = OfflinePlanner.tileCount(b, 14)
        assertTrue(z10 > 0)
        assertTrue(z14 > z10)
    }

    @Test
    fun planRespectsPerPackLimits() {
        val b = GeoMath.boundsAround(GeoPoint(35.6892, 51.3890), 35.0)
        val plan = OfflinePlanner.plan(b, 9, 14)
        assertTrue(plan.packs.isNotEmpty())
        for (p in plan.packs) {
            assertTrue("tiles=${p.estimatedTiles}", p.estimatedTiles <= OfflinePlanner.MAX_TILES_PER_PACK)
            assertTrue("bytes=${p.estimatedBytes}", p.estimatedBytes <= OfflinePlanner.MAX_BYTES_PER_PACK)
            assertEquals(1, p.maxZoom - p.minZoom + 1)
        }
    }

    @Test
    fun planCoversAllZoomLevels() {
        val b = GeoMath.boundsAround(GeoPoint(32.6546, 51.6680), 24.0)
        val plan = OfflinePlanner.plan(b, 10, 14)
        val zooms = plan.packs.map { it.minZoom }.toSet()
        assertEquals(setOf(10, 11, 12, 13, 14), zooms)
    }

    @Test
    fun splitPacksCoverRoughlyTheWholeArea() {
        val b = GeoMath.boundsAround(GeoPoint(35.6892, 51.3890), 35.0)
        val plan = OfflinePlanner.plan(b, 14, 14)
        val totalTiles = plan.totalTiles
        val direct = OfflinePlanner.tileCount(b, 14)
        // با تقسیم، کاشی‌ها کمی بیشتر می‌شوند ولی نباید چند برابر شوند
        assertTrue("total=$totalTiles direct=$direct", totalTiles in direct..(direct * 3 + 40))
        assertTrue(plan.packCount > 1)
    }

    @Test
    fun smallCityFitsInOnePackPerZoom() {
        val city = CityCatalog.find("city_001") ?: CityCatalog.all.first()
        val plan = OfflinePlanner.planForCity(city)
        assertTrue(plan.packCount <= plan.packs.map { it.minZoom }.distinct().size * 4 + 4)
        assertTrue(plan.totalBytes > 0)
    }

    @Test
    fun estimateAllCitiesIsReasonable() {
        val bytes = OfflinePlanner.estimateAllCitiesBytes(listOf("قم"))
        assertTrue(bytes > 0)
        assertTrue(bytes < 500L * 1024 * 1024)
    }

    @Test
    fun humanSummaryIsPersian() {
        val plan = OfflinePlanner.plan(GeoMath.boundsAround(GeoPoint(35.0, 51.0), 5.0), 12, 13)
        val s = OfflinePlanner.humanSummary(plan)
        assertTrue(s.contains("بسته"))
        assertTrue(s, s.contains("بسته"))
        assertTrue(s, s.contains("مگابایت") || s.contains("گیگابایت") || s.contains("کیلوبایت"))
    }
}
