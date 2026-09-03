package com.aise.field.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aise.field.data.local.dao.CaptureAssetDao
import com.aise.field.data.local.dao.CaptureSessionDao
import com.aise.field.data.local.dao.ProjectDao
import com.aise.field.data.local.dao.SyncQueueDao
import com.aise.field.data.local.entity.CaptureAssetEntity
import com.aise.field.data.local.entity.CaptureSessionEntity
import com.aise.field.data.local.entity.ProjectEntity
import com.aise.field.data.local.entity.SyncQueueEntity

@Database(
    entities = [
        ProjectEntity::class,
        CaptureSessionEntity::class,
        CaptureAssetEntity::class,
        SyncQueueEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AiseDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun captureSessionDao(): CaptureSessionDao
    abstract fun captureAssetDao(): CaptureAssetDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AiseDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE capture_assets ADD COLUMN orientX REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE capture_assets ADD COLUMN orientY REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE capture_assets ADD COLUMN orientZ REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE capture_assets ADD COLUMN orientW REAL DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        packageId TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        syncState TEXT NOT NULL,
                        retryCount INTEGER NOT NULL,
                        retryAfterMs INTEGER,
                        lastErrorCode TEXT,
                        lastErrorMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AiseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AiseDatabase::class.java,
                    "aise_field.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
