package app.relay.android.data

import app.relay.android.data.db.BatchDao
import app.relay.android.data.db.BatchEntity
import app.relay.android.data.db.IncomingDao
import app.relay.android.data.db.IncomingEntity
import app.relay.android.data.db.MessageDao
import app.relay.android.data.db.MessageEntity
import app.relay.android.data.db.ProjectDao
import app.relay.android.data.db.ProjectEntity
import app.relay.gateway.model.Batch
import app.relay.gateway.model.IncomingSms
import app.relay.gateway.model.Message
import app.relay.gateway.model.MessageTimeline
import app.relay.gateway.model.Project
import app.relay.gateway.model.ProjectActivity
import app.relay.gateway.model.ReceiveRules
import app.relay.gateway.model.StatusCounts
import app.relay.gateway.model.WebhookOutcome
import app.relay.gateway.port.BatchRepository
import app.relay.gateway.port.InboundRepository
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.ProjectRepository
import app.relay.protocol.BatchSource
import app.relay.protocol.BatchStatus
import app.relay.protocol.MessageStatus
import app.relay.protocol.MessageType
import app.relay.protocol.ReceiveRulesDto
import app.relay.protocol.RelayJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomMessageRepository(private val dao: MessageDao) : MessageRepository {
    override fun observeRecent(limit: Int) = dao.recent(limit).map { list -> list.map { it.toModel() } }

    override fun observeSearch(query: String, limit: Int): Flow<List<Message>> {
        val q = query.lowercase()
        return dao.search(q, q.filterNot(Char::isWhitespace), limit).map { list -> list.map { it.toModel() } }
    }

    override fun observe(id: String) = dao.observe(id).map { it?.toModel() }.distinctUntilChanged()

    override suspend fun find(id: String) = dao.find(id)?.toModel()

    override suspend fun messagedSince(digits: String, since: Long) = dao.messagedSince(digits, since)

    override suspend fun textedBy(digits: String, since: Long) = dao.textedBy(digits, since).toSet()

    override suspend fun lastSimFor(digits: String, since: Long) = dao.lastSimFor(digits, since)

    override suspend fun insertIfAbsent(message: Message) = dao.insert(message.toEntity()) != -1L

    override suspend fun update(message: Message) = dao.update(message.toEntity())

    override suspend fun nextSendable(now: Long, excludedBatchIds: Set<String>, sim: Int?, excludedIds: Set<String>) =
        if (sim == null) {
            dao.nextSendable(now, excludedBatchIds.toList(), excludedIds.toList())?.toModel()
        } else {
            dao.nextSendableForSim(now, excludedBatchIds.toList(), excludedIds.toList(), sim)?.toModel()
        }

    override suspend fun findUnreported() = dao.unreported().map { it.toModel() }

    override fun observeStatusCounts(since: Long) = dao.statusCounts(since).map { rows ->
        rows.fold(StatusCounts()) { acc, row -> acc + StatusCounts.single(MessageStatus.valueOf(row.status), row.count) }
    }.distinctUntilChanged()

    override fun observeSentTimes(since: Long) = dao.sentTimes(since).distinctUntilChanged()

    override fun observeBatchCounts() = dao.batchCounts().map { rows ->
        rows.groupBy { it.batchId }.mapValues { (_, group) ->
            group.fold(StatusCounts()) { acc, row -> acc + StatusCounts.single(MessageStatus.valueOf(row.status), row.count) }
        }
    }.distinctUntilChanged()

    override fun observeProjectActivity(since: Long) = dao.projectActivity(since).map { rows ->
        rows.associate { it.projectId to ProjectActivity(it.today, it.deliveredToday, it.failedToday, it.lastReceivedAt) }
    }.distinctUntilChanged()

    override suspend fun clearForNewWorkspace(keepHistory: Boolean) {
        if (keepHistory) {
            dao.deletePending()
            dao.markAllReported()
        } else {
            dao.deleteAll()
        }
    }
}

class RoomBatchRepository(private val dao: BatchDao) : BatchRepository {
    override fun observeAll() = dao.all().map { list -> list.map { it.toModel() } }
    override suspend fun find(id: String) = dao.find(id)?.toModel()
    override suspend fun upsert(batch: Batch) = dao.upsert(batch.toEntity())
    override suspend fun setStatus(id: String, status: BatchStatus) = dao.setStatus(id, status.name)
    override suspend fun pausedIds() = dao.pausedIds().toSet()
    override suspend fun clearForNewWorkspace(keepFinished: Boolean) = if (keepFinished) dao.deleteUnfinished() else dao.deleteAll()
}

class RoomProjectRepository(private val dao: ProjectDao) : ProjectRepository {
    override fun observeAll() = dao.all().map { list -> list.map { it.toModel() } }
    override suspend fun all() = dao.list().map { it.toModel() }
    override fun observe(id: String) = dao.observe(id).map { it?.toModel() }.distinctUntilChanged()
    override suspend fun find(id: String) = dao.find(id)?.toModel()
    override suspend fun upsert(project: Project) = dao.upsert(project.toEntity())
    override suspend fun replaceAll(projects: List<Project>) = dao.replaceAll(projects.map { it.toEntity() })
    override suspend fun delete(id: String) = dao.delete(id)
}

class RoomInboundRepository(private val dao: IncomingDao) : InboundRepository {
    override suspend fun insertIfAbsent(sms: IncomingSms) =
        dao.insert(IncomingEntity(sms.ref, sms.from, sms.body, sms.receivedAt, sms.sim, sms.reported)) != -1L

    override suspend fun unreported() = dao.unreported().map { IncomingSms(it.ref, it.sender, it.body, it.receivedAt, it.sim, it.reported) }

    override suspend fun markReported(ref: String) = dao.markReported(ref)

    override suspend fun clear() = dao.deleteAll()
}

private fun MessageEntity.toModel() = Message(
    id = id,
    projectId = projectId,
    to = recipient,
    body = body,
    status = MessageStatus.valueOf(status),
    timeline = MessageTimeline(receivedAt, queuedAt, sentAt, deliveredAt, failedAt),
    batchId = batchId,
    sim = sim,
    error = error,
    attempts = attempts,
    notBefore = notBefore,
    reportedStatus = reportedStatus?.let(MessageStatus::valueOf),
    webhook = webhookStatus?.let { WebhookOutcome(MessageStatus.valueOf(it), webhookHttp, webhookMs ?: 0, webhookError) },
    type = MessageType.valueOf(type),
    priority = priority,
)

private fun Message.toEntity() = MessageEntity(
    id = id,
    projectId = projectId,
    batchId = batchId,
    recipient = to,
    body = body,
    status = status.name,
    receivedAt = timeline.receivedAt,
    queuedAt = timeline.queuedAt,
    sentAt = timeline.sentAt,
    deliveredAt = timeline.deliveredAt,
    failedAt = timeline.failedAt,
    sim = sim,
    error = error,
    attempts = attempts,
    notBefore = notBefore,
    reportedStatus = reportedStatus?.name,
    webhookStatus = webhook?.status?.name,
    webhookHttp = webhook?.httpStatus,
    webhookMs = webhook?.durationMs,
    webhookError = webhook?.error,
    type = type.name,
    priority = priority,
)

private fun BatchEntity.toModel() =
    Batch(id, projectId, name, BatchSource.valueOf(source), total, rate, BatchStatus.valueOf(status), createdAt)

private fun Batch.toEntity() = BatchEntity(id, projectId, name, source.name, total, rate, status.name, createdAt)

private fun ProjectEntity.toModel() =
    Project(id, name, slug, endpoint, webhookUrl, enabled, createdAt, sim, MessageType.valueOf(defaultType), receive?.let(::decodeRules))

private fun Project.toEntity() =
    ProjectEntity(id, name, slug, endpoint, webhookUrl, enabled, createdAt, sim, defaultType.name, receive?.let(::encodeRules))

private fun encodeRules(r: ReceiveRules) = RelayJson.encodeToString(ReceiveRulesDto.serializer(), ReceiveRulesDto(r.replies, r.all, r.senders))

/** Unreadable rules read as unknown: the phone then keeps its old behaviour rather than dropping SMS. */
private fun decodeRules(json: String) = runCatching { RelayJson.decodeFromString(ReceiveRulesDto.serializer(), json) }
    .getOrNull()?.let { ReceiveRules(it.replies, it.all, it.senders) }
