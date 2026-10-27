package com.ironquest.app.domain.importing

import com.ironquest.app.domain.model.DayImport
import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.ExerciseImport
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.program.ProgramCatalog

/**
 * Workout CSV: one row per exercise.
 * `day,exercise,sets,rep_min,rep_max,type,stat,increment_kg,superset` — `type`, `stat`,
 * `increment_kg` and `superset` are optional; `day` is required except when importing exercises
 * into a day. Consecutive rows of a day with the same `superset` label form one superset (≥ 2).
 */
object WorkoutCsv {
    const val MAX_DAYS = 14
    const val MAX_EXERCISES_PER_DAY = 30
    const val MAX_NAME = 60
    const val MAX_SETS = 20
    const val MAX_REPS = 100
    const val MAX_MINUTES = 300
    const val MAX_INCREMENT_KG = 50.0
    const val MAX_SUPERSET_LABEL = 20

    /** Prefix of exercise ids created by imports; never clashes with ProgramCatalog ids. */
    const val CUSTOM_EXERCISE_PREFIX = "x_"

    internal const val HEADER = "day,exercise,sets,rep_min,rep_max,type,stat,increment_kg,superset" // not UI text: CSV format
    private const val EXERCISE_HEADER = "exercise,sets,rep_min,rep_max,type,stat,increment_kg,superset" // not UI text: CSV format

    val EXAMPLE_PROGRAM = """
        $HEADER
        Push,Barbell Bench Press,4,5,8,reps,STR,2.5,
        Push,Overhead Press,3,8,12,reps,STR,2.5,
        Pull,Barbell Row,4,6,10,reps,STR,2.5,
        Pull,Lat Pulldown,3,10,12,reps,STR,2.5,A
        Pull,Face Pull,3,12,15,reps,STR,2.5,A
        Legs,Back Squat,4,5,8,reps,CON,5,
        Legs,Leg Curl,3,10,15,reps,CON,2.5,
        Legs,Cycling,1,15,20,minutes,STA,0,
    """.trimIndent()

    val EXAMPLE_DAY = """
        $HEADER
        Push,Barbell Bench Press,4,5,8,reps,STR,2.5,
        Push,Overhead Press,3,8,12,reps,STR,2.5,
        Push,Triceps Pushdown,3,10,15,reps,STR,2.5,
    """.trimIndent()

    val EXAMPLE_EXERCISE = """
        $EXERCISE_HEADER
        Incline Dumbbell Press,3,8,12,reps,STR,2.5,
    """.trimIndent()

    val EXAMPLE_EXERCISES = """
        $EXERCISE_HEADER
        Incline Dumbbell Press,3,8,12,reps,STR,2.5,A
        Seated Cable Row,3,8,12,reps,STR,2.5,A
        Rowing Machine,1,10,15,minutes,STA,0,
    """.trimIndent()

    private val ALIASES = mapOf(
        "workout" to "day", "day_name" to "day", "session" to "day",
        "name" to "exercise", "exercise_name" to "exercise",
        "target_sets" to "sets",
        "min_reps" to "rep_min", "reps_min" to "rep_min", "rep_range_min" to "rep_min",
        "max_reps" to "rep_max", "reps_max" to "rep_max", "rep_range_max" to "rep_max",
        "tracking" to "type", "tracking_type" to "type",
        "increment" to "increment_kg", "increment_kg_" to "increment_kg",
        "group" to "superset", "superset_group" to "superset",
    )

    /**
     * [requireDay]: program/day imports need the `day` column; exercise imports ignore it and
     * return a single unnamed day. [expectedDays] / [expectedExercises]: exact count for the
     * "one …" kinds, or null.
     */
    fun parse(
        text: String,
        requireDay: Boolean,
        expectedDays: Int? = null,
        expectedExercises: Int? = null,
    ): ImportParse<List<DayImport>> {
        val table = when (val read = Csv.read(text, ALIASES)) {
            is ImportParse.Failed -> return read
            is ImportParse.Ok -> read.value
        }
        val issues = IssueCollector()
        val required = listOfNotNull("day".takeIf { requireDay }, "exercise", "sets")
        if (!issues.requireColumns(table, required)) return ImportParse.Failed(issues.issues)
        if ("rep_min" !in table.columns && "reps" !in table.columns) {
            return ImportParse.Failed(listOf(ImportIssue(line = null, ImportProblem.MissingRepColumns)))
        }

        // Insertion order = order of first appearance in the file.
        val days = linkedMapOf<String, MutableList<Pair<Int, ExerciseImport>>>()
        val dayNames = mutableMapOf<String, String>()
        for (row in table.rows) {
            val dayName = if (requireDay) row["day"] else ""
            if (requireDay && dayName.isEmpty()) {
                issues.add(row.line, ImportProblem.DayEmpty)
                continue
            }
            if (dayName.length > MAX_NAME) {
                issues.add(row.line, ImportProblem.DayNameTooLong(dayName, MAX_NAME))
                continue
            }
            val exercise = parseExercise(row, issues) ?: continue
            val key = dayName.lowercase()
            dayNames.putIfAbsent(key, dayName)
            val list = days.getOrPut(key) { mutableListOf() }
            val clash = list.firstOrNull { it.second.definition.id == exercise.definition.id }
            if (clash != null) {
                issues.add(row.line, ImportProblem.DuplicateExercise(row["exercise"], dayName.takeIf { requireDay }, clash.first))
                continue
            }
            list += row.line to exercise
        }
        if (!issues.isEmpty) return ImportParse.Failed(issues.issues)
        days.values.forEach { list -> checkSupersets(list, table.rows.associate { it.line to it["superset"] }, issues) }
        if (!issues.isEmpty) return ImportParse.Failed(issues.issues)

        if (days.size > MAX_DAYS) issues.add(line = null, ImportProblem.TooManyDays(days.size, MAX_DAYS))
        if (expectedDays != null && days.size != expectedDays) {
            issues.add(line = null, ImportProblem.WrongDayCount(expectedDays, days.size, dayNames.values.toList()))
        }
        days.forEach { (key, list) ->
            if (list.size > MAX_EXERCISES_PER_DAY) {
                issues.add(line = null, ImportProblem.DayTooLong(dayNames.getValue(key), list.size, MAX_EXERCISES_PER_DAY))
            }
        }
        val exerciseCount = days.values.sumOf { it.size }
        if (expectedExercises != null && exerciseCount != expectedExercises) {
            issues.add(line = null, ImportProblem.WrongExerciseCount(expectedExercises, exerciseCount))
        }
        if (!issues.isEmpty) return ImportParse.Failed(issues.issues)

        return ImportParse.Ok(days.map { (key, list) -> DayImport(dayNames.getValue(key), list.map { it.second }) })
    }

    /**
     * Rows of one day ([list], in file order) with the same superset label must be consecutive
     * and number at least 2. [labels] maps line → the label as written (for messages).
     */
    private fun checkSupersets(list: List<Pair<Int, ExerciseImport>>, labels: Map<Int, String>, issues: IssueCollector) {
        val lastLine = mutableMapOf<String, Int>()
        val members = linkedMapOf<String, MutableList<Int>>()
        var previous: String? = null
        for ((line, exercise) in list) {
            val key = exercise.supersetGroup
            if (key != null) {
                val last = lastLine[key]
                if (last != null && previous != key) {
                    issues.add(line, ImportProblem.SupersetNotConsecutive(labels[line].orEmpty(), line, last))
                }
                lastLine[key] = line
                members.getOrPut(key) { mutableListOf() } += line
            }
            previous = key
        }
        members.values.filter { it.size == 1 }.forEach { lines ->
            val line = lines.single()
            issues.add(line, ImportProblem.SupersetTooSmall(labels[line].orEmpty()))
        }
    }

    private fun parseExercise(row: CsvRow, issues: IssueCollector): ExerciseImport? {
        val start = issues.issues.size
        val name = row["exercise"]
        if (name.isEmpty()) issues.add(row.line, ImportProblem.ExerciseNameEmpty)
        if (name.length > MAX_NAME) issues.add(row.line, ImportProblem.ExerciseNameTooLong(MAX_NAME))

        val typeText = row["type"].lowercase()
        val tracking = when (typeText) {
            "", "reps", "rep", "weight", "weight_reps", "strength", "sets" -> TrackingType.WEIGHT_REPS
            "minutes", "minute", "min", "mins", "duration", "time", "cardio" -> TrackingType.DURATION
            else -> null.also { issues.add(row.line, ImportProblem.BadType(row["type"])) }
        }
        val statText = row["stat"].lowercase()
        val stat = when (statText) {
            "" -> if (tracking == TrackingType.DURATION) StatType.STAMINA else StatType.STRENGTH
            "str", "strength", "upper", "upper body" -> StatType.STRENGTH
            "con", "constitution", "legs", "lower", "lower body" -> StatType.CONSTITUTION
            "sta", "stamina", "cardio", "conditioning" -> StatType.STAMINA
            else -> null.also { issues.add(row.line, ImportProblem.BadStat(row["stat"])) }
        }

        val sets = Cells.int(row["sets"])
        if (sets == null || sets !in 1..MAX_SETS) issues.add(row.line, ImportProblem.BadSets(row["sets"], MAX_SETS))

        val (minText, maxText) = repTexts(row)
        val repMin = Cells.int(minText)
        val repMax = Cells.int(maxText)
        val limit = if (tracking == TrackingType.DURATION) MAX_MINUTES else MAX_REPS
        when {
            repMin == null || repMax == null ->
                issues.add(row.line, ImportProblem.BadRepNumbers(minText, maxText))
            repMin < 1 || repMax > limit || repMin > repMax ->
                issues.add(row.line, ImportProblem.BadRepRange(repMin, repMax, limit))
        }

        val defaultIncrement = if (tracking == TrackingType.DURATION) 0.0 else 2.5
        val increment = Cells.number(row["increment_kg"], blank = defaultIncrement)
        if (increment == null || increment !in 0.0..MAX_INCREMENT_KG) {
            issues.add(row.line, ImportProblem.BadIncrement(row["increment_kg"], MAX_INCREMENT_KG.toInt()))
        }

        val superset = row["superset"]
        if (superset.length > MAX_SUPERSET_LABEL) issues.add(row.line, ImportProblem.SupersetLabelTooLong(MAX_SUPERSET_LABEL))

        if (issues.issues.size != start || tracking == null || stat == null || sets == null || repMin == null || repMax == null || increment == null) {
            return null
        }
        val definition = resolve(name, stat, tracking, increment)
        return ExerciseImport(definition, sets, RepRange(repMin, repMax), supersetGroup = superset.lowercase().ifEmpty { null })
    }

    /** Built-in exercises are matched by name or id so their history and ghosts carry over. */
    fun resolve(name: String, stat: StatType, tracking: TrackingType, incrementKg: Double): ExerciseDefinition = ProgramCatalog.Exercises.all.firstOrNull { it.name.equals(name, ignoreCase = true) || it.id.equals(name, ignoreCase = true) }
        ?: ExerciseDefinition(
            id = CUSTOM_EXERCISE_PREFIX + slug(name),
            name = name,
            stat = stat,
            trackingType = tracking,
            incrementKg = if (tracking == TrackingType.DURATION) 0.0 else incrementKg,
        )

    /** Accepts rep_min/rep_max, or a single `reps` column like "8-12" or "10". */
    private fun repTexts(row: CsvRow): Pair<String, String> {
        if (row.cells.containsKey("rep_min") || row.cells.containsKey("rep_max")) return row["rep_min"] to row["rep_max"]
        val reps = row["reps"]
        val parts = reps.split('-', '–', '—').map { it.trim() }
        return if (parts.size == 2) parts[0] to parts[1] else reps to reps
    }

    /**
     * Readable and stable: the same name (any case) always maps to the same id, so re-importing
     * an exercise keeps its history. The hash keeps non-Latin names ("Čučanj", "Жим") apart.
     */
    private fun slug(name: String): String {
        val key = name.trim().lowercase()
        val readable = key.replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifEmpty { "exercise" }
        return readable + "_" + Integer.toHexString(key.hashCode())
    }
}
