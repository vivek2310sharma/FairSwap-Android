package in.fairswap.app

import in.fairswap.app.ui.FoundationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FoundationUiStateTest {
    @Test
    fun initialState_isIdle() {
        val state = FoundationUiState(
            environmentName = "staging",
            backendUrl = "https://example.supabase.co",
            isConfigured = false,
        )

        assertEquals("staging", state.environmentName)
        assertFalse(state.isChecking)
    }
}
