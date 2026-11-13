package app.relay.gateway.service

import app.relay.gateway.model.Batch
import app.relay.gateway.model.StatusCounts
import app.relay.gateway.port.BatchRepository
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.RelayConnection
import app.relay.gateway.port.SettingsRepository
import app.relay.protocol.BatchStatus
import app.relay.protocol.DeviceFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class BatchProgress(
    val batch: Batch,
    val counts: StatusCounts,
    private val deliveryReports: Boolean,
) {
    val delivered: Int get() = counts.delivered
    val failed: Int get() = counts.failed
    /** Accepted by the carrier, waiting for a delivery report. */
    val awaitingReport: Int get() = counts.sent
    val processed: Int get() = delivered + failed
    /** Not handed to the carrier yet (includes messages still held on the server). */
    val notSent: Int get() = (batch.total - processed - awaitingReport).coerceAtLeast(0)
    /** Everything without a final outcome. */
    val pending: Int get() = (batch.total - processed).coerceAtLeast(0)
    val percent: Int get() = if (batch.total == 0) 100 else processed * 100 / batch.total

    val isComplete: Boolean get() = notSent == 0 && (awaitingReport == 0 || !deliveryReports)
    val status: BatchStatus get() = if (isComplete) BatchStatus.DONE else batch.status

    /** Minutes left at the batch rate, or null while paused / done. */
    val etaMinutes: Int?
        get() = if (status != BatchStatus.RUNNING) null else maxOf(1, ceilDiv(notSent, batch.rate))
}

/** Batches sent through this phone (composed on the web or via the API), with progress and pause. */
class BatchService(
    private val batches: BatchRepository,
    private val messages: MessageRepository,
    private val settings: SettingsRepository,
    private val connection: RelayConnection,
    private val onResumed: () -> Unit,
) {
    fun observeProgress(): Flow<List<BatchProgress>> = combine(
        batches.observeAll(),
        messages.observeBatchCounts(),
        settings.settings,
    ) { all, counts, config ->
        all.sortedByDescending { it.createdAt }
            .map { BatchProgress(it, counts[it.id] ?: StatusCounts(), config.requestDeliveryReports) }
    }

    suspend fun setPaused(batchId: String, paused: Boolean) {
        val status = if (paused) BatchStatus.PAUSED else BatchStatus.RUNNING
        batches.setStatus(batchId, status)
        connection.send(DeviceFrame.BatchControl(batchId, status))
        if (!paused) onResumed()
    }
}

internal fun ceilDiv(a: Int, b: Int): Int = if (b <= 0) a else (a + b - 1) / b
