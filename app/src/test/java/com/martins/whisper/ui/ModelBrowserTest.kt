package com.martins.whisper.ui

import com.martins.whisper.core.asr.EngineState
import com.martins.whisper.core.asr.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBrowserTest {

    private val specs = ModelCatalog.specs.take(2)

    private fun browser(installed: Set<String> = emptySet(), selected: String = specs.first().id) =
        ModelBrowser(specs, installed, selected)

    @Test
    fun `installed and not installed rows render from the catalog`() {
        val state = browser(installed = setOf(specs[0].id), selected = specs[0].id).state
        assertEquals(2, state.rows.size)
        assertTrue(state.rows[0].installed)
        assertTrue(state.rows[0].selected)
        assertFalse(state.rows[1].installed)
        assertFalse(state.rows[1].selected)
        assertEquals("Installed \u00b7 selected", ModelPresentation.statusLabel(true, true))
        assertEquals("Not installed", ModelPresentation.statusLabel(false, false))
    }

    @Test
    fun `download moves through progress and installed state`() {
        val browser = browser()
        assertTrue(browser.begin(specs[1].id, ModelAction.DOWNLOAD))
        assertTrue(browser.state.isBusy)
        assertEquals(specs[1].id, browser.state.transfer?.modelId)
        assertNull((browser.state.transfer as ModelTransfer.Download).fraction)

        browser.progress(0.42f)
        assertEquals(0.42f, (browser.state.transfer as ModelTransfer.Download).fraction)

        browser.completeInstalled(specs[1].id)
        assertNull(browser.state.transfer)
        assertTrue(browser.state.row(specs[1].id)!!.installed)
        assertEquals("Model ready", browser.state.message)
    }

    @Test
    fun `import failure clears the transfer and reports the error`() {
        val browser = browser()
        assertTrue(browser.begin(specs[0].id, ModelAction.IMPORT))
        assertTrue(browser.state.transfer is ModelTransfer.Import)
        browser.fail("SHA256 mismatch")
        assertNull(browser.state.transfer)
        assertFalse(browser.state.isBusy)
        assertEquals("SHA256 mismatch", browser.state.error)
        assertFalse(browser.state.row(specs[0].id)!!.installed)
    }

    @Test
    fun `delete removes an installed model`() {
        val browser = browser(installed = setOf(specs[0].id))
        assertTrue(browser.begin(specs[0].id, ModelAction.DELETE))
        assertTrue(browser.state.transfer is ModelTransfer.Delete)
        browser.completeDeleted(specs[0].id)
        assertNull(browser.state.transfer)
        assertFalse(browser.state.row(specs[0].id)!!.installed)
        assertEquals("Model deleted", browser.state.message)
    }

    @Test
    fun `delete is rejected for a model that is not installed`() {
        val browser = browser()
        assertFalse(browser.begin(specs[0].id, ModelAction.DELETE))
        assertNull(browser.state.transfer)
    }

    @Test
    fun `only one transfer can run at a time`() {
        val browser = browser()
        assertTrue(browser.begin(specs[0].id, ModelAction.DOWNLOAD))
        assertFalse(browser.begin(specs[1].id, ModelAction.IMPORT))
        assertEquals(specs[0].id, browser.state.transfer?.modelId)
    }

    @Test
    fun `selection survives a filesystem sync`() {
        val browser = browser(selected = specs[1].id)
        browser.syncInstalled(setOf(specs[0].id))
        assertTrue(browser.state.row(specs[0].id)!!.installed)
        assertTrue(browser.state.row(specs[1].id)!!.selected)
        assertFalse(browser.state.row(specs[1].id)!!.installed)
    }

    @Test
    fun `a custom model is added to the browser when its import starts`() {
        val browser = browser()
        val custom = ModelCatalog.custom(id = "custom-mine", fileName = "mine.bin", displayName = "Mine")
        assertTrue(browser.begin(custom, ModelAction.IMPORT))
        assertEquals(custom, browser.state.row("custom-mine")!!.spec)
        assertFalse(browser.state.row("custom-mine")!!.installed)

        browser.progress(0.25f)
        assertEquals(0.25f, (browser.state.transfer as ModelTransfer.Import).fraction)

        browser.completeInstalled("custom-mine")
        browser.select("custom-mine")
        assertTrue(browser.state.row("custom-mine")!!.installed)
        assertTrue(browser.state.row("custom-mine")!!.selected)
    }

    @Test
    fun `setSpecs forgets a deleted custom model and falls back the selection`() {
        val custom = ModelCatalog.custom(id = "custom-mine", fileName = "mine.bin")
        val browser = ModelBrowser(specs + custom, setOf(custom.id), custom.id)

        browser.setSpecs(specs)

        assertNull(browser.state.row(custom.id))
        assertFalse(custom.id in browser.installedIds())
        assertEquals(specs.first().id, browser.state.rows.first { it.selected }.spec.id)
    }

    @Test
    fun `a new custom spec is not added while another transfer runs`() {
        val browser = browser()
        assertTrue(browser.begin(specs[0].id, ModelAction.DOWNLOAD))

        val custom = ModelCatalog.custom(id = "custom-mine", fileName = "mine.bin")
        assertFalse(browser.begin(custom, ModelAction.IMPORT))

        assertNull(browser.state.row(custom.id))
        assertEquals(specs[0].id, browser.state.transfer?.modelId)
    }

    @Test
    fun `presentation labels cover fallback reasons and sizes`() {
        assertEquals("native engine unavailable", ModelPresentation.fallbackLabel(EngineState.NATIVE_UNAVAILABLE))
        assertEquals("model not installed", ModelPresentation.fallbackLabel(EngineState.MODEL_MISSING))
        assertEquals("GPU unavailable; using CPU", ModelPresentation.fallbackLabel(EngineState.GPU_UNAVAILABLE))
        assertEquals("auto selected CPU", ModelPresentation.fallbackLabel(EngineState.AUTO_CPU))
        assertEquals("engine error", ModelPresentation.fallbackLabel(EngineState.ENGINE_ERROR))
        assertEquals("none", ModelPresentation.fallbackLabel(null))
        assertEquals("148 MB", ModelPresentation.sizeLabel(147_951_465L))
        assertEquals("unknown size", ModelPresentation.sizeLabel(null))
        assertEquals(
            "Downloading Base\u2026 50%",
            ModelPresentation.transferLabel(ModelTransfer.Download("base", 0.5f), "Base"),
        )
    }
}
