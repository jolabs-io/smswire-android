package app.relay.android.network

import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.port.Clock
import app.relay.gateway.port.ConnectionState
import app.relay.gateway.port.RelayConnection
import app.relay.protocol.DeviceFrame
import app.relay.protocol.RelayJson
import app.relay.protocol.RelayPaths
import app.relay.protocol.ServerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The phone's outbound WebSocket to the relay. Reconnects with backoff until [disconnect]
 * is called; callbacks from a superseded socket are ignored via [generation]. A refused
 * token (handshake 401, or close code 4401 when revoked on the web) ends in
 * [ConnectionState.Revoked] instead of retrying.
 */
class OkHttpRelayConnection(
    private val client: OkHttpClient,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val backoffMs: List<Long> = listOf(1_000, 2_000, 5_000, 10_000, 30_000),
) : RelayConnection {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val inbox = Channel<ServerFrame>(Channel.UNLIMITED)
    override val frames: Flow<ServerFrame> = inbox.receiveAsFlow()

    private val lock = Any()
    private var socket: WebSocket? = null
    private var target: Pair<String, DeviceIdentity>? = null
    private var generation = 0
    private var failures = 0
    private var reconnectJob: Job? = null

    override fun connect(serverUrl: String, identity: DeviceIdentity) = synchronized(lock) {
        target = serverUrl to identity
        failures = 0
        open()
    }

    override fun disconnect() = synchronized(lock) {
        target = null
        generation++
        reconnectJob?.cancel()
        socket?.close(NORMAL_CLOSURE, "client stopped")
        socket = null
        _state.value = ConnectionState.Disconnected
    }

    /**
     * The phone's network came back or changed: open a fresh socket now instead of waiting out
     * the backoff, or riding one that sits on a network that's gone. No-op once disconnected.
     */
    fun reconnectNow() = synchronized(lock) {
        if (target == null) return@synchronized
        failures = 0
        open()
    }

    override fun send(frame: DeviceFrame): Boolean {
        val ws = synchronized(lock) { socket.takeIf { _state.value is ConnectionState.Connected } } ?: return false
        return ws.send(RelayJson.encodeToString(DeviceFrame.serializer(), frame))
    }

    /** Caller holds [lock]. */
    private fun open() {
        val (serverUrl, identity) = target ?: return
        reconnectJob?.cancel()
        socket?.cancel()
        val gen = ++generation
        _state.value = ConnectionState.Connecting
        val request = Request.Builder()
            .url(socketUrl(serverUrl))
            .header("Authorization", "Bearer ${identity.token}")
            .build()
        socket = client.newWebSocket(request, Listener(gen))
    }

    private fun revoked(gen: Int) = synchronized(lock) {
        if (gen != generation) return@synchronized
        target = null
        socket = null
        _state.value = ConnectionState.Revoked
    }

    private fun scheduleReconnect(gen: Int) = synchronized(lock) {
        if (gen != generation || target == null) return@synchronized
        socket = null
        _state.value = ConnectionState.Disconnected
        val wait = backoffMs[failures.coerceAtMost(backoffMs.lastIndex)]
        failures++
        reconnectJob = scope.launch {
            delay(wait)
            synchronized(lock) { if (gen == generation) open() }
        }
    }

    private inner class Listener(private val gen: Int) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
            if (gen != generation) return@synchronized
            failures = 0
            _state.value = ConnectionState.Connected(clock.now())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (gen != generation) return
            try {
                inbox.trySend(RelayJson.decodeFromString(ServerFrame.serializer(), text))
            } catch (_: SerializationException) {
                // Unknown frame from a newer server: ignore rather than drop the connection.
            } catch (_: IllegalArgumentException) {
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (code == REVOKED) revoked(gen)
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
            if (code == REVOKED) revoked(gen) else scheduleReconnect(gen)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            if (response?.code == 401) revoked(gen) else scheduleReconnect(gen)
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        /** Sent by the server when the device is revoked. */
        const val REVOKED = 4401

        fun socketUrl(serverUrl: String): String {
            val base = serverUrl.trimEnd('/')
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
            return base + RelayPaths.DEVICE_SOCKET
        }
    }
}
