package app.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Lifecycle of a single SMS, shared by the relay server, the device and webhooks. */
@Serializable
enum class MessageStatus {
    @SerialName("queued") QUEUED,
    @SerialName("sending") SENDING,
    @SerialName("sent") SENT,
    @SerialName("delivered") DELIVERED,
    @SerialName("failed") FAILED;

    /** No further transitions are expected (a retry starts a new attempt explicitly). */
    val isTerminal: Boolean get() = this == DELIVERED || this == FAILED

    /** Waiting on this device: not yet handed to the carrier. */
    val isPending: Boolean get() = this == QUEUED || this == SENDING

    val wireName: String get() = name.lowercase()
}

/**
 * What the API and webhooks report: the device's statuses plus `cancelled`, a message stopped
 * before it reached a phone. Devices never see a cancelled message.
 */
@Serializable
enum class ApiMessageStatus {
    @SerialName("queued") QUEUED,
    @SerialName("sending") SENDING,
    @SerialName("sent") SENT,
    @SerialName("delivered") DELIVERED,
    @SerialName("failed") FAILED,
    @SerialName("cancelled") CANCELLED,
}

@Serializable
enum class BatchStatus {
    @SerialName("running") RUNNING,
    @SerialName("paused") PAUSED,
    @SerialName("done") DONE,
}

@Serializable
enum class BatchSource {
    @SerialName("api") API,
    @SerialName("app") APP,
    @SerialName("web") WEB,
}

/** What a message is for. Sets its dispatch priority: an OTP goes before a marketing backlog. */
@Serializable
enum class MessageType {
    @SerialName("otp") OTP,
    @SerialName("transactional") TRANSACTIONAL,
    @SerialName("notification") NOTIFICATION,
    @SerialName("marketing") MARKETING,
}
