package app.relay.gateway

import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.model.Project
import app.relay.gateway.model.SimCard
import app.relay.gateway.port.Clock
import app.relay.gateway.port.ConnectionState
import app.relay.gateway.port.OutgoingSms
import app.relay.gateway.port.PowerMonitor
import app.relay.gateway.port.PowerState
import app.relay.gateway.port.RelayApi
import app.relay.gateway.port.RelayConnection
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.port.SmsEvent
import app.relay.gateway.port.SmsEventSource
import app.relay.gateway.port.SmsSendException
import app.relay.gateway.port.SmsSender
import app.relay.gateway.port.RelayApiException
import app.relay.protocol.DeviceFrame
import app.relay.protocol.PairDeviceResponse
import app.relay.protocol.ServerFrame
import app.relay.protocol.WorkspaceRef
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeClock(var time: () -> Long) : Clock {
    override fun now() = time()
    override fun startOfToday() = 0L
}

class FakeConnection : RelayConnection {
    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val frames = MutableSharedFlow<ServerFrame>(extraBufferCapacity = 64)
    val sent = mutableListOf<DeviceFrame>()

    override fun connect(serverUrl: String, identity: DeviceIdentity) {
        state.value = ConnectionState.Connected(0)
    }

    override fun disconnect() {
        state.value = ConnectionState.Disconnected
    }

    override fun send(frame: DeviceFrame): Boolean {
        if (state.value !is ConnectionState.Connected) return false
        sent += frame
        return true
    }
}

/** Records sends; outcomes are emitted by the test through [events]. */
class FakeSmsSender : SmsSender, SmsEventSource {
    val sent = mutableListOf<Pair<Long, OutgoingSms>>()
    var failNext: String? = null
    var now: () -> Long = { 0 }
    override val events = MutableSharedFlow<SmsEvent>(extraBufferCapacity = 64)

    override suspend fun send(sms: OutgoingSms) {
        failNext?.let { failNext = null; throw SmsSendException(it) }
        sent += now() to sms
    }
}

class FakeSims(private val slots: List<Int> = listOf(1, 2)) : SimCardProvider {
    override fun available() = slots.map { SimCard(it, "Carrier $it", it) }
}

class FakePower(charging: Boolean = true) : PowerMonitor {
    override val state = MutableStateFlow(PowerState(80, charging))
}

class FakeRelayApi : RelayApi {
    val paired = mutableListOf<Pair<String, String>>()
    val phoneKeys = mutableListOf<String?>()
    var projects = listOf(project("p1", "Shop"))

    override suspend fun pair(serverUrl: String, code: String, name: String, model: String, phoneKey: String?): PairDeviceResponse {
        if (code != "GOOD-CODE") throw RelayApiException(400, "That code is wrong or has expired")
        paired += serverUrl to code
        phoneKeys += phoneKey
        return PairDeviceResponse("dev_1", "token", WorkspaceRef("ws_1", "Fortune Clinics"))
    }

    override suspend fun listProjects() = projects

    var unpaired = 0
    var unpairFails = false
    override suspend fun unpair() {
        if (unpairFails) throw RelayApiException(0, "offline")
        unpaired++
    }

    fun project(id: String, name: String) = Project(id, name, "prj_$id", "https://gw/v1/p/prj_$id/messages", null, true, 0)
}
