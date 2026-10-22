package com.iranamap.real.core

import kotlin.math.max

/**
 * یک بسته‌ی دانلود آفلاین (منطقه + بازه‌ی زوم).
 * طبق شرایط استفاده‌ی OpenFreeMap هر دانلود حداکثر ۳۰۰۰ کاشی و ۵۰ مگابایت است،
 * بنابراین بسته‌ها کوچک‌تر از این سقف‌ها ساخته می‌شوند.
 */
data class PackSpec(
    val bounds: GeoBounds,
    val minZoom: Int,
    val maxZoom: Int,
    val estimatedTiles: Int,
    val estimatedBytes: Long
) {
    val label: String get() = "z$minZoom-$maxZoom"
}

data class PackPlan(
    val packs: List<PackSpec>,
    val totalTiles: Int,
    val totalBytes: Long
) {
    val packCount: Int get() = packs.size
}

object OfflinePlanner {

    /** سقف سخت‌گیرانه‌ی خودمان؛ پایین‌تر از محدودیت رسمی. */
    const val MAX_TILES_PER_PACK: Int = 1100
    const val MAX_BYTES_PER_PACK: Long = 45L * 1024 * 1024
    const val AVG_TILE_BYTES: Long = 40_000L

    fun estimateBytes(tiles: Int): Long = tiles.toLong() * AVG_TILE_BYTES

    fun tileCount(bounds: GeoBounds, zoom: Int): Int = GeoMath.tileCount(bounds, zoom)

    /**
     * برنامه‌ی دانلود یک شهر را می‌سازد: برای هر سطح زوم یک بسته، و اگر تعداد کاشی‌های
     * یک سطح زوم از سقف بیشتر شد، کادر جغرافیایی را چهار قسمت می‌کند.
     */
    fun plan(
        bounds: GeoBounds,
        minZoom: Int,
        maxZoom: Int,
        maxTilesPerPack: Int = MAX_TILES_PER_PACK,
        maxBytesPerPack: Long = MAX_BYTES_PER_PACK
    ): PackPlan {
        val packs = ArrayList<PackSpec>()
        val limitTiles = max(50, minOf(maxTilesPerPack, (maxBytesPerPack / AVG_TILE_BYTES).toInt()))
        for (z in minZoom..maxZoom) {
            splitForZoom(bounds, z, limitTiles, packs, 0)
        }
        val totalTiles = packs.sumOf { it.estimatedTiles }
        return PackPlan(packs, totalTiles, packs.sumOf { it.estimatedBytes })
    }

    private fun splitForZoom(
        bounds: GeoBounds,
        zoom: Int,
        limitTiles: Int,
        out: MutableList<PackSpec>,
        depth: Int
    ) {
        val tiles = GeoMath.tileCount(bounds, zoom)
        if (tiles <= limitTiles || depth >= 5) {
            out.add(PackSpec(bounds, zoom, zoom, tiles, estimateBytes(tiles)))
            return
        }
        val midLat = (bounds.minLat + bounds.maxLat) / 2.0
        val midLng = (bounds.minLng + bounds.maxLng) / 2.0
        val quads = listOf(
            GeoBounds(bounds.minLat, bounds.minLng, midLat, midLng),
            GeoBounds(bounds.minLat, midLng, midLat, bounds.maxLng),
            GeoBounds(midLat, bounds.minLng, bounds.maxLat, midLng),
            GeoBounds(midLat, midLng, bounds.maxLat, bounds.maxLng)
        )
        for (q in quads) splitForZoom(q, zoom, limitTiles, out, depth + 1)
    }

    fun planForCity(city: City, maxZoomOverride: Int? = null): PackPlan =
        plan(city.bounds, city.minZoom, maxZoomOverride ?: city.maxZoom)

    /** برآورد حجم کل دانلود همه‌ی شهرها (برای هشدار به کاربر). */
    fun estimateAllCitiesBytes(provinces: List<String> = CityCatalog.provinces): Long =
        CityCatalog.all.filter { provinces.contains(it.province) }.sumOf { planForCity(it).totalBytes }

    fun humanSummary(plan: PackPlan): String =
        "${PersianText.toPersianDigits(plan.packCount.toString())} بسته • " +
            PersianText.fileSize(plan.totalBytes)
}
