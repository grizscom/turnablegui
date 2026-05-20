package ru.pnx.turnablegui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        if (!SettingsStore.loadAutoStartOnBoot(context)) {
            return
        }

        val config = SettingsStore.loadConfigText(context).trim()
        if (config.isBlank()) {
            return
        }

        runCatching {
            TurnableService.start(
                context = context.applicationContext,
                listenAddress = SettingsStore.loadListenAddress(context).trim(),
                configText = config,
                verbose = SettingsStore.loadVerbose(context)
            )
        }
    }
}
