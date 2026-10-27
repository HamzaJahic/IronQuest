package com.ironquest.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.ui.catalog.exerciseNameText
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.SummaryCell
import com.ironquest.app.ui.kit.SummaryStrip
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.today.DayUi
import com.ironquest.app.ui.today.ExerciseLineUi
import com.ironquest.app.ui.today.TodayHeader
import com.ironquest.app.ui.today.TodayUiState
import com.ironquest.app.ui.today.TodayWorkoutLine
import java.time.LocalDate
import kotlin.math.roundToInt

private const val PREVIEW_SCALE = 0.8f

/**
 * The top of the Today screen with sample numbers, drawn with Today's own pieces so it restyles
 * with the theme as the user picks. Shrunk to [PREVIEW_SCALE]; taps and semantics are swallowed.
 */
@Composable
internal fun TodayPreview(modifier: Modifier = Modifier) {
    val colors = IronTheme.colors
    val shape = IronTheme.shapes.sheet
    val description = stringResource(R.string.setup_appearance_preview_cd)
    val state = remember { sampleTodayState(LocalDate.now()) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.paper)
            .border(1.dp, colors.line, shape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(Modifier.scaled(PREVIEW_SCALE).padding(20.dp)) {
            TodayHeader(state)
            SummaryStrip(
                listOf(
                    SummaryCell(stringResource(R.string.today_summary_this_week), stringResource(R.string.today_summary_week_progress, state.weekDone, state.weekTarget)),
                    SummaryCell(stringResource(R.string.today_summary_streak), stringResource(R.string.today_summary_streak_value, state.streakWeeks)),
                    SummaryCell(stringResource(R.string.today_summary_protein), stringResource(R.string.unit_g_value, SAMPLE_PROTEIN_G)),
                ),
                Modifier.padding(top = 12.dp),
            )
            SectionHeader(stringResource(R.string.today_workout_title))
            TodayWorkoutLine(state.exercises)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.today_workout_start), onClick = {})
        }
        // On top of the sample, so its Start button can't be pressed.
        Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}

private const val SAMPLE_PROTEIN_G = 96

private fun sampleTodayState(date: LocalDate): TodayUiState {
    val template = ProgramCatalog.UPPER_1
    return TodayUiState(
        isLoading = false,
        date = date,
        level = 7,
        main = DayUi(template, lastCompleted = null, isInProgress = false),
        exercises = template.exercises.take(4).mapIndexed { index, entry ->
            ExerciseLineUi(entry.exercise.id, exerciseNameText(entry.exercise), limitBreak = index == 0)
        },
        weekDone = 2,
        weekTarget = 4,
        streakWeeks = 3,
    )
}

/** Lays the content out at full width and draws it shrunk by [scale], taking only the shrunk space. */
private fun Modifier.scaled(scale: Float): Modifier = layout { measurable, constraints ->
    val width = (constraints.maxWidth / scale).roundToInt()
    val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width))
    layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}
