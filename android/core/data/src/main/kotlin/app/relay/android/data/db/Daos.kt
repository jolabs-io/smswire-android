package app.relay.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY receivedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<MessageEntity>>

    /** [compact] is [query] without whitespace, matched against the number. */
    @Query(
        """
        SELECT * FROM messages
        WHERE :query = ''
           OR REPLACE(recipient, ' ', '') LIKE '%' || :compact || '%'
           OR LOWER(body) LIKE '%' || :query || '%'
           OR LOWER(id) LIKE '%' || :query || '%'
        ORDER BY receivedAt DESC LIMIT :limit
        """,
    )
    fun search(query: String, compact: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    fun observe(id: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun find(id: String): MessageEntity?

    /** [digits] is the tail of a number; spaces, dashes and brackets in the stored number don't count. */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM messages
            WHERE receivedAt >= :since
              AND REPLACE(REPLACE(REPLACE(REPLACE(recipient, ' ', ''), '-', ''), '(', ''), ')', '') LIKE '%' || :digits
        )
        """,
    )
    suspend fun messagedSince(digits: String, since: Long): Boolean

    /** The projects that messaged a number ending in [digits] since [since], written as in [messagedSince]. */
    @Query(
        """
        SELECT DISTINCT projectId FROM messages
        WHERE receivedAt >= :since
          AND REPLACE(REPLACE(REPLACE(REPLACE(recipient, ' ', ''), '-', ''), '(', ''), ')', '') LIKE '%' || :digits
        """,
    )
    suspend fun textedBy(digits: String, since: Long): List<String>

    /**
     * The SIM of the newest message to a number ending in [digits] that went out since [since],
     * written as in [messagedSince]. Walks the receivedAt index from the newest row and stops at the
     * first match; `+status` keeps SQLite off the status index, which would sort the whole window.
     */
    @Query(
        """
        SELECT sim FROM messages
        WHERE receivedAt >= :since
          AND sim IS NOT NULL
          AND +status IN ('SENDING', 'SENT', 'DELIVERED')
          AND REPLACE(REPLACE(REPLACE(REPLACE(recipient, ' ', ''), '-', ''), '(', ''), ')', '') LIKE '%' || :digits
        ORDER BY receivedAt DESC LIMIT 1
        """,
    )
    suspend fun lastSimFor(digits: String, since: Long): Int?

    /** Returns -1 when the id already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MessageEntity): Long

    @Update
    suspend fun update(entity: MessageEntity)

    @Query(
        """
        SELECT * FROM messages
        WHERE status = 'QUEUED' AND notBefore <= :now
          AND (batchId IS NULL OR batchId NOT IN (:excluded))
          AND id NOT IN (:holding)
        ORDER BY priority ASC, receivedAt ASC LIMIT 1
        """,
    )
    suspend fun nextSendable(now: Long, excluded: List<String>, holding: List<String>): MessageEntity?

    /** For one SIM's send loop: what it names, plus whatever names no SIM at all. */
    @Query(
        """
        SELECT * FROM messages
        WHERE status = 'QUEUED' AND notBefore <= :now
          AND (batchId IS NULL OR batchId NOT IN (:excluded))
          AND id NOT IN (:holding)
          AND (sim IS NULL OR sim = :sim)
        ORDER BY priority ASC, receivedAt ASC LIMIT 1
        """,
    )
    suspend fun nextSendableForSim(now: Long, excluded: List<String>, holding: List<String>, sim: Int): MessageEntity?

    @Query("SELECT * FROM messages WHERE reportedStatus IS NULL OR reportedStatus != status")
    suspend fun unreported(): List<MessageEntity>

    @Query("SELECT status, COUNT(*) AS count FROM messages WHERE receivedAt >= :since GROUP BY status")
    fun statusCounts(since: Long): Flow<List<StatusCountRow>>

    @Query("SELECT sentAt FROM messages WHERE sentAt IS NOT NULL AND sentAt >= :since")
    fun sentTimes(since: Long): Flow<List<Long>>

    @Query("SELECT batchId, status, COUNT(*) AS count FROM messages WHERE batchId IS NOT NULL GROUP BY batchId, status")
    fun batchCounts(): Flow<List<BatchStatusCountRow>>

    @Query(
        """
        SELECT projectId,
               SUM(CASE WHEN receivedAt >= :since THEN 1 ELSE 0 END) AS today,
               SUM(CASE WHEN receivedAt >= :since AND status = 'DELIVERED' THEN 1 ELSE 0 END) AS deliveredToday,
               SUM(CASE WHEN receivedAt >= :since AND status = 'FAILED' THEN 1 ELSE 0 END) AS failedToday,
               MAX(receivedAt) AS lastReceivedAt
        FROM messages GROUP BY projectId
        """,
    )
    fun projectActivity(since: Long): Flow<List<ProjectActivityRow>>

    @Query("DELETE FROM messages WHERE status IN ('QUEUED', 'SENDING')")
    suspend fun deletePending()

    /** Nothing left for the server: kept history mustn't reach the next workspace. */
    @Query("UPDATE messages SET reportedStatus = status")
    suspend fun markAllReported()

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches")
    fun all(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun find(id: String): BatchEntity?

    @Upsert
    suspend fun upsert(entity: BatchEntity)

    @Query("UPDATE batches SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("SELECT id FROM batches WHERE status = 'PAUSED'")
    suspend fun pausedIds(): List<String>

    @Query("DELETE FROM batches WHERE status != 'DONE'")
    suspend fun deleteUnfinished()

    @Query("DELETE FROM batches")
    suspend fun deleteAll()
}

@Dao
abstract class ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt")
    abstract fun all(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY createdAt")
    abstract suspend fun list(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    abstract fun observe(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    abstract suspend fun find(id: String): ProjectEntity?

    @Upsert
    abstract suspend fun upsert(entity: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("DELETE FROM projects")
    protected abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insertAll(entities: List<ProjectEntity>)

    /** Swaps in the server's list. */
    @Transaction
    open suspend fun replaceAll(incoming: List<ProjectEntity>) {
        clear()
        insertAll(incoming)
    }
}

@Dao
interface IncomingDao {
    /** Returns -1 when the ref already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: IncomingEntity): Long

    @Query("SELECT * FROM incoming WHERE reported = 0 ORDER BY receivedAt")
    suspend fun unreported(): List<IncomingEntity>

    @Query("UPDATE incoming SET reported = 1 WHERE ref = :ref")
    suspend fun markReported(ref: String)

    @Query("DELETE FROM incoming")
    suspend fun deleteAll()
}
