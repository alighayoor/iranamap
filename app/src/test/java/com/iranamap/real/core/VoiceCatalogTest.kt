package com.iranamap.real.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {

    @Test
    fun bundleVoiceIsPresent() {
        assertEquals("amir", VoiceCatalog.bundled.id)
        assertTrue(VoiceCatalog.bundled.bundled)
    }

    @Test
    fun allVoicesHaveHttpsUrlsAndSizes() {
        for (v in VoiceCatalog.all) {
            assertTrue(v.id, v.downloadUrl.startsWith("https://"))
            assertTrue(v.id, v.downloadUrl.endsWith(".tar.bz2"))
            assertTrue(v.id, v.sizeBytes > 1_000_000)
            assertTrue(v.id, v.modelFile.endsWith(".onnx"))
        }
    }

    @Test
    fun idsAndFoldersAreUnique() {
        assertEquals(VoiceCatalog.all.size, VoiceCatalog.all.map { it.id }.toSet().size)
        assertEquals(VoiceCatalog.all.size, VoiceCatalog.all.map { it.folder }.toSet().size)
    }

    @Test
    fun lookupById() {
        assertNotNull(VoiceCatalog.find("ganji"))
        assertNull(VoiceCatalog.find("does-not-exist"))
        assertNull(VoiceCatalog.find(null))
    }

    @Test
    fun sizeTextIsPersian() {
        assertTrue(VoiceCatalog.bundled.sizeText.contains("مگابایت"))
        assertEquals("مرد", VoiceCatalog.bundled.genderFa)
    }

    @Test
    fun allVoicesSpeakPersianPhrases() {
        for (v in VoiceCatalog.all) {
            assertTrue(VoiceCatalog.downloadUrlFor(v).contains(v.folder))
        }
    }
}
