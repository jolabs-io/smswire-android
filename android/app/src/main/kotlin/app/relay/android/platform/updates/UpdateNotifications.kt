package app.relay.android.platform.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.relay.android.MainActivity
import app.relay.android.R
import app.relay.gateway.port.UpdateNotifier
import app.relay.protocol.AppUpdate

/** Update notifications, on their own channels (critical ones can't be swiped away). */
class UpdateNotifications(private val context: Context) : UpdateNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val app get() = context.getString(R.string.app_name)

    init {
        manager?.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        manager?.createNotificationChannel(NotificationChannel(CHANNEL_CRITICAL, "Important app updates", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun available(update: AppUpdate) = post(
        update.critical,
        if (update.critical) "Important: update $app to ${update.versionName}" else "$app ${update.versionName} is available",
        "Tap to see what's new and install.",
    )

    override fun ready(update: AppUpdate) = post(update.critical, "$app ${update.versionName} is ready to install", "Tap to install.")

    override fun failed(update: AppUpdate, reason: String) = post(false, "Couldn't update to ${update.versionName}", "$reason. Tap to try again.")

    /** Android wants a tap to confirm the install; the notification opens its prompt. */
    fun tapToFinish(confirm: Intent) {
        val tap = PendingIntent.getActivity(context, ID, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notify(NotificationCompat.Builder(context, CHANNEL_CRITICAL).setContentTitle("Tap to finish updating $app").setContentIntent(tap))
    }

    /** The new version is running. */
    fun updated(versionName: String) =
        post(false, "$app updated to $versionName", "Tap to see what's new.")

    override fun clear() {
        manager?.cancel(ID)
    }

    private fun post(critical: Boolean, title: String, text: String) {
        val open = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_UPDATE).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notify(
            NotificationCompat.Builder(context, if (critical) CHANNEL_CRITICAL else CHANNEL)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(critical)
                .setContentIntent(tap),
        )
    }

    private fun notify(builder: NotificationCompat.Builder) {
        runCatching { manager?.notify(ID, builder.setSmallIcon(R.drawable.ic_stat_relay).setAutoCancel(true).build()) }
    }

    companion object {
        /** MainActivity opens the update sheet for this action. */
        const val ACTION_OPEN_UPDATE = "app.relay.android.OPEN_UPDATE"
        private const val CHANNEL = "updates"
        private const val CHANNEL_CRITICAL = "updates_critical"
        private const val ID = 4100
    }
}
