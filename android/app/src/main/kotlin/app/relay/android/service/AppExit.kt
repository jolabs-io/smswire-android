package app.relay.android.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * Stops the gateway and ends the app's process, so nothing runs in the background until the
 * app is opened again (or the phone restarts, with "start on boot" on).
 */
object AppExit {
    /** Time for the service to close the relay socket cleanly, so the server sees it go at once. */
    private const val CLOSE_GRACE_MS = 600L

    fun now(context: Context) {
        GatewayService.stop(context)
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, CLOSE_GRACE_MS)
    }
}
