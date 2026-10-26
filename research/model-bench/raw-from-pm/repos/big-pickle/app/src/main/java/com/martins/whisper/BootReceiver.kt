package com.martins.whisper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.DictationService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val settings = SettingsStore(context)
        if (!settings.overlayEnabled || !settings.bootStart) return
        val serviceIntent = Intent(context, DictationService::class.java)
            .setAction(DictationService.ACTION_START)
            .putExtra(DictationService.EXTRA_NO_MIC, true)
        runCatching { context.startForegroundService(serviceIntent) }
    }
}
