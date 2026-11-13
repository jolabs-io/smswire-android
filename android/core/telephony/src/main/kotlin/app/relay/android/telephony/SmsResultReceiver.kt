package app.relay.android.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SmsMessage

/** Receives the sent / delivered PendingIntents created by [AndroidSmsSender]. */
class SmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tracker = (context.applicationContext as? SmsResultTrackerProvider)?.smsResultTracker ?: return
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val part = intent.getIntExtra(EXTRA_PART, 0)
        when (intent.action) {
            ACTION_SENT -> tracker.onPartSent(messageId, part, if (resultCode == Activity.RESULT_OK) null else errorName(resultCode))
            ACTION_DELIVERED -> tracker.onPartDelivered(messageId, part, deliveryOutcome(intent))
        }
    }

    private fun deliveryOutcome(intent: Intent): DeliveryOutcome {
        val pdu = intent.getByteArrayExtra("pdu") ?: return DeliveryOutcome.DELIVERED
        val format = intent.getStringExtra("format") ?: "3gpp"
        val status = SmsMessage.createFromPdu(pdu, format)?.status ?: return DeliveryOutcome.DELIVERED
        return if (format == "3gpp2") {
            if (status == 0) DeliveryOutcome.DELIVERED else DeliveryOutcome.FAILED
        } else {
            // GSM TP-Status: 0x00–0x1F completed, 0x20–0x3F still trying, 0x40+ failed.
            when {
                status < 0x20 -> DeliveryOutcome.DELIVERED
                status < 0x40 -> DeliveryOutcome.PENDING
                else -> DeliveryOutcome.FAILED
            }
        }
    }

    private fun errorName(code: Int) = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "RESULT_ERROR_GENERIC_FAILURE"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "RESULT_ERROR_NO_SERVICE"
        SmsManager.RESULT_ERROR_NULL_PDU -> "RESULT_ERROR_NULL_PDU"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "RESULT_ERROR_RADIO_OFF"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "RESULT_ERROR_LIMIT_EXCEEDED"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NOT_ALLOWED"
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED"
        else -> "RESULT_ERROR_$code"
    }

    companion object {
        const val ACTION_SENT = "app.relay.android.SMS_SENT"
        const val ACTION_DELIVERED = "app.relay.android.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PART = "part"
    }
}
