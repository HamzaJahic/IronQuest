package com.ironquest.app.di

import com.ironquest.app.data.repository.OfflineNutritionRepository
import com.ironquest.app.data.repository.RearmingNutritionRepository
import com.ironquest.app.domain.nutrition.ReminderRearmer
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.notifications.ReminderCoordinator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Process-lifetime scope for reminder work (re-arming, receiver goAsync bodies).
 * Explicit FIELD target so `@Inject @ApplicationScope lateinit var` works in receivers.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Everyone injecting NutritionRepository (ViewModels, receivers) gets the decorator, so
     * reminder re-arming follows every relevant write. ReminderCoordinator reads the inner repo.
     */
    @Provides
    @Singleton
    fun provideNutritionRepository(inner: OfflineNutritionRepository, rearmer: ReminderRearmer): NutritionRepository = RearmingNutritionRepository(inner, rearmer)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderBindings {
    @Binds
    abstract fun bindReminderRearmer(impl: ReminderCoordinator): ReminderRearmer
}
