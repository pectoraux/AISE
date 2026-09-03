package com.aise.field.sync

import org.junit.Assert.*
import org.junit.Test

class IdempotencyTest {

    @Test
    fun syncStateMachine_allowedTransitions_succeed() {
        assertEquals(SyncState.UPLOADING, SyncStateMachine.transition(SyncState.PENDING, SyncState.UPLOADING))
        assertEquals(SyncState.ACCEPTED, SyncStateMachine.transition(SyncState.UPLOADING, SyncState.ACCEPTED))

        assertEquals(SyncState.DUPLICATE, SyncStateMachine.transition(SyncState.UPLOADING, SyncState.DUPLICATE))
        assertEquals(SyncState.FAILED_RETRYABLE, SyncStateMachine.transition(SyncState.UPLOADING, SyncState.FAILED_RETRYABLE))
        assertEquals(SyncState.PENDING, SyncStateMachine.transition(SyncState.FAILED_RETRYABLE, SyncState.PENDING))
        assertEquals(SyncState.FAILED_FATAL, SyncStateMachine.transition(SyncState.UPLOADING, SyncState.FAILED_FATAL))
    }

    @Test(expected = IllegalStateException::class)
    fun terminalStateMutationTest_fromAccepted_throwsIllegalStateException() {
        SyncStateMachine.transition(SyncState.ACCEPTED, SyncState.UPLOADING)
    }

    @Test(expected = IllegalStateException::class)
    fun terminalStateMutationTest_fromDuplicate_throwsIllegalStateException() {
        SyncStateMachine.transition(SyncState.DUPLICATE, SyncState.PENDING)
    }

    @Test(expected = IllegalStateException::class)
    fun terminalStateMutationTest_fromFatal_throwsIllegalStateException() {
        SyncStateMachine.transition(SyncState.FAILED_FATAL, SyncState.UPLOADING)
    }
}
