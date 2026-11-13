package app.relay.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, BatchEntity::class, ProjectEntity::class, IncomingEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun batches(): BatchDao
    abstract fun projects(): ProjectDao
    abstract fun incoming(): IncomingDao

    companion object {
        fun create(context: Context): RelayDatabase =
            Room.databaseBuilder(context.applicationContext, RelayDatabase::class.java, "relay.db")
                .addMigrations(PROJECT_SIM, PAIRED_GATEWAY, INBOX)
                .build()

        /** v2: projects can be pinned to a SIM. */
        private val PROJECT_SIM = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN sim INTEGER")
            }
        }

        /**
         * v3: messages carry their type and priority; projects are a read-only cache of the
         * web dashboard (no API keys on the phone), so that table is rebuilt and refetched.
         */
        private val PAIRED_GATEWAY = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN type TEXT NOT NULL DEFAULT 'TRANSACTIONAL'")
                db.execSQL("ALTER TABLE messages ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
                db.execSQL("DROP TABLE projects")
                db.execSQL(
                    "CREATE TABLE projects (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, slug TEXT NOT NULL, " +
                        "endpoint TEXT NOT NULL, webhookUrl TEXT, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                        "sim INTEGER, defaultType TEXT NOT NULL)",
                )
            }
        }

        /** v4: received SMS waiting to reach the server's inbox. */
        private val INBOX = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE incoming (ref TEXT NOT NULL PRIMARY KEY, sender TEXT NOT NULL, body TEXT NOT NULL, " +
                        "receivedAt INTEGER NOT NULL, sim INTEGER, reported INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX index_incoming_reported ON incoming (reported)")
            }
        }
    }
}
