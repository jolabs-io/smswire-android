package app.relay.android.feature.bulk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.android.designsystem.format.Format
import app.relay.gateway.service.BatchProgress
import app.relay.gateway.service.BatchService
import app.relay.gateway.service.ProjectService
import app.relay.protocol.BatchSource
import app.relay.protocol.BatchStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ActiveBatchUi(
    val id: String,
    val name: String,
    val meta: String,
    val running: Boolean,
    val done: String,
    val total: String,
    val percent: String,
    val deliveredFraction: Float,
    val failedFraction: Float,
    val awaitingFraction: Float,
    val delivered: String,
    val pending: String,
    val failed: String,
    val rateLine: String,
)

data class DoneBatchUi(val id: String, val name: String, val summary: String, val okPercent: String)

data class BulkUiState(
    val active: List<ActiveBatchUi> = emptyList(),
    val done: List<DoneBatchUi> = emptyList(),
)

class BulkViewModel(
    private val batches: BatchService,
    projects: ProjectService,
    private val now: () -> Long,
    ticker: Flow<Unit>,
) : ViewModel() {
    val state: StateFlow<BulkUiState> = combine(batches.observeProgress(), projects.observeAll(), ticker) { list, projectList, _ ->
        val names = projectList.associate { it.project.id to it.project.name }
        BulkUiState(
            active = list.filter { it.status != BatchStatus.DONE }.map { it.toActive(names) },
            done = list.filter { it.status == BatchStatus.DONE }.map { it.toDone() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BulkUiState())

    fun toggle(batchId: String, running: Boolean) {
        viewModelScope.launch { batches.setPaused(batchId, paused = running) }
    }

    private fun BatchProgress.toActive(names: Map<String, String>): ActiveBatchUi {
        val total = maxOf(1, batch.total).toFloat()
        val running = status == BatchStatus.RUNNING
        val source = when (batch.source) {
            BatchSource.API -> "API"
            BatchSource.WEB -> "web"
            BatchSource.APP -> "app"
        }
        return ActiveBatchUi(
            id = batch.id,
            name = batch.name,
            meta = "${names[batch.projectId] ?: "—"} · via $source · ${created(batch.createdAt)}",
            running = running,
            done = Format.count(processed),
            total = Format.count(batch.total),
            percent = "$percent%",
            deliveredFraction = delivered / total,
            failedFraction = failed / total,
            awaitingFraction = awaitingReport / total,
            delivered = Format.count(delivered),
            pending = Format.count(pending),
            failed = Format.count(failed),
            rateLine = "${batch.rate}/min · " + (etaMinutes?.let { "~$it min left" } ?: "paused"),
        )
    }

    private fun BatchProgress.toDone() = DoneBatchUi(
        id = batch.id,
        name = batch.name,
        summary = "${Format.count(delivered)} delivered · ${Format.count(failed)} failed · ${created(batch.createdAt)}",
        okPercent = Format.percent(if (batch.total == 0) null else delivered.toDouble() / batch.total),
    )

    /** "09:12" today, "Yesterday", otherwise "Oct 24". */
    private fun created(at: Long): String {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        val day: LocalDate = time.toLocalDate()
        return when (day) {
            today -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
            today.minusDays(1) -> "Yesterday"
            else -> time.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
        }
    }
}
