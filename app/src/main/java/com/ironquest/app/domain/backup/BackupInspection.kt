package com.ironquest.app.domain.backup

sealed interface BackupInspection {
    data class Valid(val summary: BackupSummary) : BackupInspection
    data class Invalid(val error: BackupError) : BackupInspection
}
