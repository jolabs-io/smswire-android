package app.relay.gateway.service

import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.port.DeviceIdentityStore
import app.relay.gateway.port.RelayApi
import app.relay.gateway.port.RelayApiException
import app.relay.gateway.port.SettingsRepository
import app.relay.protocol.PairDeviceResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLDecoder

/**
 * What a pairing link carries, in either shape: the app's own `smswire://pair?s=<server url>&c=<code>`,
 * or the web's `https://<host>/pair?s=…&c=…`, which a phone camera can read before the app is there.
 */
data class PairingLink(val serverUrl: String, val code: String)

/** What [DeviceSetup.switchTo] did. */
sealed interface SwitchResult {
    val workspaceName: String

    /** [leftOld] is false when the old workspace's server couldn't be told: it still lists this phone. */
    data class Switched(override val workspaceName: String, val leftOld: Boolean) : SwitchResult

    /** The code came from the workspace the phone is already in: it just paired again. */
    data class SameWorkspace(override val workspaceName: String) : SwitchResult
}

enum class UnpairResult {
    DONE,

    /** The server couldn't be reached: nothing changed. [DeviceSetup.forgetAnyway] leaves without telling it. */
    OFFLINE,
}

/**
 * Pairs this phone with a workspace on the web: the dashboard shows a one-time code (and a
 * QR with the server URL); redeeming it gives the phone its device token. Leaving a workspace
 * (unpairing, switching, or being revoked on the web) clears what the phone kept for it.
 */
class DeviceSetup(
    private val identityStore: DeviceIdentityStore,
    private val settings: SettingsRepository,
    private val api: RelayApi,
    private val workspaceData: WorkspaceData,
    /** Stable for this phone across reinstalls; null where the platform has none. */
    private val phoneKey: String? = null,
) {
    /** Every change of pairing goes through here, one at a time. */
    private val mutex = Mutex()
    private val _switching = MutableStateFlow(false)

    val identity: Flow<DeviceIdentity?> = identityStore.identity

    /** True while the phone moves to another workspace: briefly without a pairing, but not unpaired. */
    val switching: StateFlow<Boolean> = _switching.asStateFlow()

    /** Messages that leaving the workspace would drop. */
    val unsent: Flow<Int> = workspaceData.unsent

    suspend fun current(): DeviceIdentity? = identityStore.load()

    suspend fun pair(serverUrl: String, code: String, name: String, model: String): DeviceIdentity = mutex.withLock {
        val server = normalizeServer(serverUrl)
        save(server, api.pair(server, code.trim(), name, model, phoneKey), name, model)
    }

    /**
     * Moves a paired phone to the workspace of [code]. It joins the new one first, so a wrong or
     * expired code (or a full plan) changes nothing; then it leaves the old one and clears what
     * it kept for it ([keepHistory] as in [WorkspaceData.clear]).
     */
    suspend fun switchTo(serverUrl: String, code: String, name: String, model: String, keepHistory: Boolean): SwitchResult =
        mutex.withLock {
            val old = checkNotNull(identityStore.load()) { "This phone isn't paired with a workspace" }
            val server = normalizeServer(serverUrl)
            val joined = api.pair(server, code.trim(), name, model, phoneKey)
            if (joined.deviceId == old.deviceId) {
                return@withLock SwitchResult.SameWorkspace(save(server, joined, name, model).workspaceName)
            }
            // Joined: finish even if the screen that asked goes away meanwhile.
            withContext(NonCancellable) {
                _switching.value = true
                try {
                    // Still the old token and server: the old workspace lets this phone go.
                    val leftOld = revoke()
                    leave(keepHistory)
                    val saved = save(server, joined, name, model)
                    // Not "switching" before the new pairing shows, so the app never looks unpaired.
                    identityStore.identity.first { it?.token == saved.token }
                    SwitchResult.Switched(saved.workspaceName, leftOld)
                } finally {
                    _switching.value = false
                }
            }
        }

    /**
     * The user unpairs: the server revokes this phone (so its projects stop routing to it), then
     * the pairing and what the phone kept for the workspace are cleared. Offline, nothing changes.
     */
    suspend fun unpair(keepHistory: Boolean): UnpairResult = mutex.withLock {
        if (!revoke()) return@withLock UnpairResult.OFFLINE
        withContext(NonCancellable) { leave(keepHistory) }
        UnpairResult.DONE
    }

    /** Leaves without telling the server, which keeps listing this phone until it's removed on the web. */
    suspend fun forgetAnyway(keepHistory: Boolean) = mutex.withLock { leave(keepHistory) }

    /**
     * The server refused [revoked]'s token (revoked on the web). Only that pairing is forgotten: a
     * newer one (switched or paired again meanwhile) stays. History is kept; there's no one to ask.
     */
    suspend fun revokedOnWeb(revoked: DeviceIdentity) = mutex.withLock {
        if (identityStore.load()?.token == revoked.token) leave(keepHistory = true)
    }

    /** True once the server no longer lists this phone (a token it already refuses counts). */
    private suspend fun revoke(): Boolean = try {
        api.unpair()
        true
    } catch (e: RelayApiException) {
        e.status == 401 || e.status == 404
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private suspend fun leave(keepHistory: Boolean) {
        identityStore.clear()
        workspaceData.clear(keepHistory)
    }

    private suspend fun save(server: String, response: PairDeviceResponse, name: String, model: String): DeviceIdentity {
        settings.update { it.copy(serverUrl = server) }
        return DeviceIdentity(response.deviceId, response.token, name, model, response.workspace.name)
            .also { identityStore.save(it) }
    }

    companion object {
        fun normalizeServer(url: String): String {
            val normalized = url.trim().trimEnd('/')
            require(normalized.startsWith("http://") || normalized.startsWith("https://")) { "Server URL must start with https://" }
            return normalized
        }

        /** Reads a scanned pairing QR, or a pairing link the phone opened; null if it isn't one. */
        fun parseLink(text: String): PairingLink? {
            val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
            if (!isPairLink(uri)) return null
            val params = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
                val (k, v) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                k to URLDecoder.decode(v, Charsets.UTF_8)
            }.toMap()
            val server = params["s"]?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
            val code = params["c"]?.takeIf { it.isNotBlank() } ?: return null
            return PairingLink(server.trimEnd('/'), code)
        }

        /**
         * Both shapes, on any host: the web's pairing page is served by whichever deployment made
         * the code, and its language prefix (`/am/pair`) is still that page. What makes a link a
         * pairing link is a usable server and code, not where it came from.
         */
        private fun isPairLink(uri: URI): Boolean = when (uri.scheme?.lowercase()) {
            "smswire" -> uri.host == "pair"
            "http", "https" -> uri.path.orEmpty().trimEnd('/').substringAfterLast('/') == "pair"
            else -> false
        }
    }
}
