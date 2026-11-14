package app.relay.android.platform.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import app.relay.android.RelayApplication
import kotlinx.coroutines.launch

/**
 * PackageInstaller's answer. Success needs nothing here (the app is replaced; BootReceiver takes
 * over in the new version). A confirmation Android wants opens now if the app is on screen, or
 * through a notification; a failure goes back to the updater, which restores the gateway.
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val container = (context.applicationContext as RelayApplication).container
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                container.updateNotifications.tapToFinish(confirm)
                // Android blocks activity starts from the background; on screen, open it right away.
                if (container.inForeground) runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val reason = reason(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
                val pending = goAsync()
                container.scope.launch {
                    try {
                        container.gateway.appUpdates.onInstallFailed(reason)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_INTENT)

    private fun reason(status: Int, message: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "Another version of the app is in the way"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This phone can't run the new version"
        PackageInstaller.STATUS_FAILURE_INVALID -> "The download isn't a valid update"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install"
        else -> message ?: "Install failed ($status)"
    }

    companion object {
        const val ACTION = "app.relay.android.INSTALL_STATUS"
    }
}
