package com.ironquest.app.domain.nutrition

/** "[id] was logged/taken on [epochDay]". */
data class DayKey(val epochDay: Long, val id: Long)

data class ReminderSupplement(val id: Long, val name: String, val dose: String, val reminderTimeMin: Int?)

/** Everything [ReminderSchedule] needs, read in one go by `NutritionRepository.reminderSnapshot`. */
data class ReminderSnapshot(
    /** Slots of the active plan; empty when no plan is active. */
    val slots: List<PlanSlot> = emptyList(),
    /** Non-archived supplements. */
    val supplements: List<ReminderSupplement> = emptyList(),
    /** (day, slotId) logged within the snapshot's date range. */
    val loggedSlots: Set<DayKey> = emptySet(),
    /** (day, supplementId) taken within the snapshot's date range. */
    val takenSupplements: Set<DayKey> = emptySet(),
)
