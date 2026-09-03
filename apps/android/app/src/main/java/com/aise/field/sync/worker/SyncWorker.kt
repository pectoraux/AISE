package com.aise.field.sync.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aise.field.AiseApplication
import com.aise.field.capture.manifest.CapturePackageManifestBuilder
import com.aise.field.contracts.dto.ContractUploadOutcome
import com.aise.field.sync.SyncState
import com.aise.field.sync.SyncStateMachine
import com.aise.field.sync.client.CaptureUploadClient
import com.aise.field.sync.client.UploadResponse
import com.aise.field.sync.policy.SyncRetryPolicy
import kotlinx.coroutines.flow.first
import java.io.File

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val app = context.applicationContext as AiseApplication
    private val syncQueueDao = app.database.syncQueueDao()
    private val captureStore = app.captureStore

    // Base URL configurable for testing / production server endpoint
    var uploadClient: CaptureUploadClient = CaptureUploadClient("http://10.0.2.2:3000")

    override suspend fun doWork(): Result {
        val pendingItems = syncQueueDao.getPendingOrRetryableItems()
        if (pendingItems.isEmpty()) {
            return Result.success()
        }

        var hasRetryableFailure = false

        for (item in pendingItems) {
            // Guard: Ignore item if terminal state
            if (SyncState.valueOf(item.syncState).isTerminal) {
                continue
            }

            // 1. Transition state -> UPLOADING
            val uploadingEntity = item.copy(
                syncState = SyncStateMachine.transition(SyncState.valueOf(item.syncState), SyncState.UPLOADING).name,
                updatedAt = System.currentTimeMillis()
            )
            syncQueueDao.updateQueueItem(uploadingEntity)

            try {
                val session = captureStore.getSessionById(item.sessionId).first()
                    ?: throw IllegalStateException("Capture session ${item.sessionId} not found for queue item ${item.packageId}")
                val assets = captureStore.getAssetsForSession(item.sessionId).first()

                val manifest = CapturePackageManifestBuilder.buildManifest(session, assets)

                var allAssetsAccepted = true
                var isDuplicateOutcome = false

                for (assetDto in manifest.assets) {
                    val matchingDomainAsset = assets.find { it.id == assetDto.assetId }
                        ?: throw IllegalStateException("Asset ${assetDto.assetId} not found in local store")

                    val localFile = File(matchingDomainAsset.filePath)

                    when (val response = uploadClient.uploadAsset(manifest, assetDto, localFile)) {
                        is UploadResponse.Success -> {
                            val resultDto = response.result
                            if (resultDto.outcome == ContractUploadOutcome.DUPLICATE) {
                                isDuplicateOutcome = true
                            }
                        }
                        is UploadResponse.Failure -> {
                            allAssetsAccepted = false
                            val syncError = response.error
                            val retryDecision = SyncRetryPolicy.evaluateRetry(syncError, item.retryCount)

                            if (retryDecision.shouldRetry) {
                                hasRetryableFailure = true
                                val failedRetryableEntity = uploadingEntity.copy(
                                    syncState = SyncStateMachine.transition(SyncState.UPLOADING, SyncState.FAILED_RETRYABLE).name,
                                    retryCount = item.retryCount + 1,
                                    retryAfterMs = retryDecision.retryAfterMs,
                                    lastErrorCode = syncError.code.name,
                                    lastErrorMessage = syncError.message,
                                    updatedAt = System.currentTimeMillis()
                                )
                                syncQueueDao.updateQueueItem(failedRetryableEntity)
                            } else {
                                val failedFatalEntity = uploadingEntity.copy(
                                    syncState = SyncStateMachine.transition(SyncState.UPLOADING, SyncState.FAILED_FATAL).name,
                                    lastErrorCode = syncError.code.name,
                                    lastErrorMessage = syncError.message,
                                    updatedAt = System.currentTimeMillis()
                                )
                                syncQueueDao.updateQueueItem(failedFatalEntity)
                            }
                            break // Stop processing remaining assets for this package
                        }
                    }
                }

                if (allAssetsAccepted) {
                    val targetTerminalState = if (isDuplicateOutcome) SyncState.DUPLICATE else SyncState.ACCEPTED
                    val completedEntity = uploadingEntity.copy(
                        syncState = SyncStateMachine.transition(SyncState.UPLOADING, targetTerminalState).name,
                        lastErrorCode = null,
                        lastErrorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    syncQueueDao.updateQueueItem(completedEntity)
                }

            } catch (e: Exception) {
                val errorEntity = uploadingEntity.copy(
                    syncState = SyncStateMachine.transition(SyncState.UPLOADING, SyncState.FAILED_FATAL).name,
                    lastErrorCode = "LOCAL_EXCEPTION",
                    lastErrorMessage = e.message,
                    updatedAt = System.currentTimeMillis()
                )
                syncQueueDao.updateQueueItem(errorEntity)
            }
        }

        return if (hasRetryableFailure) Result.retry() else Result.success()
    }
}
