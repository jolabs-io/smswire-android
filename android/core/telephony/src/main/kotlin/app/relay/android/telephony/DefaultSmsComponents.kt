package app.relay.android.telephony

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat

/*
 * Components Android requires of the default SMS app (see DefaultSmsApp). Relay is a
 * gateway, so they do the minimum: keep incoming SMS, answer "reply with a message".
 */

/**
 * Only the default SMS app receives incoming SMS, and it must store them. They go into the
 * system inbox (so any messaging app shows them after switching back) and a notification.
 */
class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sms = readSms(intent) ?: return
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sms.from)
            put(Telephony.Sms.BODY, sms.body)
            put(Telephony.Sms.DATE, sms.receivedAt)
            put(Telephony.Sms.DATE_SENT, sms.sentAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        runCatching { context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }
        notify(context, sms.from, sms.body)
    }

    private fun notify(context: Context, from: String, body: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Incoming SMS", NotificationManager.IMPORTANCE_DEFAULT))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(from)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "incoming_sms"
    }
}

/** MMS isn't supported; Android only requires the receiver to exist. */
class MmsPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** "Reply with a message" when declining a call. */
class RespondViaMessageService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val to = intent?.data?.schemeSpecificPart?.substringBefore('?')
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!to.isNullOrBlank() && !text.isNullOrBlank()) {
            runCatching {
                val manager = checkNotNull(smsManager(this, subscriptionId = null))
                manager.sendMultipartTextMessage(to, null, manager.divideMessage(text), null, null)
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
