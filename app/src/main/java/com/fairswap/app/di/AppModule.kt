package com.fairswap.app.di

import com.fairswap.app.BuildConfig
import com.fairswap.core.auth.AuthRepository
import com.fairswap.core.auth.SupabaseAuthRepository
import com.fairswap.core.account.AccountGateway
import com.fairswap.core.account.AccountRepository
import com.fairswap.core.account.SupabaseAccountGateway
import com.fairswap.core.auth.AuthCallbackConfig
import com.fairswap.core.auth.RecoveryStateStore
import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.core.network.BackendHealthRepository
import com.fairswap.core.network.FairSwapBackendConfig
import com.fairswap.core.network.SupabaseClientFactory
import com.fairswap.core.discovery.DiscoveryDataSource
import com.fairswap.core.discovery.DiscoveryRepository
import com.fairswap.core.discovery.PublicMediaUrlBuilder
import com.fairswap.core.discovery.PublicMarketplaceDataSource
import com.fairswap.core.discovery.PublicMarketplaceGateway
import com.fairswap.core.discovery.PublicMarketplaceRepository
import com.fairswap.core.discovery.SupabasePublicMarketplaceGateway
import com.fairswap.core.favourites.FavouritesGateway
import com.fairswap.core.favourites.FavouritesRepository
import com.fairswap.core.favourites.FavouriteStateStore
import com.fairswap.core.favourites.SupabaseFavouritesGateway
import com.fairswap.core.sellerlistings.SellerListingGateway
import com.fairswap.core.sellerlistings.SellerListingRepository
import com.fairswap.core.sellerlistings.SupabaseSellerListingGateway
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
    fun provideAccountGateway(supabase: SupabaseClient): AccountGateway = SupabaseAccountGateway(supabase)

    @Provides
    @Singleton
    fun provideAccountRepository(gateway: AccountGateway) = AccountRepository(gateway)

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
    fun providePublicMarketplaceGateway(supabase: SupabaseClient): PublicMarketplaceGateway =
        SupabasePublicMarketplaceGateway(supabase)

    @Provides
    @Singleton
    fun providePublicMarketplaceDataSource(
        gateway: PublicMarketplaceGateway,
        mediaUrls: PublicMediaUrlBuilder,
    ): PublicMarketplaceDataSource = PublicMarketplaceRepository(gateway, mediaUrls)

    @Provides
    @Singleton
    fun provideFavouriteStateStore() = FavouriteStateStore()

    @Provides
    @Singleton
    fun provideFavouritesGateway(supabase: SupabaseClient): FavouritesGateway = SupabaseFavouritesGateway(supabase)

    @Provides
    @Singleton
    fun provideFavouritesRepository(
        gateway: FavouritesGateway,
        publicListings: PublicMarketplaceDataSource,
        stateStore: FavouriteStateStore,
    ) = FavouritesRepository(gateway, publicListings, stateStore)

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
    ): AuthRepository = SupabaseAuthRepository(supabase, callbacks, recoveryState)

    @Provides
    @Singleton
    fun provideSellerSessionOwner(): com.fairswap.core.sellerlistings.SellerSessionOwner =
        com.fairswap.core.sellerlistings.SellerSessionOwner()

    @Provides
    @Singleton
    fun provideSellerListingGateway(supabase: SupabaseClient): SellerListingGateway =
        SupabaseSellerListingGateway(supabase)

    @Provides
    @Singleton
    fun provideSellerListingRepository(
        gateway: SellerListingGateway,
        sessionOwner: com.fairswap.core.sellerlistings.SellerSessionOwner,
    ): SellerListingRepository =
        SellerListingRepository(gateway, sessionOwner)

    @Provides
    @Singleton
    fun provideSellerMediaConfig(): com.fairswap.core.sellerlistings.SellerMediaConfig =
        com.fairswap.core.sellerlistings.SellerMediaConfig(
            webBaseUrl = BuildConfig.FAIRSWAP_WEB_BASE_URL,
        )

    @Provides
    @Singleton
    fun provideMediaHttpClient(): io.ktor.client.HttpClient =
        io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            followRedirects = false
        }

    @Provides
    @Singleton
    fun provideSellerMediaGateway(
        config: com.fairswap.core.sellerlistings.SellerMediaConfig,
        supabase: SupabaseClient,
        httpClient: io.ktor.client.HttpClient,
    ): com.fairswap.core.sellerlistings.SellerMediaGateway =
        com.fairswap.core.sellerlistings.KtorSellerMediaGateway(config, supabase, httpClient)

    @Provides
    @Singleton
    fun provideSellerMediaRepository(
        gateway: com.fairswap.core.sellerlistings.SellerMediaGateway,
        listingRepository: SellerListingRepository,
    ): com.fairswap.core.sellerlistings.SellerMediaRepository =
        com.fairswap.core.sellerlistings.SellerMediaRepository(gateway, listingRepository)
}
