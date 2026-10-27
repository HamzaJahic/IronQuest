package com.ironquest.app.data.backup

import com.ironquest.app.domain.backup.BackupData
import com.ironquest.app.domain.backup.BackupError
import com.ironquest.app.domain.backup.BackupException
import com.ironquest.app.domain.backup.BackupFormat
import com.ironquest.app.domain.backup.BackupRow
import com.ironquest.app.domain.nutrition.FoodSettings
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroTargets
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON form of a backup: `{ format, version, appSchemaVersion, exportedAt, settings, tables }`.
 * Rows are objects keyed by column name, so a newer app can read an older file (missing columns
 * take the table default) and an older one ignores columns it doesn't know. Uses `opt()` and
 * `as? Number`, because Android's and json.org's number types differ.
 */
object BackupJson {

    fun encode(data: BackupData): String {
        val tables = JSONObject()
        data.tables.forEach { (name, rows) ->
            tables.put(name, JSONArray().also { array -> rows.forEach { array.put(rowToJson(it)) } })
        }
        return JSONObject()
            .put("format", BackupFormat.NAME)
            .put("version", data.version)
            .put("appSchemaVersion", data.appSchemaVersion) // not UI text: backup JSON keys and technical damage locations
            .put("exportedAt", data.exportedAtMillis) // not UI text: backup JSON keys and technical damage locations
            .put("settings", settingsToJson(data))
            .put("tables", tables)
            .toString(1)
    }

    /** Throws [BackupException] with a typed [BackupError]; never anything else. */
    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text.trim().removePrefix("\uFEFF")) // not UI text: byte order mark
        } catch (jsonError: JSONException) {
            throw BackupException(BackupError.NotABackup)
        }
        if (root.opt("format") != BackupFormat.NAME) throw BackupException(BackupError.NotABackup)
        val version = (root.opt("version") as? Number)?.toInt() ?: throw BackupException(BackupError.NoVersion)
        val tablesJson = root.optJSONObject("tables") ?: throw BackupException(BackupError.NoData)
        val tables = mutableMapOf<String, List<BackupRow>>()
        for (name in tablesJson.keys()) {
            val array = tablesJson.optJSONArray(name) ?: throw BackupException(BackupError.Damaged("table $name")) // not UI text: backup JSON keys and technical damage locations
            tables[name] = (0 until array.length()).map { rowIndex ->
                rowFromJson(array.optJSONObject(rowIndex) ?: throw BackupException(BackupError.Damaged("table $name, row ${rowIndex + 1}"))) // not UI text: backup JSON keys and technical damage locations
            }
        }
        val settings = root.optJSONObject("settings")
        return BackupData(
            version = version,
            exportedAtMillis = (root.opt("exportedAt") as? Number)?.toLong() ?: 0L, // not UI text: backup JSON keys and technical damage locations
            appSchemaVersion = (root.opt("appSchemaVersion") as? Number)?.toInt() ?: 0, // not UI text: backup JSON keys and technical damage locations
            tables = tables,
            // not UI text: backup JSON keys
            builtInProgramHidden = settings?.opt("builtInProgramHidden") as? Boolean,
            food = settings?.let(::foodFromJson),
        )
    }

    private fun settingsToJson(data: BackupData): JSONObject {
        val json = JSONObject()
        // not UI text: backup JSON keys
        data.builtInProgramHidden?.let { json.put("builtInProgramHidden", it) }
        data.food?.let { food ->
            // not UI text: backup JSON keys
            json.put("foodTrackingMode", food.mode.name)
            val targets = food.ownTargets
            val own = JSONObject()
            // not UI text: backup JSON keys
            targets.proteinG?.let { own.put("proteinG", it) }
            // not UI text: backup JSON keys
            targets.carbsG?.let { own.put("carbsG", it) }
            // not UI text: backup JSON keys
            targets.fatG?.let { own.put("fatG", it) }
            // not UI text: backup JSON keys
            targets.fiberG?.let { own.put("fiberG", it) }
            targets.kcal?.let { own.put("kcal", it) }
            // not UI text: backup JSON keys
            targets.waterMl?.let { own.put("waterMl", it) }
            // not UI text: backup JSON keys
            json.put("ownTargets", own)
        }
        return json
    }

    /** Null when the file has neither food key; an unknown mode name falls back to the default. */
    private fun foodFromJson(settings: JSONObject): FoodSettings? {
        // not UI text: backup JSON keys
        val modeValue = settings.opt("foodTrackingMode")
        // not UI text: backup JSON keys
        val own = settings.optJSONObject("ownTargets")
        if (modeValue == null && own == null) return null
        val mode = FoodTrackingMode.entries.firstOrNull { it.name == (modeValue as? String) } ?: FoodTrackingMode.DEFAULT
        val targets = if (own == null) {
            MacroTargets()
        } else {
            MacroTargets(
                // not UI text: backup JSON keys
                proteinG = (own.opt("proteinG") as? Number)?.toDouble(),
                // not UI text: backup JSON keys
                carbsG = (own.opt("carbsG") as? Number)?.toDouble(),
                // not UI text: backup JSON keys
                fatG = (own.opt("fatG") as? Number)?.toDouble(),
                // not UI text: backup JSON keys
                fiberG = (own.opt("fiberG") as? Number)?.toDouble(),
                kcal = (own.opt("kcal") as? Number)?.toDouble(),
                // not UI text: backup JSON keys
                waterMl = (own.opt("waterMl") as? Number)?.toInt(),
            )
        }
        return FoodSettings(mode, targets)
    }

    private fun rowToJson(row: BackupRow): JSONObject {
        val json = JSONObject()
        row.forEach { (column, value) -> json.put(column, value ?: JSONObject.NULL) }
        return json
    }

    private fun rowFromJson(json: JSONObject): BackupRow {
        val row = linkedMapOf<String, Any?>()
        for (column in json.keys()) {
            row[column] = when (val value = json.opt(column)) {
                null, JSONObject.NULL -> null
                is Boolean -> if (value) 1L else 0L
                is Int, is Long -> (value as Number).toLong()
                is Number -> value.toDouble()
                is String -> value
                else -> throw BackupException(BackupError.Damaged("column $column")) // not UI text: backup JSON keys and technical damage locations
            }
        }
        return row
    }
}
