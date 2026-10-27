package com.ironquest.app.domain.backup

object BackupFormat {
    const val NAME = "ironquest-backup"

    /**
     * Version of the backup *file format*, not of the Room schema. Bump it whenever the meaning
     * of the data in a file changes (a table or column added, renamed, or reinterpreted), and add
     * the step that upgrades the previous version to [BackupMigrations].
     *
     * v2: user_foods, meal_log_items, food settings.
     */
    const val CURRENT_VERSION = 2
}
