package com.ironquest.app.fakes

import com.ironquest.app.domain.nutrition.DayKey
import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.DayRow
import com.ironquest.app.domain.nutrition.DayTotals
import com.ironquest.app.domain.nutrition.DeleteOutcome
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.MealUsage
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.nutrition.NutritionHistory
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.nutrition.PlanDraft
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.domain.nutrition.ReminderSnapshot
import com.ironquest.app.domain.nutrition.ReminderSupplement
import com.ironquest.app.domain.nutrition.SavePlanResult
import com.ironquest.app.domain.nutrition.StreakCalculator
import com.ironquest.app.domain.nutrition.SupplementDraft
import com.ironquest.app.domain.nutrition.SupplementRef
import com.ironquest.app.domain.nutrition.SupplementStatus
import com.ironquest.app.domain.repository.NutritionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * In-memory NutritionRepository for ViewModel tests. Mirrors the real XP rules closely
 * enough for UI tests (targets always come from the active plan); the exact persistence
 * semantics are covered by the instrumented NutritionRepositoryTest.
 */
class FakeNutritionRepository : NutritionRepository {

    val meals = MutableStateFlow<List<Meal>>(emptyList())
    val plans = MutableStateFlow<List<MealPlan>>(emptyList())
    val logs = MutableStateFlow<Map<LocalDate, List<MealLogEntry>>>(emptyMap())
    val water = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    val supplements = MutableStateFlow<List<SupplementStatus>>(emptyList())
    val intakes = MutableStateFlow<Set<Pair<Long, LocalDate>>>(emptySet())

    var totalXp = 0
    val savedMeals = mutableListOf<MealDraft>()
    val savedPlans = mutableListOf<PlanDraft>()
    val activeCalls = mutableListOf<Long?>()
    var logSlotCalls = 0

    /** When set, the next suspend write throws it once, then clears it. */
    var failNext: Throwable? = null
    private val archivedMeals = MutableStateFlow<List<Meal>>(emptyList())

    /** When set, logSlot suspends until it completes — lets tests hold a write "in flight". */
    var gate: CompletableDeferred<Unit>? = null

    private var nextId = 1_000L
    private fun failIfRequested() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    private val bonusByDay = mutableMapOf<LocalDate, Int>()

    // ----- meals -----

    override fun observeMeals(): Flow<List<Meal>> = meals
    override fun observeMeal(id: Long): Flow<Meal?> = combine(meals, archivedMeals) { active, archived -> (active + archived).firstOrNull { it.id == id } }
    override fun observeMealUsages(mealId: Long): Flow<List<MealUsage>> = plans.map { list ->
        list.flatMap { plan -> plan.slots.filter { it.mealId == mealId }.map { MealUsage(plan.name, it.label, it.timeOfDayMin) } }
    }

    override suspend fun saveMeal(draft: MealDraft): Long {
        failIfRequested()
        savedMeals += draft
        val id = draft.id ?: nextId++
        val meal = Meal(id, draft.name, draft.macros, draft.tags, draft.recipeText, draft.recipeUrl)
        meals.update { list -> list.filterNot { it.id == id } + meal }
        return id
    }

    override suspend fun deleteMeal(id: Long): DeleteOutcome {
        failIfRequested()
        val used = plans.value.any { plan -> plan.slots.any { it.mealId == id } }
        if (used) meals.value.firstOrNull { it.id == id }?.let { meal -> archivedMeals.update { it + meal.copy(archived = true) } }
        meals.update { list -> list.filterNot { it.id == id } }
        return if (used) DeleteOutcome.ARCHIVED else DeleteOutcome.DELETED
    }

    override fun observeArchivedMeals(): Flow<List<Meal>> = archivedMeals.map { list -> list.sortedBy { it.name.lowercase() } }

    override suspend fun restoreMeal(id: Long) {
        failIfRequested()
        val meal = archivedMeals.value.firstOrNull { it.id == id } ?: return
        archivedMeals.update { list -> list.filterNot { it.id == id } }
        meals.update { it + meal.copy(archived = false) }
    }

    // ----- plans -----

    override fun observePlans(): Flow<List<MealPlan>> = plans
    override fun observePlan(id: Long): Flow<MealPlan?> = plans.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun savePlan(draft: PlanDraft): SavePlanResult {
        failIfRequested()
        savedPlans += draft
        val id = draft.id ?: nextId++
        val byId = meals.value.associateBy { it.id }
        val plan = MealPlan(
            id = id,
            name = draft.name,
            isActive = plans.value.firstOrNull { it.id == id }?.isActive ?: false,
            targets = draft.targets,
            slots = draft.slots.map { slotDraft ->
                PlanSlot(slotDraft.id ?: nextId++, slotDraft.label, slotDraft.mealId, byId[slotDraft.mealId]?.name ?: "?", byId[slotDraft.mealId]?.macros ?: Macros.ZERO, slotDraft.timeOfDayMin, slotDraft.reminderOffsetMin)
            },
        )
        plans.update { list -> list.filterNot { it.id == id } + plan }
        // Like the real repository: only the active plan's targets re-evaluate today's bonuses.
        return SavePlanResult(id, if (plan.isActive) award(TODAY, 0) else NutritionAward.NONE)
    }

    override suspend fun setActivePlan(id: Long?): NutritionAward {
        failIfRequested()
        activeCalls += id
        plans.update { list -> list.map { it.copy(isActive = it.id == id) } }
        return award(TODAY, 0)
    }

    override suspend fun duplicatePlan(id: Long, copyName: (String) -> String): Long {
        failIfRequested()
        val source = plans.value.first { it.id == id }
        val newId = nextId++
        plans.update { it + source.copy(id = newId, name = copyName(source.name), isActive = false) }
        return newId
    }

    override suspend fun deletePlan(id: Long): NutritionAward {
        failIfRequested()
        val wasActive = plans.value.firstOrNull { it.id == id }?.isActive == true
        plans.update { list -> list.filterNot { it.id == id } }
        return if (wasActive) award(TODAY, 0) else NutritionAward.NONE
    }

    // ----- day -----

    override fun observeDay(date: LocalDate): Flow<DayNutrition> = combine(plans, logs, water, supplements, intakes) { ps, ls, w, ss, ins ->
        val active = ps.firstOrNull { it.isActive }
        DayNutrition(
            date = date,
            activePlan = active,
            targets = active?.targets,
            logs = ls[date].orEmpty(),
            waterMl = w[date] ?: 0,
            supplements = ss.map { supplement ->
                val days = ins.filter { it.first == supplement.id }.map { it.second.toEpochDay() }.toSet()
                supplement.copy(taken = date.toEpochDay() in days, streak = StreakCalculator.currentStreak(days, date.toEpochDay()))
            },
        )
    }

    override suspend fun logSlot(date: LocalDate, slotId: Long, portion: Double): NutritionAward {
        failIfRequested()
        require(!date.isAfter(TODAY)) { "future date" }
        logSlotCalls++
        gate?.await()
        if (logs.value[date].orEmpty().any { it.slotId == slotId }) return NutritionAward(0, null, alreadyDone = true)
        val slot = plans.value.flatMap { it.slots }.first { it.id == slotId }
        addLog(date, MealLogEntry(nextId++, slotId, slot.mealId, slot.mealName, portion, slot.mealMacros))
        return award(date, NutritionRules.MEAL_XP)
    }

    override suspend fun logOffPlan(date: LocalDate, mealId: Long, portion: Double): NutritionAward {
        failIfRequested()
        require(!date.isAfter(TODAY)) { "future date" }
        gate?.await()
        val meal = (meals.value + archivedMeals.value).first { it.id == mealId }
        addLog(date, MealLogEntry(nextId++, null, meal.id, meal.name, portion, meal.macros))
        return award(date, NutritionRules.MEAL_XP)
    }

    val savedQuickMeals = mutableListOf<Triple<Long?, String, List<MealLogItem>>>()

    override suspend fun saveQuickMeal(date: LocalDate, logId: Long?, name: String, items: List<MealLogItem>): NutritionAward {
        failIfRequested()
        require(!date.isAfter(TODAY)) { "future date" }
        require(name.isNotBlank()) { "Meal name is required" }
        require(items.isNotEmpty()) { "A meal needs at least one food" }
        gate?.await()
        savedQuickMeals += Triple(logId, name, items)
        val total = items.fold(Macros.ZERO) { sum, item -> sum + item.macros }
        if (logId == null) {
            addLog(date, MealLogEntry(nextId++, null, null, name.trim(), 1.0, total, items))
            return award(date, NutritionRules.MEAL_XP)
        }
        val day = dateOf(logId) ?: return NutritionAward(0, null, alreadyDone = true)
        logs.update { map -> map + (day to map.getValue(day).map { if (it.id == logId) it.copy(mealName = name.trim(), portion = 1.0, macros1x = total, items = items) else it }) }
        return award(day, 0)
    }

    override suspend fun getLog(logId: Long): MealLogEntry? = logs.value.values.flatten().firstOrNull { it.id == logId }

    override suspend fun updatePortion(logId: Long, portion: Double): NutritionAward {
        failIfRequested()
        val date = dateOf(logId) ?: return NutritionAward(0, null, alreadyDone = true)
        logs.update { map -> map + (date to map.getValue(date).map { if (it.id == logId) it.copy(portion = portion) else it }) }
        return award(date, 0)
    }

    override suspend fun removeLog(logId: Long): NutritionAward {
        failIfRequested()
        val date = dateOf(logId) ?: return NutritionAward(0, null, alreadyDone = true)
        logs.update { map -> map + (date to map.getValue(date).filterNot { it.id == logId }) }
        return award(date, -NutritionRules.MEAL_XP)
    }

    override suspend fun addWater(date: LocalDate, deltaMl: Int): NutritionAward {
        failIfRequested()
        require(!date.isAfter(TODAY)) { "future date" }
        water.update { map -> map + (date to NutritionRules.clampWater(map[date] ?: 0, deltaMl)) }
        return award(date, 0)
    }

    // ----- supplements -----

    override suspend fun setSupplementTaken(date: LocalDate, supplementId: Long, taken: Boolean): NutritionAward {
        failIfRequested()
        require(!date.isAfter(TODAY)) { "future date" }
        val key = supplementId to date
        if (taken == (key in intakes.value)) return NutritionAward(0, null, alreadyDone = true)
        intakes.update { if (taken) it + key else it - key }
        return award(date, if (taken) NutritionRules.SUPPLEMENT_XP else -NutritionRules.SUPPLEMENT_XP)
    }

    override suspend fun saveSupplement(draft: SupplementDraft): Long {
        failIfRequested()
        val id = draft.id ?: nextId++
        supplements.update { list -> list.filterNot { it.id == id } + SupplementStatus(id, draft.name, draft.dose, false, 0, draft.reminderTimeMin) }
        return id
    }

    override suspend fun deleteSupplement(id: Long): DeleteOutcome {
        failIfRequested()
        supplements.update { list -> list.filterNot { it.id == id } }
        return DeleteOutcome.DELETED
    }

    // ----- phase 2: history (Week / Month) -----

    /**
     * Target snapshots for observeHistory rows. A day that has logs or water but no entry here
     * gets the active plan's current targets. Days listed here get a row even without data.
     */
    val daySnapshots = MutableStateFlow<Map<LocalDate, MacroTargets>>(emptyMap())

    /** Days that had a successful write (logSlot/logOffPlan/removeLog/updatePortion/addWater/setSupplementTaken): they keep a row. */
    private val touchedDays = MutableStateFlow<Set<LocalDate>>(emptySet())

    override fun observeHistory(from: LocalDate, to: LocalDate): Flow<NutritionHistory> = combine(plans, logs, water, daySnapshots, combine(supplements, intakes, touchedDays, ::Triple)) { ps, ls, w, snaps, (ss, ins, touched) ->
        val active = ps.firstOrNull { it.isActive }
        fun inRange(day: LocalDate) = !day.isBefore(from) && !day.isAfter(to)
        val logged = ls.filter { (day, dayLogs) -> inRange(day) && dayLogs.isNotEmpty() }
        val rowDays = logged.keys + touched.filter(::inRange) + w.keys.filter(::inRange) + snaps.keys.filter(::inRange)
        NutritionHistory(
            from = from,
            to = to,
            totals = logged.mapValues { (_, dayLogs) -> DayTotals(dayLogs.fold(Macros.ZERO) { acc, entry -> acc + entry.eaten }, dayLogs.size) },
            rows = rowDays.associateWith { day -> DayRow(snaps[day] ?: active?.targets ?: MacroTargets(), w[day] ?: 0) },
            liveTargets = active?.targets,
            supplements = ss.map { SupplementRef(it.id, it.name) },
            intakeEpochDays = ins.filter { !it.second.isAfter(to) }
                .groupBy({ it.first }, { it.second.toEpochDay() })
                .mapValues { (_, days) -> days.toSet() },
        )
    }

    // ----- reminders (phase 3) -----

    override suspend fun reminderSnapshot(from: LocalDate, to: LocalDate): ReminderSnapshot {
        val range = from.toEpochDay()..to.toEpochDay()
        return ReminderSnapshot(
            slots = plans.value.firstOrNull { it.isActive }?.slots.orEmpty(),
            supplements = supplements.value.map { ReminderSupplement(it.id, it.name, it.dose, it.reminderTimeMin) },
            loggedSlots = logs.value.flatMap { (date, entries) ->
                entries.mapNotNull { entry -> entry.slotId?.let { DayKey(date.toEpochDay(), it) } }
            }.filter { it.epochDay in range }.toSet(),
            takenSupplements = intakes.value.map { (id, date) -> DayKey(date.toEpochDay(), id) }
                .filter { it.epochDay in range }.toSet(),
        )
    }

    // ----- helpers -----

    private fun addLog(date: LocalDate, entry: MealLogEntry) {
        logs.update { map -> map + (date to map[date].orEmpty() + entry) }
    }

    private fun dateOf(logId: Long): LocalDate? = logs.value.entries.firstOrNull { (_, l) -> l.any { it.id == logId } }?.key

    /** Item XP plus the change in the day's protein/water bonus, like the real repository. */
    private fun award(date: LocalDate, itemXp: Int): NutritionAward {
        // The real repository creates a nutrition_days row on every successful write and never deletes it.
        touchedDays.update { it + date }
        val targets = plans.value.firstOrNull { it.isActive }?.targets
        val protein = logs.value[date].orEmpty().sumOf { it.eaten.proteinG }
        val bonus = NutritionRules.proteinBonus(protein, targets) + NutritionRules.waterBonus(water.value[date] ?: 0, targets)
        val delta = itemXp + bonus - (bonusByDay[date] ?: 0)
        bonusByDay[date] = bonus
        totalXp += delta
        return NutritionAward(delta, levelUp = null)
    }

    companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 5)
    }
}
