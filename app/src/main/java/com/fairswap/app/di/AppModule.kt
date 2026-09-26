package com.fairswap.app.di

import com.fairswap.app.BuildConfig
import com.fairswap.core.auth.AuthRepository
import com.fairswap.core.network.BackendHealthRepository
import com.fairswap.core.network.FairSwapBackendConfig
import com.fairswap.core.network.SupabaseClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
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
    fun provideSupabaseClient(
        config: FairSwapBackendConfig,
    ): SupabaseClient = SupabaseClientFactory.create(config)

    @Provides
    @Singleton
    fun provideBackendHealthRepository(
        config: FairSwapBackendConfig,
        supabase: SupabaseClient,
    ): BackendHealthRepository = BackendHealthRepository(config, supabase)

    @Provides
    @Singleton
    fun provideAuthRepository(
        supabase: SupabaseClient,
    ): AuthRepository = AuthRepository(supabase)
}
