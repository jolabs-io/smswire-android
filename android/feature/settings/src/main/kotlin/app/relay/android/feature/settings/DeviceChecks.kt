package app.relay.android.feature.settings

import app.relay.android.designsystem.format.Format
import app.relay.gateway.model.GatewaySettings

/** Android's cap on outgoing SMS as parts a minute; null when this app is exempt. */
fun smsLimitPerMinute(device: DeviceStatusProvider): Int? =
    device.smsQuota()?.let { (it.maxParts * 60_000L / it.windowMs.coerceAtLeast(1)).toInt() }

/**
 * The phone's health, one row per check, in the order Settings lists them. Amber rows carry a
 * one-tap fix; [needingAttention] picks the ones the Gateway screen and the tab badge show.
 */
fun deviceChecks(device: DeviceStatusProvider, s: GatewaySettings, limit: Int? = smsLimitPerMinute(device)): List<DeviceCheckUi> = listOfNotNull(
    device.ignoresBatteryOptimisations().let { ok ->
        DeviceCheckUi("Battery optimisation", if (ok) "Unrestricted" else "Optimised", if (ok) CheckTone.OK else CheckTone.WARN, if (ok) null else CheckAction.BATTERY)
    },
    // The app holds sends under this limit; above it Android would stop to ask the user.
    if (limit == null) {
        DeviceCheckUi("Android SMS limit", "None", CheckTone.OK, CheckAction.SMS_LIMIT)
    } else {
        DeviceCheckUi("Android SMS limit", "${Format.count(limit)} / min", if (limit >= s.maxRatePerMinute) CheckTone.OK else CheckTone.WARN, CheckAction.SMS_LIMIT)
    },
    device.smsPermissionGranted().let { ok ->
        DeviceCheckUi("Send SMS", if (ok) "Granted" else "Not granted", if (ok) CheckTone.OK else CheckTone.WARN, if (ok) null else CheckAction.SMS_PERMISSION)
    },
    device.receiveSmsPermissionGranted().takeIf { device.receivesSms() }?.let { ok ->
        DeviceCheckUi("Receive SMS", if (ok) "Granted" else "Replies lost", if (ok) CheckTone.OK else CheckTone.WARN, if (ok) null else CheckAction.RECEIVE_SMS_PERMISSION)
    },
    device.notificationsEnabled().let { ok ->
        DeviceCheckUi("Notifications", if (ok) "On" else "Off", if (ok) CheckTone.OK else CheckTone.WARN, if (ok) null else CheckAction.NOTIFICATIONS)
    },
)

/**
 * The checks that stop the phone sending, lose replies or hide its status: the amber ones with a
 * fix. Not the SMS limit, which only slows sending: the app already holds to it.
 */
fun List<DeviceCheckUi>.needingAttention(): List<DeviceCheckUi> =
    filter { it.tone == CheckTone.WARN && it.action != null && it.action != CheckAction.SMS_LIMIT }
