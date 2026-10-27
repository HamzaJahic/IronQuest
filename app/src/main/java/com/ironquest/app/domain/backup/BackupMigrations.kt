package com.ironquest.app.domain.backup

/**
 * Brings a file of any supported version up to [BackupFormat.CURRENT_VERSION]. This is the
 * failsafe for outdated files: an old backup is upgraded step by step instead of being rejected
 * or half-applied, and a file from a *newer* app is refused with a clear message.
 */
object BackupMigrations {

    /** `steps[i]` upgrades version `i + 1` to `i + 2`. */
    private val steps: List<(BackupData) -> BackupData> = listOf(
        // 1 -> 2: only new tables (filled empty in prepare) and optional settings; rows are unchanged.
        { data -> data },
    )

    fun prepare(data: BackupData): BackupData {
        if (data.version < 1) throw BackupException(BackupError.NotValid)
        if (data.version > BackupFormat.CURRENT_VERSION) {
            throw BackupException(BackupError.MadeByNewerVersion)
        }
        // Tables added after the file was written are empty in it, not missing.
        val filled = data.tables.toMutableMap()
        BackupTable.entries.filter { it.since > data.version }.forEach { filled.putIfAbsent(it.sqlName, emptyList()) }
        var current = data.copy(tables = filled)
        while (current.version < BackupFormat.CURRENT_VERSION) {
            val step = steps.getOrNull(current.version - 1)
                ?: throw BackupException(BackupError.TooOld(current.version))
            current = step(current).copy(version = current.version + 1)
        }
        val missing = BackupTable.entries.filter { it.since <= current.version && it.sqlName !in current.tables }
        if (missing.isNotEmpty()) {
            throw BackupException(BackupError.Incomplete(missing.map { it.sqlName }))
        }
        return current
    }

    fun summarize(data: BackupData) = BackupSummary(
        exportedAtMillis = data.exportedAtMillis,
        workouts = data.rows(BackupTable.WORKOUTS).size,
        meals = data.rows(BackupTable.MEALS).size,
        mealPlans = data.rows(BackupTable.MEAL_PLANS).size,
        programs = data.rows(BackupTable.PROGRAMS).size,
        mealLogs = data.rows(BackupTable.MEAL_LOGS).size,
    )
}
