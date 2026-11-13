package app.relay.android.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import app.relay.gateway.port.OutgoingSms
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.port.SmsSendException
import app.relay.gateway.port.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends through the SIM's [SmsManager]; per-part results arrive at [SmsResultReceiver]. */
class AndroidSmsSender(
    private val context: Context,
    private val sims: SimCardProvider,
    private val tracker: SmsResultTracker,
) : SmsSender {

    override suspend fun send(sms: OutgoingSms) = withContext(Dispatchers.IO) {
        val manager = managerFor(sms.simSlot)
        val parts = manager.divideMessage(sms.body)
        val sent = ArrayList(parts.indices.map { intent(SmsResultReceiver.ACTION_SENT, sms.messageId, it) })
        val delivered = if (sms.requestDeliveryReport) {
            ArrayList(parts.indices.map { intent(SmsResultReceiver.ACTION_DELIVERED, sms.messageId, it) })
        } else {
            null
        }
        tracker.expect(sms.messageId, parts.size, awaitDelivery = sms.requestDeliveryReport)
        try {
            if (parts.size == 1) {
                manager.sendTextMessage(sms.to, null, parts[0], sent[0], delivered?.get(0))
            } else {
                manager.sendMultipartTextMessage(sms.to, null, parts, sent, delivered)
            }
        } catch (e: SecurityException) {
            tracker.forget(sms.messageId)
            throw SmsSendException("SMS permission not granted", e)
        } catch (e: RuntimeException) {
            tracker.forget(sms.messageId)
            throw SmsSendException(e.message ?: "RESULT_ERROR_GENERIC_FAILURE", e)
        }
    }

    /** A requested SIM must be present: falling back would send from a different number. */
    private fun managerFor(slot: Int?): SmsManager {
        val subscriptionId = slot?.let { s ->
            sims.available().firstOrNull { it.slot == s }?.subscriptionId ?: throw SmsSendException("SIM $s not available")
        }
        return smsManager(context, subscriptionId) ?: throw SmsSendException("This device has no SMS service")
    }

    private fun intent(action: String, messageId: String, part: Int): PendingIntent {
        val intent = Intent(context, SmsResultReceiver::class.java)
            .setAction(action)
            .putExtra(SmsResultReceiver.EXTRA_MESSAGE_ID, messageId)
            .putExtra(SmsResultReceiver.EXTRA_PART, part)
        // Mutable: the platform fills in the delivery PDU and error code. The intent is explicit.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, "$action|$messageId|$part".hashCode(), intent, flags)
    }
}

/** The [SmsManager] for a SIM subscription, or the default SIM's when [subscriptionId] is null. */
internal fun smsManager(context: Context, subscriptionId: Int?): SmsManager? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(SmsManager::class.java)?.let { base ->
            if (subscriptionId != null) base.createForSubscriptionId(subscriptionId) else base
        }
    } else {
        @Suppress("DEPRECATION")
        if (subscriptionId != null) SmsManager.getSmsManagerForSubscriptionId(subscriptionId) else SmsManager.getDefault()
    }
