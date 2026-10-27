package com.ironquest.app.data.repository

import com.ironquest.app.data.local.entity.DayTotalsRow
import com.ironquest.app.data.local.entity.MealEntity
import com.ironquest.app.data.local.entity.MealLogEntity
import com.ironquest.app.data.local.entity.MealLogItemEntity
import com.ironquest.app.data.local.entity.MealPlanEntity
import com.ironquest.app.data.local.entity.MealWithTags
import com.ironquest.app.data.local.entity.NutritionDayEntity
import com.ironquest.app.data.local.entity.PlanWithSlots
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.food.UnitKind
import com.ironquest.app.domain.nutrition.DayTotals
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.PlanSlot

internal fun MealEntity.macros() = Macros(proteinG, carbsG, fatG, fiberG, kcal)

internal fun MealWithTags.toDomain() = Meal(
    id = meal.id,
    name = meal.name,
    macros = meal.macros(),
    tags = tags.map { it.tag }.sorted(),
    recipeText = meal.recipeText,
    recipeUrl = meal.recipeUrl,
    archived = meal.archived,
)

internal fun MealDraft.toEntity(id: Long, createdAtMillis: Long, archived: Boolean) = MealEntity(
    id = id,
    name = name.trim(),
    proteinG = macros.proteinG,
    carbsG = macros.carbsG,
    fatG = macros.fatG,
    fiberG = macros.fiberG,
    kcal = macros.kcal,
    recipeText = recipeText?.trim()?.ifEmpty { null },
    recipeUrl = recipeUrl?.trim()?.ifEmpty { null },
    archived = archived,
    createdAtMillis = createdAtMillis,
)

internal fun MealPlanEntity.targets() = MacroTargets(targetProteinG, targetCarbsG, targetFatG, targetFiberG, targetKcal, waterTargetMl)

internal fun MealPlanEntity.withTargets(targets: MacroTargets) = copy(
    targetProteinG = targets.proteinG,
    targetCarbsG = targets.carbsG,
    targetFatG = targets.fatG,
    targetFiberG = targets.fiberG,
    targetKcal = targets.kcal,
    waterTargetMl = targets.waterMl,
)

internal fun PlanWithSlots.toDomain() = MealPlan(
    id = plan.id,
    name = plan.name,
    isActive = plan.isActive,
    targets = plan.targets(),
    slots = slots.sortedBy { it.slot.position }.map { planSlot ->
        PlanSlot(
            id = planSlot.slot.id,
            label = planSlot.slot.label,
            mealId = planSlot.meal.id,
            mealName = planSlot.meal.name,
            mealMacros = planSlot.meal.macros(),
            timeOfDayMin = planSlot.slot.timeOfDayMin,
            reminderOffsetMin = planSlot.slot.reminderOffsetMin,
        )
    },
)

internal fun MealLogEntity.toDomain(items: List<MealLogItem> = emptyList()) = MealLogEntry(
    id = id,
    slotId = slotId,
    mealId = mealId,
    mealName = mealName,
    portion = portion,
    macros1x = Macros(proteinG, carbsG, fatG, fiberG, kcal),
    items = items,
)

internal fun MealLogItemEntity.toDomain() = MealLogItem(
    foodKey = foodKey,
    name = foodName,
    amount = amount,
    unit = FoodUnit.fromStorage(unitKey, gramsPerUnit),
    macros = Macros(proteinG, carbsG, fatG, fiberG, kcal),
)

internal fun MealLogItem.toEntity(logId: Long, position: Int) = MealLogItemEntity(
    logId = logId,
    position = position,
    foodKey = foodKey,
    foodName = name,
    amount = amount,
    unitKey = unit.storageKey,
    gramsPerUnit = unit.gramsPerUnit.takeIf { unit.kind != UnitKind.GRAM && unit.kind != UnitKind.MEAL_PORTION },
    proteinG = macros.proteinG,
    carbsG = macros.carbsG,
    fatG = macros.fatG,
    fiberG = macros.fiberG,
    kcal = macros.effectiveKcal,
)

/** 1× snapshot of the meal as it is right now. */
internal fun MealEntity.toLog(day: Long, slotId: Long?, portion: Double, xp: Int, nowMillis: Long) = MealLogEntity(
    dateEpochDay = day,
    slotId = slotId,
    mealId = id,
    mealName = name,
    portion = portion,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
    kcal = macros().effectiveKcal,
    xpAwarded = xp,
    loggedAtMillis = nowMillis,
)

internal fun emptyNutritionDay(day: Long) = NutritionDayEntity(
    dateEpochDay = day,
    planId = null,
    targetProteinG = null,
    targetCarbsG = null,
    targetFatG = null,
    targetFiberG = null,
    targetKcal = null,
    waterTargetMl = null,
    waterMl = 0,
    proteinBonusXp = 0,
    waterBonusXp = 0,
)

internal fun NutritionDayEntity.withTargets(planId: Long?, targets: MacroTargets?) = copy(
    planId = planId,
    targetProteinG = targets?.proteinG,
    targetCarbsG = targets?.carbsG,
    targetFatG = targets?.fatG,
    targetFiberG = targets?.fiberG,
    targetKcal = targets?.kcal,
    waterTargetMl = targets?.waterMl,
)

internal fun NutritionDayEntity.targets() = MacroTargets(targetProteinG, targetCarbsG, targetFatG, targetFiberG, targetKcal, waterTargetMl)

internal fun DayTotalsRow.toDomain() = DayTotals(Macros(proteinG, carbsG, fatG, fiberG, kcal), logCount)
