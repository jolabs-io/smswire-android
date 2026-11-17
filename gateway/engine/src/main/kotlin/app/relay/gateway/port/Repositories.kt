package app.relay.gateway.port

import app.relay.gateway.model.Batch
import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.IncomingSms
import app.relay.gateway.model.Message
import app.relay.gateway.model.Project
import app.relay.gateway.model.ProjectActivity
import app.relay.gateway.model.StatusCounts
import app.relay.protocol.BatchStatus
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeRecent(limit: Int): Flow<List<Message>>

    /** Newest first; [query] matches number, body or id. Blank query returns everything. */
    fun observeSearch(query: String, limit: Int): Flow<List<Message>>

    fun observe(id: String): Flow<Message?>

    suspend fun find(id: String): Message?

    /** Stores a newly dispatched message. Returns false if the id is already known (duplicate dispatch). */
    suspend fun insertIfAbsent(message: Message): Boolean

    suspend fun update(message: Message)

    /** The most urgent (lowest priority), then oldest, queued message whose backoff has elapsed and whose batch isn't paused. */
    suspend fun nextSendable(now: Long, excludedBatchIds: Set<String>): Message?

    suspend fun findUnreported(): List<Message>

    /** Whether a message to a number ending in [digits] arrived since [since], however the number was written. */
    suspend fun messagedSince(digits: String, since: Long): Boolean

    /** The projects that messaged a number ending in [digits] since [since]. */
    suspend fun textedBy(digits: String, since: Long): Set<String>

    fun observeStatusCounts(since: Long): Flow<StatusCounts>

    fun observeSentTimes(since: Long): Flow<List<Long>>

    fun observeBatchCounts(): Flow<Map<String, StatusCounts>>

    /** Counts since [since]; `lastReceivedAt` is all-time. */
    fun observeProjectActivity(since: Long): Flow<Map<String, ProjectActivity>>

    /**
     * The phone leaves its workspace: what still waits to send is dropped. [keepHistory] keeps the
     * rest, marked reported so none of it reaches the next workspace's server; otherwise all goes.
     */
    suspend fun clearForNewWorkspace(keepHistory: Boolean)
}

interface BatchRepository {
    fun observeAll(): Flow<List<Batch>>
    suspend fun find(id: String): Batch?
    suspend fun upsert(batch: Batch)
    suspend fun setStatus(id: String, status: BatchStatus)
    suspend fun pausedIds(): Set<String>

    /** The phone leaves its workspace: running and paused batches go; [keepFinished] keeps the done ones. */
    suspend fun clearForNewWorkspace(keepFinished: Boolean)
}

/** Local cache of the projects this phone sends for. The web dashboard is the source of truth. */
interface ProjectRepository {
    fun observeAll(): Flow<List<Project>>
    suspend fun all(): List<Project>
    fun observe(id: String): Flow<Project?>
    suspend fun find(id: String): Project?
    suspend fun upsert(project: Project)

    suspend fun replaceAll(projects: List<Project>)
    suspend fun delete(id: String)
}

interface SettingsRepository {
    val settings: Flow<GatewaySettings>
    suspend fun current(): GatewaySettings
    suspend fun update(transform: (GatewaySettings) -> GatewaySettings)
}

interface DeviceIdentityStore {
    /** The current pairing; null while the phone isn't paired. */
    val identity: Flow<DeviceIdentity?>
    suspend fun load(): DeviceIdentity?
    suspend fun save(identity: DeviceIdentity)
    suspend fun clear()
}

/** The workspace's join keywords (normalised), as its server last sent them. Kept across restarts. */
interface JoinKeywordStore {
    suspend fun keywords(): Set<String>
    suspend fun replace(keywords: Set<String>)
}

/** Received SMS waiting to reach the server's inbox. */
interface InboundRepository {
    /** False if this ref is already stored. */
    suspend fun insertIfAbsent(sms: IncomingSms): Boolean
    suspend fun unreported(): List<IncomingSms>
    suspend fun markReported(ref: String)

    /** The phone leaves its workspace: nothing it received may reach the next one's inbox. */
    suspend fun clear()
}
