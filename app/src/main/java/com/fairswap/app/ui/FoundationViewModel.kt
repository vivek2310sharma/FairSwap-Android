package com.fairswap.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.network.BackendHealth
import com.fairswap.core.network.BackendHealthRepository
import com.fairswap.core.network.FairSwapBackendConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class FoundationViewModel @Inject constructor(
    private val backendHealthRepository: BackendHealthRepository,
    backendConfig: FairSwapBackendConfig,
) : ViewModel() {
    var uiState by mutableStateOf(
        FoundationUiState(
            environmentName = backendConfig.environmentName,
            backendUrl = backendConfig.supabaseUrl,
            isConfigured = backendConfig.isConfigured,
        )
    )
        private set

    fun checkConnection() {
        if (uiState.isChecking) return

        viewModelScope.launch {
            uiState = uiState.copy(isChecking = true, health = null)
            val result = backendHealthRepository.check()
            uiState = uiState.copy(isChecking = false, health = result)
        }
    }
}

data class FoundationUiState(
    val environmentName: String,
    val backendUrl: String,
    val isConfigured: Boolean,
    val isChecking: Boolean = false,
    val health: BackendHealth? = null,
)
