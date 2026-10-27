package com.iranamap.real.offline

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.iranamap.real.core.City
import com.iranamap.real.core.CityCatalog
import com.iranamap.real.core.OfflinePlanner
import com.iranamap.real.core.PackSpec
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume

/** اطلاعات یک بسته‌ی آفلاین ذخیره‌شده روی دستگاه. */
data class OfflineRegionInfo(
    val regionId: Long,
    val cityId: String?,
    val zoom: Int,
    val chunk: Int,
    val sizeBytes: Long,
    val isComplete: Boolean
) {
    val city: City? get() = cityId?.let { CityCatalog.find(it) }
}

data class DownloadProgress(
    val cityId: String,
    val packIndex: Int,
    val packCount: Int,
    val percent: Int,
    val bytes: Long,
    val currentZoom: Int
)

private data class PackMetadata(val cityId: String, val zoom: Int, val chunk: Int)

/**
 * مدیریت نقشه‌های آفلاین با MapLibre OfflineManager.
 * هر شهر به بسته‌های کوچک تقسیم می‌شود و دانلودها پشت‌سرهم انجام می‌شوند.
 * سقف‌ها برآورد داخلی هستند، نه تضمین انطباق با سیاست منبع یا حجم واقعی.
 */
class OfflineRepository(private val context: Context) {

    private val tag = "OfflineRepo"
    private val gson = Gson()
    private val manager: OfflineManager by lazy { OfflineManager.getInstance(context) }

    init {
        try {
            // سقف پیش‌فرض ۶۰۰۰ کاشی است؛ برای چند شهر کافی نیست.
            // حالت کم‌مصرف Redmi 10C: 800K کاشی تا رم پر نشود
            val isLite = try { com.iranamap.real.util.DevicePerformance.isLiteMode(context) } catch (_: Exception) { false }
            val limit = if (isLite) 800_000L else 2_000_000L
            manager.setOfflineMapboxTileCountLimit(limit)
            Log.i(tag, "سقف کاشی: $limit liteMode=$isLite")
        } catch (e: Exception) {
            Log.w(tag, "تنظیم سقف کاشی ناموفق بود", e)
        }
    }

    suspend fun listRegions(): List<OfflineRegionInfo> = withContext(Dispatchers.Main) {
        val regions = suspendCancellableCoroutine<List<OfflineRegion>> { cont ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (cont.isActive) cont.resume(offlineRegions?.toList().orEmpty())
                }

                override fun onError(error: String) {
                    Log.w(tag, "فهرست بسته‌ها ناموفق: $error")
                    if (cont.isActive) cont.resume(emptyList())
                }
            })
        }
        val result = ArrayList<OfflineRegionInfo>()
        for (region in regions) {
            val meta = parseMetadata(region.metadata) ?: continue
            val status = statusOf(region)
            result.add(
                OfflineRegionInfo(
                    regionId = region.id,
                    cityId = meta.cityId,
                    zoom = meta.zoom,
                    chunk = meta.chunk,
                    sizeBytes = status?.completedResourceSize ?: 0L,
                    isComplete = status?.isComplete == true
                )
            )
        }
        result
    }

    private suspend fun statusOf(region: OfflineRegion): OfflineRegionStatus? =
        suspendCancellableCoroutine { cont ->
            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) {
                    if (cont.isActive) cont.resume(status)
                }
                override fun onError(error: String?) {
                    Log.w(tag, "خواندن وضعیت بسته ناموفق: $error")
                    if (cont.isActive) cont.resume(null)
                }
            })
        }

    /** دانلود کامل یک شهر در قالب چند بسته‌ی کوچک. */
    suspend fun downloadCity(
        city: City,
        styleUrl: String,
        onProgress: (DownloadProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.Main) {
        val plan = OfflinePlanner.planForCity(city)
        if (plan.packs.isEmpty()) return@withContext Result.failure(IllegalStateException("برنامه‌ی دانلود خالی است"))
        var completedBytes = 0L
        for ((index, pack) in plan.packs.withIndex()) {
            val result = downloadPack(city, pack, index, plan.packs.size, styleUrl, completedBytes) { p ->
                onProgress(p)
            }
            if (result.isFailure) {
                return@withContext Result.failure(
                    result.exceptionOrNull() ?: IllegalStateException("دانلود بسته ناموفق بود")
                )
            }
            completedBytes += pack.estimatedBytes
        }
        Result.success(Unit)
    }

    private suspend fun downloadPack(
        city: City,
        pack: PackSpec,
        index: Int,
        total: Int,
        styleUrl: String,
        previousBytes: Long,
        onProgress: (DownloadProgress) -> Unit
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        val bounds = LatLngBounds.Builder()
            .include(LatLng(pack.bounds.maxLat, pack.bounds.minLng))
            .include(LatLng(pack.bounds.minLat, pack.bounds.maxLng))
            .build()
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            bounds,
            pack.minZoom.toDouble(),
            pack.maxZoom.toDouble(),
            context.resources.displayMetrics.density
        )
        val metadata = gson.toJson(PackMetadata(city.id, pack.minZoom, index)).toByteArray(Charsets.UTF_8)
        var activeRegion: OfflineRegion? = null
        fun stopRegion(region: OfflineRegion) {
            runCatching { region.setObserver(null) }
            runCatching { region.setDownloadState(OfflineRegion.STATE_INACTIVE) }
        }
        cont.invokeOnCancellation {
            // MapLibre APIs must be called on the main thread.
            Handler(Looper.getMainLooper()).post { activeRegion?.let { stopRegion(it) } }
        }

        manager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    if (!cont.isActive) {
                        stopRegion(region)
                        return
                    }
                    activeRegion = region
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (!cont.isActive) return
                            val estimate = OfflinePlanner.estimateProgress(
                                packIndex = index,
                                packCount = total,
                                completedResources = status.completedResourceCount,
                                requiredResources = status.requiredResourceCount,
                                previousEstimatedBytes = previousBytes,
                                currentEstimatedBytes = pack.estimatedBytes
                            )
                            onProgress(
                                DownloadProgress(
                                    cityId = city.id,
                                    packIndex = index,
                                    packCount = total,
                                    percent = estimate.percent,
                                    bytes = estimate.estimatedBytes,
                                    currentZoom = pack.minZoom
                                )
                            )
                            if (status.isComplete) {
                                stopRegion(region)
                                if (cont.isActive) cont.resume(Result.success(Unit))
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            Log.w(tag, "خطای بسته: ${error.message}")
                            stopRegion(region)
                            if (cont.isActive) {
                                cont.resume(Result.failure(IllegalStateException(error.message ?: "خطای دانلود")))
                            }
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            stopRegion(region)
                            if (cont.isActive) {
                                cont.resume(
                                    Result.failure(IllegalStateException("سقف تعداد کاشی دستگاه پر شد ($limit)"))
                                )
                            }
                        }
                    })
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    if (cont.isActive) cont.resume(Result.failure(IllegalStateException(error)))
                }
            }
        )
    }

    suspend fun deleteRegionsOfCity(cityId: String): Result<Unit> = withContext(Dispatchers.Main) {
        val regions = suspendCancellableCoroutine<List<OfflineRegion>> { cont ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (cont.isActive) cont.resume(offlineRegions?.toList().orEmpty())
                }

                override fun onError(error: String) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            })
        }
        var failures = 0
        for (region in regions) {
            if (parseMetadata(region.metadata)?.cityId != cityId) continue
            val ok = suspendCancellableCoroutine<Boolean> { cont ->
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(error: String) {
                        Log.w(tag, "حذف بسته ناموفق: $error")
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
            if (!ok) failures++
        }
        if (failures == 0) Result.success(Unit)
        else Result.failure(IllegalStateException("حذف $failures بسته ناموفق بود"))
    }

    suspend fun cachedSizeOf(cityId: String): Long = withContext(Dispatchers.Main) {
        val regions = suspendCancellableCoroutine<Long> { cont ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val list = offlineRegions?.filter {
                        parseMetadata(it.metadata)?.cityId == cityId
                    }.orEmpty()
                    if (list.isEmpty()) {
                        if (cont.isActive) cont.resume(0L)
                        return
                    }
                    var total = 0L
                    var remaining = list.size
                    var failure = false
                    list.forEach { region ->
                        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                            override fun onStatus(status: OfflineRegionStatus?) {
                                if (failure) return
                                total += status?.completedResourceSize ?: 0L
                                remaining--
                                if (remaining == 0 && cont.isActive) cont.resume(total)
                            }

                            override fun onError(error: String?) {
                                failure = true
                                if (cont.isActive) cont.resume(total)
                            }
                        })
                    }
                }

                override fun onError(error: String) {
                    if (cont.isActive) cont.resume(0L)
                }
            })
        }
        regions
    }

    private fun parseMetadata(bytes: ByteArray?): PackMetadata? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val text = String(bytes, Charsets.UTF_8)
            gson.fromJson(text, PackMetadata::class.java)
        } catch (e: Exception) {
            Log.w(tag, "فراداده‌ی بسته خوانده نشد", e)
            null
        }
    }
}
