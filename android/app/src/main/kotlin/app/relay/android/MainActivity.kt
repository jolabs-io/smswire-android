package app.relay.android

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayTheme
import app.relay.android.feature.settings.CheckAction
import app.relay.android.platform.RestrictedSettings
import app.relay.android.platform.updates.UpdateNotifications
import app.relay.android.service.AppExit
import app.relay.android.telephony.DefaultSmsApp
import app.relay.android.ui.RelayApp
import app.relay.android.ui.RestrictedSmsState
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as RelayApplication).container

    /** Android's block on SMS for apps installed from a browser, and how to lift it. */
    private val restrictedSms = RestrictedSmsState()

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.startup.onForeground()
        if (smsBlocked()) restrictedSms.show()
    }

    private val sendSmsRequest = smsPermissionRequest(Manifest.permission.SEND_SMS)
    private val receiveSmsRequest = smsPermissionRequest(Manifest.permission.RECEIVE_SMS)
    private val notificationsRequest = permissionRequest(Manifest.permission.POST_NOTIFICATIONS, ::openNotificationSettings)

    /** If the user denied for good the dialog no longer shows, so [fallback] opens the system page for it. */
    private fun permissionRequest(permission: String, fallback: () -> Unit) =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && !shouldShowRequestPermissionRationale(permission)) fallback()
        }

    /** As [permissionRequest], but when Android blocked SMS outright ("App was denied access"), explain how to lift that. */
    private fun smsPermissionRequest(permission: String) =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when {
                granted -> Unit
                RestrictedSettings.blockSms(this) -> restrictedSms.show()
                !shouldShowRequestPermissionRationale(permission) -> openAppSettings()
            }
        }

    private fun smsBlocked() = !smsGranted() && RestrictedSettings.blockSms(this)

    /** The send-only build has no RECEIVE_SMS to ask for: sending is all it needs. */
    private fun smsGranted() =
        granted(Manifest.permission.SEND_SMS) && (!BuildConfig.RECEIVES_SMS || granted(Manifest.permission.RECEIVE_SMS))

    /** The role prompt closes at once unless started for a result. The settings screen re-checks on resume. */
    private val defaultSmsAppRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState?.getBoolean(RESTRICTED_SMS_OPEN) == true) restrictedSms.show()
        setContent {
            RelayTheme {
                CompositionLocalProvider(LocalAppName provides getString(R.string.app_name)) {
                    RelayApp(
                        container,
                        onFix = ::fix,
                        onExit = ::exit,
                        scan = ::scanQr,
                        restrictedSms = restrictedSms,
                        onOpenAppInfo = ::openAppSettings,
                        onPaired = ::onPaired,
                    )
                }
            }
        }
        requestMissingPermissions()
        container.startup.start()
        // Opened right after an update this app installed: resume the gateway, show what's new.
        container.scope.launch { container.gateway.appUpdates.finishUpdate() }
        takePairingLink(intent)
        takeUpdateTap(intent)
    }

    // singleTask: a link opened while the app is running arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takePairingLink(intent)
        takeUpdateTap(intent)
    }

    /** An update notification was tapped: open the update sheet. */
    private fun takeUpdateTap(intent: Intent?) {
        if (intent?.action == UpdateNotifications.ACTION_OPEN_UPDATE) container.openUpdate.value = true
    }

    /** The camera app opened the web's pairing QR: the pairing screen fills the server and code from it. */
    private fun takePairingLink(intent: Intent?) {
        val link = intent?.data?.takeIf { it.scheme == "smswire" }?.toString() ?: return
        container.pairingLinks.value = link
    }

    override fun onStart() {
        super.onStart()
        container.inForeground = true
        container.startup.onForeground()
        // Back from App info with SMS allowed: the help has done its job.
        if (smsGranted()) restrictedSms.dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(RESTRICTED_SMS_OPEN, restrictedSms.open)
    }

    override fun onStop() {
        super.onStop()
        container.inForeground = false
        if (!isChangingConfigurations) container.startup.onBackground()
    }

    /** Power button or Settings → Exit, once confirmed: close the app's screens, then stop the gateway and end the process. */
    private fun exit() {
        finishAndRemoveTask()
        AppExit.now(this)
    }

    /**
     * Google's code scanner: it runs in Play services and hands back only the scanned text,
     * so the app needs no camera permission.
     */
    private fun scanQr(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.let(onResult) ?: onError("Couldn't read that code. Try again or type it.") }
            .addOnFailureListener { onError("The scanner isn't available on this phone. Type the code instead.") }
    }

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.SEND_SMS)
        if (BuildConfig.RECEIVES_SMS) add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestMissingPermissions() {
        val missing = requiredPermissions().filterNot(::granted)
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    /**
     * Just paired: ask to run unrestricted in the background now, not only when the user finds
     * the check in Settings. Under battery optimization, Android holds back the socket and sends
     * while the screen is off.
     */
    private fun onPaired() {
        if (!container.deviceStatus.ignoresBatteryOptimisations()) requestBatteryExemption()
    }

    /** Android's "let the app always run in the background?" dialog; the list of apps where a phone maker removed it. */
    private fun requestBatteryExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun fix(action: CheckAction) {
        when (action) {
            CheckAction.BATTERY -> requestBatteryExemption()
            CheckAction.SMS_PERMISSION -> sendSmsRequest.launch(Manifest.permission.SEND_SMS)
            CheckAction.RECEIVE_SMS_PERMISSION -> if (BuildConfig.RECEIVES_SMS) receiveSmsRequest.launch(Manifest.permission.RECEIVE_SMS)
            // Granted but switched off (or below Android 13, no permission at all): only the settings page turns them on.
            CheckAction.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                    notificationsRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings()
                }
            // The send-only build has no default-SMS-app components, so Android wouldn't offer the role.
            CheckAction.DEFAULT_SMS_APP -> if (BuildConfig.RECEIVES_SMS) defaultSmsAppRequest.launch(DefaultSmsApp.intent(this))
            CheckAction.SMS_LIMIT -> Unit
            CheckAction.INSTALL_UPDATES ->
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
        }
    }

    private companion object {
        const val RESTRICTED_SMS_OPEN = "restrictedSmsOpen"
    }
}
