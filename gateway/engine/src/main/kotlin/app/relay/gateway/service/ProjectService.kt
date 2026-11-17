package app.relay.gateway.service

import app.relay.gateway.model.Project
import app.relay.gateway.model.ProjectActivity
import app.relay.gateway.model.ReceiveRules
import app.relay.gateway.port.Clock
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.ProjectRepository
import app.relay.gateway.port.RelayApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

data class ProjectOverview(val project: Project, val activity: ProjectActivity)

/** The projects this phone sends for, with today's traffic. Managed on the web; the phone can change what they receive. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectService(
    private val projects: ProjectRepository,
    private val messages: MessageRepository,
    private val api: RelayApi,
    private val clock: Clock,
    private val dayTicker: Flow<Unit>,
) {
    private val activity: Flow<Map<String, ProjectActivity>> =
        dayTicker.flatMapLatest { messages.observeProjectActivity(clock.startOfToday()) }

    fun observeAll(): Flow<List<ProjectOverview>> = combine(projects.observeAll(), activity) { list, stats ->
        list.sortedBy { it.createdAt }.map { ProjectOverview(it, stats[it.id] ?: ProjectActivity()) }
    }

    fun observe(id: String): Flow<ProjectOverview?> = combine(projects.observe(id), activity) { p, stats ->
        p?.let { ProjectOverview(it, stats[it.id] ?: ProjectActivity()) }
    }

    /**
     * Changes what [id] receives, on the server first: the saved project replaces the one kept, and
     * the server tells the web and the project's other phones. Throws when the server can't be reached.
     */
    suspend fun updateReceive(id: String, rules: ReceiveRules) {
        projects.upsert(api.updateReceive(id, rules))
    }

    /** Fetches the list again (on start and whenever the server says it changed). */
    suspend fun refresh() {
        projects.replaceAll(api.listProjects())
    }
}
