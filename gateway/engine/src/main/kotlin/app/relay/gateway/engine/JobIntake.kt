package app.relay.gateway.engine

import app.relay.gateway.model.Batch
import app.relay.gateway.model.Message
import app.relay.gateway.model.MessageTimeline
import app.relay.gateway.port.BatchRepository
import app.relay.gateway.port.Clock
import app.relay.gateway.port.MessageRepository
import app.relay.protocol.BatchDto
import app.relay.protocol.BatchStatus
import app.relay.protocol.MessageStatus
import app.relay.protocol.SmsJob

/** Turns work pushed by the relay server into local state. Idempotent: dispatch is at-least-once. */
class JobIntake(
    private val messages: MessageRepository,
    private val batches: BatchRepository,
    private val clock: Clock,
    private val onNewWork: () -> Unit,
) {
    suspend fun accept(jobs: List<SmsJob>) {
        val now = clock.now()
        var added = false
        for (job in jobs) {
            added = messages.insertIfAbsent(job.toMessage(now)) || added
        }
        if (added) onNewWork()
    }

    suspend fun accept(batch: BatchDto) {
        // Local pause state wins over the server's copy once the batch is known here.
        val status = batches.find(batch.id)?.status ?: batch.status
        batches.upsert(batch.toBatch().copy(status = status))
    }

    /** A batch paused or resumed on the web. */
    suspend fun control(batchId: String, status: BatchStatus) {
        if (batches.find(batchId) == null) return
        batches.setStatus(batchId, status)
        if (status == BatchStatus.RUNNING) onNewWork()
    }

    private fun SmsJob.toMessage(now: Long) = Message(
        id = messageId,
        projectId = projectId,
        to = to,
        body = body,
        status = MessageStatus.QUEUED,
        timeline = MessageTimeline(receivedAt = receivedAt, queuedAt = now),
        batchId = batchId,
        sim = sim,
        // The server recorded "queued" when it accepted the request.
        reportedStatus = MessageStatus.QUEUED,
        type = type,
        priority = priority,
    )
}

fun BatchDto.toBatch() = Batch(
    id = id,
    projectId = projectId,
    name = name,
    source = source,
    total = total,
    rate = rate,
    status = status,
    createdAt = createdAt,
)
