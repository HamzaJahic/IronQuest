package com.ironquest.app.domain.nutrition

object NextMeal {
    /**
     * The slot the Today card points at: the unchecked slot with the earliest time at or after
     * [nowMin] (minutes after midnight). If none is upcoming, the first unchecked slot in plan
     * order, whether overdue or untimed. Null when every slot is logged or there is no plan.
     */
    fun pick(plan: MealPlan?, logs: List<MealLogEntry>, nowMin: Int): PlanSlot? {
        val slots = plan?.slots ?: return null
        val logged = logs.mapNotNull { it.slotId }.toSet()
        val open = slots.filter { it.id !in logged }
        // minByOrNull keeps the first of equal times, i.e. plan order.
        return open.filter { (it.timeOfDayMin ?: -1) >= nowMin }.minByOrNull { checkNotNull(it.timeOfDayMin) { "filtered above: only timed slots reach here" } }
            ?: open.firstOrNull()
    }
}
