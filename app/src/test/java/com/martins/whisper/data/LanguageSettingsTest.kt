package com.martins.whisper.data

import android.app.Application
import com.martins.whisper.core.asr.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class LanguageSettingsTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.deleteSharedPreferences(SettingsStore.PREFS_NAME)
    }

    @Test
    fun `default language is English and auto detection is opt-in`() {
        val settings = SettingsStore(app)

        assertEquals("en", SettingsStore.DEFAULT_LANGUAGE)
        assertEquals("en", settings.language)
        assertEquals("en", settings.effectiveLanguage())
        assertEquals(setOf("auto", "en", "cs"), SettingsStore.SUPPORTED_LANGUAGES)
        assertTrue(SettingsStore.LANGUAGE_AUTO in SettingsStore.SUPPORTED_LANGUAGES)
    }

    @Test
    fun `selected Czech is persisted and passed to the engine`() {
        SettingsStore(app).language = SettingsStore.LANGUAGE_CZECH

        val reloaded = SettingsStore(app)
        assertEquals("cs", reloaded.language)
        assertEquals("cs", reloaded.effectiveLanguage())
    }

    @Test
    fun `auto language leaves whisper to detect`() {
        SettingsStore(app).language = SettingsStore.LANGUAGE_AUTO

        assertNull(SettingsStore(app).languageOrNull())
        assertNull(SettingsStore(app).effectiveLanguage())
    }

    @Test
    fun `an unsupported stored language falls back to English`() {
        app.getSharedPreferences(SettingsStore.PREFS_NAME, Application.MODE_PRIVATE)
            .edit()
            .putString(SettingsStore.KEY_LANGUAGE, "de")
            .commit()

        assertEquals("en", SettingsStore(app).language)
        assertEquals("en", SettingsStore(app).effectiveLanguage())
    }

    @Test
    fun `default model stays the multilingual base model`() {
        assertEquals("base", SettingsStore.DEFAULT_MODEL_ID)
        val base = ModelCatalog.byId(SettingsStore.DEFAULT_MODEL_ID)
        assertEquals("base", base?.id)
        assertTrue(base!!.multilingual)
        assertFalse(ModelCatalog.byId("base.en")!!.multilingual)
    }
}
