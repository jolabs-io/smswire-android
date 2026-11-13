package app.relay.android.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index("status"), Index("receivedAt"), Index("sentAt"), Index("batchId"), Index("projectId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val batchId: String?,
    val recipient: String,
    val body: String,
    val status: String,
    val receivedAt: Long,
    val queuedAt: Long,
    val sentAt: Long?,
    val deliveredAt: Long?,
    val failedAt: Long?,
    val sim: Int?,
    val error: String?,
    val attempts: Int,
    val notBefore: Long,
    val reportedStatus: String?,
    val webhookStatus: String?,
    val webhookHttp: Int?,
    val webhookMs: Long?,
    val webhookError: String?,
    val type: String = "TRANSACTIONAL",
    val priority: Int = 1,
)

@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val source: String,
    val total: Int,
    val rate: Int,
    val status: String,
    val createdAt: Long,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val slug: String,
    val endpoint: String,
    val webhookUrl: String?,
    val enabled: Boolean,
    val createdAt: Long,
    val sim: Int?,
    val defaultType: String,
)

/** A received SMS on its way to the server's inbox. */
@Entity(tableName = "incoming", indices = [Index("reported")])
data class IncomingEntity(
    @PrimaryKey val ref: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val sim: Int?,
    val reported: Boolean,
)

data class StatusCountRow(val status: String, val count: Int)

data class BatchStatusCountRow(val batchId: String, val status: String, val count: Int)

data class ProjectActivityRow(
    val projectId: String,
    val today: Int,
    val deliveredToday: Int,
    val failedToday: Int,
    val lastReceivedAt: Long?,
)
