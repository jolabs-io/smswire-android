package app.relay.android.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** A received SMS, as the radio delivered it. [subscriptionId] says which SIM got it, when known. */
data class ReceivedSms(val from: String, val body: String, val sentAt: Long, val receivedAt: Long, val subscriptionId: Int?)

/**
 * Where received SMS go. The app sets this at startup (the gateway forwards them to the web
 * inbox); a broadcast that arrives first starts the process, which sets it before onReceive.
 */
object IncomingSmsSink {
    @Volatile var handler: ((ReceivedSms) -> Unit)? = null
}

/**
 * Every app holding RECEIVE_SMS gets SMS_RECEIVED, default SMS app or not, so replies reach
 * the inbox either way. (As default SMS app Relay also stores them; see IncomingSmsReceiver.)
 */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        readSms(intent)?.let { sms -> IncomingSmsSink.handler?.invoke(sms) }
    }
}

/** Joins a multipart SMS from a received-SMS broadcast. */
internal fun readSms(intent: Intent): ReceivedSms? {
    val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
    val first = parts.firstOrNull() ?: return null
    val from = first.displayOriginatingAddress ?: return null
    val subscription = intent.getIntExtra(SUBSCRIPTION_INDEX, intent.getIntExtra(LEGACY_SUBSCRIPTION, -1)).takeIf { it >= 0 }
    return ReceivedSms(from, parts.joinToString("") { it.displayMessageBody.orEmpty() }, first.timestampMillis, System.currentTimeMillis(), subscription)
}

// SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX (API 30) and the extra older releases used.
private const val SUBSCRIPTION_INDEX = "android.telephony.extra.SUBSCRIPTION_INDEX"
private const val LEGACY_SUBSCRIPTION = "subscription"
