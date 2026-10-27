package com.ironquest.app.domain.workout

import com.ironquest.app.domain.model.SupersetRounds

/** The superset's current step (member + set), following [SupersetRounds]; null when every round is done. */
fun List<SessionExercise>.supersetCurrent(): SupersetRounds.Step? = SupersetRounds.next(setCounts(), loggedCheck())

/** The step after [current] ("Log, then Cable row"), or null when [current] is the last. */
fun List<SessionExercise>.supersetAfter(current: SupersetRounds.Step): SupersetRounds.Step? = SupersetRounds.after(setCounts(), current, loggedCheck())

fun List<SessionExercise>.isBlockDone(): Boolean = all { exercise -> exercise.isDone }

/** Rows, not prescriptions, so added sets count. */
private fun List<SessionExercise>.setCounts(): List<Int> = map { exercise -> exercise.rows.size }

private fun List<SessionExercise>.loggedCheck(): (SupersetRounds.Step) -> Boolean = { step -> this[step.member].rows.getOrNull(step.setIndex)?.isLogged == true }
