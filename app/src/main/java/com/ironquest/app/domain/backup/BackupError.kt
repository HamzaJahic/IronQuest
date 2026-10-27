package com.ironquest.app.domain.backup

/**
 * What is wrong with a backup file. The UI words it (`ui/you/BackupErrorText`); the values carried
 * (table names, an exception class name, a truncated system message) are data, not copy.
 */
sealed interface BackupError {
    /** Not JSON, or JSON that isn't an IronQuest backup. */
    data object NotABackup : BackupError

    /** The version number is below 1. */
    data object NotValid : BackupError

    /** The file has no version number. */
    data object NoVersion : BackupError

    /** The file has no `tables` object. */
    data object NoData : BackupError

    /** [where] is "table meals", "table meals, row 3" or "column x". */
    data class Damaged(val where: String) : BackupError

    data object MadeByNewerVersion : BackupError
    data class TooOld(val version: Int) : BackupError
    data class Incomplete(val missingTables: List<String>) : BackupError

    /** A foreign key in the file points at something that isn't in it. */
    data class Inconsistent(val detail: String) : BackupError

    /** A row of [table] was rejected by the database. */
    data class RowNotStored(val table: String) : BackupError

    /** The database refused the data; [detail] is a truncated system message, if any. */
    data class DoesNotFit(val detail: String?) : BackupError

    /** Anything unexpected; [reason] is the exception class name. */
    data class Unreadable(val reason: String) : BackupError
}

/** A problem with a backup file. Nothing was changed when this is raised. */
class BackupException(val error: BackupError) : Exception(error.toString())
