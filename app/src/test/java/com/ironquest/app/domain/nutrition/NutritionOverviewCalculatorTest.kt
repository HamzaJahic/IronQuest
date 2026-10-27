package com.ironquest.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionOverviewCalculatorTest {

    /** 8 Oct 2026 (a Thursday) is "today" in every test. */
    private val today = d(8)

    private fun d(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    private fun history(
        from: LocalDate,
        to: LocalDate,
        totals: Map<LocalDate, DayTotals> = emptyMap(),
        rows: Map<LocalDate, DayRow> = emptyMap(),
        live: MacroTargets? = null,
        supplements: List<SupplementRef> = emptyList(),
        intakes: Map<Long, Set<Long>> = emptyMap(),
    ) = NutritionHistory(from, to, totals, rows, live, supplements, intakes)

    private fun protein(grams: Double) = DayTotals(Macros(proteinG = grams, kcal = grams * 4), logCount = 1)

    @Test
    fun `future days are flagged and carry no data`() {
        val days = NutritionOverviewCalculator.days(history(d(7), d(10), totals = mapOf(d(7) to protein(10.0))), today)
        assertEquals(listOf(d(7), d(8), d(9), d(10)), days.map { it.date })
        assertEquals(listOf(false, false, true, true), days.map { it.isFuture })
        assertNull(days[2].summary)
        assertEquals(DayMark.NO_DATA, days[2].mark(MacroKind.PROTEIN))
    }

    @Test
    fun `a day with neither row nor logs has no data, even today with a live plan`() {
        val days = NutritionOverviewCalculator.days(history(d(6), d(8), live = MacroTargets(proteinG = 100.0)), today)
        assertTrue(days.all { it.summary == null })
        assertTrue(days.all { it.mark(MacroKind.PROTEIN) == DayMark.NO_DATA })
    }

    @Test
    fun `past days use their snapshot and today the live plan`() {
        val snapshot = DayRow(MacroTargets(proteinG = 200.0), waterMl = 0)
        val days = NutritionOverviewCalculator.days(
            history(
                d(7),
                d(8),
                totals = mapOf(d(7) to protein(100.0), d(8) to protein(100.0)),
                rows = mapOf(d(7) to snapshot, d(8) to snapshot),
                live = MacroTargets(proteinG = 100.0),
            ),
            today,
        )
        assertEquals(200.0, checkNotNull(checkNotNull(days[0].summary).protein.target), 1e-9)
        assertEquals(DayMark.UNDER, days[0].mark(MacroKind.PROTEIN))
        assertEquals(100.0, checkNotNull(checkNotNull(days[1].summary).protein.target), 1e-9)
        assertEquals(DayMark.ON_TARGET, days[1].mark(MacroKind.PROTEIN))
    }

    @Test
    fun `a row without logs counts as under but not as logged`() {
        val days = NutritionOverviewCalculator.days(history(d(7), d(7), rows = mapOf(d(7) to DayRow(MacroTargets(proteinG = 100.0), 500))), today)
        val day = days.single()
        assertEquals(DayMark.UNDER, day.mark(MacroKind.PROTEIN))
        assertFalse(day.isLogged)
        assertEquals(500, day.waterMl)
    }

    @Test
    fun `a logged day without a target is marked no target`() {
        val days = NutritionOverviewCalculator.days(history(d(7), d(7), totals = mapOf(d(7) to protein(50.0)), rows = mapOf(d(7) to DayRow(MacroTargets(), 0))), today)
        assertEquals(DayMark.NO_TARGET, days.single().mark(MacroKind.PROTEIN))
        assertEquals(DayMark.NO_TARGET, days.single().mark(MacroKind.KCAL))
    }

    @Test
    fun `averages and the on-target count use logged days only`() {
        val target = DayRow(MacroTargets(proteinG = 100.0), 0)
        val history = history(
            d(5),
            d(7),
            totals = mapOf(
                d(5) to DayTotals(Macros(100.0, 200.0, 50.0, 20.0, kcal = 1650.0), 2),
                d(6) to DayTotals(Macros(50.0, 100.0, 30.0, 10.0, kcal = 870.0), 1),
            ),
            rows = mapOf(d(5) to target, d(6) to target, d(7) to DayRow(MacroTargets(proteinG = 100.0), 750)),
        )
        val stats = NutritionOverviewCalculator.stats(NutritionOverviewCalculator.days(history, today), history, MacroKind.PROTEIN, today)
        assertEquals(2, stats.loggedDays)
        assertEquals(1, stats.onTargetDays)
        val avg = checkNotNull(stats.average)
        assertEquals(75.0, avg.proteinG, 1e-9)
        assertEquals(150.0, avg.carbsG, 1e-9)
        assertEquals(40.0, avg.fatG, 1e-9)
        assertEquals(15.0, avg.fiberG, 1e-9)
        assertEquals(1260.0, avg.effectiveKcal, 1e-9)
    }

    @Test
    fun `average water ignores days without water and is null when there is none`() {
        val none = MacroTargets()
        val history = history(d(5), d(7), rows = mapOf(d(5) to DayRow(none, 2000), d(6) to DayRow(none, 0), d(7) to DayRow(none, 3000)))
        assertEquals(2500, NutritionOverviewCalculator.stats(NutritionOverviewCalculator.days(history, today), history, MacroKind.PROTEIN, today).avgWaterMl)

        val empty = history(d(5), d(7))
        val stats = NutritionOverviewCalculator.stats(NutritionOverviewCalculator.days(empty, today), empty, MacroKind.PROTEIN, today)
        assertNull(stats.avgWaterMl)
        assertNull(stats.average)
        assertEquals(0, stats.loggedDays)
    }

    @Test
    fun `adherence counts elapsed days and the streak ends at the last visible day`() {
        val creatine = SupplementRef(1, "Creatine")
        val intakes = mapOf(1L to setOf(d(3), d(4), d(5), d(7), d(8)).map { it.toEpochDay() }.toSet())

        // Current week Mon 5 – Sun 11: 4 elapsed days (5..8), taken on 5, 7, 8; streak 8, 7 → 2.
        val week = history(d(5), d(11), supplements = listOf(creatine), intakes = intakes)
        val current = NutritionOverviewCalculator.stats(NutritionOverviewCalculator.days(week, today), week, MacroKind.PROTEIN, today).supplements.single()
        assertEquals(SupplementAdherence(1, "Creatine", takenDays = 3, ofDays = 4, streak = 2), current)

        // A past range is anchored at its own last day (4 Oct): run 4, 3 → 2.
        val past = history(d(1), d(4), supplements = listOf(creatine), intakes = intakes)
        val earlier = NutritionOverviewCalculator.stats(NutritionOverviewCalculator.days(past, today), past, MacroKind.PROTEIN, today).supplements.single()
        assertEquals(SupplementAdherence(1, "Creatine", takenDays = 2, ofDays = 4, streak = 2), earlier)
    }

    @Test
    fun `adherence counts only days since the supplement was added`() {
        // Added Wednesday 7 Oct, taken 7 and 8: Mon 5 and Tue 6 are not "missed".
        val fishOil = SupplementRef(2, "Fish oil", createdAtEpochDay = d(7).toEpochDay())
        val intakes = mapOf(2L to setOf(d(7), d(8)).map { it.toEpochDay() }.toSet())
        val week = history(d(5), d(11), supplements = listOf(fishOil), intakes = intakes)
        val stats = NutritionOverviewCalculator.stats(NutritionOverviewCalculator.days(week, today), week, MacroKind.PROTEIN, today).supplements.single()
        assertEquals(SupplementAdherence(2, "Fish oil", takenDays = 2, ofDays = 2, streak = 2), stats)
    }

    @Test
    fun `a supplement added before the range counts every elapsed day`() {
        val creatine = SupplementRef(1, "Creatine", createdAtEpochDay = d(1).toEpochDay())
        val week = history(d(5), d(11), supplements = listOf(creatine))
        val stats = NutritionOverviewCalculator.stats(NutritionOverviewCalculator.days(week, today), week, MacroKind.PROTEIN, today).supplements.single()
        assertEquals(4, stats.ofDays)
    }

    @Test
    fun `a supplement added after the range has no adherence days`() {
        val later = SupplementRef(3, "Zinc", createdAtEpochDay = d(8).toEpochDay())
        val past = history(d(1), d(4), supplements = listOf(later))
        val stats = NutritionOverviewCalculator.stats(NutritionOverviewCalculator.days(past, today), past, MacroKind.PROTEIN, today).supplements.single()
        assertEquals(0, stats.ofDays)
        assertEquals(0, stats.takenDays)
    }
}
