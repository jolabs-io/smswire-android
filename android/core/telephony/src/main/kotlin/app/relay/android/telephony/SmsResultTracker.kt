package app.relay.android.telephony

import app.relay.gateway.port.Clock
import app.relay.gateway.port.SmsEvent
import app.relay.gateway.port.SmsEventSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Folds per-part radio callbacks into one outcome per message: sent once every part is
 * accepted, delivered once every part is confirmed, failed as soon as any part fails.
 * Events are buffered until the gateway engine collects them.
 */
class SmsResultTracker(private val clock: Clock) : SmsEventSource {
    private class Pending(val parts: Int, val awaitDelivery: Boolean) {
        val sent = HashSet<Int>()
        val delivered = HashSet<Int>()
        var finished = false
    }

    private val pending = ConcurrentHashMap<String, Pending>()
    private val channel = Channel<SmsEvent>(Channel.UNLIMITED)

    override val events: Flow<SmsEvent> = channel.receiveAsFlow()

    fun expect(messageId: String, parts: Int, awaitDelivery: Boolean) {
        pending[messageId] = Pending(parts, awaitDelivery)
    }

    fun forget(messageId: String) {
        pending.remove(messageId)
    }

    /** @param error null when the radio accepted the part. */
    fun onPartSent(messageId: String, part: Int, error: String?) {
        val p = pending[messageId]
        if (p == null) {
            // Process was restarted mid-send: treat the part we hear about as the whole message.
            emit(if (error == null) SmsEvent.Sent(messageId, clock.now()) else failed(messageId, error, retryable = true))
            return
        }
        val event = synchronized(p) {
            if (p.finished) return
            if (error != null) {
                p.finished = true
                return@synchronized failed(messageId, error, retryable = true)
            }
            p.sent += part
            if (p.sent.size == p.parts) SmsEvent.Sent(messageId, clock.now()) else null
        }
        if (event is SmsEvent.Failed || (event is SmsEvent.Sent && !p.awaitDelivery)) pending.remove(messageId)
        event?.let(::emit)
    }

    fun onPartDelivered(messageId: String, part: Int, outcome: DeliveryOutcome) {
        if (outcome == DeliveryOutcome.PENDING) return
        val p = pending[messageId]
        if (p == null) {
            emit(outcome.toEvent(messageId))
            return
        }
        val event = synchronized(p) {
            if (outcome == DeliveryOutcome.FAILED) {
                p.finished = true
                return@synchronized outcome.toEvent(messageId)
            }
            p.delivered += part
            if (p.delivered.size == p.parts) outcome.toEvent(messageId) else null
        }
        if (event != null) pending.remove(messageId)
        event?.let(::emit)
    }

    private fun DeliveryOutcome.toEvent(messageId: String): SmsEvent = when (this) {
        DeliveryOutcome.DELIVERED -> SmsEvent.Delivered(messageId, clock.now())
        else -> failed(messageId, UNDELIVERED, retryable = false)
    }

    private fun failed(messageId: String, error: String, retryable: Boolean) =
        SmsEvent.Failed(messageId, clock.now(), error, retryable)

    private fun emit(event: SmsEvent) {
        channel.trySend(event)
    }

    private companion object {
        const val UNDELIVERED = "Carrier reported undelivered"
    }
}

enum class DeliveryOutcome { DELIVERED, PENDING, FAILED }

/** Implemented by the Application so manifest receivers can reach the live tracker. */
interface SmsResultTrackerProvider {
    val smsResultTracker: SmsResultTracker
}
