package com.martins.whisper.data

import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.asr.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun `default model is base and exists in the catalog`() {
        assertEquals("base", SettingsStore.DEFAULT_MODEL_ID)
        assertNotNull(ModelCatalog.byId("base"))
        assertEquals("base", ModelCatalog.byId(SettingsStore.DEFAULT_MODEL_ID)?.id)
        assertEquals(ModelCatalog.DEFAULT_MODEL_ID, SettingsStore.DEFAULT_MODEL_ID)
    }

    @Test
    fun `catalog covers product requirement ids`() {
        val ids = ModelCatalog.specs.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("tiny", "base", "small", "medium", "large-v3-turbo")))
    }

    @Test
    fun `idle unload default is five minutes`() {
        assertEquals(300, SettingsStore.DEFAULT_IDLE_UNLOAD_SECONDS)
    }

    @Test
    fun `backend parser is defensive`() {
        assertEquals(ComputeBackend.AUTO, SettingsStore.parseBackend(null))
        assertEquals(ComputeBackend.AUTO, SettingsStore.parseBackend("nonsense"))
        assertEquals(ComputeBackend.GPU, SettingsStore.parseBackend("GPU"))
        assertEquals(ComputeBackend.CPU, SettingsStore.parseBackend("CPU"))
    }

    @Test
    fun `auto language has no engine override`() {
        assertEquals("auto", SettingsStore.LANGUAGE_AUTO)
        assertNull(ModelCatalog.byId("missing"))
    }
}
