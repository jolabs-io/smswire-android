package app.relay.gateway.engine

import app.relay.gateway.port.Clock
import app.relay.gateway.port.ConnectionState
import app.relay.gateway.port.PowerMonitor
import app.relay.gateway.port.RelayConnection
import app.relay.gateway.port.SettingsRepository
import app.relay.protocol.DeviceFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class GatewayStatus(
    val online: Boolean,
    val connection: ConnectionState,
    /** Requests the server is holding while the gateway is paused. */
    val heldCount: Int,
    val latencyMs: Long?,
    /** "Only send while charging" is on and the phone is on battery: the queue waits for a charger. */
    val waitingForCharger: Boolean = false,
)

/** Owns the gateway on/off switch, the server-side hold count and connection latency. */
class GatewayController(
    private val settings: SettingsRepository,
    private val connection: RelayConnection,
    private val clock: Clock,
    power: PowerMonitor,
    private val heartbeatMs: Long = 15_000,
) {
    private val held = MutableStateFlow(0)
    private val latency = MutableStateFlow<Long?>(null)

    val status: Flow<GatewayStatus> = combine(
        settings.settings,
        connection.state,
        held,
        latency,
        power.state,
    ) { s, conn, heldCount, rtt, p ->
        GatewayStatus(
            online = s.gatewayOnline,
            connection = conn,
            heldCount = heldCount,
            latencyMs = rtt.takeIf { conn is ConnectionState.Connected },
            waitingForCharger = s.onlySendWhileCharging && !p.charging,
        )
    }

    suspend fun setOnline(online: Boolean) {
        settings.update { it.copy(gatewayOnline = online) }
        connection.send(DeviceFrame.GatewayState(online))
    }

    fun onHeldCount(count: Int) {
        held.value = count
    }

    fun onPong(sentAt: Long) {
        latency.value = clock.now() - sentAt
    }

    suspend fun runHeartbeat(): Nothing {
        while (true) {
            if (connection.state.value is ConnectionState.Connected) {
                connection.send(DeviceFrame.Ping(clock.now()))
            }
            delay(heartbeatMs)
        }
    }
}
