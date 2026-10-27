package com.ironquest.app.data.repository

import com.ironquest.app.domain.nutrition.DeleteOutcome
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.nutrition.PlanDraft
import com.ironquest.app.domain.nutrition.ReminderRearmer
import com.ironquest.app.domain.nutrition.SavePlanResult
import com.ironquest.app.domain.nutrition.SupplementDraft
import com.ironquest.app.domain.repository.NutritionRepository
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/**
 * Asks for a reminder re-arm after every successful write that can change what fires next.
 * Reads and every other method (including any added to [NutritionRepository] later) pass straight through via delegation, so ViewModels and
 * notification receivers never need to know about reminders.
 */
class RearmingNutritionRepository(
    private val inner: NutritionRepository,
    private val rearmer: ReminderRearmer,
) : NutritionRepository by inner {

    override suspend fun savePlan(draft: PlanDraft): SavePlanResult = rearmAfter { inner.savePlan(draft) }

    override suspend fun setActivePlan(id: Long?): NutritionAward = rearmAfter { inner.setActivePlan(id) }

    override suspend fun deletePlan(id: Long): NutritionAward = rearmAfter { inner.deletePlan(id) }

    override suspend fun logSlot(date: LocalDate, slotId: Long, portion: Double): NutritionAward = rearmAfter { inner.logSlot(date, slotId, portion) }

    override suspend fun removeLog(logId: Long): NutritionAward = rearmAfter { inner.removeLog(logId) }

    override suspend fun setSupplementTaken(date: LocalDate, supplementId: Long, taken: Boolean): NutritionAward = rearmAfter { inner.setSupplementTaken(date, supplementId, taken) }

    override suspend fun saveSupplement(draft: SupplementDraft): Long = rearmAfter { inner.saveSupplement(draft) }

    override suspend fun deleteSupplement(id: Long): DeleteOutcome = rearmAfter { inner.deleteSupplement(id) }

    /**
     * Re-arms after [block] returns, and also when it is cancelled: a cancelled caller can
     * still have committed the write (withTransaction resumes into the cancellation), and a
     * re-arm after a write that didn't commit is harmless. Other failures don't re-arm.
     */
    private inline fun <T> rearmAfter(block: () -> T): T {
        val result = try {
            block()
        } catch (cancellation: CancellationException) {
            rearm()
            throw cancellation
        }
        rearm()
        return result
    }

    /** The port promises not to throw; this guard keeps a broken scheduler from failing a committed write. */
    private fun rearm() {
        runCatching { rearmer.requestRearm() }
    }
}
