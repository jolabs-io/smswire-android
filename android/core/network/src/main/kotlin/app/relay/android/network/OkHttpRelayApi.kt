package app.relay.android.network

import app.relay.gateway.model.Project
import app.relay.gateway.port.DeviceIdentityStore
import app.relay.gateway.port.RelayApi
import app.relay.gateway.port.RelayApiException
import app.relay.gateway.port.SettingsRepository
import app.relay.protocol.ErrorResponse
import app.relay.protocol.PairDeviceRequest
import app.relay.protocol.PairDeviceResponse
import app.relay.protocol.ProjectDto
import app.relay.protocol.RelayJson
import app.relay.protocol.RelayPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** REST client for the device side of the relay API. */
class OkHttpRelayApi(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val identity: DeviceIdentityStore,
) : RelayApi {

    override suspend fun pair(serverUrl: String, code: String, name: String, model: String, phoneKey: String?): PairDeviceResponse =
        call(
            url = serverUrl.trimEnd('/') + RelayPaths.DEVICE_PAIR,
            method = "POST",
            body = json(PairDeviceRequest.serializer(), PairDeviceRequest(code, name, model, phoneKey)),
            authenticated = false,
            response = PairDeviceResponse.serializer(),
        )

    override suspend fun listProjects(): List<Project> =
        call(path(RelayPaths.DEVICE_PROJECTS), "GET", null, ListSerializer(ProjectDto.serializer())).map { it.toProject() }

    override suspend fun unpair() = call<Unit>(path(RelayPaths.DEVICE_SELF), "DELETE", null, null)

    private suspend fun path(p: String) = settings.current().serverUrl.trimEnd('/') + p

    private fun <T> json(serializer: KSerializer<T>, value: T): RequestBody =
        RelayJson.encodeToString(serializer, value).toRequestBody(JSON)

    private suspend fun <T> call(
        url: String,
        method: String,
        body: RequestBody?,
        response: KSerializer<T>?,
        authenticated: Boolean = true,
    ): T {
        val token = if (authenticated) {
            identity.load()?.token ?: throw RelayApiException(401, "This phone isn't paired with a workspace yet")
        } else {
            null
        }
        val request = Request.Builder().url(url).method(method, body).apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val message = runCatching { RelayJson.decodeFromString(ErrorResponse.serializer(), text).message }
                        .getOrDefault("HTTP ${res.code}")
                    throw RelayApiException(res.code, message)
                }
                @Suppress("UNCHECKED_CAST")
                if (response == null) Unit as T else RelayJson.decodeFromString(response, text)
            }
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

internal fun ProjectDto.toProject() = Project(
    id = id,
    name = name,
    slug = slug,
    endpoint = endpoint,
    webhookUrl = webhookUrl,
    enabled = enabled,
    createdAt = createdAt,
    sim = sim,
    defaultType = defaultType,
)
