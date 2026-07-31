package com.ja2.reborn

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalizedJsonDirectorySerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun externalizedJsonDirectoryRoundTripsThroughJa2Json() {
        val path = "/storage/emulated/0/JA2/externalized"

        val encoded = json.encodeToString(Ja2Json(externalizedJsonDir = path))
        val decoded: Ja2Json = json.decodeFromString(encoded)

        assertTrue(encoded.contains("\"externalized_json_dir\":\"$path\""))
        assertEquals(path, decoded.externalizedJsonDir)
    }

    @Test
    fun missingExternalizedJsonDirectoryUsesBundledDefault() {
        val decoded: Ja2Json = json.decodeFromString("{}")

        assertNull(decoded.externalizedJsonDir)
    }
}
