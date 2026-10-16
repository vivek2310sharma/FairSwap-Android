package com.fairswap.core.communication

import io.github.jan.supabase.SupabaseClient

object CommunicationCoreFactory {
    fun createRepository(
        supabase: SupabaseClient,
        sessionOwner: CommunicationSessionOwner,
    ): CommunicationRepository = DefaultCommunicationRepository(
        gateway = SupabaseCommunicationGateway(supabase),
        realtime = SupabaseCommunicationRealtime(supabase),
        sessionOwner = sessionOwner,
    )
}
