package com.fairswap.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.fairswap.feature.auth.AuthScreen
import com.fairswap.feature.auth.AuthViewModel
import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.feature.discovery.DiscoveryScreen
import com.fairswap.feature.discovery.DiscoveryViewModel
import kotlinx.serialization.Serializable

@Serializable
private data object AuthRoute : NavKey

@Serializable
private data object DiscoveryRoute : NavKey

@Composable
fun FairSwapApp(callbacks: AuthCallbackCoordinator) {
    val backStack = rememberNavBackStack(DiscoveryRoute)
    val pendingNavigation = callbacks.pendingNavigation.collectAsStateWithLifecycle().value
    val discoveryViewModel: DiscoveryViewModel = hiltViewModel()

    LaunchedEffect(pendingNavigation) {
        pendingNavigation?.let { sequence ->
            if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
            callbacks.acknowledgeNavigation(sequence)
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                DiscoveryRoute -> NavEntry(key) {
                    val discoveryState = discoveryViewModel.state.collectAsStateWithLifecycle().value
                    DiscoveryScreen(
                        state = discoveryState,
                        onEdit = discoveryViewModel::editFilters,
                        onCitySelected = discoveryViewModel::selectCity,
                        onApply = discoveryViewModel::applyFilters,
                        onClear = discoveryViewModel::clearFilters,
                        onRefresh = discoveryViewModel::refresh,
                        onLoadMore = discoveryViewModel::loadMore,
                        onRetryMetadata = discoveryViewModel::retryMetadata,
                        onAccount = { if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute) },
                    )
                }
                AuthRoute -> NavEntry(key) {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    AuthScreen(
                        uiState = authViewModel.uiState,
                        onBrowse = { backStack.removeLastOrNull() },
                        onShowSignIn = authViewModel::showSignIn,
                        onShowSignUp = authViewModel::showSignUp,
                        onShowForgotPassword = authViewModel::showForgotPassword,
                        onSignIn = authViewModel::signIn,
                        onSignUp = authViewModel::signUp,
                        onRequestPasswordReset = authViewModel::requestPasswordReset,
                        onSetNewPassword = authViewModel::setNewPassword,
                        onReacceptPolicies = authViewModel::reacceptPolicies,
                        onSignOut = authViewModel::signOut,
                    )
                }
                else -> error("Unknown navigation key: $key")
            }
        },
    )
}
