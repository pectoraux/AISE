package com.aise.field.sync.policy

import com.aise.field.contracts.dto.SyncErrorContractDto
import kotlin.math.min

data class SyncRetryDecision(
    val shouldRetry: Boolean,
    val retryAfterMs: Long
)

object SyncRetryPolicy {

    private const val DEFAULT_BASE_DELAY_MS = 5000L
    private const val MAX_DELAY_MS = 300000L // 5 minutes

    fun evaluateRetry(syncError: SyncErrorContractDto, attemptCount: Int): SyncRetryDecision {
        if (!syncError.retryable) {
            return SyncRetryDecision(shouldRetry = false, retryAfterMs = 0L)
        }

        val delayMs = syncError.retryAfterMs ?: run {
            val expFactor = 1 shl min(attemptCount, 6)
            min(MAX_DELAY_MS, DEFAULT_BASE_DELAY_MS * expFactor)
        }

        return SyncRetryDecision(shouldRetry = true, retryAfterMs = delayMs)
    }
}
