package app.relay.gateway.engine

import app.relay.gateway.model.Message
import app.relay.gateway.model.WebhookOutcome
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.RelayConnection
import app.relay.protocol.DeviceFrame
import app.relay.protocol.MessageStatus
import app.relay.protocol.ServerFrame

/**
 * At-least-once status reporting to the relay server (which fires the project webhook).
 * A status counts as reported only once the server acks it, so anything sent while
 * offline is re-sent by [flush] on reconnect.
 */
class StatusSync(
    private val messages: MessageRepository,
    private val ledger: MessageLedger,
    private val connection: RelayConnection,
) {
    fun report(message: Message) {
        if (!message.needsReport) return
        connection.send(
            DeviceFrame.StatusReport(
                messageId = message.id,
                status = message.status,
                at = message.statusTime(),
                error = message.error.takeIf { message.status == MessageStatus.FAILED },
            ),
        )
    }

    suspend fun flush() {
        messages.findUnreported().forEach(::report)
    }

    suspend fun onAck(ack: ServerFrame.StatusAck) {
        ledger.modify(ack.messageId) { m ->
            if (m.status == ack.status) m.copy(reportedStatus = ack.status) else null
        }
    }

    suspend fun onWebhookResult(result: ServerFrame.WebhookResult) {
        ledger.modify(result.messageId) { m ->
            m.copy(webhook = WebhookOutcome(result.status, result.httpStatus, result.durationMs, result.error))
        }
    }

    private fun Message.statusTime(): Long = with(timeline) {
        when (status) {
            MessageStatus.QUEUED, MessageStatus.SENDING -> queuedAt
            MessageStatus.SENT -> sentAt ?: queuedAt
            MessageStatus.DELIVERED -> deliveredAt ?: queuedAt
            MessageStatus.FAILED -> failedAt ?: queuedAt
        }
    }
}
