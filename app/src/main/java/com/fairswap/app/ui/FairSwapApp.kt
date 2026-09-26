package com.fairswap.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.fairswap.core.favourites.FavouriteStateStore
import com.fairswap.core.favourites.FavouritesRepository
import com.fairswap.core.favourites.FavouriteMutationResult
import com.fairswap.core.favourites.FavouriteValue
import com.fairswap.feature.favourites.FavouritesScreen
import com.fairswap.feature.favourites.FavouritesViewModel
import com.fairswap.feature.auth.AuthScreen
import com.fairswap.feature.auth.AuthViewModel
import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.feature.discovery.DiscoveryScreen
import com.fairswap.feature.discovery.DiscoveryViewModel
import com.fairswap.feature.listingdetail.ListingDetailScreen
import com.fairswap.feature.listingdetail.ListingDetailViewModel
import com.fairswap.feature.sellerprofile.SellerProfileScreen
import com.fairswap.feature.sellerprofile.SellerProfileViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.serialization.Serializable

@Serializable
private data object AuthRoute : NavKey

@Serializable
private data object DiscoveryRoute : NavKey

@Serializable
data class ListingDetailRoute(val listingId: String) : NavKey

@Serializable
data class SellerProfileRoute(val sellerId: String) : NavKey

@Serializable
data object FavouritesRoute : NavKey

@Composable
fun FairSwapApp(
    callbacks: AuthCallbackCoordinator,
    favouritesRepository: FavouritesRepository,
    favouriteStore: FavouriteStateStore,
    supabase: SupabaseClient,
) {
    val backStack = rememberNavBackStack(DiscoveryRoute)
    val pendingNavigation = callbacks.pendingNavigation.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    val favouriteSnapshot = favouriteStore.state.collectAsStateWithLifecycle().value
    var favouriteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(supabase) {
        supabase.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> favouriteStore.reconcile(status.session.user?.id)
                is SessionStatus.NotAuthenticated, SessionStatus.Initializing -> favouriteStore.reconcile(null)
                is SessionStatus.RefreshFailure -> Unit
            }
        }
    }

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
                    val visibleIds = discoveryState.results.items.map { it.id }
                    LaunchedEffect(visibleIds, favouriteSnapshot.userId, favouriteSnapshot.generation) { favouritesRepository.lookup(visibleIds) }
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
                        favouriteValues = favouriteSnapshot.values,
                        mutatingIds = favouriteSnapshot.mutating,
                        onFavourite = { id, desired ->
                            scope.launch {
                                when (val result = favouritesRepository.setFavourite(id, desired)) {
                                    FavouriteMutationResult.AuthRequired -> { favouriteError = null; if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute) }
                                    FavouriteMutationResult.PolicyReviewRequired -> { favouriteError = null; if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute) }
                                    is FavouriteMutationResult.Updated -> favouriteError = null
                                    else -> favouriteError = "Unable to update saved favourite. Retry."
                                }
                            }
                        },
                        favouriteError = favouriteError,
                    )
                }
                is ListingDetailRoute -> NavEntry(key) {
                    val viewModel: ListingDetailViewModel = hiltViewModel()
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    LaunchedEffect(key.listingId, favouriteSnapshot.userId, favouriteSnapshot.generation) { viewModel.syncFavourite(key.listingId) }
                    LaunchedEffect(key.listingId) { viewModel.load(key.listingId) }
                    ListingDetailScreen(
                        state = state,
                        onBack = { backStack.removeLastOrNull() },
                        onRetry = viewModel::retry,
                        onSeller = { sellerId -> backStack.add(SellerProfileRoute(sellerId)) },
                        favourite = favouriteSnapshot.values[key.listingId] ?: FavouriteValue.UNKNOWN,
                        favouriteMutating = key.listingId in favouriteSnapshot.mutating,
                        onFavourite = { desired -> scope.launch {
                            when (favouritesRepository.setFavourite(key.listingId, desired)) {
                                FavouriteMutationResult.AuthRequired -> { favouriteError = null; if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute) }
                                FavouriteMutationResult.PolicyReviewRequired -> { favouriteError = null; if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute) }
                                is FavouriteMutationResult.Updated -> favouriteError = null
                                else -> favouriteError = "Unable to update saved favourite. Retry."
                            }
                        } },
                        favouriteError = favouriteError,
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
                FavouritesRoute -> NavEntry(key) {
                    val viewModel: FavouritesViewModel = hiltViewModel()
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    LaunchedEffect(favouriteSnapshot.userId, favouriteSnapshot.generation) { viewModel.sessionChanged() }
                    FavouritesScreen(
                        state = state,
                        onBack = { backStack.removeLastOrNull() },
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        onClearUnavailable = viewModel::clearUnavailable,
                        onBrowse = { backStack.clear(); backStack.add(DiscoveryRoute) },
                        onListing = { id -> backStack.add(ListingDetailRoute(id)) },
                        onAuth = { if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute) },
                    )
                }
                AuthRoute -> NavEntry(key) {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val authState = authViewModel.uiState
                    val currentUser = when (val gate = authState.gate) {
                        is com.fairswap.core.auth.AuthGate.SignedIn -> gate.account.userId
                        is com.fairswap.core.auth.AuthGate.PolicyReviewRequired -> gate.account.userId
                        else -> null
                    }
                    LaunchedEffect(currentUser) {
                        favouriteStore.reconcile(currentUser)
                    }
                    AuthScreen(
                        uiState = authState,
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
                        onFavourites = { if (backStack.lastOrNull() != FavouritesRoute) backStack.add(FavouritesRoute) },
                    )
                }
                else -> error("Unknown navigation key: $key")
            }
        },
    )
}
