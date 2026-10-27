package com.ironquest.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetResolutionTest {
    private val plan = MacroTargets(proteinG = 160.0, waterMl = 3000)
    private val own = MacroTargets(proteinG = 120.0, kcal = 2200.0)

    @Test
    fun `on the fly always uses your own targets`() {
        assertEquals(own, TargetResolution.resolve(FoodTrackingMode.ON_THE_FLY, plan, own))
    }

    @Test
    fun `plan modes use the active plan, else your own`() {
        assertEquals(plan, TargetResolution.resolve(FoodTrackingMode.PLAN, plan, own))
        assertEquals(plan, TargetResolution.resolve(FoodTrackingMode.BOTH, plan, own))
        assertEquals(own, TargetResolution.resolve(FoodTrackingMode.BOTH, planTargets = null, own))
    }

    @Test
    fun `empty own targets count as none`() {
        assertNull(TargetResolution.resolve(FoodTrackingMode.ON_THE_FLY, plan, MacroTargets()))
        assertNull(TargetResolution.resolve(FoodTrackingMode.PLAN, planTargets = null, MacroTargets()))
    }

    @Test
    fun `modes say what they show`() {
        assertEquals(listOf(true, false, true), FoodTrackingMode.entries.map { it.showsPlan })
        assertEquals(listOf(false, true, true), FoodTrackingMode.entries.map { it.showsBuilder })
    }
}
