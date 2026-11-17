package app.relay.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.relay.android.MainActivity
import app.relay.android.R
import app.relay.android.RelayApplication
import app.relay.android.platform.NetworkWatcher
import app.relay.gateway.engine.GatewayStatus
import app.relay.gateway.port.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the gateway running with the screen off: hosts the engine (relay socket, send loop,
 * radio callbacks) and shows the persistent status notification.
 */
class GatewayService : LifecycleService() {
    private var engine: Job? = null
    private var network: NetworkWatcher? = null
    private var statusUpdates: Job? = null
    private var shown = "Starting gateway…"

    /**
     * Since Android 14 people can swipe the notification away while the gateway runs, which
     * leaves no sign that it does: it comes straight back.
     */
    private val dismissed = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = post(shown)
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(shown), type)
        ContextCompat.registerReceiver(this, dismissed, IntentFilter(ACTION_DISMISSED), ContextCompat.RECEIVER_NOT_EXPORTED)

        val container = (application as RelayApplication).container
        val gateway = container.gateway
        engine = lifecycleScope.launch {
            if (gateway.deviceSetup.current() == null) {
                stopSelf()
                return@launch
            }
            network = NetworkWatcher(this@GatewayService, container.connection::reconnectNow).also { it.start() }
            // One engine per pairing: switching workspace (or pairing again) swaps in a new token,
            // which needs a fresh engine and socket. Unpaired, StartupCoordinator stops the service.
            gateway.deviceSetup.identity.distinctUntilChanged().collectLatest { identity ->
                if (identity != null) coroutineScope { gateway.engine.start(this) }
            }
        }
        statusUpdates = lifecycleScope.launch {
            gateway.controller.status.map(::describe).distinctUntilChanged().collect { text ->
                shown = text
                post(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        // Stop updating the notification first: closing the socket below flips the status to
        // "Reconnecting…", and a notify() after the service has stopped posts a stray notification
        // that outlives the app.
        statusUpdates?.cancel()
        unregisterReceiver(dismissed)
        network?.stop()
        engine?.cancel()
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun describe(status: GatewayStatus): String = when {
        !status.online -> if (status.heldCount > 0) "Paused · ${status.heldCount} held on server" else "Gateway paused"
        status.connection !is ConnectionState.Connected -> "Reconnecting to relay…"
        status.waitingForCharger -> "Waiting for charger · sends only while charging"
        else -> "Gateway is live"
    }

    private fun post(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_relay)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setDeleteIntent(
            PendingIntent.getBroadcast(
                this,
                2,
                Intent(ACTION_DISMISSED).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        // Opens the app's "Exit?" confirmation: a stray tap in the notification shade mustn't
        // take the gateway offline.
        .addAction(
            0,
            getString(R.string.action_exit),
            PendingIntent.getActivity(
                this,
                1,
                Intent(this, MainActivity::class.java).setAction(ACTION_CONFIRM_EXIT).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "gateway"
        private const val NOTIFICATION_ID = 1
        /** The notification's "Exit": the app asks before stopping the gateway. */
        const val ACTION_CONFIRM_EXIT = "app.relay.android.action.CONFIRM_EXIT"
        /** The notification was swiped away. */
        private const val ACTION_DISMISSED = "app.relay.android.action.GATEWAY_NOTIFICATION_DISMISSED"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GatewayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GatewayService::class.java))
        }
    }
}
