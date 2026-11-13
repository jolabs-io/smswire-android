package app.relay.gateway.engine

import app.relay.gateway.model.Message
import app.relay.gateway.port.MessageRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises read-modify-write updates to messages. The dispatcher, the radio callbacks
 * and server acks all touch the same rows concurrently; going through here keeps a late
 * writer from clobbering a newer status.
 */
class MessageLedger(private val messages: MessageRepository) {
    private val mutex = Mutex()

    /** Applies [change]; returns the stored message, or null if missing or [change] declined. */
    suspend fun modify(id: String, change: (Message) -> Message?): Message? = mutex.withLock {
        val current = messages.find(id) ?: return null
        val next = change(current) ?: return null
        if (next != current) messages.update(next)
        next
    }

    suspend fun transition(id: String, transition: Transition, at: Long): Message? =
        modify(id) { MessageStateMachine.apply(it, transition, at) }
}
