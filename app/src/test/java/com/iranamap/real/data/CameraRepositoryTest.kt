package com.iranamap.real.data

import com.iranamap.real.core.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CameraRepositoryTest {
    private val center = GeoPoint(29.620, 52.520)

    private class FakeApi(private val reply: suspend (Int) -> OverpassResponse) : OverpassApi {
        var calls = 0
        override suspend fun query(data: String): OverpassResponse = reply(++calls)
    }

    @Test
    fun temporaryFailureDoesNotPoisonTheCache() = runBlocking {
        val api = FakeApi { count ->
            if (count == 1) throw IOException("offline")
            OverpassResponse(listOf(OverpassElement(
                id = 17L, lat = center.lat, lon = center.lng,
                tags = mapOf("highway" to "speed_camera", "maxspeed" to "60")
            )))
        }
        val repo = CameraRepository(api)
        assertNull(repo.camerasAround(center))
        assertEquals(17L, repo.camerasAround(center)!!.single().id)
        assertEquals(60, repo.camerasAround(center)!!.single().maxspeedKmh)
        assertEquals(2, api.calls)
    }

    @Test
    fun malformedResponseIsNotCachedAsEmpty() = runBlocking {
        val api = FakeApi { count -> if (count == 1) OverpassResponse() else OverpassResponse(emptyList()) }
        val repo = CameraRepository(api)
        assertNull(repo.camerasAround(center))
        assertTrue(repo.camerasAround(center)!!.isEmpty())
        assertEquals(2, api.calls)
    }

    @Test
    fun genuineEmptyResponseIsCached() = runBlocking {
        val api = FakeApi { OverpassResponse(emptyList()) }
        val repo = CameraRepository(api)
        assertTrue(repo.camerasAround(center)!!.isEmpty())
        assertTrue(repo.camerasAround(center)!!.isEmpty())
        assertEquals(1, api.calls)
    }

    @Test
    fun cancellationIsNotMistakenForNetworkFailure() = runBlocking {
        val api = FakeApi { throw CancellationException("cancelled") }
        try {
            CameraRepository(api).camerasAround(center)
            throw AssertionError("CancellationException should propagate")
        } catch (_: CancellationException) {
            assertEquals(1, api.calls)
        }
    }
}
