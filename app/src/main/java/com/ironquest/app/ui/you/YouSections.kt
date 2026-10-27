package com.ironquest.app.ui.you

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.goals.GoalReminder
import com.ironquest.app.domain.goals.GoalSettings
import com.ironquest.app.domain.goals.GoalSwitch
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.model.PlayerProfile
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.format.formatTimeOfDay
import com.ironquest.app.ui.goals.GoalCopy
import com.ironquest.app.ui.kit.BulletList
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronSwitchRow
import com.ironquest.app.ui.kit.ProgressLine
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.TimeDialog
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import java.time.Instant
import java.time.ZoneId

/** "Show tips again": every tip shows once more, the next time its screen opens. */
@Composable
internal fun TipsSection(onShowAgain: () -> Unit) {
    RowGroup(Modifier.padding(top = 14.dp)) {
        RuledRow(onClick = onShowAgain) {
            Text(stringResource(R.string.you_tips_show_again), style = IronTheme.type.body, color = IronTheme.colors.ink, modifier = Modifier.weight(1f))
        }
    }
}

/** "Erase all data" behind two confirmations. Logged data only: programs, meals and plans stay. */
@Composable
internal fun WipeSection(onWipe: () -> Unit, onDialogOpenChange: (Boolean) -> Unit = {}) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    // 0 = closed, 1 = first question, 2 = last chance
    var step by rememberSaveable { mutableStateOf(0) }
    val currentOnDialogOpenChange by rememberUpdatedState(onDialogOpenChange)
    LaunchedEffect(step != 0) { currentOnDialogOpenChange(step != 0) }
    RowGroup {
        RuledRow(onClick = { step = 1 }) {
            Text(stringResource(R.string.you_wipe_row), style = type.body, color = colors.danger, modifier = Modifier.weight(1f))
        }
    }
    BulletList(
        stringResource(R.string.you_wipe_removes),
        stringResource(R.string.you_wipe_stays),
        style = type.secondary,
        color = colors.muted,
        modifier = Modifier.padding(top = 10.dp),
    )
    when (step) {
        1 -> IronDialog(
            title = stringResource(R.string.you_wipe_title),
            text = stringResource(R.string.you_wipe_text),
            confirmLabel = stringResource(R.string.you_wipe_continue),
            onConfirm = { step = 2 },
            onDismiss = { step = 0 },
            destructive = true,
        )
        2 -> IronDialog(
            title = stringResource(R.string.you_wipe_sure_title),
            text = stringResource(R.string.you_wipe_sure_text),
            confirmLabel = stringResource(R.string.you_wipe_confirm),
            onConfirm = {
                step = 0
                onWipe()
            },
            onDismiss = { step = 0 },
            destructive = true,
        )
    }
}

@Composable
internal fun GoalsSection(
    goals: GoalSettings,
    onSwitch: (GoalType, GoalSwitch, Boolean) -> Unit,
    onTime: (GoalReminder, Int) -> Unit,
    onDialogOpenChange: (Boolean) -> Unit = {},
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    var picking by rememberSaveable { mutableStateOf<GoalReminder?>(null) }
    val currentOnDialogOpenChange by rememberUpdatedState(onDialogOpenChange)
    LaunchedEffect(picking != null) { currentOnDialogOpenChange(picking != null) }
    GoalType.entries.forEach { goalType ->
        Text(
            GoalCopy.label(goalType).resolve(),
            style = type.bodyStrong,
            color = colors.ink,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
        RowGroup {
            GoalSwitch.entries.forEach { switch ->
                IronSwitchRow(
                    title = stringResource(switch.labelRes()),
                    checked = goals.isOn(goalType, switch),
                    onCheckedChange = { onSwitch(goalType, switch, it) },
                )
            }
        }
    }
    Text(stringResource(R.string.you_goals_notification_times), style = type.bodyStrong, color = colors.ink, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    RowGroup {
        GoalReminder.entries.forEach { reminder ->
            RuledRow(onClick = { picking = reminder }) {
                Text(stringResource(reminder.labelRes()), style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
                Text(formatTimeOfDay(goals.reminderMin(reminder)), style = type.body, color = colors.muted)
            }
        }
    }
    picking?.let { reminder ->
        TimeDialog(
            initialMin = goals.reminderMin(reminder),
            onConfirm = {
                onTime(reminder, it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@StringRes
private fun GoalSwitch.labelRes() = when (this) {
    GoalSwitch.SHOW -> R.string.you_goal_switch_show
    GoalSwitch.CELEBRATE -> R.string.you_goal_switch_celebrate
    GoalSwitch.NOTIFY -> R.string.you_goal_switch_notify
}

@StringRes
private fun GoalReminder.labelRes() = when (this) {
    GoalReminder.WORKOUT -> R.string.you_goal_reminder_workout
    GoalReminder.FOOD -> R.string.you_goal_reminder_food
}

@Composable
internal fun LevelSection(profile: PlayerProfile) {
    val colors = IronTheme.colors
    val xp = Formats.integer(profile.totalXp, currentLocale())
    val workouts = profile.questsCompleted
    Text(
        stringResource(
            R.string.you_level_summary,
            profile.level.level,
            xp,
            pluralStringResource(R.plurals.you_workouts_count, workouts, workouts),
        ),
        style = IronTheme.type.secondary,
        color = colors.muted,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
    ProgressLine(profile.level.fraction, colors.reward)
}

@Composable
internal fun StatsSection(profile: PlayerProfile) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    SectionHeader(stringResource(R.string.you_stats_title))
    RowGroup {
        STAT_ORDER.forEach { stat ->
            val progress = profile.stats.firstOrNull { it.stat == stat }?.progress ?: return@forEach
            RuledRow {
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(stringResource(stat.nameRes()), style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.you_stat_level, progress.level), style = type.secondary, color = colors.muted)
                    }
                    ProgressLine(progress.fraction, colors.stat(stat), Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

private val STAT_ORDER = listOf(StatType.STRENGTH, StatType.CONSTITUTION, StatType.STAMINA)

/** Export and import a copy of everything you logged and set up. Drive is offered by the system pickers. */
@Composable
internal fun BackupSection(actions: BackupActions) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    BulletList(
        stringResource(R.string.you_backup_hint_export),
        stringResource(R.string.you_backup_hint_import),
        stringResource(R.string.you_backup_hint_drive),
        style = type.secondary,
        color = colors.muted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    RowGroup {
        BackupRow(stringResource(R.string.you_backup_export), actions.onSave)
        BackupRow(stringResource(R.string.you_backup_import), actions.onLoad)
    }
}

@Composable
private fun BackupRow(title: String, onClick: () -> Unit) {
    RuledRow(onClick = onClick) {
        Text(title, style = IronTheme.type.body, color = IronTheme.colors.ink, modifier = Modifier.weight(1f))
        Text("›", style = IronTheme.type.body, color = IronTheme.colors.muted)
    }
}

@Composable
internal fun BackupDialogs(state: BackupUiState, onConfirm: () -> Unit, onCancel: () -> Unit, onDismissMessage: () -> Unit) {
    state.pending?.let { summary ->
        val locale = currentLocale()
        val date = Formats.date(
            Instant.ofEpochMilli(summary.exportedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate(),
            stringResource(R.string.you_backup_date_pattern),
            locale,
        )
        IronDialog(
            title = stringResource(R.string.you_backup_preview_title),
            confirmLabel = stringResource(R.string.you_backup_preview_confirm),
            onConfirm = onConfirm,
            onDismiss = onCancel,
            destructive = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.you_backup_preview_from, date), style = IronTheme.type.body, color = IronTheme.colors.ink)
                BulletList(
                    pluralStringResource(R.plurals.you_backup_workouts_count, summary.workouts, summary.workouts),
                    pluralStringResource(R.plurals.you_backup_logged_meals_count, summary.mealLogs, summary.mealLogs),
                    stringResource(
                        R.string.you_backup_preview_library,
                        pluralStringResource(R.plurals.you_backup_meals_count, summary.meals, summary.meals),
                        pluralStringResource(R.plurals.you_backup_meal_plans_count, summary.mealPlans, summary.mealPlans),
                        pluralStringResource(R.plurals.you_backup_programs_count, summary.programs, summary.programs),
                    ),
                    style = IronTheme.type.secondary,
                )
                Text(stringResource(R.string.you_backup_preview_warning), style = IronTheme.type.bodyStrong, color = IronTheme.colors.danger)
            }
        }
    }
    if (state.busy) {
        IronDialog(
            title = stringResource(R.string.you_backup_importing_title),
            confirmLabel = stringResource(R.string.you_backup_importing_wait),
            onConfirm = {},
            onDismiss = {},
            confirmEnabled = false,
            showDismiss = false,
        )
    }
    state.message?.let { message ->
        IronDialog(
            title = stringResource(R.string.you_backup_message_title),
            text = message.resolve(),
            confirmLabel = stringResource(R.string.you_backup_message_ok),
            onConfirm = onDismissMessage,
            onDismiss = onDismissMessage,
            showDismiss = false,
        )
    }
}
