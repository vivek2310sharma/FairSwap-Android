package com.fairswap.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FairSwapBackendConfigTest {
    @Test
    fun configured_requiresHttpsUrlAndPublishableKey() {
        assertTrue(
            FairSwapBackendConfig(
                environmentName = "staging",
                supabaseUrl = "https://example.supabase.co",
                publishableKey = "publishable-key",
            ).isConfigured
        )

        assertFalse(
            FairSwapBackendConfig(
                environmentName = "staging",
                supabaseUrl = "https://example.supabase.co",
                publishableKey = "",
            ).isConfigured
        )
    }
}
