package app.relay.android.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.relay.android.feature.settings.DeviceStatusProvider
import app.relay.android.telephony.DefaultSmsApp
import app.relay.gateway.port.Clock
import app.relay.gateway.port.SmsQuota
import app.relay.gateway.port.SmsQuotaProvider
import java.time.LocalDate
import java.time.ZoneId

class AndroidClock : Clock {
    override fun now(): Long = System.currentTimeMillis()

    override fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** [receivesSms] is false in the send-only build: no RECEIVE_SMS, and it can't be the default SMS app. */
class AndroidDeviceStatus(
    private val context: Context,
    private val quota: SmsQuotaProvider,
    private val receivesSms: Boolean,
) : DeviceStatusProvider {
    override fun receivesSms(): Boolean = receivesSms

    override fun ignoresBatteryOptimisations(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    override fun smsQuota(): SmsQuota? = quota.current()

    override fun smsPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    override fun receiveSmsPermissionGranted(): Boolean =
        receivesSms && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    override fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun defaultSmsAppLiftsLimit(): Boolean = receivesSms && DefaultSmsApp.liftsLimit()

    override fun isDefaultSmsApp(): Boolean = receivesSms && DefaultSmsApp.isHeld(context)
}
