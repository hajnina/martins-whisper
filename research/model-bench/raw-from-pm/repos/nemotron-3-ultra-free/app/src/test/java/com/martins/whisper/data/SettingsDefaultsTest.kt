package com.martins.whisper.data

import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.ui.ModelOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun `default model is base and exists in catalog stub`() {
        assertEquals("base", SettingsStore.DEFAULT_MODEL_ID)
        assertNotNull(ModelOptions.byId("base"))
        assertEquals("base", ModelOptions.byId(SettingsStore.DEFAULT_MODEL_ID)?.id)
    }

    @Test
    fun `catalog covers product requirement ids`() {
        val ids = ModelOptions.specs.map { it.id }.toSet()
        assertEquals(
            setOf("tiny", "base", "small", "medium", "large-v3-turbo"),
            ids,
        )
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
        assertNull(ModelOptions.byId("missing"))
    }
}
