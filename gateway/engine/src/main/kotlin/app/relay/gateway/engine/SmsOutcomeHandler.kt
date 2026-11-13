package app.relay.gateway.engine

import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.SmsEvent
import app.relay.protocol.MessageStatus

/** Applies radio callbacks (sent / delivered / failed) and schedules automatic retries. */
class SmsOutcomeHandler(
    private val ledger: MessageLedger,
    private val settings: SettingsRepository,
    private val retryPolicy: RetryPolicy,
    private val statusSync: StatusSync,
) {
    suspend fun handle(event: SmsEvent) {
        val updated = when (event) {
            is SmsEvent.Sent -> ledger.transition(event.messageId, Transition.Sent, event.at)
            is SmsEvent.Delivered -> ledger.transition(event.messageId, Transition.Delivered, event.at)
            is SmsEvent.Failed -> {
                val maxRetries = settings.current().maxRetries
                ledger.modify(event.messageId) { m ->
                    val backoff = retryPolicy.delayBefore(m.attempts, maxRetries)
                        .takeIf { event.retryable && m.status == MessageStatus.SENDING }
                    val transition = if (backoff != null) {
                        Transition.Requeue(event.error, notBefore = event.at + backoff)
                    } else {
                        Transition.Failed(event.error)
                    }
                    MessageStateMachine.apply(m, transition, event.at)
                }
            }
        }
        updated?.let(statusSync::report)
    }
}
