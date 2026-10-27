package com.ironquest.app.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.ui.catalog.exerciseName
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.goals.GoalCopy
import com.ironquest.app.ui.kit.PageTitle
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.Stamp
import com.ironquest.app.ui.kit.SummaryCell
import com.ironquest.app.ui.kit.SummaryStrip
import com.ironquest.app.ui.kit.reachedLine
import com.ironquest.app.ui.kit.stampText
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme

@Composable
internal fun SummaryPage(state: ActiveWorkoutUiState, victory: VictoryUi, onDone: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val locale = currentLocale()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            )
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Text(stringResource(R.string.workout_summary_done), style = type.secondary, color = colors.muted)
        PageTitle(state.templateName.resolve())
        victory.levelUp?.let { levelUp ->
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Stamp(levelUp.stampText().resolve())
            }
        }
        SummaryStrip(
            listOf(
                SummaryCell(
                    stringResource(R.string.workout_summary_time),
                    victory.durationMillis?.let { durationText(it).resolve() } ?: stringResource(R.string.common_no_value),
                ),
                SummaryCell(stringResource(R.string.workout_summary_sets), Formats.integer(victory.sets, locale)),
                SummaryCell(stringResource(R.string.workout_summary_xp), stringResource(R.string.workout_xp_short, victory.totalXp)),
            ),
            Modifier.padding(top = 12.dp),
        )
        SectionHeader(stringResource(R.string.workout_summary_xp))
        RowGroup {
            XpLine(stringResource(R.string.workout_summary_sets), victory.setXp, colors.ink)
            victory.goals.forEach { goal ->
                XpLine(stringResource(R.string.workout_dot_pair, GoalCopy.label(goal.type).resolve(), exerciseName(goal.exerciseId, goal.exerciseName)), goal.xp, colors.reward)
            }
            XpLine(stringResource(R.string.workout_summary_completion_bonus), victory.bonusXp, colors.ink)
            RuledRow {
                Text(stringResource(R.string.workout_summary_total), style = type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.common_xp_gain, victory.totalXp), style = type.bodyStrong, color = colors.reward)
            }
        }
        victory.levelUp?.let { levelUp ->
            Text(levelUp.reachedLine().resolve(), style = type.body, color = colors.reward, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.workout_back_to_today), onClick = onDone)
    }
}

@Composable
private fun XpLine(label: String, xp: Int, valueColor: Color) {
    val type = IronTheme.type
    RuledRow {
        Text(label, style = type.body, color = IronTheme.colors.ink, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.workout_xp_short, xp), style = type.body, color = valueColor)
    }
}
