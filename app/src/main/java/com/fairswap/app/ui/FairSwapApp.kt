package com.fairswap.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.fairswap.feature.auth.AuthScreen
import com.fairswap.feature.auth.AuthViewModel
import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.feature.discovery.DiscoveryScreen
import com.fairswap.feature.discovery.DiscoveryViewModel
import com.fairswap.feature.listingdetail.ListingDetailScreen
import com.fairswap.feature.listingdetail.ListingDetailViewModel
import com.fairswap.feature.sellerprofile.SellerProfileScreen
import com.fairswap.feature.sellerprofile.SellerProfileViewModel
import kotlinx.serialization.Serializable

@Serializable
private data object AuthRoute : NavKey

@Serializable
private data object DiscoveryRoute : NavKey

@Serializable
data class ListingDetailRoute(val listingId: String) : NavKey

@Serializable
data class SellerProfileRoute(val sellerId: String) : NavKey

@Composable
fun FairSwapApp(callbacks: AuthCallbackCoordinator) {
    val backStack = rememberNavBackStack(DiscoveryRoute)
    val pendingNavigation = callbacks.pendingNavigation.collectAsStateWithLifecycle().value

    LaunchedEffect(pendingNavigation) {
        pendingNavigation?.let { sequence ->
            if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
            callbacks.acknowledgeNavigation(sequence)
        }
    }

    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                DiscoveryRoute -> NavEntry(key) {
                    val discoveryViewModel: DiscoveryViewModel = hiltViewModel()
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
                        onListingSelected = { id -> backStack.add(ListingDetailRoute(id)) },
                    )
                }
                is ListingDetailRoute -> NavEntry(key) {
                    val viewModel: ListingDetailViewModel = hiltViewModel()
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    LaunchedEffect(key.listingId) { viewModel.load(key.listingId) }
                    ListingDetailScreen(
                        state = state,
                        onBack = { backStack.removeLastOrNull() },
                        onRetry = viewModel::retry,
                        onSeller = { sellerId -> backStack.add(SellerProfileRoute(sellerId)) },
                    )
                }
                is SellerProfileRoute -> NavEntry(key) {
                    val viewModel: SellerProfileViewModel = hiltViewModel()
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    LaunchedEffect(key.sellerId) { viewModel.load(key.sellerId) }
                    SellerProfileScreen(
                        state = state,
                        onBack = { backStack.removeLastOrNull() },
                        onRetry = viewModel::retry,
                        onListing = { listingId -> backStack.add(ListingDetailRoute(listingId)) },
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
