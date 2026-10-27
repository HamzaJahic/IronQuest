package com.ironquest.app.di

import android.content.Context
import androidx.room.Room
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.MIGRATION_1_2
import com.ironquest.app.data.local.MIGRATION_2_3
import com.ironquest.app.data.local.MIGRATION_3_4
import com.ironquest.app.data.local.MIGRATION_4_5
import com.ironquest.app.data.local.MIGRATION_5_6
import com.ironquest.app.data.local.SeedCallback
import com.ironquest.app.data.repository.OfflineFoodRepository
import com.ironquest.app.data.repository.OfflineProgramRepository
import com.ironquest.app.data.repository.OfflineWorkoutRepository
import com.ironquest.app.data.settings.PrefsAppSettingsRepository
import com.ironquest.app.data.settings.PrefsOnboardingRepository
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.FoodRepository
import com.ironquest.app.domain.repository.OnboardingRepository
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IronQuestDatabase = Room.databaseBuilder(context, IronQuestDatabase::class.java, IronQuestDatabase.NAME)
        .addCallback(SeedCallback(context))
        // Every version bump needs a Migration here and a MigrationTest case
        // against the exported schemas in app/schemas.
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .build()

    /** Injected so tests can pin "today" (calendar dates, workout timestamps). */
    @Provides
    @Singleton
    fun provideClock(): Clock = LiveZoneClock
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindWorkoutRepository(impl: OfflineWorkoutRepository): WorkoutRepository

    @Binds
    abstract fun bindDataResetRepository(impl: com.ironquest.app.data.repository.OfflineDataResetRepository): com.ironquest.app.domain.repository.DataResetRepository

    @Binds
    abstract fun bindBackupRepository(impl: com.ironquest.app.data.backup.OfflineBackupRepository): com.ironquest.app.domain.repository.BackupRepository

    @Binds
    abstract fun bindProgramRepository(impl: OfflineProgramRepository): ProgramRepository

    @Binds
    abstract fun bindAppSettingsRepository(impl: PrefsAppSettingsRepository): AppSettingsRepository

    @Binds
    abstract fun bindFoodRepository(impl: OfflineFoodRepository): FoodRepository

    @Binds
    abstract fun bindOnboardingRepository(impl: PrefsOnboardingRepository): OnboardingRepository
}
