package app.relay.gateway.model

import app.relay.protocol.BatchSource
import app.relay.protocol.BatchStatus
import app.relay.protocol.MessageStatus
import app.relay.protocol.MessageType

data class MessageTimeline(
    val receivedAt: Long,
    val queuedAt: Long,
    val sentAt: Long? = null,
    val deliveredAt: Long? = null,
    val failedAt: Long? = null,
)

data class Message(
    val id: String,
    val projectId: String,
    val to: String,
    val body: String,
    val status: MessageStatus,
    val timeline: MessageTimeline,
    val batchId: String? = null,
    /** SIM slot the message was (or will be) sent from, 1-based. */
    val sim: Int? = null,
    val error: String? = null,
    /** Send attempts made so far, including the current one. */
    val attempts: Int = 0,
    /** Earliest time the next attempt may start (retry backoff). */
    val notBefore: Long = 0,
    /** Last status the relay server acknowledged; drives the at-least-once status sync. */
    val reportedStatus: MessageStatus? = null,
    /** Result of the latest webhook call the server made for this message. */
    val webhook: WebhookOutcome? = null,
    val type: MessageType = MessageType.TRANSACTIONAL,
    /** Lower is sent sooner (otp 0, marketing 2); set by the server. */
    val priority: Int = 1,
) {
    val needsReport: Boolean get() = reportedStatus != status
}

data class WebhookOutcome(
    val status: MessageStatus,
    val httpStatus: Int?,
    val durationMs: Long,
    val error: String?,
) {
    val succeeded: Boolean get() = httpStatus != null && httpStatus in 200..299
}

data class Batch(
    val id: String,
    val projectId: String,
    val name: String,
    val source: BatchSource,
    val total: Int,
    val rate: Int,
    val status: BatchStatus,
    val createdAt: Long,
)

data class StatusCounts(
    val queued: Int = 0,
    val sending: Int = 0,
    val sent: Int = 0,
    val delivered: Int = 0,
    val failed: Int = 0,
) {
    /** Handed to the carrier, whatever the outcome. */
    val attempted: Int get() = sent + delivered + failed
    val pending: Int get() = queued + sending

    operator fun plus(other: StatusCounts) = StatusCounts(
        queued + other.queued,
        sending + other.sending,
        sent + other.sent,
        delivered + other.delivered,
        failed + other.failed,
    )

    companion object {
        fun of(statuses: Iterable<MessageStatus>): StatusCounts =
            statuses.fold(StatusCounts()) { acc, s -> acc + single(s) }

        fun single(status: MessageStatus, count: Int = 1) = when (status) {
            MessageStatus.QUEUED -> StatusCounts(queued = count)
            MessageStatus.SENDING -> StatusCounts(sending = count)
            MessageStatus.SENT -> StatusCounts(sent = count)
            MessageStatus.DELIVERED -> StatusCounts(delivered = count)
            MessageStatus.FAILED -> StatusCounts(failed = count)
        }
    }
}

data class Project(
    val id: String,
    val name: String,
    val slug: String,
    val endpoint: String,
    val webhookUrl: String?,
    val enabled: Boolean,
    val createdAt: Long,
    /** SIM slot every message uses unless the request names one; null = the device's default. */
    val sim: Int? = null,
    val defaultType: MessageType = MessageType.TRANSACTIONAL,
    /** Which received SMS the project takes; null until the server has sent them. */
    val receive: ReceiveRules? = null,
)

/**
 * Which received SMS a project takes, set on the web: replies from people it texted in the last
 * 30 days, [senders] always, and every SMS on its SIM when the phone is set to allow it ([all]).
 */
data class ReceiveRules(
    val replies: Boolean = true,
    val all: Boolean = false,
    val senders: List<String> = emptyList(),
)

/** Per-project traffic seen by this device. */
data class ProjectActivity(
    val today: Int = 0,
    val deliveredToday: Int = 0,
    val failedToday: Int = 0,
    val lastReceivedAt: Long? = null,
) {
    /** Share of finished messages delivered today, or null when nothing finished yet. */
    val deliveryRate: Double?
        get() = (deliveredToday + failedToday).takeIf { it > 0 }?.let { deliveredToday.toDouble() / it }
}

data class DeviceIdentity(
    val deviceId: String,
    val token: String,
    val name: String,
    val model: String,
    /** The web workspace this phone was paired into. */
    val workspaceName: String = "",
)

data class SimCard(val slot: Int, val carrier: String, val subscriptionId: Int)

/** An SMS this phone received, kept until the server acknowledges it. */
data class IncomingSms(
    /** Stable for the same SMS, so a repeated broadcast (or report) is stored once. */
    val ref: String,
    val from: String,
    val body: String,
    val receivedAt: Long,
    val sim: Int?,
    val reported: Boolean = false,
)
