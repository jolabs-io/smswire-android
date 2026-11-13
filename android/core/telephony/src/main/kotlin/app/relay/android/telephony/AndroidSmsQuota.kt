package app.relay.android.telephony

import android.content.Context
import android.provider.Settings
import app.relay.gateway.port.SmsQuota
import app.relay.gateway.port.SmsQuotaProvider

/**
 * Android asks the user to confirm once an app sends more than `sms_outgoing_check_max_count`
 * parts per `sms_outgoing_check_interval_ms`. The default SMS app is exempt on Android 12+.
 */
class AndroidSmsQuota(private val context: Context) : SmsQuotaProvider {
    override fun current(): SmsQuota? {
        if (DefaultSmsApp.liftsLimit() && DefaultSmsApp.isHeld(context)) return null
        val resolver = context.contentResolver
        return SmsQuota(
            maxParts = Settings.Global.getInt(resolver, MAX_COUNT_KEY, DEFAULT_MAX_COUNT),
            windowMs = Settings.Global.getLong(resolver, INTERVAL_KEY, DEFAULT_INTERVAL_MS),
        )
    }

    private companion object {
        // Only adb can write these, and Android reads them once at boot.
        const val MAX_COUNT_KEY = "sms_outgoing_check_max_count"
        const val INTERVAL_KEY = "sms_outgoing_check_interval_ms"
        const val DEFAULT_MAX_COUNT = 30
        const val DEFAULT_INTERVAL_MS = 60_000L
    }
}
