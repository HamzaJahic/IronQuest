package com.ironquest.app.ui.you

import com.ironquest.app.R
import com.ironquest.app.domain.backup.BackupError
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText

/**
 * The words for a [BackupError]. [nothingChanged] adds the "Nothing was changed." sentence that a
 * failed load shows; checking a file before loading it shows the plain problem.
 */
fun BackupError.toText(nothingChanged: Boolean): UiText {
    fun pick(error: Int, failed: Int, vararg args: Any) = uiText(if (nothingChanged) failed else error, *args)
    return when (this) {
        BackupError.NotABackup -> pick(R.string.you_backup_error_not_a_backup, R.string.you_backup_failed_not_a_backup)
        BackupError.NotValid -> pick(R.string.you_backup_error_not_valid, R.string.you_backup_failed_not_valid)
        BackupError.NoVersion -> pick(R.string.you_backup_error_no_version, R.string.you_backup_failed_no_version)
        BackupError.NoData -> pick(R.string.you_backup_error_no_data, R.string.you_backup_failed_no_data)
        is BackupError.Damaged -> pick(R.string.you_backup_error_damaged, R.string.you_backup_failed_damaged, where)
        BackupError.MadeByNewerVersion -> pick(R.string.you_backup_error_newer_version, R.string.you_backup_failed_newer_version)
        is BackupError.TooOld -> pick(R.string.you_backup_error_too_old, R.string.you_backup_failed_too_old, version)
        is BackupError.Incomplete -> pick(
            R.string.you_backup_error_incomplete,
            R.string.you_backup_failed_incomplete,
            UiText.Joined(missingTables.map(UiText::Verbatim), R.string.common_list_separator),
        )
        is BackupError.Inconsistent -> pick(R.string.you_backup_error_inconsistent, R.string.you_backup_failed_inconsistent, detail)
        is BackupError.RowNotStored -> pick(R.string.you_backup_error_row_not_stored, R.string.you_backup_failed_row_not_stored, table)
        is BackupError.DoesNotFit ->
            if (detail == null) {
                pick(R.string.you_backup_error_does_not_fit_plain, R.string.you_backup_failed_does_not_fit_plain)
            } else {
                pick(R.string.you_backup_error_does_not_fit, R.string.you_backup_failed_does_not_fit, detail)
            }
        is BackupError.Unreadable -> pick(R.string.you_backup_error_unreadable, R.string.you_backup_failed_unreadable, reason)
    }
}
