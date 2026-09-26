package in.fairswap.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClientFactory {
    fun create(config: FairSwapBackendConfig): SupabaseClient {
        require(config.isConfigured) {
            "Supabase client configuration is incomplete for ${config.environmentName}."
        }

        return createSupabaseClient(
            supabaseUrl = config.supabaseUrl,
            supabaseKey = config.publishableKey,
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Functions)
            install(Storage)
        }
    }
}
