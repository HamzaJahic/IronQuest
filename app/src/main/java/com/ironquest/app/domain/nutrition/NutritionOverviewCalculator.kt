package com.ironquest.app.domain.nutrition

import java.time.LocalDate
import kotlin.math.roundToInt

/** Pure rules behind the Week and Month views. */
object NutritionOverviewCalculator {

    /** One entry per date in the range, in order. */
    fun days(history: NutritionHistory, today: LocalDate): List<DayOverview> {
        val result = mutableListOf<DayOverview>()
        var date = history.from
        while (!date.isAfter(history.to)) {
            result += overview(date, history, today)
            date = date.plusDays(1)
        }
        return result
    }

    private fun overview(date: LocalDate, history: NutritionHistory, today: LocalDate): DayOverview {
        if (date.isAfter(today)) return DayOverview(date, isFuture = true, logCount = 0, summary = null, waterMl = 0)
        val totals = history.totals[date]
        val row = history.rows[date]
        if (totals == null && row == null) return DayOverview(date, isFuture = false, logCount = 0, summary = null, waterMl = 0)
        // Same rule as observeDay: today reads the live plan, past days their snapshot.
        val targets = if (date == today) history.liveTargets else row?.targets
        return DayOverview(
            date = date,
            isFuture = false,
            logCount = totals?.logCount ?: 0,
            summary = DaySummaryCalculator.summarize(totals?.eaten ?: Macros.ZERO, targets),
            waterMl = row?.waterMl ?: 0,
        )
    }

    fun stats(days: List<DayOverview>, history: NutritionHistory, kind: MacroKind, today: LocalDate): OverviewStats {
        val logged = days.filter { it.isLogged }
        val average = if (logged.isEmpty()) {
            null
        } else {
            val total = logged.map { checkNotNull(it.summary) { "a logged day always has a summary" }.eaten }.reduce { sum, eaten -> sum + eaten }
            val dayCount = logged.size.toDouble()
            Macros(total.proteinG / dayCount, total.carbsG / dayCount, total.fatG / dayCount, total.fiberG / dayCount, kcal = total.effectiveKcal / dayCount)
        }
        val watered = days.filter { it.waterMl > 0 }
        val avgWater = if (watered.isEmpty()) null else (watered.sumOf { it.waterMl }.toDouble() / watered.size).roundToInt()

        val elapsed = days.filterNot { it.isFuture }.map { it.date.toEpochDay() }.toSet()
        val anchor = minOf(history.to, today).toEpochDay()
        val supplements = history.supplements.map { supplement ->
            val taken = history.intakeEpochDays[supplement.id].orEmpty()
            // Days before the supplement was added are not "missed".
            val counted = supplement.createdAtEpochDay?.let { created -> elapsed.filter { it >= created }.toSet() } ?: elapsed
            SupplementAdherence(
                id = supplement.id,
                name = supplement.name,
                takenDays = taken.count { it in counted },
                ofDays = counted.size,
                streak = StreakCalculator.currentStreak(taken, anchor),
            )
        }
        return OverviewStats(
            loggedDays = logged.size,
            onTargetDays = logged.count { it.mark(kind) == DayMark.ON_TARGET },
            average = average,
            avgWaterMl = avgWater,
            supplements = supplements,
        )
    }
}
