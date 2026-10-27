package com.ironquest.app.di

import com.ironquest.app.BuildConfig
import com.ironquest.app.data.food.AssetBasicFoodSource
import com.ironquest.app.data.off.OffClient
import com.ironquest.app.domain.repository.BasicFoodSource
import com.ironquest.app.domain.repository.OnlineFoodSearch
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FoodModule {
    @Binds
    abstract fun bindBasicFoodSource(impl: AssetBasicFoodSource): BasicFoodSource

    companion object {
        /** Singleton so the in-memory query cache survives between visits. */
        @Provides
        @Singleton
        fun provideOnlineFoodSearch(): OnlineFoodSearch =
            OffClient(userAgent = "IronQuest/${BuildConfig.VERSION_NAME} (Android)") // not UI text: HTTP User-Agent
    }
}
