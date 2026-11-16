package com.martins.whisper.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.martins.whisper.service.DictationService

/**
 * Installs [InjectAudioSource] as the debug capture source. ContentProvider.onCreate runs before
 * every app component, so DictationService.onCreate sees the swapped factory. Debug source set
 * only; release builds never see this class.
 */
class GateE2EInit : ContentProvider() {

    override fun onCreate(): Boolean {
        DictationService.audioSourceFactory = { InjectAudioSource(it) }
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = 0
}
