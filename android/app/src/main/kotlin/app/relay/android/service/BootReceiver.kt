package app.relay.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.relay.android.RelayApplication
import kotlinx.coroutines.launch

/** Restarts the gateway after a reboot or app update when "Start on boot" is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as RelayApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                val paired = container.gateway.deviceSetup.current() != null
                if (paired && container.gateway.settings.current().startOnBoot) GatewayService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
