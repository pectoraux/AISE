package com.aise.field.contracts.adapter

import com.aise.field.contracts.dto.SyncErrorContractDto

data class SyncRetryDecision(
    val retryable: Boolean,
    val retryAfterMs: Long?
)

object SyncRetryPolicy {
    fun evaluateRetry(syncError: SyncErrorContractDto): SyncRetryDecision {
        // Retry decision is driven strictly by data (retryable & retryAfterMs), not string parsing.
        return SyncRetryDecision(
            retryable = syncError.retryable,
            retryAfterMs = syncError.retryAfterMs
        )
    }
}
