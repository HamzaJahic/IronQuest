package com.ironquest.app.domain.backup

sealed interface RestoreResult {
    data class Restored(val summary: BackupSummary) : RestoreResult
    data class Failed(val error: BackupError) : RestoreResult
}
