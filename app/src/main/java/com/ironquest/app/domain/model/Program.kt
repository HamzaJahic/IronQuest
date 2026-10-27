package com.ironquest.app.domain.model

/**
 * A training program: the days that rotate on Today. `id == null` is the built-in
 * Upper/Lower split from ProgramCatalog; imported programs live in Room.
 */
data class Program(
    val id: Long?,
    val name: String,
    val isActive: Boolean,
    val templates: List<WorkoutTemplate>,
) {
    val isBuiltIn: Boolean get() = id == null

    /** Next workout in the rotation after [lastCompletedTemplateId]; starts at the first day. */
    fun nextInRotation(lastCompletedTemplateId: String?): WorkoutTemplate {
        val lastIndex = templates.indexOfFirst { it.id == lastCompletedTemplateId }
        return if (lastIndex == -1) templates.first() else templates[(lastIndex + 1) % templates.size]
    }

    companion object {
        private const val CUSTOM_PREFIX = "C"

        /** Template id of an imported day. Built-in ids (UPPER_1…) never start with "C" + digits. */
        fun customTemplateId(dayId: Long) = "$CUSTOM_PREFIX$dayId" // not UI text

        /** Day id of an imported template id, or null for a built-in one. */
        fun customDayId(templateId: String): Long? = templateId.takeIf { it.startsWith(CUSTOM_PREFIX) }?.drop(CUSTOM_PREFIX.length)?.toLongOrNull()
    }
}

/** One exercise row of an import, already validated and resolved to a definition. */
data class ExerciseImport(
    val definition: ExerciseDefinition,
    val targetSets: Int,
    val repRange: RepRange,
    /** Normalised `superset` label of the CSV row, scoped to its day; null = standalone. */
    val supersetGroup: String? = null,
)

/** One workout day of an import. */
data class DayImport(val name: String, val exercises: List<ExerciseImport>)
