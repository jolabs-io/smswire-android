package app.relay.gateway.data.memory

import app.relay.gateway.model.Batch
import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.IncomingSms
import app.relay.gateway.model.Message
import app.relay.gateway.model.Project
import app.relay.gateway.model.ProjectActivity
import app.relay.gateway.model.StatusCounts
import app.relay.gateway.port.BatchRepository
import app.relay.gateway.port.DeviceIdentityStore
import app.relay.gateway.port.InboundRepository
import app.relay.gateway.port.JoinKeywordStore
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.ProjectRepository
import app.relay.gateway.port.PendingUpdate
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.UpdateStore
import app.relay.protocol.BatchStatus
import app.relay.protocol.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Non-persistent adapters: for tests, previews and a server-less demo mode. */
class InMemoryMessageRepository : MessageRepository {
    private val store = MutableStateFlow<Map<String, Message>>(emptyMap())
    private val newestFirst = store.map { m -> m.values.sortedByDescending { it.timeline.receivedAt } }

    val all: List<Message> get() = store.value.values.toList()

    override fun observeRecent(limit: Int) = newestFirst.map { it.take(limit) }.distinctUntilChanged()

    override fun observeSearch(query: String, limit: Int): Flow<List<Message>> {
        val q = query.lowercase()
        val digits = q.filterNot(Char::isWhitespace)
        return newestFirst.map { list ->
            list.filter {
                q.isEmpty() || it.to.filterNot(Char::isWhitespace).contains(digits) ||
                    it.body.lowercase().contains(q) || it.id.lowercase().contains(q)
            }.take(limit)
        }.distinctUntilChanged()
    }

    override fun observe(id: String) = store.map { it[id] }.distinctUntilChanged()

    override suspend fun find(id: String) = store.value[id]

    override suspend fun messagedSince(digits: String, since: Long) =
        store.value.values.any { it.timeline.receivedAt >= since && it.to.filter(Char::isDigit).endsWith(digits) }

    override suspend fun textedBy(digits: String, since: Long) =
        store.value.values.filter { it.timeline.receivedAt >= since && it.to.filter(Char::isDigit).endsWith(digits) }.map { it.projectId }.toSet()

    override suspend fun insertIfAbsent(message: Message): Boolean {
        var inserted = false
        store.update { current ->
            if (message.id in current) current else current.plus(message.id to message).also { inserted = true }
        }
        return inserted
    }

    override suspend fun update(message: Message) = store.update { it + (message.id to message) }

    override suspend fun nextSendable(now: Long, excludedBatchIds: Set<String>, sim: Int?, excludedIds: Set<String>) = store.value.values
        .filter {
            it.status == MessageStatus.QUEUED && it.notBefore <= now && it.batchId !in excludedBatchIds && it.id !in excludedIds &&
                (sim == null || it.sim == null || it.sim == sim)
        }
        .minWithOrNull(compareBy<Message>({ it.priority }, { it.timeline.receivedAt }))

    override suspend fun findUnreported() = store.value.values.filter { it.needsReport }

    override fun observeStatusCounts(since: Long) = store.map { m ->
        StatusCounts.of(m.values.filter { it.timeline.receivedAt >= since }.map { it.status })
    }.distinctUntilChanged()

    override fun observeSentTimes(since: Long) = store.map { m ->
        m.values.mapNotNull { it.timeline.sentAt }.filter { it >= since }.sorted()
    }.distinctUntilChanged()

    override fun observeBatchCounts() = store.map { m ->
        m.values.filter { it.batchId != null }.groupBy { it.batchId!! }
            .mapValues { (_, list) -> StatusCounts.of(list.map { it.status }) }
    }.distinctUntilChanged()

    override fun observeProjectActivity(since: Long) = store.map { m ->
        m.values.groupBy { it.projectId }.mapValues { (_, list) ->
            val today = list.filter { it.timeline.receivedAt >= since }
            ProjectActivity(
                today = today.size,
                deliveredToday = today.count { it.status == MessageStatus.DELIVERED },
                failedToday = today.count { it.status == MessageStatus.FAILED },
                lastReceivedAt = list.maxOfOrNull { it.timeline.receivedAt },
            )
        }
    }.distinctUntilChanged()

    override suspend fun clearForNewWorkspace(keepHistory: Boolean) = store.update { current ->
        if (!keepHistory) emptyMap()
        else current.filterValues { !it.status.isPending }.mapValues { (_, m) -> m.copy(reportedStatus = m.status) }
    }
}

class InMemoryBatchRepository : BatchRepository {
    private val store = MutableStateFlow<Map<String, Batch>>(emptyMap())

    override fun observeAll() = store.map { it.values.toList() }
    override suspend fun find(id: String) = store.value[id]
    override suspend fun upsert(batch: Batch) = store.update { it + (batch.id to batch) }
    override suspend fun setStatus(id: String, status: BatchStatus) = store.update { current ->
        val batch = current[id] ?: return@update current
        current + (id to batch.copy(status = status))
    }
    override suspend fun pausedIds() = store.value.values.filter { it.status == BatchStatus.PAUSED }.map { it.id }.toSet()
    override suspend fun clearForNewWorkspace(keepFinished: Boolean) = store.update { current ->
        if (keepFinished) current.filterValues { it.status == BatchStatus.DONE } else emptyMap()
    }
}

class InMemoryProjectRepository : ProjectRepository {
    private val store = MutableStateFlow<Map<String, Project>>(emptyMap())

    override fun observeAll() = store.map { it.values.toList() }
    override suspend fun all() = store.value.values.toList()
    override fun observe(id: String) = store.map { it[id] }.distinctUntilChanged()
    override suspend fun find(id: String) = store.value[id]
    override suspend fun upsert(project: Project) = store.update { it + (project.id to project) }
    override suspend fun replaceAll(projects: List<Project>) = store.update { projects.associateBy { it.id } }
    override suspend fun delete(id: String) = store.update { it - id }
}

class InMemorySettingsRepository(initial: GatewaySettings = GatewaySettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<GatewaySettings> = state
    override suspend fun current() = state.value
    override suspend fun update(transform: (GatewaySettings) -> GatewaySettings) = state.update(transform)
}

class InMemoryDeviceIdentityStore(initial: DeviceIdentity? = null) : DeviceIdentityStore {
    private val state = MutableStateFlow(initial)
    override val identity: Flow<DeviceIdentity?> = state
    override suspend fun load() = state.value
    override suspend fun save(identity: DeviceIdentity) {
        state.value = identity
    }

    override suspend fun clear() {
        state.value = null
    }
}

class InMemoryInboundRepository : InboundRepository {
    private val store = MutableStateFlow<Map<String, IncomingSms>>(emptyMap())

    override suspend fun insertIfAbsent(sms: IncomingSms): Boolean {
        var inserted = false
        store.update { current -> if (sms.ref in current) current else current.plus(sms.ref to sms).also { inserted = true } }
        return inserted
    }

    override suspend fun unreported() = store.value.values.filterNot { it.reported }.sortedBy { it.receivedAt }

    override suspend fun markReported(ref: String) = store.update { current ->
        current[ref]?.let { current + (ref to it.copy(reported = true)) } ?: current
    }

    override suspend fun clear() = store.update { emptyMap() }
}

class InMemoryJoinKeywordStore : JoinKeywordStore {
    @Volatile private var keywords: Set<String> = emptySet()

    override suspend fun keywords() = keywords
    override suspend fun replace(keywords: Set<String>) {
        this.keywords = keywords
    }
}

class InMemoryUpdateStore : UpdateStore {
    private var snoozed: Pair<String, Long>? = null
    private var pending: PendingUpdate? = null
    private var whatsNew: Pair<String, String>? = null

    override suspend fun snoozed() = snoozed
    override suspend fun snooze(versionName: String, until: Long) {
        snoozed = versionName to until
    }

    override suspend fun installing(versionName: String, notes: String?, wasOnline: Boolean) {
        pending = PendingUpdate(versionName, notes, wasOnline)
    }

    override suspend fun pending() = pending
    override suspend fun clearPending() {
        pending = null
    }

    override suspend fun whatsNew() = whatsNew
    override suspend fun setWhatsNew(versionName: String?, notes: String?) {
        whatsNew = if (versionName != null && notes != null) versionName to notes else null
    }
}
