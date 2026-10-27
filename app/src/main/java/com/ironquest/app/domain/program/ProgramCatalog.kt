package com.ironquest.app.domain.program

import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.StatType.CONSTITUTION
import com.ironquest.app.domain.model.StatType.STAMINA
import com.ironquest.app.domain.model.StatType.STRENGTH
import com.ironquest.app.domain.model.TemplateExercise
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.model.WorkoutTemplate

/**
 * The static 4-day Upper/Lower program.
 *
 * Exercise definitions are mirrored into the `exercises` Room table on every DB open
 * (see IronQuestDatabase) so logged sets have a real foreign key, while the program
 * structure itself stays in code where it is easy to version and test.
 */
object ProgramCatalog {

    object Exercises {
        val BENCH_PRESS = ExerciseDefinition("bench_press", "Barbell Bench Press", STRENGTH)
        val BARBELL_ROW = ExerciseDefinition("barbell_row", "Barbell Row", STRENGTH)
        val OVERHEAD_PRESS = ExerciseDefinition("overhead_press", "Overhead Press", STRENGTH)
        val LAT_PULLDOWN = ExerciseDefinition("lat_pulldown", "Lat Pulldown", STRENGTH)
        val BARBELL_CURL = ExerciseDefinition("barbell_curl", "Barbell Curl", STRENGTH)
        val INCLINE_BENCH = ExerciseDefinition("incline_bench", "Incline Bench Press", STRENGTH)
        val CABLE_ROW = ExerciseDefinition("cable_row", "Seated Cable Row", STRENGTH)
        val TRICEPS_PUSHDOWN = ExerciseDefinition("triceps_pushdown", "Triceps Pushdown", STRENGTH)

        val BACK_SQUAT = ExerciseDefinition("back_squat", "Back Squat", CONSTITUTION, incrementKg = 5.0)
        val ROMANIAN_DEADLIFT = ExerciseDefinition("romanian_deadlift", "Romanian Deadlift", CONSTITUTION, incrementKg = 5.0)
        val LEG_PRESS = ExerciseDefinition("leg_press", "Leg Press", CONSTITUTION, incrementKg = 5.0)
        val LEG_CURL = ExerciseDefinition("leg_curl", "Lying Leg Curl", CONSTITUTION)
        val CALF_RAISE = ExerciseDefinition("calf_raise", "Standing Calf Raise", CONSTITUTION)
        val DEADLIFT = ExerciseDefinition("deadlift", "Deadlift", CONSTITUTION, incrementKg = 5.0)
        val FRONT_SQUAT = ExerciseDefinition("front_squat", "Front Squat", CONSTITUTION)
        val SPLIT_SQUAT = ExerciseDefinition("bulgarian_split_squat", "Bulgarian Split Squat", CONSTITUTION)
        val LEG_EXTENSION = ExerciseDefinition("leg_extension", "Leg Extension", CONSTITUTION)

        val CYCLING = ExerciseDefinition(
            id = "cycling",
            name = "Cycling Finisher", // not UI text: stored English name (DB mirror, CSV, import matching); shown via ui/catalog
            stat = STAMINA,
            trackingType = TrackingType.DURATION,
            incrementKg = 0.0,
        )

        val all: List<ExerciseDefinition> = listOf(
            BENCH_PRESS, BARBELL_ROW, OVERHEAD_PRESS, LAT_PULLDOWN, BARBELL_CURL, INCLINE_BENCH,
            CABLE_ROW, TRICEPS_PUSHDOWN, BACK_SQUAT, ROMANIAN_DEADLIFT, LEG_PRESS, LEG_CURL,
            CALF_RAISE, DEADLIFT, FRONT_SQUAT, SPLIT_SQUAT, LEG_EXTENSION, CYCLING,
        )
    }

    private fun ex(def: ExerciseDefinition, sets: Int, min: Int, max: Int) = TemplateExercise(def, sets, RepRange(min, max))

    val UPPER_1 = WorkoutTemplate(
        id = "UPPER_1", // not UI text: template id
        name = "Upper 1", // not UI text: stored English name (DB mirror, CSV, import matching); shown via ui/catalog
        exercises = listOf(
            ex(Exercises.BENCH_PRESS, 4, 5, 8),
            ex(Exercises.BARBELL_ROW, 4, 6, 10),
            ex(Exercises.OVERHEAD_PRESS, 3, 8, 12),
            ex(Exercises.LAT_PULLDOWN, 3, 10, 12),
            ex(Exercises.BARBELL_CURL, 2, 10, 15),
        ),
    )

    val LOWER_1 = WorkoutTemplate(
        id = "LOWER_1", // not UI text: template id
        name = "Lower 1", // not UI text: stored English name (DB mirror, CSV, import matching); shown via ui/catalog
        exercises = listOf(
            ex(Exercises.BACK_SQUAT, 4, 5, 8),
            ex(Exercises.ROMANIAN_DEADLIFT, 3, 6, 10),
            ex(Exercises.LEG_PRESS, 3, 10, 12),
            ex(Exercises.LEG_CURL, 3, 10, 15),
            ex(Exercises.CALF_RAISE, 3, 10, 15),
            ex(Exercises.CYCLING, 1, 10, 15),
        ),
    )

    val UPPER_2 = WorkoutTemplate(
        id = "UPPER_2", // not UI text: template id
        name = "Upper 2", // not UI text: stored English name (DB mirror, CSV, import matching); shown via ui/catalog
        exercises = listOf(
            ex(Exercises.OVERHEAD_PRESS, 4, 5, 8),
            ex(Exercises.INCLINE_BENCH, 3, 8, 12),
            ex(Exercises.CABLE_ROW, 4, 8, 12),
            ex(Exercises.LAT_PULLDOWN, 3, 8, 12),
            ex(Exercises.TRICEPS_PUSHDOWN, 3, 10, 15),
        ),
    )

    val LOWER_2 = WorkoutTemplate(
        id = "LOWER_2", // not UI text: template id
        name = "Lower 2", // not UI text: stored English name (DB mirror, CSV, import matching); shown via ui/catalog
        exercises = listOf(
            ex(Exercises.DEADLIFT, 3, 4, 6),
            ex(Exercises.FRONT_SQUAT, 3, 6, 10),
            ex(Exercises.SPLIT_SQUAT, 3, 8, 12),
            ex(Exercises.LEG_EXTENSION, 3, 10, 15),
            ex(Exercises.CYCLING, 1, 15, 25),
        ),
    )

    /** Rotation order of the split. */
    val templates: List<WorkoutTemplate> = listOf(UPPER_1, LOWER_1, UPPER_2, LOWER_2)

    fun template(id: String): WorkoutTemplate = templates.firstOrNull { it.id == id } ?: error("Unknown workout template '$id'")

    fun exercise(id: String): ExerciseDefinition? = Exercises.all.firstOrNull { it.id == id }

    /** Next workout in the rotation after [lastCompletedTemplateId]; starts at Upper 1. */
    fun nextInRotation(lastCompletedTemplateId: String?): WorkoutTemplate {
        val lastIndex = templates.indexOfFirst { it.id == lastCompletedTemplateId }
        return if (lastIndex == -1) templates.first() else templates[(lastIndex + 1) % templates.size]
    }
}
