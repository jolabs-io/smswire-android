package app.relay.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Starting gateway…"), type)

        val container = (application as RelayApplication).container
        val gateway = container.gateway
        lifecycleScope.launch {
            if (gateway.deviceSetup.current() == null) {
                stopSelf()
                return@launch
            }
            engine = gateway.engine.start(lifecycleScope)
            network = NetworkWatcher(this@GatewayService, container.connection::reconnectNow).also { it.start() }
        }
        statusUpdates = lifecycleScope.launch {
            gateway.controller.status.map(::describe).distinctUntilChanged().collect { text ->
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(text))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_EXIT) {
            AppExit.now(this)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // Stop updating the notification first: closing the socket below flips the status to
        // "Reconnecting…", and a notify() after the service has stopped posts a stray notification
        // that outlives the app.
        statusUpdates?.cancel()
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
        .addAction(
            0,
            getString(R.string.action_exit),
            PendingIntent.getService(
                this,
                1,
                Intent(this, GatewayService::class.java).setAction(ACTION_EXIT),
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
        /** The notification's "Exit": stop the gateway and end the app. */
        private const val ACTION_EXIT = "app.relay.android.action.EXIT"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GatewayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GatewayService::class.java))
        }
    }
}
