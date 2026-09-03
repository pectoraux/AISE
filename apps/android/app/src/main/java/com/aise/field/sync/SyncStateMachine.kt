package com.aise.field.sync

object SyncStateMachine {

    fun canTransition(current: SyncState, target: SyncState): Boolean {
        if (current == target) return true
        if (current.isTerminal) return false

        return when (current) {
            SyncState.PENDING -> target == SyncState.UPLOADING
            SyncState.UPLOADING -> target in listOf(
                SyncState.ACCEPTED,
                SyncState.DUPLICATE,
                SyncState.FAILED_RETRYABLE,
                SyncState.FAILED_FATAL
            )
            SyncState.FAILED_RETRYABLE -> target == SyncState.PENDING
            SyncState.ACCEPTED, SyncState.DUPLICATE, SyncState.FAILED_FATAL -> false
        }
    }

    fun transition(current: SyncState, target: SyncState): SyncState {
        if (!canTransition(current, target)) {
            throw IllegalStateException("Invalid sync state transition from $current to $target")
        }
        return target
    }
}
