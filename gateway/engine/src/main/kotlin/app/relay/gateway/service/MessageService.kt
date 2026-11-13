package app.relay.gateway.service

import app.relay.gateway.engine.MessageLedger
import app.relay.gateway.engine.StatusSync
import app.relay.gateway.engine.Transition
import app.relay.gateway.model.Message
import app.relay.gateway.port.Clock
import app.relay.gateway.port.MessageRepository
import kotlinx.coroutines.flow.Flow

/** Message log queries and user actions on single messages. */
class MessageService(
    private val messages: MessageRepository,
    private val ledger: MessageLedger,
    private val statusSync: StatusSync,
    private val clock: Clock,
    private val onRequeued: () -> Unit,
) {
    fun observeRecent(limit: Int): Flow<List<Message>> = messages.observeRecent(limit)

    fun search(query: String, limit: Int = LOG_LIMIT): Flow<List<Message>> = messages.observeSearch(query.trim(), limit)

    fun observe(id: String): Flow<Message?> = messages.observe(id)

    /** Puts a failed message back in the queue. Returns false if it isn't failed (anymore). */
    suspend fun retry(id: String): Boolean {
        val requeued = ledger.transition(id, Transition.ManualRetry, clock.now()) ?: return false
        statusSync.report(requeued)
        onRequeued()
        return true
    }

    companion object {
        const val LOG_LIMIT = 200
    }
}
