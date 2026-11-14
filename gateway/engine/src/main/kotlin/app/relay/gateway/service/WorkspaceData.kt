package app.relay.gateway.service

import app.relay.gateway.port.BatchRepository
import app.relay.gateway.port.InboundRepository
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What this phone keeps for the workspace it's paired with. None of it may reach the next one. */
class WorkspaceData(
    private val messages: MessageRepository,
    private val batches: BatchRepository,
    private val projects: ProjectRepository,
    private val inbound: InboundRepository,
) {
    /** Messages still waiting to be sent from this phone: leaving the workspace drops them. */
    val unsent: Flow<Int> = messages.observeStatusCounts(since = 0).map { it.pending }.distinctUntilChanged()

    /**
     * Leaving the workspace: the queue, received SMS not yet uploaded, unfinished batches and the
     * project list always go. [keepHistory] keeps sent messages and finished batches to look back on.
     */
    suspend fun clear(keepHistory: Boolean) {
        messages.clearForNewWorkspace(keepHistory)
        batches.clearForNewWorkspace(keepFinished = keepHistory)
        inbound.clear()
        projects.replaceAll(emptyList())
    }
}
