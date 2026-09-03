package com.aise.field.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val packageId: String,
    val sessionId: String,
    val syncState: String,
    val retryCount: Int = 0,
    val retryAfterMs: Long? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
