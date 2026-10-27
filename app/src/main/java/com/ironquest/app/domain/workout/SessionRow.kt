package com.ironquest.app.domain.workout

import com.ironquest.app.domain.model.LoggedSet

/** One set row of a live workout: what's typed, what was done last time, and what's logged. */
data class SessionRow(
    val index: Int,
    val weightInput: String = "",
    /** Reps for strength work, minutes for cardio. */
    val amountInput: String = "",
    /** What was done at this set index last session. */
    val ghost: LoggedSet? = null,
    val persistedId: Long? = null,
    val persisted: LoggedSet? = null,
    /** XP the set paid when it was logged; edits never change it. */
    val xp: Int? = null,
    val isSaving: Boolean = false,
) {
    val isLogged: Boolean get() = persistedId != null
}

/** A row's state for rendering, derived so it can't disagree with the row. */
enum class RowStatus { EMPTY, READY, LOGGED, DIRTY }
