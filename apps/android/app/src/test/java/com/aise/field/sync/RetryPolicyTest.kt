package com.aise.field.sync

import com.aise.field.contracts.dto.ContractSyncErrorCode
import com.aise.field.contracts.dto.SyncErrorContractDto
import com.aise.field.sync.policy.SyncRetryPolicy
import org.junit.Assert.*
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun evaluateRetry_whenRetryableTrue_returnsShouldRetryTrueWithDelay() {
        val error = SyncErrorContractDto(
            contractVersion = "1.0",
            code = ContractSyncErrorCode.RATE_LIMITED,
            message = "Rate limit exceeded",
            retryable = true,
            retryAfterMs = 60000L
        )

        val decision = SyncRetryPolicy.evaluateRetry(error, attemptCount = 0)

        assertTrue(decision.shouldRetry)
        assertEquals(60000L, decision.retryAfterMs)
    }

    @Test
    fun evaluateRetry_whenRetryableFalse_returnsShouldRetryFalse() {
        val fatalError = SyncErrorContractDto(
            contractVersion = "1.0",
            code = ContractSyncErrorCode.VALIDATION_FAILED,
            message = "Invalid schema payload",
            retryable = false,
            retryAfterMs = null
        )

        val decision = SyncRetryPolicy.evaluateRetry(fatalError, attemptCount = 0)

        assertFalse(decision.shouldRetry)
        assertEquals(0L, decision.retryAfterMs)
    }

    @Test
    fun evaluateRetry_exponentialBackoff_calculatesIncreasingDelays() {
        val retryableErrorWithoutExplicitDelay = SyncErrorContractDto(
            contractVersion = "1.0",
            code = ContractSyncErrorCode.SERVER_ERROR,
            message = "Internal Server Error",
            retryable = true,
            retryAfterMs = null
        )

        val decision0 = SyncRetryPolicy.evaluateRetry(retryableErrorWithoutExplicitDelay, attemptCount = 0)
        val decision1 = SyncRetryPolicy.evaluateRetry(retryableErrorWithoutExplicitDelay, attemptCount = 1)
        val decision2 = SyncRetryPolicy.evaluateRetry(retryableErrorWithoutExplicitDelay, attemptCount = 2)

        assertTrue(decision0.shouldRetry)
        assertTrue(decision1.shouldRetry)
        assertTrue(decision2.shouldRetry)

        assertEquals(5000L, decision0.retryAfterMs)   // 5s * 2^0 = 5s
        assertEquals(10000L, decision1.retryAfterMs)  // 5s * 2^1 = 10s
        assertEquals(20000L, decision2.retryAfterMs)  // 5s * 2^2 = 20s
    }
}
