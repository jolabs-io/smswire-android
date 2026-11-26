package app.relay.gateway

import app.relay.gateway.engine.BackoffRetryPolicy
import app.relay.gateway.engine.ChargingCondition
import app.relay.gateway.engine.DefaultSimSelector
import app.relay.gateway.engine.GatewayController
import app.relay.gateway.engine.GatewayEngine
import app.relay.gateway.engine.GatewayOnlineCondition
import app.relay.gateway.engine.InboundSync
import app.relay.gateway.engine.IntervalSendThrottle
import app.relay.gateway.engine.JobIntake
import app.relay.gateway.engine.MessageDispatcher
import app.relay.gateway.engine.MessageLedger
import app.relay.gateway.engine.RemoteSettingsSync
import app.relay.gateway.engine.RetryPolicy
import app.relay.gateway.engine.SendCondition
import app.relay.gateway.engine.SendThrottle
import app.relay.gateway.engine.SimSelector
import app.relay.gateway.engine.SmsOutcomeHandler
import app.relay.gateway.engine.SmsQuotaGuard
import app.relay.gateway.engine.StatusSync
import app.relay.gateway.port.BatchRepository
import app.relay.gateway.port.Clock
import app.relay.gateway.data.memory.InMemoryInboundRepository
import app.relay.gateway.data.memory.InMemoryJoinKeywordStore
import app.relay.gateway.port.DeviceIdentityStore
import app.relay.gateway.port.InboundRepository
import app.relay.gateway.port.JoinKeywordStore
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.PowerMonitor
import app.relay.gateway.port.ProjectRepository
import app.relay.gateway.port.RelayApi
import app.relay.gateway.port.RelayConnection
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.port.SmsEventSource
import app.relay.gateway.port.SmsQuotaProvider
import app.relay.gateway.port.SmsSender
import app.relay.gateway.data.memory.InMemoryUpdateStore
import app.relay.gateway.engine.UpdateSync
import app.relay.gateway.port.AppInstaller
import app.relay.gateway.port.NoAppInstaller
import app.relay.gateway.port.NoUpdateNotifier
import app.relay.gateway.port.UpdateNotifier
import app.relay.gateway.port.UpdateStore
import app.relay.gateway.service.AppUpdates
import app.relay.gateway.service.BatchService
import app.relay.gateway.service.DeviceSetup
import app.relay.gateway.service.MessageService
import app.relay.gateway.service.ProjectService
import app.relay.gateway.service.StatsService
import app.relay.gateway.service.WorkspaceData
import app.relay.gateway.service.ticker
import app.relay.sms.GsmSegmentCalculator
import app.relay.sms.SegmentCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Everything the gateway needs from the host platform. */
class GatewayPorts(
    val messages: MessageRepository,
    val batches: BatchRepository,
    val projects: ProjectRepository,
    val settings: SettingsRepository,
    val identity: DeviceIdentityStore,
    val connection: RelayConnection,
    val api: RelayApi,
    val sender: SmsSender,
    val smsEvents: SmsEventSource,
    val sims: SimCardProvider,
    val power: PowerMonitor,
    val clock: Clock,
    val appVersion: String,
    /** The build number (Android's versionCode); null where there's none. */
    val appVersionCode: Int? = null,
    /** The platform's own send cap; none by default (e.g. on the JVM). */
    val smsQuota: SmsQuotaProvider = SmsQuotaProvider { null },
    /** Received SMS on their way to the server's inbox. */
    val inbound: InboundRepository = InMemoryInboundRepository(),
    /** Stable id of this phone (Android's ANDROID_ID): re-pairing it keeps its record on the server. */
    val phoneKey: String? = null,
    /** False in a build that can't receive SMS (Android's send-only build): nothing reaches the inbox. */
    val receivesSms: Boolean = true,
    /** Installs new versions of the app; none on hosts that can't. */
    val installer: AppInstaller = NoAppInstaller,
    val updateNotifier: UpdateNotifier = NoUpdateNotifier,
    val updateStore: UpdateStore = InMemoryUpdateStore(),
    /** The workspace's join keywords, kept so they work before the next hello. */
    val joinKeywords: JoinKeywordStore = InMemoryJoinKeywordStore(),
)

/**
 * Composition root for the gateway. Policies are constructor parameters with defaults,
 * so a host can swap one (e.g. a different [RetryPolicy]) without touching the rest.
 */
class GatewayComponent(
    private val ports: GatewayPorts,
    retryPolicy: RetryPolicy = BackoffRetryPolicy(),
    /** One per send loop: with a loop per SIM, each SIM is paced on its own timeline. */
    newThrottle: () -> SendThrottle = { IntervalSendThrottle(ports.clock) },
    simSelector: SimSelector = DefaultSimSelector(ports.sims, ports.messages, ports.clock),
    extraConditions: List<SendCondition> = emptyList(),
    val segmentCalculator: SegmentCalculator = GsmSegmentCalculator,
    /** Where app-lifetime work runs (updating the app). */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val ledger = MessageLedger(ports.messages)
    private val statusSync = StatusSync(ports.messages, ledger, ports.connection)
    private val outcomes = SmsOutcomeHandler(ledger, ports.settings, retryPolicy, statusSync)

    private val dispatcher = MessageDispatcher(
        messages = ports.messages,
        batches = ports.batches,
        settings = ports.settings,
        sims = ports.sims,
        ledger = ledger,
        conditions = listOf(GatewayOnlineCondition, ChargingCondition(ports.power)) + extraConditions,
        newThrottle = newThrottle,
        quota = SmsQuotaGuard(ports.smsQuota, ports.clock),
        segments = segmentCalculator,
        simSelector = simSelector,
        sender = ports.sender,
        statusSync = statusSync,
        outcomes = outcomes,
        clock = ports.clock,
    )

    /** Hand received SMS here (from the platform's SMS receiver). */
    val inboundSync = InboundSync(ports.inbound, ports.connection, ports.settings, ports.messages, ports.joinKeywords, ports.projects)

    private val intake = JobIntake(ports.messages, ports.batches, ports.clock, dispatcher::wake)
    private val uiTicker = ticker(TICK_MS)

    val controller = GatewayController(ports.settings, ports.connection, ports.clock, ports.power)
    val projectService = ProjectService(ports.projects, ports.messages, ports.api, ports.clock, uiTicker)
    val deviceSetup = DeviceSetup(
        identityStore = ports.identity,
        settings = ports.settings,
        api = ports.api,
        workspaceData = WorkspaceData(ports.messages, ports.batches, ports.projects, ports.inbound, ports.joinKeywords),
        phoneKey = ports.phoneKey,
    )

    /** The update the server offers, and the steps reported back. */
    val updateSync = UpdateSync(ports.connection, ports.clock)

    val appUpdates = AppUpdates(
        sync = updateSync,
        api = ports.api,
        installer = ports.installer,
        notifier = ports.updateNotifier,
        store = ports.updateStore,
        settings = ports.settings,
        messages = ports.messages,
        clock = ports.clock,
        scope = scope,
        versionName = ports.appVersion,
        versionCode = ports.appVersionCode,
        receivesSms = ports.receivesSms,
    )

    val engine = GatewayEngine(
        identityStore = ports.identity,
        settings = ports.settings,
        connection = ports.connection,
        smsEvents = ports.smsEvents,
        intake = intake,
        dispatcher = dispatcher,
        outcomes = outcomes,
        statusSync = statusSync,
        inboundSync = inboundSync,
        controller = controller,
        remoteSettings = RemoteSettingsSync(ports.settings, ports.connection, ports.receivesSms),
        sims = ports.sims,
        power = ports.power,
        smsQuota = ports.smsQuota,
        appVersion = ports.appVersion,
        appVersionCode = ports.appVersionCode,
        onProjectsChanged = projectService::refresh,
        onRevoked = deviceSetup::revokedOnWeb,
        updates = updateSync,
    )

    val messageService = MessageService(ports.messages, ledger, statusSync, ports.clock, dispatcher::wake)
    val batchService = BatchService(
        batches = ports.batches,
        messages = ports.messages,
        settings = ports.settings,
        connection = ports.connection,
        onResumed = dispatcher::wake,
    )
    val statsService = StatsService(ports.messages, ports.clock, uiTicker)
    val settings: SettingsRepository get() = ports.settings
    val sims: SimCardProvider get() = ports.sims
    val power: PowerMonitor get() = ports.power
    val smsQuota: SmsQuotaProvider get() = ports.smsQuota

    private companion object {
        const val TICK_MS = 10_000L
    }
}
