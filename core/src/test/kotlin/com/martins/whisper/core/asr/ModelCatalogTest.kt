package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `default is multilingual base`() {
        assertEquals("base", ModelCatalog.DEFAULT_MODEL_ID)
        assertEquals("base", ModelCatalog.default.id)
        assertTrue(ModelCatalog.default.multilingual)
        assertEquals(
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            ModelCatalog.default.url,
        )
    }

    @Test
    fun `required models are present`() {
        val ids = ModelCatalog.specs.map { it.id }
        assertTrue(ids.containsAll(listOf("tiny", "base", "small", "medium", "large-v3-turbo")))
        assertTrue(ids.containsAll(listOf("tiny.en", "base.en", "small.en", "medium.en")))
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `catalog entries are internally consistent`() {
        ModelCatalog.specs.forEach { spec ->
            assertTrue(spec.url!!.startsWith("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-"))
            assertTrue(spec.url.endsWith(spec.fileName))
            assertNotNull("size missing for ${spec.id}", spec.sizeBytes)
            assertTrue(spec.sizeBytes!! > 0)
            assertNotNull("sha missing for ${spec.id}", spec.sha256)
            assertTrue("bad sha for ${spec.id}", spec.sha256!!.matches(Regex("[0-9a-f]{64}")))
            assertEquals(spec.multilingual, !spec.id.endsWith(".en"))
        }
    }

    @Test
    fun `lookup works and unknown id is null`() {
        assertEquals("large-v3-turbo", ModelCatalog.byId("large-v3-turbo")?.id)
        assertNull(ModelCatalog.byId("nope"))
        assertFalse(ModelCatalog.isKnown("nope"))
        try {
            ModelCatalog.requireById("nope")
            throw AssertionError("expected exception")
        } catch (_: NoSuchElementException) {
        }
    }

    @Test
    fun `custom model has no url`() {
        val custom = ModelCatalog.custom(id = "my-model", fileName = "my-model.bin")
        assertNull(custom.url)
        assertNull(custom.sha256)
        assertTrue(custom.multilingual)
        assertEquals("Custom imported model.", custom.notes)
    }

    @Test
    fun `custom model validates inputs`() {
        listOf("", "..", "../evil", "a/b").forEach { bad ->
            try {
                ModelCatalog.custom(id = bad, fileName = "x.bin")
                throw AssertionError("expected rejection for '$bad'")
            } catch (_: IllegalArgumentException) {
            }
        }
        try {
            ModelCatalog.custom(id = "ok", fileName = "x.bin", sha256 = "not-a-hash")
            throw AssertionError("expected bad sha rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `verified hashes anchor known files`() {
        assertEquals(
            "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
            ModelCatalog.byId("base")!!.sha256,
        )
        assertEquals(147_951_465L, ModelCatalog.byId("base")!!.sizeBytes)
        assertEquals(
            "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
            ModelCatalog.byId("tiny")!!.sha256,
        )
        assertEquals(77_691_713L, ModelCatalog.byId("tiny")!!.sizeBytes)
    }
}
