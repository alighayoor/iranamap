package com.iranamap.real

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iranamap.real.map.MapController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.snapshotter.MapSnapshotter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Experimental tiny, single-region probe, NOT the paused city-download feature.
 * The controlled MapLibre HTTP client sends an identifying UA and has at most
 * four requests in flight globally. The test stops far below published limits.
 */
@RunWith(AndroidJUnit4::class)
class OfflineSmallProbeTest {
    private val latitude = 29.5918
    private val longitude = 52.5837
    private val requestCount = AtomicInteger()

    @Test
    fun tinyRegionCanRenderAfterMapLibreNetworkIsDisabled() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        withTimeout(150_000L) {
            withContext(Dispatchers.Main) {
                MapLibre.getInstance(context)
                val dispatcher = Dispatcher().apply {
                    maxRequests = 4
                    maxRequestsPerHost = 4
                }
                val client = OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .addInterceptor { chain ->
                        requestCount.incrementAndGet()
                        chain.proceed(chain.request().newBuilder()
                            .header("User-Agent", "IranAmap-QA-OfflineProbe/1.0 (https://github.com/alighayoor/iranamap)")
                            .build())
                    }
                    .build()
                HttpRequestUtil.setOkHttpClient(client)
                val manager = OfflineManager.getInstance(context)
                var region: OfflineRegion? = null
                try {
                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(latitude - 0.04, longitude - 0.04))
                        .include(LatLng(latitude + 0.04, longitude + 0.04))
                        .build()
                    val definition = OfflineTilePyramidRegionDefinition(
                        MapController.STYLE_DARK, bounds, 12.0, 12.0, 1.0f
                    )
                    val created = createRegion(manager, definition)
                    region = created
                    val status = awaitComplete(created)
                    assertTrue("No offline tiles were downloaded", status.completedTileCount > 0)
                    assertTrue("Probe exceeded the conservative tile budget", status.completedTileCount <= 100)
                    assertTrue("Probe exceeded the conservative byte budget", status.completedResourceSize < 20_000_000L)
                    Log.i("IranAmapOfflineProbe", "completed tiles=${status.completedTileCount}, bytes=${status.completedResourceSize}, requests=${requestCount.get()}")

                    // Remove ordinary browsing cache so the subsequent render depends on the pinned region.
                    clearAmbientCache(manager)
                    MapLibre.setConnected(false)
                    val bitmap = withTimeout(35_000L) { snapshot(context) }
                    val colors = HashSet<Int>()
                    for (y in 24 until bitmap.height - 24 step 4) {
                        for (x in 24 until bitmap.width - 24 step 4) colors.add(bitmap.getPixel(x, y))
                    }
                    assertTrue("Offline map snapshot appears uniformly blank", colors.size >= 8)
                    saveSnapshot(context, bitmap)
                    Log.i("IranAmapOfflineProbe", "MapLibre-offline snapshot rendered; interior colors=${colors.size}")
                } finally {
                    withContext(NonCancellable) {
                        MapLibre.setConnected(null)
                        region?.setObserver(null)
                        region?.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        region?.let { withTimeoutOrNull(15_000L) { delete(it) } }
                        HttpRequestUtil.setOkHttpClient(null)
                    }
                }
            }
        }
    }

    private suspend fun createRegion(manager: OfflineManager, definition: OfflineTilePyramidRegionDefinition): OfflineRegion =
        suspendCancellableCoroutine { cont ->
            manager.createOfflineRegion(definition, "iranamap-small-probe".toByteArray(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(region: OfflineRegion) {
                        if (cont.isActive) cont.resume(region)
                        else region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    }
                    override fun onError(error: String) {
                        if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
                    }
                })
        }

    private suspend fun awaitComplete(region: OfflineRegion): OfflineRegionStatus =
        suspendCancellableCoroutine { cont ->
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    if (!cont.isActive) return
                    if (status.completedTileCount > 100 || status.completedResourceSize >= 20_000_000L) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        cont.resumeWithException(IllegalStateException("Probe budget reached"))
                    } else if (status.isComplete) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        cont.resume(status)
                    }
                }
                override fun onError(error: OfflineRegionError) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error.message ?: "offline resource error"))
                }
                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("tile limit $limit"))
                }
            })
            cont.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { region.setDownloadState(OfflineRegion.STATE_INACTIVE) }
            }
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        }

    private suspend fun clearAmbientCache(manager: OfflineManager): Unit = suspendCancellableCoroutine { cont ->
        manager.clearAmbientCache(object : OfflineManager.FileSourceCallback {
            override fun onSuccess() { if (cont.isActive) cont.resume(Unit) }
            override fun onError(error: String) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
            }
        })
    }

    private suspend fun snapshot(context: Context): Bitmap = suspendCancellableCoroutine { cont ->
        val camera = CameraPosition.Builder().target(LatLng(latitude, longitude)).zoom(12.0).build()
        val options = MapSnapshotter.Options(256, 256)
            .withStyle(MapController.STYLE_DARK)
            .withPixelRatio(1.0f)
            .withCameraPosition(camera)
            .withLogo(false)
        val snapshotter = MapSnapshotter(context, options)
        cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { snapshotter.cancel() } }
        snapshotter.start(
            { result -> if (cont.isActive) cont.resume(result.bitmap) },
            { error -> if (cont.isActive) cont.resumeWithException(IllegalStateException(error)) }
        )
    }

    private suspend fun delete(region: OfflineRegion): Unit = suspendCancellableCoroutine { cont ->
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() { if (cont.isActive) cont.resume(Unit) }
            override fun onError(error: String) { if (cont.isActive) cont.resume(Unit) }
        })
    }

    private fun saveSnapshot(context: Context, bitmap: Bitmap) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "iranamap-offline-probe.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IranAmapTest")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
