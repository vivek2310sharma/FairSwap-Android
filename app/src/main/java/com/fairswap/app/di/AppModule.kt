package com.fairswap.app.di

import com.fairswap.app.BuildConfig
import com.fairswap.core.auth.AuthRepository
import com.fairswap.core.auth.AuthCallbackConfig
import com.fairswap.core.auth.RecoveryStateStore
import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.core.network.BackendHealthRepository
import com.fairswap.core.network.FairSwapBackendConfig
import com.fairswap.core.network.SupabaseClientFactory
import com.fairswap.core.discovery.DiscoveryDataSource
import com.fairswap.core.discovery.DiscoveryRepository
import com.fairswap.core.discovery.PublicMediaUrlBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providePublicMediaUrlBuilder() = PublicMediaUrlBuilder(BuildConfig.FAIRSWAP_WEB_BASE_URL)

    @Provides
    @Singleton
    fun provideDiscoveryDataSource(
        supabase: SupabaseClient,
        mediaUrls: PublicMediaUrlBuilder,
    ): DiscoveryDataSource = DiscoveryRepository(supabase, mediaUrls)

    @Provides
    @Singleton
    fun provideAuthCallbackConfig() = AuthCallbackConfig(
        BuildConfig.AUTH_CALLBACK_SCHEME,
        BuildConfig.AUTH_CALLBACK_HOST,
    )

    @Provides
    @Singleton
    fun provideRecoveryStateStore(@ApplicationContext context: Context) = RecoveryStateStore(context)

    @Provides
    @Singleton
    fun provideAuthCallbackCoordinator() = AuthCallbackCoordinator()

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
        callbacks: AuthCallbackConfig,
    ): SupabaseClient = SupabaseClientFactory.create(config, callbacks.scheme, callbacks.host)

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
        callbacks: AuthCallbackConfig,
        recoveryState: RecoveryStateStore,
    ): AuthRepository = AuthRepository(supabase, callbacks, recoveryState)
}
