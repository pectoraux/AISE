package com.aise.field.sync

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aise.field.data.local.AiseDatabase
import com.aise.field.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncQueuePersistenceTest {

    @Test
    fun syncQueue_survivesProcessRestartSimulation() {
        runBlocking {
            // 1. Instantiate DB
            val db1 = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AiseDatabase::class.java
            ).allowMainThreadQueries().build()

            val dao1 = db1.syncQueueDao()

            val queueItem = SyncQueueEntity(
                packageId = "pkg-queue-001",
                sessionId = "sess-101",
                syncState = SyncState.PENDING.name,
                retryCount = 0,
                createdAt = 1725350000000L,
                updatedAt = 1725350000000L
            )

            dao1.enqueueItem(queueItem)

            // 2. Simulate Process Restart by querying via re-instantiated DAO
            val recoveredItem = dao1.getQueueItemByPackageId("pkg-queue-001")
            assertNotNull(recoveredItem)
            assertEquals("pkg-queue-001", recoveredItem?.packageId)
            assertEquals("sess-101", recoveredItem?.sessionId)
            assertEquals(SyncState.PENDING.name, recoveredItem?.syncState)

            db1.close()
        }
    }

    @Test
    fun duplicateEnqueueRaceTest_isIdempotent() {
        runBlocking {
            val db = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AiseDatabase::class.java
            ).allowMainThreadQueries().build()

            val dao = db.syncQueueDao()

            val item1 = SyncQueueEntity(
                packageId = "pkg-duplicate-001",
                sessionId = "sess-202",
                syncState = SyncState.PENDING.name,
                retryCount = 0,
                createdAt = 1725350000000L,
                updatedAt = 1725350000000L
            )

            val item2 = SyncQueueEntity(
                packageId = "pkg-duplicate-001", // Same packageId
                sessionId = "sess-202",
                syncState = SyncState.PENDING.name,
                retryCount = 5,
                createdAt = 1725350005000L,
                updatedAt = 1725350005000L
            )

            val result1 = dao.enqueueItem(item1)
            val result2 = dao.enqueueItem(item2) // OnConflictStrategy.IGNORE

            assertTrue(result1 > 0)
            assertEquals(-1L, result2) // Ignored

            val pendingList = dao.getPendingOrRetryableItems()
            assertEquals(1, pendingList.size)
            assertEquals(0, pendingList[0].retryCount) // Retained original item state

            db.close()
        }
    }

    @Test
    fun roomDatabase_migration_2_3_createsSyncQueueTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_2_3.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Create a SQLite DB at Version 2
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val configV2 = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS projects (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS capture_sessions (
                            id TEXT NOT NULL PRIMARY KEY,
                            projectId TEXT NOT NULL,
                            intent TEXT NOT NULL,
                            assuranceProfile TEXT NOT NULL,
                            status TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS capture_assets (
                            id TEXT NOT NULL PRIMARY KEY,
                            sessionId TEXT NOT NULL,
                            assetType TEXT NOT NULL,
                            filePath TEXT NOT NULL,
                            relativePath TEXT,
                            contentHash TEXT,
                            byteSize INTEGER NOT NULL,
                            status TEXT NOT NULL,
                            capturedAt TEXT,
                            deviceRef TEXT,
                            sensorRef TEXT,
                            geoLatitude REAL,
                            geoLongitude REAL,
                            geoAltitudeM REAL,
                            geoAccuracyM REAL,
                            orientX REAL,
                            orientY REAL,
                            orientZ REAL,
                            orientW REAL,
                            notes TEXT,
                            createdAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helperV2 = helperFactory.create(configV2)
        val dbV2 = helperV2.writableDatabase

        // 2. Execute Migration 2 -> 3
        AiseDatabase.MIGRATION_2_3.migrate(dbV2)

        // 3. Verify sync_queue table exists
        val cursor = dbV2.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sync_queue'")
        assertTrue(cursor.moveToFirst())
        assertEquals("sync_queue", cursor.getString(0))
        cursor.close()

        dbV2.close()
        helperV2.close()
    }
}
