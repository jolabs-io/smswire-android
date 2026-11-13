package app.relay.android.feature.gateway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.android.designsystem.component.MessageRowModel
import app.relay.android.designsystem.format.Format
import app.relay.gateway.engine.GatewayController
import app.relay.gateway.engine.GatewayStatus
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.Message
import app.relay.gateway.model.SimPreference
import app.relay.gateway.port.Clock
import app.relay.gateway.port.ConnectionState
import app.relay.gateway.port.PowerMonitor
import app.relay.gateway.port.PowerState
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.service.DeviceSetup
import app.relay.gateway.service.MessageService
import app.relay.gateway.service.ProjectOverview
import app.relay.gateway.service.ProjectService
import app.relay.gateway.service.StatsService
import app.relay.gateway.service.Throughput
import app.relay.gateway.service.TodayStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GatewayMode {
    ONLINE,
    CONNECTING,
    PAUSED,

    /** Connected, but "only send while charging" holds the queue until the phone is plugged in. */
    ON_BATTERY,
}

data class TodayUi(
    val sent: String = "0",
    val delivered: String = "0",
    val awaiting: String = "0",
    val failed: String = "0",
    val deliveryRate: String = "—",
    val deliveredFraction: Float = 0f,
    val awaitingFraction: Float = 0f,
    val failedFraction: Float = 0f,
)

data class GatewayUiState(
    val deviceLine: String = "",
    val mode: GatewayMode = GatewayMode.CONNECTING,
    val switchOn: Boolean = true,
    val enabledProjects: Int = 0,
    val uptime: String? = null,
    val server: String = "",
    val heldCount: Int = 0,
    val sim: String = "—",
    val latency: String = "—",
    val battery: String = "—",
    val today: TodayUi = TodayUi(),
    /** Bar heights in 0..1, oldest first; the last bar is the current minute. */
    val bars: List<Float> = List(30) { 0.04f },
    val nowRate: Int = 0,
    val inQueue: Int = 0,
    val rateLimit: Int = GatewaySettings.DEFAULT_RATE,
    val feed: List<MessageRowModel> = emptyList(),
)

class GatewayViewModel(
    private val controller: GatewayController,
    stats: StatsService,
    messages: MessageService,
    projects: ProjectService,
    settings: kotlinx.coroutines.flow.Flow<GatewaySettings>,
    power: PowerMonitor,
    private val sims: SimCardProvider,
    deviceSetup: DeviceSetup,
    private val clock: Clock,
    ticker: kotlinx.coroutines.flow.Flow<Unit>,
) : ViewModel() {
    private val deviceLine = MutableStateFlow("")

    init {
        viewModelScope.launch {
            deviceSetup.current()?.let { deviceLine.value = listOf(it.workspaceName, it.model).filter(String::isNotBlank).joinToString(" · ") }
        }
    }

    private val traffic = combine(stats.observeToday(), stats.observeThroughput(), messages.observeRecent(FEED_SIZE)) { t, tp, m ->
        Triple(t, tp, m)
    }
    private val context = combine(settings, power.state, projects.observeAll(), deviceLine, ticker) { s, p, list, line, _ ->
        Context(s, p, list, line)
    }

    val state: StateFlow<GatewayUiState> = combine(controller.status, traffic, context) { status, (today, throughput, recent), ctx ->
        render(status, today, throughput, recent, ctx)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GatewayUiState())

    fun setOnline(online: Boolean) {
        viewModelScope.launch { controller.setOnline(online) }
    }

    private data class Context(
        val settings: GatewaySettings,
        val power: PowerState,
        val projects: List<ProjectOverview>,
        val deviceLine: String,
    )

    private fun render(
        status: GatewayStatus,
        today: TodayStats,
        throughput: Throughput,
        recent: List<Message>,
        ctx: Context,
    ): GatewayUiState {
        val connected = status.connection as? ConnectionState.Connected
        val mode = when {
            !status.online -> GatewayMode.PAUSED
            connected == null -> GatewayMode.CONNECTING
            status.waitingForCharger -> GatewayMode.ON_BATTERY
            else -> GatewayMode.ONLINE
        }
        val now = clock.now()
        val total = maxOf(1, today.sent).toFloat()
        val peak = maxOf(MIN_BAR_SCALE, throughput.perMinute.maxOrNull() ?: 0).toFloat()
        val names = ctx.projects.associate { it.project.id to it.project.name }
        return GatewayUiState(
            deviceLine = ctx.deviceLine,
            mode = mode,
            switchOn = status.online,
            enabledProjects = ctx.projects.count { it.project.enabled },
            uptime = connected?.let { Format.duration(now - it.since) },
            server = ctx.settings.serverUrl.substringAfter("://"),
            heldCount = status.heldCount,
            sim = simLabel(ctx.settings.defaultSim),
            latency = status.latencyMs?.let { "$it ms" } ?: "—",
            battery = "${ctx.power.batteryPercent}%" + if (ctx.power.charging) " ⚡" else "",
            today = TodayUi(
                sent = Format.count(today.sent),
                delivered = Format.count(today.delivered),
                awaiting = Format.count(today.awaiting),
                failed = Format.count(today.failed),
                deliveryRate = Format.percent(today.deliveryRate),
                deliveredFraction = today.delivered / total,
                awaitingFraction = today.awaiting / total,
                failedFraction = today.failed / total,
            ),
            bars = throughput.perMinute.map { maxOf(0.04f, it / peak) },
            nowRate = throughput.current,
            inQueue = today.counts.pending + status.heldCount,
            rateLimit = ctx.settings.maxRatePerMinute,
            feed = recent.map {
                MessageRowModel(
                    id = it.id,
                    to = it.to,
                    preview = it.body,
                    project = names[it.projectId] ?: "Deleted project",
                    time = Format.ago(it.timeline.receivedAt, now),
                    status = it.status,
                )
            },
        )
    }

    private fun simLabel(preference: SimPreference): String {
        if (preference == SimPreference.ALTERNATE) return "Alternate"
        val slot = if (preference == SimPreference.SIM1) 1 else 2
        val card = sims.available().firstOrNull { it.slot == slot }
        return if (card == null) "S$slot" else "S$slot ${card.carrier}"
    }

    private companion object {
        const val FEED_SIZE = 5
        const val MIN_BAR_SCALE = 14
    }
}
