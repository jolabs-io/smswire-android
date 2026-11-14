package app.relay.gateway.engine

import app.relay.gateway.port.ConnectionState
import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.port.DeviceIdentityStore
import app.relay.gateway.port.PowerMonitor
import app.relay.gateway.port.RelayConnection
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.port.SmsEventSource
import app.relay.gateway.port.SmsQuotaProvider
import app.relay.protocol.DeviceFrame
import app.relay.protocol.ServerFrame
import app.relay.protocol.SimInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wires the gateway together for one run (typically the lifetime of the foreground service):
 * relay socket → intake, radio callbacks → outcomes, plus the send loop and heartbeat. It
 * tells the server what this phone can do (SIMs, power, SMS limit) and how it's set up, for
 * the dashboard.
 */
class GatewayEngine(
    private val identityStore: DeviceIdentityStore,
    private val settings: SettingsRepository,
    private val connection: RelayConnection,
    private val smsEvents: SmsEventSource,
    private val intake: JobIntake,
    private val dispatcher: MessageDispatcher,
    private val outcomes: SmsOutcomeHandler,
    private val statusSync: StatusSync,
    private val inboundSync: InboundSync,
    private val controller: GatewayController,
    private val remoteSettings: RemoteSettingsSync,
    private val sims: SimCardProvider,
    private val power: PowerMonitor,
    private val smsQuota: SmsQuotaProvider,
    private val appVersion: String,
    /** The build number, which the server compares with releases. */
    private val appVersionCode: Int?,
    /** The projects this phone sends for changed on the web. */
    private val onProjectsChanged: suspend () -> Unit,
    /** The server refused the device token: the pairing it connected with is gone. */
    private val onRevoked: suspend (DeviceIdentity) -> Unit,
    /** Where the server's app_update offers go. */
    private val updates: UpdateSync,
) {
    /** Starts the gateway in [scope]; cancel the returned job to stop it. Requires a paired device. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        val identity = checkNotNull(identityStore.load()) { "Device is not paired with a workspace" }
        connection.connect(settings.current().serverUrl, identity)

        launch { connection.frames.collect(::route) }
        launch {
            connection.state.filterIsInstance<ConnectionState.Connected>().collect {
                connection.send(hello())
                statusSync.flush()
                inboundSync.flush()
            }
        }
        launch {
            connection.state.first { it is ConnectionState.Revoked }
            onRevoked(identity)
        }
        launch {
            // Every percent (and plugging in or out): a few dozen tiny frames a day, and the
            // dashboard matches the phone instead of lagging up to 5 points behind it.
            power.state.drop(1).collect {
                connection.send(status())
            }
        }
        launch { smsEvents.events.collect(outcomes::handle) }
        launch { dispatcher.run() }
        launch { controller.runHeartbeat() }
        launch { remoteSettings.run() }
    }.also { job -> job.invokeOnCompletion { connection.disconnect() } }

    private suspend fun route(frame: ServerFrame) {
        when (frame) {
            is ServerFrame.Welcome -> controller.onHeldCount(frame.heldCount)
            is ServerFrame.HeldCount -> controller.onHeldCount(frame.count)
            is ServerFrame.Dispatch -> intake.accept(frame.jobs)
            is ServerFrame.BatchAnnounced -> intake.accept(frame.batch)
            is ServerFrame.BatchControl -> intake.control(frame.batchId, frame.status)
            is ServerFrame.StatusAck -> statusSync.onAck(frame)
            is ServerFrame.WebhookResult -> statusSync.onWebhookResult(frame)
            is ServerFrame.Pong -> controller.onPong(frame.sentAt)
            ServerFrame.ProjectsChanged -> runCatching { onProjectsChanged() }
            is ServerFrame.IncomingAck -> inboundSync.onAck(frame.ref)
            is ServerFrame.ApplySettings -> remoteSettings.apply(frame.settings)
            is ServerFrame.AppUpdateOffer -> updates.offered(frame.release)
        }
    }

    private suspend fun hello(): DeviceFrame.Hello {
        val p = power.state.value
        val s = remoteSettings.snapshot()
        return DeviceFrame.Hello(s.gatewayOnline, appVersion, simInfo(), p.batteryPercent, p.charging, smsLimitPerMinute(), s, appVersionCode)
    }

    private fun status(): DeviceFrame.DeviceStatus {
        val p = power.state.value
        return DeviceFrame.DeviceStatus(simInfo(), p.batteryPercent, p.charging, smsLimitPerMinute())
    }

    private fun simInfo() = sims.available().map { SimInfo(it.slot, it.carrier) }

    private fun smsLimitPerMinute(): Int? = smsQuota.current()?.let { (it.maxParts * 60_000L / it.windowMs).toInt() }
}
