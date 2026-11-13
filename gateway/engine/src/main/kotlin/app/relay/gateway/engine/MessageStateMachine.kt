package app.relay.gateway.engine

import app.relay.gateway.model.Message
import app.relay.protocol.MessageStatus
import app.relay.protocol.MessageStatus.DELIVERED
import app.relay.protocol.MessageStatus.FAILED
import app.relay.protocol.MessageStatus.QUEUED
import app.relay.protocol.MessageStatus.SENDING
import app.relay.protocol.MessageStatus.SENT

sealed interface Transition {
    data class StartSending(val sim: Int?) : Transition
    data object Sent : Transition
    data object Delivered : Transition
    data class Failed(val error: String) : Transition

    /** Automatic retry after a send failure: back to the queue once [notBefore] passes. */
    data class Requeue(val error: String, val notBefore: Long) : Transition

    /** User-triggered retry of a failed message: starts over with a fresh attempt budget. */
    data object ManualRetry : Transition
}

/**
 * The only place message status changes are decided. Returns null for transitions that
 * don't apply to the current status (late or duplicate radio callbacks), which callers ignore.
 */
object MessageStateMachine {
    private val allowed: Map<MessageStatus, Set<MessageStatus>> = mapOf(
        QUEUED to setOf(SENDING),
        // SENDING -> DELIVERED covers a delivery report that overtakes a lost "sent" callback.
        SENDING to setOf(SENT, DELIVERED, FAILED, QUEUED),
        SENT to setOf(DELIVERED, FAILED),
        DELIVERED to emptySet(),
        FAILED to setOf(QUEUED),
    )

    fun canMove(from: MessageStatus, to: MessageStatus) = to in allowed.getValue(from)

    fun apply(message: Message, transition: Transition, at: Long): Message? {
        val target = targetOf(transition)
        if (!canMove(message.status, target)) return null
        if (transition is Transition.ManualRetry && message.status != FAILED) return null
        val t = message.timeline
        return when (transition) {
            is Transition.StartSending -> message.copy(
                status = SENDING,
                sim = transition.sim,
                attempts = message.attempts + 1,
                error = null,
            )
            Transition.Sent -> message.copy(status = SENT, timeline = t.copy(sentAt = at))
            Transition.Delivered -> message.copy(
                status = DELIVERED,
                timeline = t.copy(sentAt = t.sentAt ?: at, deliveredAt = at),
            )
            is Transition.Failed -> message.copy(
                status = FAILED,
                error = transition.error,
                timeline = t.copy(failedAt = at),
            )
            is Transition.Requeue -> message.copy(
                status = QUEUED,
                error = transition.error,
                notBefore = transition.notBefore,
            )
            Transition.ManualRetry -> message.copy(
                status = QUEUED,
                error = null,
                attempts = 0,
                notBefore = 0,
                timeline = t.copy(queuedAt = at, sentAt = null, deliveredAt = null, failedAt = null),
            )
        }
    }

    private fun targetOf(transition: Transition) = when (transition) {
        is Transition.StartSending -> SENDING
        Transition.Sent -> SENT
        Transition.Delivered -> DELIVERED
        is Transition.Failed -> FAILED
        is Transition.Requeue, Transition.ManualRetry -> QUEUED
    }
}
