package app.relay.gateway.service

import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.port.DeviceIdentityStore
import app.relay.gateway.port.RelayApi
import app.relay.gateway.port.SettingsRepository
import kotlinx.coroutines.flow.Flow
import java.net.URI
import java.net.URLDecoder

/** What a pairing QR carries: `smswire://pair?s=<server url>&c=<code>`. */
data class PairingLink(val serverUrl: String, val code: String)

/**
 * Pairs this phone with a workspace on the web: the dashboard shows a one-time code (and a
 * QR with the server URL); redeeming it gives the phone its device token.
 */
class DeviceSetup(
    private val identityStore: DeviceIdentityStore,
    private val settings: SettingsRepository,
    private val api: RelayApi,
    /** Stable for this phone across reinstalls; null where the platform has none. */
    private val phoneKey: String? = null,
) {
    val identity: Flow<DeviceIdentity?> = identityStore.identity

    suspend fun current(): DeviceIdentity? = identityStore.load()

    suspend fun pair(serverUrl: String, code: String, name: String, model: String): DeviceIdentity {
        val server = normalizeServer(serverUrl)
        val response = api.pair(server, code.trim(), name, model, phoneKey)
        settings.update { it.copy(serverUrl = server) }
        return DeviceIdentity(response.deviceId, response.token, name, model, response.workspace.name)
            .also { identityStore.save(it) }
    }

    /**
     * The user unpairs: the server revokes this phone (so its projects stop routing to it),
     * then the pairing is forgotten. Offline, it's forgotten anyway; pairing again later
     * takes over the old record.
     */
    suspend fun unpair() {
        runCatching { api.unpair() }
        forget()
    }

    /** Forgets the pairing locally (revoked on the web: the token is already dead). */
    suspend fun forget() = identityStore.clear()

    companion object {
        fun normalizeServer(url: String): String {
            val normalized = url.trim().trimEnd('/')
            require(normalized.startsWith("http://") || normalized.startsWith("https://")) { "Server URL must start with https://" }
            return normalized
        }

        /** Reads a scanned pairing QR; null if it isn't one. */
        fun parseLink(text: String): PairingLink? {
            val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "smswire" || uri.host != "pair") return null
            val params = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
                val (k, v) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                k to URLDecoder.decode(v, Charsets.UTF_8)
            }.toMap()
            val server = params["s"]?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
            val code = params["c"]?.takeIf { it.isNotBlank() } ?: return null
            return PairingLink(server.trimEnd('/'), code)
        }
    }
}
