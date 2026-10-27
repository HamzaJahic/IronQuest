package com.ironquest.app.data.repository

import androidx.room.withTransaction
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.entity.DayTotalsRow
import com.ironquest.app.data.local.entity.MealLogEntity
import com.ironquest.app.data.local.entity.MealPlanEntity
import com.ironquest.app.data.local.entity.MealTagEntity
import com.ironquest.app.data.local.entity.NutritionDayEntity
import com.ironquest.app.data.local.entity.PlanSlotEntity
import com.ironquest.app.data.local.entity.PlayerProfileEntity
import com.ironquest.app.data.local.entity.SupplementEntity
import com.ironquest.app.data.local.entity.SupplementIntakeEntity
import com.ironquest.app.domain.nutrition.DayKey
import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.DayRow
import com.ironquest.app.domain.nutrition.DeleteOutcome
import com.ironquest.app.domain.nutrition.FoodTrackingMode
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
import com.ironquest.app.domain.nutrition.ReminderSnapshot
import com.ironquest.app.domain.nutrition.ReminderSupplement
import com.ironquest.app.domain.nutrition.SavePlanResult
import com.ironquest.app.domain.nutrition.StreakCalculator
import com.ironquest.app.domain.nutrition.SupplementDraft
import com.ironquest.app.domain.nutrition.SupplementRef
import com.ironquest.app.domain.nutrition.SupplementStatus
import com.ironquest.app.domain.nutrition.TargetResolution
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.NutritionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private data class DaySnapshot(val row: NutritionDayEntity?, val planTargets: MacroTargets?)

private data class HistoryParts(
    val totals: List<DayTotalsRow>,
    val rows: List<NutritionDayEntity>,
    val planTargets: MacroTargets?,
    val supplements: List<SupplementEntity>,
    val intakes: List<SupplementIntakeEntity>,
)

@Singleton
class OfflineNutritionRepository @Inject constructor(
    private val db: IronQuestDatabase,
    private val clock: Clock,
    private val settings: AppSettingsRepository,
) : NutritionRepository {

    private val dao = db.nutritionDao()
    private val profileDao = db.playerProfileDao()
    private val foodDao = db.foodDao()

    /** Today's targets: the tracking mode decides between the active plan and your own. */
    private fun resolvedTargets(active: MealPlanEntity?): MacroTargets? {
        val food = settings.currentFood()
        return TargetResolution.resolve(food.mode, active?.targets(), food.ownTargets)
    }

    /** Plan id kept in the snapshot only when the plan's targets are the ones used. */
    private fun snapshotPlanId(active: MealPlanEntity?): Long? = active?.id?.takeIf { settings.currentFood().mode != FoodTrackingMode.ON_THE_FLY }

    private fun today(): Long = LocalDate.now(clock).toEpochDay()

    override fun observeMeals(): Flow<List<Meal>> = dao.observeActiveMeals().map { rows -> rows.map { it.toDomain() } }

    override fun observeArchivedMeals(): Flow<List<Meal>> = dao.observeArchivedMeals().map { rows -> rows.map { it.toDomain() } }

    override fun observeMeal(id: Long): Flow<Meal?> = dao.observeMeal(id).map { it?.toDomain() }

    override fun observeMealUsages(mealId: Long): Flow<List<MealUsage>> = dao.observeSlotUsages(mealId).map { rows -> rows.map { MealUsage(it.planName, it.label, it.timeOfDayMin) } }

    override suspend fun saveMeal(draft: MealDraft): Long = db.withTransaction {
        require(draft.name.isNotBlank()) { "Meal name is required" }
        val id = if (draft.id == null) {
            dao.insertMeal(draft.toEntity(id = 0, createdAtMillis = clock.millis(), archived = false))
        } else {
            val existing = dao.getMeal(draft.id) ?: error("Meal ${draft.id} not found")
            dao.updateMeal(draft.toEntity(draft.id, existing.createdAtMillis, existing.archived))
            draft.id
        }
        dao.clearTags(id)
        dao.insertTags(draft.tags.mapNotNull(NutritionRules::normalizeTag).distinct().map { MealTagEntity(id, it) })
        id
    }

    override suspend fun deleteMeal(id: Long): DeleteOutcome = db.withTransaction {
        if (dao.countSlotsUsingMeal(id) > 0) {
            dao.archiveMeal(id)
            DeleteOutcome.ARCHIVED
        } else {
            // meal_logs.mealId is SET NULL; the logs keep their snapshot.
            dao.deleteMeal(id)
            DeleteOutcome.DELETED
        }
    }

    override suspend fun restoreMeal(id: Long) = dao.restoreMeal(id)

    override fun observePlans(): Flow<List<MealPlan>> = dao.observePlans().map { rows -> rows.map { it.toDomain() } }

    override fun observePlan(id: Long): Flow<MealPlan?> = dao.observePlan(id).map { it?.toDomain() }

    override suspend fun savePlan(draft: PlanDraft): SavePlanResult = db.withTransaction {
        require(draft.name.isNotBlank()) { "Plan name is required" }
        draft.slots.forEach { slot ->
            require(slot.label.isNotBlank()) { "Slot label is required" }
            require(NutritionRules.isValidReminder(slot.timeOfDayMin, slot.reminderOffsetMin)) { "Invalid reminder" }
        }
        val planId = if (draft.id == null) {
            dao.insertPlan(
                MealPlanEntity(
                    name = draft.name.trim(),
                    isActive = false,
                    targetProteinG = null,
                    targetCarbsG = null,
                    targetFatG = null,
                    targetFiberG = null,
                    targetKcal = null,
                    waterTargetMl = null,
                    position = dao.nextPlanPosition(),
                ).withTargets(draft.targets),
            )
        } else {
            val existing = dao.getPlan(draft.id) ?: error("Plan ${draft.id} not found")
            dao.updatePlan(existing.copy(name = draft.name.trim()).withTargets(draft.targets))
            draft.id
        }

        // Diff, never delete-and-reinsert: a new slot id would SET NULL today's meal_logs.slotId
        // and silently turn checked slots into off-plan logs.
        val existingIds = dao.slotsForPlan(planId).map { it.id }.toSet()
        val keptIds = draft.slots.mapNotNull { it.id }.filter { it in existingIds }.toSet()
        val removed = (existingIds - keptIds).toList()
        if (removed.isNotEmpty()) dao.deleteSlots(removed)
        draft.slots.forEachIndexed { index, slot ->
            val entity = PlanSlotEntity(
                id = slot.id?.takeIf { it in existingIds } ?: 0,
                planId = planId,
                label = slot.label.trim(),
                mealId = slot.mealId,
                timeOfDayMin = slot.timeOfDayMin,
                reminderOffsetMin = slot.reminderOffsetMin,
                position = index,
            )
            if (entity.id == 0L) dao.insertSlot(entity) else dao.updateSlot(entity)
        }

        // Editing the active plan's targets re-evaluates today's bonuses straight away.
        val award = if (dao.getPlan(planId)?.isActive == true) xpWrite(today()) { 0 } else NutritionAward.NONE
        SavePlanResult(planId, award)
    }

    override suspend fun setActivePlan(id: Long?): NutritionAward = xpWrite(today()) {
        if (id != null && dao.getPlan(id) == null) throw IllegalArgumentException("Plan $id not found")
        dao.deactivateAllPlans()
        if (id != null) dao.activatePlan(id)
        0
    }

    override suspend fun duplicatePlan(id: Long, copyName: (String) -> String): Long = db.withTransaction {
        val source = dao.getPlanWithSlots(id) ?: error("Plan $id not found")
        val newId = dao.insertPlan(
            source.plan.copy(id = 0, name = copyName(source.plan.name), isActive = false, position = dao.nextPlanPosition()),
        )
        source.slots.sortedBy { it.slot.position }.forEach { dao.insertSlot(it.slot.copy(id = 0, planId = newId)) }
        newId
    }

    override suspend fun deletePlan(id: Long): NutritionAward = db.withTransaction {
        val wasActive = dao.getPlan(id)?.isActive == true
        if (wasActive) {
            xpWrite(today()) {
                dao.deletePlan(id)
                0
            }
        } else {
            dao.deletePlan(id)
            NutritionAward.NONE
        }
    }

    override fun observeDay(date: LocalDate): Flow<DayNutrition> {
        val day = date.toEpochDay()
        val logsWithItems = combine(dao.observeLogs(day), foodDao.observeItemsForDay(day)) { logs, items ->
            val itemsByLog = items.groupBy { it.logId }
            logs.map { log -> log.toDomain(itemsByLog[log.id].orEmpty().map { it.toDomain() }) }
        }
        return combine(
            logsWithItems,
            dao.observeDay(day),
            dao.observeActivePlan(),
            dao.observeSupplements(),
            dao.observeIntakesBetween(NutritionRules.streakWindowStart(day), day),
        ) { logs, row, active, supplements, intakes ->
            val daysBySupplement = intakes.groupBy({ it.supplementId }, { it.dateEpochDay })
            DayNutrition(
                date = date,
                activePlan = active?.toDomain(),
                targets = null,
                logs = logs,
                waterMl = row?.waterMl ?: 0,
                supplements = supplements.map { supplement ->
                    val days = daysBySupplement[supplement.id].orEmpty().toSet()
                    SupplementStatus(supplement.id, supplement.name, supplement.dose, taken = day in days, streak = StreakCalculator.currentStreak(days, day), reminderTimeMin = supplement.reminderTimeMin)
                },
            ) to DaySnapshot(row, active?.plan?.targets())
        }.combine(settings.observeFood()) { (dayNutrition, snapshot), food ->
            val (row, planTargets) = snapshot
            val live = TargetResolution.resolve(food.mode, planTargets, food.ownTargets)
            // Today always reads the live targets; past days read their snapshot. A past day nothing was
            // written for yet shows the live targets, which its first entry then snapshots.
            dayNutrition.copy(targets = if (day == today()) live else row?.targets() ?: live)
        }
    }

    override suspend fun logSlot(date: LocalDate, slotId: Long, portion: Double): NutritionAward {
        requirePortion(portion)
        val day = date.toEpochDay()
        return xpWrite(day) {
            if (dao.findSlotLog(day, slotId) != null) return@xpWrite null
            val slot = dao.getSlot(slotId) ?: error("Slot $slotId not found")
            val meal = dao.getMeal(slot.mealId) ?: error("Meal ${slot.mealId} not found")
            dao.insertLog(meal.toLog(day, slotId, portion, NutritionRules.MEAL_XP, clock.millis()))
            NutritionRules.MEAL_XP
        }
    }

    override suspend fun logOffPlan(date: LocalDate, mealId: Long, portion: Double): NutritionAward {
        requirePortion(portion)
        val day = date.toEpochDay()
        return xpWrite(day) {
            val meal = dao.getMeal(mealId) ?: error("Meal $mealId not found")
            dao.insertLog(meal.toLog(day, slotId = null, portion, NutritionRules.MEAL_XP, clock.millis()))
            NutritionRules.MEAL_XP
        }
    }

    override suspend fun saveQuickMeal(date: LocalDate, logId: Long?, name: String, items: List<MealLogItem>): NutritionAward {
        require(name.isNotBlank()) { "Meal name is required" }
        require(items.isNotEmpty()) { "A meal needs at least one food" }
        val total = items.fold(Macros.ZERO) { sum, item -> sum + item.macros }
        val day = date.toEpochDay()
        return xpWrite(day) {
            val id = if (logId == null) {
                dao.insertLog(
                    MealLogEntity(
                        dateEpochDay = day,
                        slotId = null,
                        mealId = null,
                        mealName = name.trim(),
                        portion = 1.0,
                        proteinG = total.proteinG,
                        carbsG = total.carbsG,
                        fatG = total.fatG,
                        fiberG = total.fiberG,
                        kcal = total.effectiveKcal,
                        xpAwarded = NutritionRules.MEAL_XP,
                        loggedAtMillis = clock.millis(),
                    ),
                )
            } else {
                val existing = dao.getLog(logId) ?: return@xpWrite null
                dao.updateLog(
                    existing.copy(
                        mealName = name.trim(),
                        portion = 1.0,
                        proteinG = total.proteinG,
                        carbsG = total.carbsG,
                        fatG = total.fatG,
                        fiberG = total.fiberG,
                        kcal = total.effectiveKcal,
                    ),
                )
                // Items are leaves (nothing references them), so replacing them is safe.
                foodDao.deleteItems(logId)
                logId
            }
            foodDao.insertItems(items.mapIndexed { index, item -> item.toEntity(id, index) })
            if (logId == null) NutritionRules.MEAL_XP else 0
        }
    }

    override suspend fun getLog(logId: Long): MealLogEntry? = db.withTransaction {
        dao.getLog(logId)?.toDomain(foodDao.itemsFor(logId).map { it.toDomain() })
    }

    override suspend fun updatePortion(logId: Long, portion: Double): NutritionAward {
        requirePortion(portion)
        val log = dao.getLog(logId) ?: return NutritionAward(xpDelta = 0, levelUp = null, alreadyDone = true)
        return xpWrite(log.dateEpochDay) {
            dao.getLog(logId) ?: return@xpWrite null
            dao.updateLogPortion(logId, portion)
            0
        }
    }

    override suspend fun removeLog(logId: Long): NutritionAward {
        val log = dao.getLog(logId) ?: return NutritionAward(xpDelta = 0, levelUp = null, alreadyDone = true)
        return xpWrite(log.dateEpochDay) {
            val current = dao.getLog(logId) ?: return@xpWrite null
            dao.deleteLog(logId)
            -current.xpAwarded
        }
    }

    override suspend fun addWater(date: LocalDate, deltaMl: Int): NutritionAward {
        val day = date.toEpochDay()
        return xpWrite(day) {
            val existing = dao.getDay(day)
            if (existing == null) {
                val active = dao.getActivePlan()
                dao.insertDay(emptyNutritionDay(day).withTargets(snapshotPlanId(active), resolvedTargets(active)).copy(waterMl = NutritionRules.clampWater(0, deltaMl)))
            } else {
                dao.updateDay(existing.copy(waterMl = NutritionRules.clampWater(existing.waterMl, deltaMl)))
            }
            0
        }
    }

    override suspend fun setSupplementTaken(date: LocalDate, supplementId: Long, taken: Boolean): NutritionAward {
        val day = date.toEpochDay()
        return xpWrite(day) {
            val supplement = dao.getSupplement(supplementId) ?: return@xpWrite null
            if (supplement.archived && taken) return@xpWrite null
            val existing = dao.getIntake(supplementId, day)
            when {
                taken && existing == null -> {
                    dao.insertIntake(SupplementIntakeEntity(supplementId, day, NutritionRules.SUPPLEMENT_XP, clock.millis()))
                    NutritionRules.SUPPLEMENT_XP
                }
                !taken && existing != null -> {
                    dao.deleteIntake(supplementId, day)
                    -existing.xpAwarded
                }
                else -> null
            }
        }
    }

    override suspend fun saveSupplement(draft: SupplementDraft): Long = db.withTransaction {
        require(draft.name.isNotBlank()) { "Supplement name is required" }
        require(NutritionRules.isValidReminder(draft.reminderTimeMin, reminderOffsetMin = null)) { "Invalid reminder time" }
        if (draft.id == null) {
            dao.insertSupplement(
                SupplementEntity(
                    name = draft.name.trim(),
                    dose = draft.dose.trim(),
                    reminderTimeMin = draft.reminderTimeMin,
                    archived = false,
                    position = dao.nextSupplementPosition(),
                    createdAtEpochDay = today(),
                ),
            )
        } else {
            val existing = dao.getSupplement(draft.id) ?: error("Supplement ${draft.id} not found")
            dao.updateSupplement(existing.copy(name = draft.name.trim(), dose = draft.dose.trim(), reminderTimeMin = draft.reminderTimeMin))
            draft.id
        }
    }

    override suspend fun deleteSupplement(id: Long): DeleteOutcome = db.withTransaction {
        // CASCADE would silently drop intakes (and the XP they paid), so archive instead.
        if (dao.countIntakes(id) > 0) {
            dao.archiveSupplement(id)
            DeleteOutcome.ARCHIVED
        } else {
            dao.deleteSupplement(id)
            DeleteOutcome.DELETED
        }
    }

    override fun observeHistory(from: LocalDate, to: LocalDate): Flow<NutritionHistory> {
        require(!to.isBefore(from)) { "Empty range" }
        val first = from.toEpochDay()
        val last = to.toEpochDay()
        return combine(
            dao.observeDayTotals(first, last),
            dao.observeDaysBetween(first, last),
            dao.observeActivePlan(),
            dao.observeSupplements(),
            dao.observeIntakesBetween(minOf(first, NutritionRules.streakWindowStart(last)), last),
        ) { totals, rows, active, supplements, intakes ->
            HistoryParts(totals, rows, active?.plan?.targets(), supplements, intakes)
        }.combine(settings.observeFood()) { parts, food ->
            val (totals, rows, planTargets, supplements, intakes) = parts
            NutritionHistory(
                from = from,
                to = to,
                totals = totals.associate { LocalDate.ofEpochDay(it.dateEpochDay) to it.toDomain() },
                rows = rows.associate { LocalDate.ofEpochDay(it.dateEpochDay) to DayRow(it.targets(), it.waterMl) },
                liveTargets = TargetResolution.resolve(food.mode, planTargets, food.ownTargets),
                supplements = supplements.map { SupplementRef(it.id, it.name, it.createdAtEpochDay) },
                intakeEpochDays = intakes.groupBy({ it.supplementId }, { it.dateEpochDay }).mapValues { (_, days) -> days.toSet() },
            )
        }
    }

    override suspend fun reminderSnapshot(from: LocalDate, to: LocalDate): ReminderSnapshot = db.withTransaction {
        val fromDay = from.toEpochDay()
        val toDay = to.toEpochDay()
        ReminderSnapshot(
            slots = if (settings.currentFood().mode.showsPlan) dao.getActivePlanWithSlots()?.toDomain()?.slots.orEmpty() else emptyList(),
            supplements = dao.getActiveSupplements().map { ReminderSupplement(it.id, it.name, it.dose, it.reminderTimeMin) },
            loggedSlots = dao.slotLogKeys(fromDay, toDay).map { DayKey(it.dateEpochDay, it.slotId) }.toSet(),
            takenSupplements = dao.intakesBetween(fromDay, toDay).map { DayKey(it.dateEpochDay, it.supplementId) }.toSet(),
        )
    }

    /**
     * Runs [block] and the day's bonus re-evaluation in one transaction, then pays the net
     * XP change. [block] returns the item XP delta, or null when the write was already done
     * (the whole call then becomes a no-op with `alreadyDone = true`).
     */
    private suspend fun xpWrite(day: Long, block: suspend () -> Int?): NutritionAward = db.withTransaction {
        require(day <= today()) { "Cannot log nutrition for a future date" }
        val before = requireProfile()
        val itemXp = block() ?: return@withTransaction NutritionAward(xpDelta = 0, levelUp = null, alreadyDone = true)
        val delta = itemXp + syncDay(day)
        if (delta != 0) profileDao.addCharacterXp(delta)
        val after = requireProfile()
        NutritionAward(delta, profileDao.reportLevelUp(before, after))
    }

    /**
     * Creates/refreshes the day row and pays or revokes the protein/water bonuses so they
     * match the day's current totals and targets. Returns the XP change. Today's target
     * snapshot is refreshed from the active plan on every write; past days keep theirs.
     */
    private suspend fun syncDay(day: Long): Int {
        val existing = dao.getDay(day)
        val active = dao.getActivePlan()
        val row = when {
            existing == null -> emptyNutritionDay(day).withTargets(snapshotPlanId(active), resolvedTargets(active))
            day == today() -> existing.withTargets(snapshotPlanId(active), resolvedTargets(active))
            else -> existing
        }
        val targets = row.targets()
        val proteinBonus = NutritionRules.proteinBonus(dao.sumProtein(day), targets)
        val waterBonus = NutritionRules.waterBonus(row.waterMl, targets)
        val delta = (proteinBonus - row.proteinBonusXp) + (waterBonus - row.waterBonusXp)
        val updated = row.copy(proteinBonusXp = proteinBonus, waterBonusXp = waterBonus)
        if (existing == null) dao.insertDay(updated) else dao.updateDay(updated)
        return delta
    }

    private suspend fun requireProfile(): PlayerProfileEntity = profileDao.get() ?: error("player_profile row missing — SeedCallback did not run")

    /** Also guards phase-3 notification receivers, which bypass the ViewModels. */
    private fun requirePortion(portion: Double) = require(portion in NutritionRules.MIN_PORTION..NutritionRules.MAX_PORTION) { "Portion out of range" }
}
