package app.relay.android.platform

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi

/**
 * Android 15+ keeps SMS from apps installed from a browser or a file ("restricted settings"):
 * asking only shows "App was denied access" until the user taps ⋮ → Allow restricted settings
 * in App info. The system marks such apps with this app op; allowing them sets it to allowed.
 */
object RestrictedSettings {
    private const val OP = "android:access_restricted_settings"

    fun blockSms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val mode = try {
            context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(OP, Process.myUid(), context.packageName)
        } catch (_: RuntimeException) {
            // No such op on this build (IllegalArgumentException), or the build refuses apps this check
            // (SecurityException from AppOpsService.verifyIncomingOp, seen on Samsung): go by where the
            // app came from. Thrown here, it crashed the permission result ("Failure delivering result").
            AppOpsManager.MODE_DEFAULT
        }
        return when (mode) {
            AppOpsManager.MODE_ERRORED -> true
            AppOpsManager.MODE_DEFAULT -> sideloaded(context)
            else -> false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun sideloaded(context: Context): Boolean = try {
        val source = context.packageManager.getInstallSourceInfo(context.packageName).packageSource
        source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE || source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
    } catch (_: Exception) {
        false
    }
}
