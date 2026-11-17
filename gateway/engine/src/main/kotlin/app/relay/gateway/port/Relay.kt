package app.relay.gateway.port

import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.model.Project
import app.relay.gateway.model.ReceiveRules
import app.relay.protocol.AppUpdate
import app.relay.protocol.DeviceFrame
import app.relay.protocol.PairDeviceResponse
import app.relay.protocol.ServerFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val since: Long) : ConnectionState

    /** The server refused the device token (revoked on the web): stop and pair again. */
    data object Revoked : ConnectionState
}

/** Long-lived outbound socket to the relay server. Implementations reconnect on their own. */
interface RelayConnection {
    val state: StateFlow<ConnectionState>
    val frames: Flow<ServerFrame>

    fun connect(serverUrl: String, identity: DeviceIdentity)
    fun disconnect()

    /** @return false if the frame could not be queued (not connected). */
    fun send(frame: DeviceFrame): Boolean
}

class RelayApiException(val status: Int, message: String) : Exception(message)

/** REST side of the device ↔ relay contract. Projects are managed on the web; the phone reads them and can change what they receive. */
interface RelayApi {
    /** Redeems a pairing code from the web dashboard at [serverUrl]; [phoneKey] lets a re-paired phone keep its record. */
    suspend fun pair(serverUrl: String, code: String, name: String, model: String, phoneKey: String?): PairDeviceResponse
    suspend fun listProjects(): List<Project>
    /** Replaces one of this phone's projects' receive rules; the server's copy of the project comes back. */
    suspend fun updateReceive(projectId: String, rules: ReceiveRules): Project
    /** Revokes this phone's token on the server, so it leaves its projects. */
    suspend fun unpair()

    /** The newest build for this app (GET /app/android-update), or null when it's up to date. Needs no pairing. */
    suspend fun checkUpdate(versionName: String, versionCode: Int?, sendOnly: Boolean): AppUpdate?
}
