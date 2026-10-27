package com.ironquest.app.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.domain.backup.BackupData
import com.ironquest.app.domain.backup.BackupError
import com.ironquest.app.domain.backup.BackupException
import com.ironquest.app.domain.backup.BackupFormat
import com.ironquest.app.domain.backup.BackupInspection
import com.ironquest.app.domain.backup.BackupMigrations
import com.ironquest.app.domain.backup.BackupRow
import com.ironquest.app.domain.backup.BackupTable
import com.ironquest.app.domain.backup.RestoreResult
import com.ironquest.app.domain.nutrition.ReminderRearmer
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.BackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dumps and restores tables generically through their column names (`SELECT *`, `PRAGMA
 * table_info`), so adding a column or table needs no change here: new columns are written when
 * present in the file and fall back to their default when not. Restore runs in one transaction
 * with foreign keys deferred and checked at the end, so a bad file changes nothing.
 */
@Singleton
class OfflineBackupRepository @Inject constructor(
    private val db: IronQuestDatabase,
    private val clock: Clock,
    private val settings: AppSettingsRepository,
    private val rearmer: ReminderRearmer,
) : BackupRepository {

    override fun suggestedFileName(): String = "ironquest-backup-${LocalDate.now(clock)}.json" // not UI text: backup file name

    override suspend fun createBackup(): String = withContext(Dispatchers.IO) {
        val tables = db.runInTransaction<Map<String, List<BackupRow>>> {
            val sdb = db.openHelper.writableDatabase
            BackupTable.entries.associate { it.sqlName to dump(sdb, it.sqlName) }
        }
        BackupJson.encode(
            BackupData(
                version = BackupFormat.CURRENT_VERSION,
                exportedAtMillis = clock.millis(),
                appSchemaVersion = db.openHelper.readableDatabase.version,
                tables = tables,
                builtInProgramHidden = settings.currentBuiltInProgramHidden(),
                food = settings.currentFood(),
            ),
        )
    }

    override suspend fun inspect(text: String): BackupInspection = try {
        val data = BackupMigrations.prepare(BackupJson.decode(text))
        BackupInspection.Valid(BackupMigrations.summarize(data))
    } catch (backupError: BackupException) {
        BackupInspection.Invalid(backupError.error)
    }

    override suspend fun restore(text: String): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val data = BackupMigrations.prepare(BackupJson.decode(text))
            db.runInTransaction { apply(db.openHelper.writableDatabase, data) }
            data.builtInProgramHidden?.let(settings::setBuiltInProgramHidden)
            data.food?.let { food ->
                settings.setFoodTrackingMode(food.mode)
                settings.setOwnTargets(food.ownTargets)
            }
            runCatching { rearmer.requestRearm() }
            RestoreResult.Restored(BackupMigrations.summarize(data))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (backupError: BackupException) {
            RestoreResult.Failed(backupError.error)
        } catch (sqlError: SQLiteException) {
            RestoreResult.Failed(BackupError.DoesNotFit(sqlError.message?.take(120)))
        } catch (error: Exception) {
            RestoreResult.Failed(BackupError.Unreadable(error.javaClass.simpleName))
        }
    }

    /** Runs inside the caller's transaction; any exception rolls everything back. */
    private fun apply(sdb: SupportSQLiteDatabase, data: BackupData) {
        sdb.execSQL("PRAGMA defer_foreign_keys = ON")
        // Children first.
        BackupTable.entries.reversed().filter { it.replaced }.forEach { sdb.execSQL("DELETE FROM ${it.sqlName}") }
        BackupTable.entries.forEach { table ->
            val columns = columnsOf(sdb, table.sqlName)
            val conflict = if (table.replaced) SQLiteDatabase.CONFLICT_ABORT else SQLiteDatabase.CONFLICT_IGNORE
            data.rows(table).forEach { row ->
                val values = ContentValues()
                row.forEach { (column, value) ->
                    if (column in columns) {
                        when (value) {
                            null -> values.putNull(column)
                            is Long -> values.put(column, value)
                            is Double -> values.put(column, value)
                            else -> values.put(column, value.toString())
                        }
                    }
                }
                if (sdb.insert(table.sqlName, conflict, values) == -1L && table.replaced) {
                    throw BackupException(BackupError.RowNotStored(table.sqlName))
                }
            }
        }
        // A file without a profile row must not leave the app without one (SeedCallback only runs on open).
        sdb.execSQL(
            "INSERT OR IGNORE INTO player_profile (id, totalXp, constitutionXp, strengthXp, staminaXp, questsCompleted, highestLevelReached) VALUES (1, 0, 0, 0, 0, 0, 1)",
        )
        sdb.query("PRAGMA foreign_key_check").use { cursor ->
            if (cursor.moveToFirst()) throw BackupException(BackupError.Inconsistent(cursor.getString(0)))
        }
    }

    private fun dump(sdb: SupportSQLiteDatabase, table: String): List<BackupRow> = sdb.query("SELECT * FROM $table").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    buildMap {
                        for (columnIndex in 0 until cursor.columnCount) {
                            put(
                                cursor.getColumnName(columnIndex),
                                when (cursor.getType(columnIndex)) {
                                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(columnIndex)
                                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(columnIndex)
                                    Cursor.FIELD_TYPE_STRING -> cursor.getString(columnIndex)
                                    else -> null
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun columnsOf(sdb: SupportSQLiteDatabase, table: String): Set<String> = sdb.query("PRAGMA table_info($table)").use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
    }
}
