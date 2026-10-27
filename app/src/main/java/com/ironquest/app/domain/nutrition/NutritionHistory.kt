package com.ironquest.app.domain.nutrition

import java.time.LocalDate

/** Sum of one day's meal logs: portions applied, kcal resolved. */
data class DayTotals(val eaten: Macros, val logCount: Int)

/** A stored `nutrition_days` row: its target snapshot and water. */
data class DayRow(val targets: MacroTargets, val waterMl: Int)

/**
 * [createdAtEpochDay] is when the supplement was added; null for supplements that existed before
 * v3 and were never taken (adherence then counts from the range start).
 */
data class SupplementRef(val id: Long, val name: String, val createdAtEpochDay: Long? = null)

/**
 * Raw nutrition data for the inclusive range [from]..[to], as stored. Only days with meal logs
 * appear in [totals] and only days with a `nutrition_days` row appear in [rows].
 * [NutritionOverviewCalculator] turns this into per-day overviews.
 */
data class NutritionHistory(
    val from: LocalDate,
    val to: LocalDate,
    val totals: Map<LocalDate, DayTotals>,
    val rows: Map<LocalDate, DayRow>,
    /** The active plan's targets right now (what "today" uses); null without an active plan. */
    val liveTargets: MacroTargets?,
    /** Non-archived supplements, in list order. */
    val supplements: List<SupplementRef>,
    /** supplementId → epochDays of every intake on or before [to]. Streaks need the run before [from]. */
    val intakeEpochDays: Map<Long, Set<Long>>,
)
