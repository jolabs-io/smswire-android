package app.relay.android

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import app.relay.android.data.DataStoreDeviceIdentityStore
import app.relay.android.data.DataStoreSettingsRepository
import app.relay.android.data.RoomBatchRepository
import app.relay.android.data.RoomInboundRepository
import app.relay.android.data.RoomMessageRepository
import app.relay.android.data.RoomProjectRepository
import app.relay.android.data.db.RelayDatabase
import app.relay.android.designsystem.component.ToastController
import app.relay.android.network.OkHttpRelayApi
import app.relay.android.network.OkHttpRelayConnection
import app.relay.android.platform.AndroidClock
import app.relay.android.platform.AndroidDeviceStatus
import app.relay.android.service.GatewayService
import app.relay.android.startup.GatewayServiceController
import app.relay.android.startup.StartupCoordinator
import app.relay.android.telephony.AndroidPowerMonitor
import app.relay.android.telephony.AndroidSimCardProvider
import app.relay.android.telephony.AndroidSmsQuota
import app.relay.android.telephony.AndroidSmsSender
import app.relay.android.telephony.IncomingSmsSink
import app.relay.android.telephony.SmsResultTracker
import app.relay.gateway.GatewayComponent
import app.relay.gateway.GatewayPorts
import app.relay.gateway.service.ticker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Composition root: the one place that knows which implementation backs each port. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock = AndroidClock()

    private val database = RelayDatabase.create(app)
    private val settings = DataStoreSettingsRepository(app)
    private val identity = DataStoreDeviceIdentityStore(app)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val sims = AndroidSimCardProvider(app)

    val smsResults = SmsResultTracker(clock)

    /** The relay socket; [NetworkWatcher] reconnects it when the network changes. */
    val connection = OkHttpRelayConnection(http, clock, scope)

    val gateway = GatewayComponent(
        GatewayPorts(
            messages = RoomMessageRepository(database.messages()),
            batches = RoomBatchRepository(database.batches()),
            projects = RoomProjectRepository(database.projects()),
            settings = settings,
            identity = identity,
            connection = connection,
            api = OkHttpRelayApi(http, settings, identity),
            sender = AndroidSmsSender(app, sims, smsResults),
            smsEvents = smsResults,
            sims = sims,
            power = AndroidPowerMonitor(app),
            clock = clock,
            appVersion = BuildConfig.VERSION_NAME,
            smsQuota = AndroidSmsQuota(app),
            phoneKey = phoneKey(app),
            inbound = RoomInboundRepository(database.incoming()),
            receivesSms = BuildConfig.RECEIVES_SMS,
        ),
    )

    init {
        // Received SMS go to the web inbox (stored first, so nothing is lost while offline).
        IncomingSmsSink.handler = { sms ->
            val slot = sms.subscriptionId?.let { id -> sims.available().firstOrNull { it.subscriptionId == id }?.slot }
            scope.launch { gateway.inboundSync.receive(sms.from, sms.body, sms.sentAt, sms.receivedAt, slot) }
        }
    }

    val toast = ToastController()

    /** A pairing link opened from outside the app: the phone's camera scanned the web's QR. */
    val pairingLinks = MutableStateFlow<String?>(null)
    val uiTicker = ticker(10_000)
    val deviceStatus = AndroidDeviceStatus(app, gateway.smsQuota, BuildConfig.RECEIVES_SMS)

    /** How this phone introduces itself when pairing. */
    val deviceName = "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}"
    val deviceModel: String = Build.MODEL

    /** Prefilled on the pairing screen; a scanned QR brings its own. Set per build type in build.gradle.kts. */
    val defaultServerUrl: String = BuildConfig.DEFAULT_SERVER_URL

    val startup = StartupCoordinator(
        setup = gateway.deviceSetup,
        projects = gateway.projectService,
        settings = settings,
        service = object : GatewayServiceController {
            // Android 12+ refuses foreground-service starts from the background; the next
            // onForeground() or boot retries.
            override fun start() {
                runCatching { GatewayService.start(app) }
            }
            override fun stop() = GatewayService.stop(app)
        },
        scope = scope,
    )
}

/**
 * Android's per-app id for this phone: survives reinstalling and clearing data (same signing key),
 * changes on a factory reset. Only used so pairing again finds the phone's old record.
 */
@SuppressLint("HardwareIds")
private fun phoneKey(context: Context): String? =
    runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()?.takeIf { it.isNotBlank() }
