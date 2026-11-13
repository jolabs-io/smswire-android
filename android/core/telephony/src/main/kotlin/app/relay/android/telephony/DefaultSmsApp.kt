package app.relay.android.telephony

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony

/**
 * Relay as the phone's default SMS app. Android 12+ exempts the default SMS app from the
 * send limit; in exchange it must receive and store incoming SMS ([IncomingSmsReceiver]).
 */
object DefaultSmsApp {
    fun liftsLimit(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isHeld(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // getDefaultSmsPackage hides the caller's own role unless it holds every SMS app-op
            // (read SMS, WAP push, cell broadcasts…), which Relay doesn't request.
            context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

    /** The system prompt to become the default SMS app, or the default-apps page to switch back. */
    fun intent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isHeld(context)) {
            context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
}
