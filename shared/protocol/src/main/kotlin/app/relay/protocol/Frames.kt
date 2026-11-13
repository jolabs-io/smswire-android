package app.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * WebSocket frames on /v1/device/connect. The phone keeps this outbound socket
 * open, so it needs no public IP or port forwarding.
 */

/** A message the device should send. Delivered at least once; the device de-duplicates by [messageId]. */
@Serializable
data class SmsJob(
    val messageId: String,
    val projectId: String,
    val to: String,
    val body: String,
    val receivedAt: Long,
    val batchId: String? = null,
    val sim: Int? = null,
    val type: MessageType = MessageType.TRANSACTIONAL,
    /** Lower goes first: otp 0, transactional/notification 1, marketing 2. */
    val priority: Int = 1,
)

/** A SIM the phone can send from, as reported to the dashboard. */
@Serializable
data class SimInfo(val slot: Int, val carrier: String)

/** Which SIM sends when neither the request nor the project names one. */
@Serializable
enum class DefaultSim {
    @SerialName("sim1") SIM1,
    @SerialName("sim2") SIM2,
    @SerialName("alternate") ALTERNATE,
}

/** Which received SMS the phone sends up to the inbox. The web sees it but can't change it. */
@Serializable
enum class InboxSyncMode {
    @SerialName("replies") REPLIES,
    @SerialName("all") ALL,
    @SerialName("off") OFF,
}

/** The phone's own send settings, which the web can change too. The server URL stays on the phone. */
@Serializable
data class RemoteSettings(
    /** Off: the server holds new requests and nothing is sent. */
    val gatewayOnline: Boolean,
    val defaultSim: DefaultSim,
    val maxRatePerMinute: Int,
    val maxRetries: Int,
    val requestDeliveryReports: Boolean,
    val keepAlive: Boolean,
    val startOnBoot: Boolean,
    val onlySendWhileCharging: Boolean,
    /** Reported only: no patch carries it, so the server can't widen what leaves the phone. */
    val inboxSync: InboxSyncMode = InboxSyncMode.REPLIES,
    /** Reported only: false from the send-only build, which can't receive SMS (no replies or STOP). */
    val receivesSms: Boolean? = null,
)

/** Settings changed on the web; null fields stay as they are. */
@Serializable
data class RemoteSettingsPatch(
    val gatewayOnline: Boolean? = null,
    val defaultSim: DefaultSim? = null,
    val maxRatePerMinute: Int? = null,
    val maxRetries: Int? = null,
    val requestDeliveryReports: Boolean? = null,
    val keepAlive: Boolean? = null,
    val startOnBoot: Boolean? = null,
    val onlySendWhileCharging: Boolean? = null,
)

@Serializable
sealed interface ServerFrame {
    @Serializable @SerialName("welcome")
    data class Welcome(val deviceId: String, val heldCount: Int) : ServerFrame

    @Serializable @SerialName("dispatch")
    data class Dispatch(val jobs: List<SmsJob>) : ServerFrame

    @Serializable @SerialName("batch")
    data class BatchAnnounced(val batch: BatchDto) : ServerFrame

    /** Requests waiting on the server while the gateway is paused. */
    @Serializable @SerialName("held")
    data class HeldCount(val count: Int) : ServerFrame

    @Serializable @SerialName("status_ack")
    data class StatusAck(val messageId: String, val status: MessageStatus) : ServerFrame

    /** Outcome of the server's POST to the project webhook for a status change. */
    @Serializable @SerialName("webhook_result")
    data class WebhookResult(
        val messageId: String,
        val status: MessageStatus,
        val httpStatus: Int? = null,
        val durationMs: Long,
        val error: String? = null,
    ) : ServerFrame

    @Serializable @SerialName("pong")
    data class Pong(val sentAt: Long) : ServerFrame

    /** A batch was paused or resumed on the web. */
    @Serializable @SerialName("batch_control")
    data class BatchControl(val batchId: String, val status: BatchStatus) : ServerFrame

    /** The phone's projects changed on the web: fetch them again. */
    @Serializable @SerialName("projects_changed")
    data object ProjectsChanged : ServerFrame

    /** An incoming SMS was stored; the phone stops re-sending it. */
    @Serializable @SerialName("incoming_ack")
    data class IncomingAck(val ref: String) : ServerFrame

    /** Settings changed on the web: apply them and report back with [DeviceFrame.Settings]. */
    @Serializable @SerialName("apply_settings")
    data class ApplySettings(val settings: RemoteSettingsPatch) : ServerFrame
}

@Serializable
sealed interface DeviceFrame {
    @Serializable @SerialName("hello")
    data class Hello(
        val gatewayOnline: Boolean,
        val appVersion: String,
        val sims: List<SimInfo> = emptyList(),
        val battery: Int? = null,
        val charging: Boolean? = null,
        /** Android's cap on SMS parts per minute for this app; null when exempt. */
        val smsLimitPerMinute: Int? = null,
        val settings: RemoteSettings? = null,
    ) : DeviceFrame

    /** SIMs, power or the SMS limit changed since the hello. */
    @Serializable @SerialName("device_status")
    data class DeviceStatus(
        val sims: List<SimInfo>,
        val battery: Int? = null,
        val charging: Boolean? = null,
        val smsLimitPerMinute: Int? = null,
    ) : DeviceFrame

    @Serializable @SerialName("gateway_state")
    data class GatewayState(val online: Boolean) : DeviceFrame

    /** The settings changed, on the phone or after an [ServerFrame.ApplySettings]. */
    @Serializable @SerialName("settings")
    data class Settings(val settings: RemoteSettings) : DeviceFrame

    @Serializable @SerialName("status")
    data class StatusReport(
        val messageId: String,
        val status: MessageStatus,
        val at: Long,
        val error: String? = null,
    ) : DeviceFrame

    @Serializable @SerialName("batch_control")
    data class BatchControl(val batchId: String, val status: BatchStatus) : DeviceFrame

    @Serializable @SerialName("ping")
    data class Ping(val sentAt: Long) : DeviceFrame

    /**
     * An SMS this phone received. Sent until acked; [ref] is the phone's own id for it, so
     * the server stores it once.
     */
    @Serializable @SerialName("incoming")
    data class Incoming(
        val ref: String,
        val from: String,
        val body: String,
        val receivedAt: Long,
        val sim: Int? = null,
    ) : DeviceFrame
}
