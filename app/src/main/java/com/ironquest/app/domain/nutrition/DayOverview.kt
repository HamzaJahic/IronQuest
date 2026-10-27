package com.ironquest.app.domain.nutrition

import java.time.LocalDate

data class DayOverview(
    val date: LocalDate,
    val isFuture: Boolean,
    val logCount: Int,
    /** Null = future, or no `nutrition_days` row and no logs ("no data"). */
    val summary: DaySummary?,
    val waterMl: Int,
) {
    /** At least one meal logged. Water/supplements alone don't make a logged day. */
    val isLogged: Boolean get() = logCount > 0

    fun mark(kind: MacroKind): DayMark {
        val daySummary = summary ?: return DayMark.NO_DATA
        return when (kind.of(daySummary).status) {
            TargetStatus.UNDER -> DayMark.UNDER
            TargetStatus.ON_TARGET -> DayMark.ON_TARGET
            TargetStatus.OVER -> DayMark.OVER
            TargetStatus.NO_TARGET -> DayMark.NO_TARGET
        }
    }
}
