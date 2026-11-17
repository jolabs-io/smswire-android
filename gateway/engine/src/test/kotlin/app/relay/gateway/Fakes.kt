package app.relay.gateway

import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.model.Project
import app.relay.gateway.model.ReceiveRules
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
import app.relay.protocol.AppUpdate
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
        val response = when (code) {
            "GOOD-CODE" -> PairDeviceResponse("dev_1", "token", WorkspaceRef("ws_1", "Fortune Clinics"))
            // Another workspace: a new record there.
            "OTHER-CODE" -> PairDeviceResponse("dev_2", "token-2", WorkspaceRef("ws_2", "Abebe Shop"))
            // The same workspace again: its record back, with a new token.
            "SAME-CODE" -> PairDeviceResponse("dev_1", "token-3", WorkspaceRef("ws_1", "Fortune Clinics"))
            else -> throw RelayApiException(400, "That code is wrong or has expired")
        }
        paired += serverUrl to code
        phoneKeys += phoneKey
        return response
    }

    override suspend fun listProjects() = projects

    /** Receive rules the phone saved, in order. */
    val savedRules = mutableListOf<Pair<String, ReceiveRules>>()
    var updateReceiveFails = false
    override suspend fun updateReceive(projectId: String, rules: ReceiveRules): Project {
        if (updateReceiveFails) throw java.io.IOException("offline")
        savedRules += projectId to rules
        val saved = projects.first { it.id == projectId }.copy(receive = rules)
        projects = projects.map { if (it.id == projectId) saved else it }
        return saved
    }

    /** What [checkUpdate] answers; [checkFails] makes it fail as offline. */
    var update: AppUpdate? = null
    var checkFails = false
    val checks = mutableListOf<Triple<String, Int?, Boolean>>()
    override suspend fun checkUpdate(versionName: String, versionCode: Int?, sendOnly: Boolean): AppUpdate? {
        if (checkFails) throw java.io.IOException("offline")
        checks += Triple(versionName, versionCode, sendOnly)
        return update
    }

    var unpaired = 0
    var unpairFails = false
    /** Thrown by [unpair] instead of the plain offline failure. */
    var unpairError: Exception? = null
    override suspend fun unpair() {
        unpairError?.let { throw it }
        if (unpairFails) throw java.io.IOException("offline")
        unpaired++
    }

    fun project(id: String, name: String) = Project(id, name, "prj_$id", "https://gw/v1/p/prj_$id/messages", null, true, 0)
}
