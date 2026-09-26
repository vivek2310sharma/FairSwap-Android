package in.fairswap.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import in.fairswap.app.BuildConfig
import in.fairswap.core.network.BackendHealthRepository
import in.fairswap.core.network.FairSwapBackendConfig
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
