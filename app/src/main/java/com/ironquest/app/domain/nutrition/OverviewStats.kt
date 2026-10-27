package com.ironquest.app.domain.nutrition

/** "Creatine 6/7" in Week, "Creatine streak 23" in Month. */
data class SupplementAdherence(val id: Long, val name: String, val takenDays: Int, val ofDays: Int, val streak: Int)

data class OverviewStats(
    val loggedDays: Int,
    /** Logged days whose chosen macro is within ±10 % of its target. */
    val onTargetDays: Int,
    /** Mean of the logged days; null if none. */
    val average: Macros?,
    /** Mean over days with water > 0; null if none. */
    val avgWaterMl: Int?,
    val supplements: List<SupplementAdherence>,
)
