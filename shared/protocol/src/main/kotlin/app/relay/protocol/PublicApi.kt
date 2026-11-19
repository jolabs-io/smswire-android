package app.relay.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Contract for the web apps that send SMS through a project (mirrors packages/contracts):
 *   POST /v1/p/{slug}/messages        Authorization: Bearer <api key>   [Idempotency-Key]
 *   POST /v1/p/{slug}/messages/bulk
 *   GET  /v1/p/{slug}/messages        ?status&type&tag&to&batch_id&from&until&cursor&limit
 *   GET  /v1/p/{slug}/messages/{id}
 * The phone doesn't use these; they are mirrored for the end-to-end tests.
 */

@Serializable
data class SendMessageRequest(
    val to: String,
    /** The text; may contain {{placeholders}} filled from [variables]. Or give a [template]. */
    val body: String? = null,
    /** A saved template (id or name) used instead of [body]. */
    val template: String? = null,
    val variables: JsonObject? = null,
    /** A named SIM (its label, or its number) to send from: names a phone and a slot at once. */
    val from: String? = null,
    /**
     * Send only from this phone (its id from GET /v1/p/{project}/status). Omitted: whichever of
     * the project's phones is free, so a bulk send goes out over all of them at once.
     */
    val phone: String? = null,
    /**
     * Optional SIM slot (1-based). With [phone], that phone's slot; without it, only the
     * project's phones that have the slot may take the message. Falls back to the project's SIM.
     */
    val sim: Int? = null,
    /** Defaults to the project's type. Sets priority: otp first, marketing last. */
    val type: MessageType? = null,
    /** Up to 10 labels (a-z, 0-9, _ and -) for filtering and analytics. */
    val tags: List<String>? = null,
    /** Up to 2 KB of your own data, echoed in responses and webhooks. */
    val metadata: JsonObject? = null,
    /** ISO 8601 time to send at, up to 30 days ahead; omitted or past means now. */
    @SerialName("send_at") val sendAt: String? = null,
)

@Serializable
data class BulkItem(
    val to: String,
    /** This recipient's text; defaults to the batch's body or template. */
    val body: String? = null,
    val variables: JsonObject? = null,
    val type: MessageType? = null,
    val tags: List<String>? = null,
    val metadata: JsonObject? = null,
)

@Serializable
data class BulkSendRequest(
    val name: String? = null,
    val messages: List<BulkItem>,
    /** Messages per minute for each phone that takes a share; capped by that phone's max send rate. */
    val rate: Int? = null,
    /** A named SIM (its label, or its number) to send the whole batch from. */
    val from: String? = null,
    /** Send the whole batch from this one phone; omitted, it is spread over the project's phones. */
    val phone: String? = null,
    /** SIM slot (1-based) for the whole batch; without [phone], only phones that have it may take it. */
    val sim: Int? = null,
    /** Text (or saved template) for items without their own body. */
    val body: String? = null,
    val template: String? = null,
    /** Defaults for every item; an item's own value wins. */
    val type: MessageType? = null,
    val tags: List<String>? = null,
    val metadata: JsonObject? = null,
    /** ISO 8601 time to start the batch at, up to 30 days ahead. */
    @SerialName("send_at") val sendAt: String? = null,
)

@Serializable
data class MessageView(
    val id: String,
    val to: String,
    val status: ApiMessageStatus,
    val error: String? = null,
    val segments: Int,
    val type: MessageType = MessageType.TRANSACTIONAL,
    val tags: List<String> = emptyList(),
    val metadata: JsonObject? = null,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("received_at") val receivedAt: String,
    /** When a scheduled message is due to go out. */
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
)

@Serializable
data class MessagePage(
    val data: List<MessageView>,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class RejectedItem(val index: Int, val to: String, val reason: String)

@Serializable
data class BulkSendResponse(
    @SerialName("batch_id") val batchId: String,
    val accepted: Int,
    val rejected: List<RejectedItem> = emptyList(),
)

@Serializable
data class ErrorResponse(val error: String, val message: String)

/**
 * Body POSTed to a project's webhook. Signed: `X-SMSWire-Signature: t=<unix>,v1=<hex>` where
 * v1 = HMAC-SHA256(signing secret, "<t>.<raw body>").
 */
@Serializable
data class StatusWebhookPayload(
    val event: String = "message.status",
    val id: String,
    @SerialName("project_id") val projectId: String? = null,
    val to: String,
    val status: ApiMessageStatus,
    val error: String? = null,
    val segments: Int,
    val type: MessageType = MessageType.TRANSACTIONAL,
    val tags: List<String> = emptyList(),
    val metadata: JsonObject? = null,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
)

// ── One-time codes ──────────────────────────────────────────────────────────

/** POST /v1/p/{slug}/otp: texts a fresh code to [to]. */
@Serializable
data class OtpRequest(
    val to: String,
    /** Digits in the code, 4–8. */
    val length: Int = 6,
    /** Seconds the code stays valid, 60–1800. */
    val ttl: Int = 300,
    /** The text, with {{code}}; defaults to "Your code is {{code}}". */
    val body: String? = null,
    /** A saved template (id or name) with {{code}}, instead of [body]. */
    val template: String? = null,
    /** A named SIM to send the code from, so it always comes from one number. */
    val from: String? = null,
    /** Send only from this phone, so the code always comes from one number. */
    val phone: String? = null,
    val sim: Int? = null,
)

@Serializable
data class OtpResponse(
    val id: String,
    val to: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("message_id") val messageId: String,
)

/** POST /v1/p/{slug}/otp/verify */
@Serializable
data class OtpVerifyRequest(val id: String, val code: String)

@Serializable
enum class OtpFailure {
    @SerialName("invalid") INVALID,
    @SerialName("expired") EXPIRED,
    @SerialName("too_many_attempts") TOO_MANY_ATTEMPTS,
    @SerialName("already_used") ALREADY_USED,
}

@Serializable
data class OtpVerifyResponse(val valid: Boolean, val reason: OtpFailure? = null)

// ── Recurring sends ─────────────────────────────────────────────────────────

/** One recipient of a recurring send, with its own {{variables}}. */
@Serializable
data class ScheduleRecipient(val to: String, val variables: JsonObject? = null)

/** POST /v1/p/{slug}/schedules: a message (or batch) sent again and again on a cron schedule. */
@Serializable
data class ScheduleRequest(
    val name: String? = null,
    /** 5-field cron: minute hour day-of-month month day-of-week, e.g. "0 9 * * 1-5". */
    val cron: String,
    /** IANA time zone the cron is read in. */
    val timezone: String = "UTC",
    val to: String? = null,
    val messages: List<ScheduleRecipient>? = null,
    /** Contact groups, read at each run. */
    @SerialName("group_ids") val groupIds: List<String>? = null,
    val body: String? = null,
    val template: String? = null,
    val variables: JsonObject? = null,
    val type: MessageType? = null,
    val tags: List<String>? = null,
    val metadata: JsonObject? = null,
    /** A named SIM every run sends from. */
    val from: String? = null,
    /** Send every run from this one phone. */
    val phone: String? = null,
    val sim: Int? = null,
    val rate: Int? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("max_runs") val maxRuns: Int? = null,
    val enabled: Boolean = true,
)

/** PATCH /v1/p/{slug}/schedules/{id}: absent fields stay as they are. */
@Serializable
data class ScheduleUpdate(
    val name: String? = null,
    val cron: String? = null,
    val timezone: String? = null,
    val body: String? = null,
    val template: String? = null,
    val type: MessageType? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("max_runs") val maxRuns: Int? = null,
    val enabled: Boolean? = null,
)

@Serializable
enum class ScheduleRunStatus {
    @SerialName("sent") SENT,
    @SerialName("failed") FAILED,
    @SerialName("skipped") SKIPPED,
}

@Serializable
data class ScheduleRunView(
    val at: String,
    val status: ScheduleRunStatus,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("batch_id") val batchId: String? = null,
    val accepted: Int,
    val rejected: Int,
    val error: String? = null,
)

@Serializable
data class ScheduleRecipients(
    val numbers: Int,
    @SerialName("group_ids") val groupIds: List<String> = emptyList(),
)

@Serializable
data class ScheduleView(
    val id: String,
    val name: String,
    val cron: String,
    val timezone: String,
    val enabled: Boolean,
    val body: String? = null,
    @SerialName("template_id") val templateId: String? = null,
    val type: MessageType? = null,
    val tags: List<String> = emptyList(),
    val recipients: ScheduleRecipients,
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("last_run_at") val lastRunAt: String? = null,
    @SerialName("run_count") val runCount: Int,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("max_runs") val maxRuns: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_run") val lastRun: ScheduleRunView? = null,
    val runs: List<ScheduleRunView>? = null,
)

@Serializable
data class SchedulePage(val data: List<ScheduleView>)
