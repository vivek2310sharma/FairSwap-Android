package com.fairswap.app.di

import com.fairswap.app.BuildConfig
import com.fairswap.core.network.BackendHealthRepository
import com.fairswap.core.network.FairSwapBackendConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideBackendConfig(): FairSwapBackendConfig = FairSwapBackendConfig(
        environmentName = BuildConfig.ENVIRONMENT_NAME,
        supabaseUrl = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    )

    @Provides
    @Singleton
    fun provideBackendHealthRepository(
        config: FairSwapBackendConfig,
    ): BackendHealthRepository = BackendHealthRepository(config)
}
