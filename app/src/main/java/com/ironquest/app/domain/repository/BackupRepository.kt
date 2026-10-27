package com.ironquest.app.domain.repository

import com.ironquest.app.domain.backup.BackupInspection
import com.ironquest.app.domain.backup.RestoreResult

/**
 * Whole-app backups as one JSON text: everything the user logged and set up (workouts, sets,
 * level and XP, meals, plans, supplements, programs). Appearance and goal settings are not included.
 */
interface BackupRepository {
    /** "ironquest-backup-2026-10-12.json" */
    fun suggestedFileName(): String

    suspend fun createBackup(): String

    /** Reads [text] without touching any data: validates it and counts what it holds. Never throws. */
    suspend fun inspect(text: String): BackupInspection

    /**
     * Replaces the app's data with the backup in one transaction: on any problem nothing changes.
     * Never throws (except cancellation).
     */
    suspend fun restore(text: String): RestoreResult
}
