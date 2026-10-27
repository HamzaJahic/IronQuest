package com.ironquest.app.domain.program

import com.ironquest.app.domain.model.Program

/** "Do next": the day the user picked to come next, and when (so completions after it can end it). */
data class NextWorkoutChoice(val templateId: String, val chosenAtMillis: Long)

/**
 * Today's main day. [templateId] is null only without a program and without an unfinished workout;
 * [chosen] means the user's choice decided it, not the rotation; [rotationTemplateId] is what the
 * rotation alone would pick.
 */
data class NextPick(val templateId: String?, val chosen: Boolean, val rotationTemplateId: String?)

/**
 * Which day comes next. Expiry of a choice is derived here rather than written on completion, so Today
 * and the goal notification agree without any hook in the workout repository.
 */
object NextWorkout {
    fun pick(
        program: Program?,
        lastCompletedTemplateId: String?,
        lastCompletedAtMillis: Long?,
        inProgressTemplateId: String?,
        choice: NextWorkoutChoice?,
    ): NextPick {
        val rotation = program?.takeIf { it.templates.isNotEmpty() }?.nextInRotation(lastCompletedTemplateId)?.id
        if (inProgressTemplateId != null) return NextPick(inProgressTemplateId, chosen = false, rotationTemplateId = rotation)
        val chosen = choice?.takeIf { isActive(it, program, lastCompletedAtMillis) && it.templateId != rotation }
        return NextPick(chosen?.templateId ?: rotation, chosen = chosen != null, rotationTemplateId = rotation)
    }

    /**
     * A choice lasts until a workout is completed after it was set, and only while its day is in
     * [program]. Template ids belong to one program, so another active program ignores it too.
     */
    fun isActive(choice: NextWorkoutChoice, program: Program?, lastCompletedAtMillis: Long?): Boolean =
        program?.templates?.any { it.id == choice.templateId } == true &&
            (lastCompletedAtMillis == null || lastCompletedAtMillis <= choice.chosenAtMillis)
}
