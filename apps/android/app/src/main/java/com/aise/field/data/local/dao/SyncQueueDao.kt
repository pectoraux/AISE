package com.aise.field.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aise.field.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE packageId = :packageId")
    suspend fun getQueueItemByPackageId(packageId: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE syncState = :state ORDER BY createdAt ASC")
    fun getQueueItemsByState(state: String): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE syncState IN ('PENDING', 'FAILED_RETRYABLE') ORDER BY createdAt ASC")
    suspend fun getPendingOrRetryableItems(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueItem(entity: SyncQueueEntity): Long

    @Update
    suspend fun updateQueueItem(entity: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE packageId = :packageId")
    suspend fun deleteQueueItem(packageId: String)
}
