package com.ironquest.app.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.MacroLine
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.nutrition.SupplementStatus
import com.ironquest.app.domain.nutrition.TargetStatus
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.format.formatTimeOfDay
import com.ironquest.app.ui.kit.ActionTone
import com.ironquest.app.ui.kit.CheckBox
import com.ironquest.app.ui.kit.CheckRow
import com.ironquest.app.ui.kit.IronIcons
import com.ironquest.app.ui.kit.ProgressLine
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.theme.IronTheme

/** Plan name, protein bar (with the bonus tick), then one muted line for the other macros. */
@Composable
internal fun Targets(state: FoodDayUiState, onOpenPlans: () -> Unit, onSetTargets: () -> Unit) {
    val summary = state.summary ?: return
    val colors = IronTheme.colors
    val locale = currentLocale()
    val unitG = stringResource(R.string.unit_g)
    val unitKcal = stringResource(R.string.unit_kcal)
    val kcalTarget = summary.kcal.target
    if (state.mode.showsPlan) {
        SectionHeader(
            state.planName ?: stringResource(R.string.food_no_targets),
            Modifier.clickable(onClickLabel = stringResource(R.string.food_day_open_plans_cd), role = Role.Button, onClick = onOpenPlans),
        )
    } else {
        SectionHeader(stringResource(R.string.food_targets_title))
        val hasTargets = listOf(summary.protein.target, summary.carbs.target, summary.fat.target, kcalTarget).any { it != null } || state.waterTargetMl != null
        if (!hasTargets) TextAction(stringResource(R.string.food_day_set_targets_link), onClick = onSetTargets, tone = ActionTone.MUTED)
    }
    MacroBar(stringResource(R.string.food_macro_protein), summary.protein, bonusXp = NutritionRules.PROTEIN_BONUS_XP)
    Text(
        listOf(
            stringResource(R.string.food_day_carbs_line, summary.carbs.display(unitG)),
            stringResource(R.string.food_day_fat_line, summary.fat.display(unitG)),
            stringResource(R.string.food_day_fiber_line, summary.fiber.display(unitG)),
            if (kcalTarget != null) {
                stringResource(R.string.food_progress_of_target, summary.kcal.eaten.wholeText(locale), kcalTarget.wholeText(locale), unitKcal)
            } else {
                stringResource(R.string.food_progress_eaten, summary.kcal.eaten.wholeText(locale), unitKcal)
            },
        ).joinToString(stringResource(R.string.common_dot_separator)),
        style = IronTheme.type.secondary,
        color = colors.muted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * "Protein   45 / 160 g" over a progress line (`ink`; `hold` once over). With [bonusXp], a `reward`
 * tick marks 100 % of the target and "+25 XP" shows once it is reached.
 */
@Composable
fun MacroBar(label: String, line: MacroLine, modifier: Modifier = Modifier, unit: String = stringResource(R.string.unit_g), bonusXp: Int? = null) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val target = line.target
    val hasTick = bonusXp != null && target != null && target > 0
    val bonusEarned = target != null && hasTick && line.eaten >= target
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
            if (bonusEarned && bonusXp != null) {
                Text(stringResource(R.string.common_xp_gain, bonusXp), style = type.secondary, color = colors.reward, modifier = Modifier.padding(end = 8.dp))
            }
            Text(line.display(unit), style = type.bodyStrong, color = colors.ink)
        }
        Box(Modifier.padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) {
            ProgressLine(line.fraction, if (line.status == TargetStatus.OVER) colors.hold else colors.ink)
            if (hasTick) Box(Modifier.width(2.dp).height(11.dp).background(colors.reward))
        }
    }
}

/**
 * One planned meal: small "07:30 · Breakfast" line, the meal's name on its own line (struck
 * through once eaten), then its macros in words. The portion sits on the right once logged.
 */
@Composable
internal fun SlotRow(row: SlotRowUi, onToggle: () -> Unit, onPortion: (MealLogEntry) -> Unit, onOpenMeal: (Long) -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    CheckRow(
        checked = row.checked,
        onToggle = onToggle,
        enabled = !row.isSaving,
        label = row.slot.reminderOffsetMin?.let { offset ->
            pluralStringResource(R.plurals.food_day_slot_reminder_cd, offset, row.slot.label, row.slot.mealName, offset)
        } ?: stringResource(R.string.food_day_slot_cd, row.slot.label, row.slot.mealName),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.food_day_open_meal_cd), role = Role.Button) { onOpenMeal(row.slot.mealId) }
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                IronTheme.style.cased(
                    listOfNotNull(
                        row.slot.timeOfDayMin?.let { formatTimeOfDay(it) },
                        row.slot.label,
                        row.slot.reminderOffsetMin?.let { stringResource(R.string.food_day_slot_reminder_line, it) },
                    ).joinToString(stringResource(R.string.common_dot_separator)),
                ),
                style = type.label,
                color = colors.muted,
            )
            Text(
                row.slot.mealName,
                style = type.bodyStrong.copy(textDecoration = if (row.checked) TextDecoration.LineThrough else null),
                color = if (row.checked) colors.muted else colors.ink,
            )
            Text(macroWords(row.log?.eaten ?: row.slot.mealMacros), style = type.secondary, color = colors.muted)
        }
        row.log?.let { log -> PortionButton(log.portion) { onPortion(log) } }
    }
}

@Composable
private fun PortionButton(portion: Double, onClick: (() -> Unit)?) {
    val text = stringResource(R.string.food_portion_value, portion.amountText(currentLocale()))
    val portionCd = stringResource(R.string.food_day_portion_change_cd, text)
    if (onClick == null) {
        Text(text, style = IronTheme.type.body, color = IronTheme.colors.muted)
    } else {
        TextAction(text, onClick, modifier = Modifier.semantics { contentDescription = portionCd }, strong = true)
    }
}

/** An off-plan (or earlier-plan) log: already eaten, so it shows a ticked box and a remove button. */
@Composable
internal fun LogRow(log: MealLogEntry, onPortion: (() -> Unit)?, onRemove: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    RuledRow {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { CheckBox(checked = true) }
        Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(IronTheme.style.cased(stringResource(R.string.food_day_extra_label)), style = type.label, color = colors.muted)
            Text(log.mealName, style = type.bodyStrong.copy(textDecoration = TextDecoration.LineThrough), color = colors.muted)
            Text(macroWords(log.eaten), style = type.secondary, color = colors.muted)
        }
        PortionButton(log.portion, onPortion)
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.food_day_remove_log_cd, log.mealName), tint = colors.muted)
        }
    }
}

/** A quick meal: ticked box, "Lunch · 3 foods" (tap to edit it in the builder), kcal on the right, remove button. */
@Composable
internal fun QuickMealRow(log: MealLogEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    RuledRow {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { CheckBox(checked = true) }
        Text(
            stringResource(
                R.string.food_day_quick_meal_line,
                log.mealName,
                pluralStringResource(R.plurals.food_builder_foods_count, log.items.size, log.items.size),
            ),
            style = type.bodyStrong,
            color = colors.ink,
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.food_day_open_meal_cd), role = Role.Button, onClick = onOpen)
                .padding(vertical = 12.dp),
        )
        Text(
            stringResource(R.string.food_amount_kcal, log.eaten.effectiveKcal.wholeText(currentLocale())),
            style = type.secondary,
            color = colors.muted,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.food_day_remove_log_cd, log.mealName), tint = colors.muted)
        }
    }
}

/** "−   1,250 ml   +      of 3,000" */
@Composable
internal fun WaterRow(state: FoodDayUiState, onAction: (FoodDayAction) -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val canAdd = state.waterMl < NutritionRules.MAX_WATER_DAY_ML
    val locale = currentLocale()
    RowGroup(Modifier.onboardingAnchor(OnboardingTarget.FOOD_WATER)) {
        RuledRow {
            IconButton(onClick = { onAction(FoodDayAction.RemoveWater) }, enabled = state.waterMl > 0) {
                Icon(IronIcons.Remove, contentDescription = stringResource(R.string.food_day_water_remove_cd), tint = if (state.waterMl > 0) colors.ink else colors.line)
            }
            Text(stringResource(R.string.food_amount_ml, Formats.integer(state.waterMl, locale)), style = type.bodyStrong, color = colors.ink)
            IconButton(onClick = { onAction(FoodDayAction.AddWater) }, enabled = canAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.food_day_water_add_cd), tint = if (canAdd) colors.ink else colors.line)
            }
            Spacer(Modifier.weight(1f))
            state.waterTargetMl?.let { Text(stringResource(R.string.food_day_water_target, Formats.integer(it, locale)), style = type.secondary, color = colors.muted) }
        }
    }
}

/** "Creatine · 5 g · 12 days" with a check box; tap the text to edit. */
@Composable
internal fun SupplementRow(supplement: SupplementStatus, onToggle: () -> Unit, onEdit: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    CheckRow(checked = supplement.taken, onToggle = onToggle, label = supplement.name) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.food_day_edit_supplement_cd, supplement.name), role = Role.Button, onClick = onEdit)
                .padding(vertical = 4.dp),
        ) {
            val dot = stringResource(R.string.common_dot_separator)
            Text(listOf(supplement.name, supplement.dose).filter { it.isNotBlank() }.joinToString(dot), style = type.body, color = colors.ink)
            val details = listOfNotNull(
                supplement.streak.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.food_day_count, it, it) },
                supplement.reminderTimeMin?.let { stringResource(R.string.food_day_supplement_reminder, formatTimeOfDay(it)) },
            )
            if (details.isNotEmpty()) Text(details.joinToString(dot), style = type.secondary, color = colors.muted)
        }
    }
}
