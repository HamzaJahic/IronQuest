package com.ironquest.app.data.repository

import androidx.room.withTransaction
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.domain.nutrition.ReminderRearmer
import com.ironquest.app.domain.repository.DataResetRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineDataResetRepository @Inject constructor(
    private val db: IronQuestDatabase,
    private val rearmer: ReminderRearmer,
) : DataResetRepository {

    override suspend fun wipeLoggedData() {
        db.withTransaction { db.wipeDao().wipeLoggedData() }
        // Nothing is logged any more, so today's reminders are due again.
        runCatching { rearmer.requestRearm() }
    }
}
