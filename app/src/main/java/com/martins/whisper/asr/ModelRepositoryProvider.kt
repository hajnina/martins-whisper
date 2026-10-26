package com.martins.whisper.asr

import android.content.Context
import com.martins.whisper.core.asr.ModelRepository

/**
 * Process-wide model repository rooted at `<filesDir>/models`.
 *
 * Callers must not create their own [ModelRepository] for the app: the downloader, the UI and the
 * engine factory have to agree on one directory, otherwise a successful download is invisible to
 * the engine.
 */
object ModelRepositoryProvider {

    @Volatile
    private var instance: ModelRepository? = null

    fun forContext(context: Context): ModelRepository {
        instance?.let { return it }
        val appContext = context.applicationContext ?: context
        return synchronized(this) {
            instance ?: ModelRepository.forFilesDir(appContext.filesDir).also { instance = it }
        }
    }

    /** Tests only: forget the cached repository after swapping the app's filesDir. */
    fun resetForTests() {
        synchronized(this) { instance = null }
    }
}
