package com.ironquest.app.data.local.entity

/** Projection for `NutritionDao.slotLogKeys` (the query filters out null slot ids). */
data class SlotLogKeyRow(val dateEpochDay: Long, val slotId: Long)
