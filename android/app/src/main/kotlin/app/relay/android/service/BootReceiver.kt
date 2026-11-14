package app.relay.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.relay.android.BuildConfig
import app.relay.android.RelayApplication
import kotlinx.coroutines.launch

/**
 * Restarts the gateway after a reboot or app update when "Start on boot" is on, and always after an
 * update this app installed (which paused it first).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as RelayApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                val updated = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED && container.gateway.appUpdates.finishUpdate()
                if (updated) container.updateNotifications.updated(BuildConfig.VERSION_NAME)
                val paired = container.gateway.deviceSetup.current() != null
                if (paired && (updated || container.gateway.settings.current().startOnBoot)) GatewayService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
