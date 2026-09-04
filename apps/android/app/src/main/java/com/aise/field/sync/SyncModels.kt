package com.aise.field.sync

enum class SyncState {
    PENDING,
    UPLOADING,
    ACCEPTED,
    DUPLICATE,
    FAILED_RETRYABLE,
    FAILED_FATAL;

    val isTerminal: Boolean
        get() = this == ACCEPTED || this == DUPLICATE || this == FAILED_FATAL
}
