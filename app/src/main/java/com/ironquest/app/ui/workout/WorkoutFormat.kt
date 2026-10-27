package com.ironquest.app.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ironquest.app.R
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.WorkoutBlock
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText

/** "8–12". */
@Composable
internal fun rangeText(range: RepRange): String = stringResource(R.string.common_range, range.min, range.max)

/** "A2": the superset letter and the member's position. */
@Composable
internal fun supersetMember(block: WorkoutBlock.Superset, member: Int): String = stringResource(R.string.workout_superset_member, block.letter, member + 1)

/** A weight for reading: 82.5 → "82.5" / "82,5". */
@Composable
internal fun weightText(kg: Double): String = Formats.decimal(kg, currentLocale(), maxDecimals = 3)

/** Editable weight text (typed with "." or ",") shown in the locale's own style; unreadable text stays as typed. */
@Composable
internal fun weightText(editable: String): String = editable.replace(',', '.').toDoubleOrNull()?.let { weightText(it) } ?: editable

/** "82.5 × 7" / "8 reps" (bodyweight) / "30 min". */
@Composable
internal fun setText(set: LoggedSet, cardio: Boolean): String = when {
    cardio -> stringResource(R.string.unit_min_value, set.durationMinutes ?: 0)
    set.weightKg <= 0.0 -> pluralStringResource(R.plurals.workout_set_reps_count, set.reps, set.reps)
    else -> stringResource(R.string.workout_set_weight_reps, weightText(set.weightKg), set.reps)
}

/** 52 min → "52 min", 75 min → "1 h 15 min", under a minute → "< 1 min". */
internal fun durationText(millis: Long): UiText {
    val minutes = (millis / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> uiText(R.string.workout_duration_under_minute)
        minutes < 60 -> uiText(R.string.unit_min_value, minutes)
        else -> uiText(R.string.workout_duration_hours_minutes, minutes / 60, minutes % 60)
    }
}
