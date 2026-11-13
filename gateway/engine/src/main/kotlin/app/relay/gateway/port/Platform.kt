package app.relay.gateway.port

import app.relay.gateway.model.SimCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Clock {
    fun now(): Long
    fun startOfToday(): Long
}

data class OutgoingSms(
    val messageId: String,
    val to: String,
    val body: String,
    val simSlot: Int?,
    val requestDeliveryReport: Boolean,
)

class SmsSendException(val reason: String, cause: Throwable? = null) : Exception(reason, cause)

/** Hands an SMS to the radio. Outcomes arrive asynchronously through [SmsEventSource]. */
interface SmsSender {
    /** @throws SmsSendException when the platform rejects the message before it reaches the carrier. */
    suspend fun send(sms: OutgoingSms)
}

sealed interface SmsEvent {
    val messageId: String
    val at: Long

    data class Sent(override val messageId: String, override val at: Long) : SmsEvent
    data class Delivered(override val messageId: String, override val at: Long) : SmsEvent

    /** @param retryable false when the carrier reported the message undelivered. */
    data class Failed(
        override val messageId: String,
        override val at: Long,
        val error: String,
        val retryable: Boolean,
    ) : SmsEvent
}

interface SmsEventSource {
    val events: Flow<SmsEvent>
}

interface SimCardProvider {
    fun available(): List<SimCard>
}

/** A platform cap on outgoing SMS parts per window, e.g. Android's "confirm after 30 a minute". */
data class SmsQuota(val maxParts: Int, val windowMs: Long)

fun interface SmsQuotaProvider {
    /** The cap that applies to this app right now, or null when it is exempt. */
    fun current(): SmsQuota?
}

data class PowerState(val batteryPercent: Int, val charging: Boolean)

interface PowerMonitor {
    val state: StateFlow<PowerState>
}
