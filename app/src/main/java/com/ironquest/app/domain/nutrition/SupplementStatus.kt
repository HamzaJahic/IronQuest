package com.ironquest.app.domain.nutrition

data class SupplementStatus(
    val id: Long,
    val name: String,
    val dose: String,
    val taken: Boolean,
    val streak: Int,
    /** Minutes after midnight, 0..1439; null = no reminder. */
    val reminderTimeMin: Int? = null,
)

/**
 * [reminderTimeMin] is written as given on update too — callers editing a supplement must
 * pass the current value to keep its reminder.
 */
data class SupplementDraft(val id: Long?, val name: String, val dose: String, val reminderTimeMin: Int? = null)
