package com.iranamap.real.data

import com.iranamap.real.core.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepositoryTest {
    private class FakeApi(private val answer: suspend () -> List<NominatimPlace>) : NominatimApi {
        var calls = 0
        var lastQuery = ""
        var lastViewBox: String? = null
        override suspend fun search(
            query: String, format: String, limit: Int, countryCodes: String,
            language: String, addressDetails: Int, viewBox: String?, bounded: Int
        ): List<NominatimPlace> {
            calls++
            lastQuery = query
            lastViewBox = viewBox
            return answer()
        }
        override suspend fun reverse(lat: Double, lon: Double, format: String, language: String, zoom: Int): NominatimPlace =
            NominatimPlace()
    }

    @Test
    fun trimsQueryFiltersInvalidCoordinatesAndRemovesDuplicates() = runBlocking {
        val place = NominatimPlace(name = "شیراز", lat = "29.620", lon = "52.520")
        val api = FakeApi { listOf(place, place, NominatimPlace(name = "خارج", lat = "48.8", lon = "2.3")) }
        val results = SearchRepository(api).search("  شیراز  ", GeoPoint(29.6, 52.5))
        assertEquals("شیراز", api.lastQuery)
        assertNotNull(api.lastViewBox)
        assertEquals(1, results.size)
        assertEquals("شیراز", results.first().title)
    }

    @Test
    fun shortSearchDoesNotCallService() = runBlocking {
        val api = FakeApi { error("should not be called") }
        assertTrue(SearchRepository(api).search("ش").isEmpty())
        assertEquals(0, api.calls)
    }

    @Test
    fun cancellationIsPropagatedToCaller() = runBlocking {
        val api = FakeApi { throw CancellationException("cancelled") }
        try {
            SearchRepository(api).search("شیراز")
            throw AssertionError("CancellationException should propagate")
        } catch (_: CancellationException) {
            assertEquals(1, api.calls)
        }
    }
}
