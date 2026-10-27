package com.ironquest.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class DaySummaryCalculatorTest {

    private fun log(id: Long, protein: Double, portion: Double = 1.0) = MealLogEntry(id, null, null, "m$id", portion, Macros(proteinG = protein, carbsG = 10.0))

    @Test
    fun `status boundaries are inclusive at plus-minus 10 percent`() {
        assertEquals(TargetStatus.ON_TARGET, DaySummaryCalculator.statusOf(162.0, 180.0))
        assertEquals(TargetStatus.UNDER, DaySummaryCalculator.statusOf(161.9, 180.0))
        assertEquals(TargetStatus.ON_TARGET, DaySummaryCalculator.statusOf(198.0, 180.0))
        assertEquals(TargetStatus.OVER, DaySummaryCalculator.statusOf(198.1, 180.0))
    }

    @Test
    fun `missing or zero target means no target`() {
        assertEquals(TargetStatus.NO_TARGET, DaySummaryCalculator.statusOf(50.0, null))
        assertEquals(TargetStatus.NO_TARGET, DaySummaryCalculator.statusOf(50.0, 0.0))
    }

    @Test
    fun `sums portions and compares to targets`() {
        val summary = DaySummaryCalculator.summarize(
            logs = listOf(log(1, 40.0), log(2, 40.0, portion = 1.5)),
            targets = MacroTargets(proteinG = 100.0),
        )
        assertEquals(100.0, summary.protein.eaten, 1e-9)
        assertEquals(TargetStatus.ON_TARGET, summary.protein.status)
        assertEquals(1f, summary.protein.fraction, 0f)
        assertEquals(TargetStatus.NO_TARGET, summary.carbs.status)
        assertEquals(25.0, summary.carbs.eaten, 1e-9)
    }

    @Test
    fun `no logs is all zero`() {
        val summary = DaySummaryCalculator.summarize(emptyList(), null)
        assertEquals(0.0, summary.eaten.effectiveKcal, 0.0)
        assertEquals(0f, summary.protein.fraction, 0f)
    }

    @Test
    fun `pre-summed totals give the same summary as the logs`() {
        val logs = listOf(log(1, 40.0), log(2, 40.0, portion = 1.5))
        val targets = MacroTargets(proteinG = 100.0)
        val total = logs.fold(Macros.ZERO) { acc, l -> acc + l.eaten }
        assertEquals(DaySummaryCalculator.summarize(logs, targets), DaySummaryCalculator.summarize(total, targets))
    }
}
