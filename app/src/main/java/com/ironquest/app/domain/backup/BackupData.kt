package com.ironquest.app.domain.backup

import com.ironquest.app.domain.nutrition.FoodSettings

/** A stored value: Long, Double, String or null (what SQLite holds). */
typealias BackupRow = Map<String, Any?>

data class BackupData(
    val version: Int,
    val exportedAtMillis: Long,
    /** Room schema version of the app that wrote the file (informational). */
    val appSchemaVersion: Int,
    val tables: Map<String, List<BackupRow>>,
    /** True when the built-in program was deleted; null when the file doesn't say. */
    val builtInProgramHidden: Boolean? = null,
    /** Food tracking mode and own targets; null when the file has neither (version 1). */
    val food: FoodSettings? = null,
) {
    fun rows(table: BackupTable): List<BackupRow> = tables[table.sqlName].orEmpty()
}
