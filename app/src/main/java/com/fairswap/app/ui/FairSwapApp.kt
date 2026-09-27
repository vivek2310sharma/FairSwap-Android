package com.fairswap.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fairswap.feature.account.AccountScreen
import com.fairswap.feature.account.AccountViewModel
import com.fairswap.feature.account.AccountUiState
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
    val sessionStatus = supabase.auth.sessionStatus.collectAsStateWithLifecycle().value
    val liveSessionUserId = when (val status = sessionStatus) {
        is SessionStatus.Authenticated -> status.session.user?.id
        is SessionStatus.RefreshFailure -> supabase.auth.currentSessionOrNull()?.user?.id
        is SessionStatus.NotAuthenticated, SessionStatus.Initializing -> null
    }
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
                    val identityGate = authState.gate is com.fairswap.core.auth.AuthGate.SignedIn ||
                        authState.gate is com.fairswap.core.auth.AuthGate.PolicyReviewRequired
                    val sessionAligned = !identityGate || accountGateSessionMatches(currentUser, liveSessionUserId)
                    LaunchedEffect(authViewModel, currentUser, authState.gate, authState.isLoading, authState.isInitializing, sessionStatus) {
                        if (authState.isLoading || authState.isInitializing) return@LaunchedEffect
                        val mismatch = when (authState.gate) {
                            is com.fairswap.core.auth.AuthGate.SignedIn,
                            is com.fairswap.core.auth.AuthGate.PolicyReviewRequired -> !accountGateSessionMatches(currentUser, liveSessionUserId)
                            com.fairswap.core.auth.AuthGate.SignedOut -> liveSessionUserId != null
                            else -> false
                        }
                        if (mismatch) authViewModel.refresh()
                    }
                    if (!sessionAligned) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (authState.gate is com.fairswap.core.auth.AuthGate.SignedIn && currentUser != null) {
                        val accountViewModel: AccountViewModel = hiltViewModel()
                        val storedAccountState = accountViewModel.state.collectAsStateWithLifecycle().value
                        val accountState = storedAccountState.takeIf { it.userId == currentUser }
                            ?: AccountUiState(userId = currentUser, loading = true)
                        LaunchedEffect(accountViewModel, currentUser, authState.gate) {
                            accountViewModel.onUserChanged(currentUser)
                            accountViewModel.refresh()
                        }
                        LaunchedEffect(accountViewModel, authViewModel) {
                            accountViewModel.events.collect { event ->
                                when (event) {
                                    com.fairswap.feature.account.AccountEvent.AuthRequired,
                                    com.fairswap.feature.account.AccountEvent.PolicyReviewRequired,
                                    com.fairswap.feature.account.AccountEvent.Restricted -> authViewModel.refresh()
                                }
                            }
                        }
                        AccountScreen(
                            state = accountState,
                            onEdit = accountViewModel::beginEditing,
                            onCancel = accountViewModel::cancelEditing,
                            onFormChange = accountViewModel::updateForm,
                            onSave = accountViewModel::save,
                            onRetry = accountViewModel::retry,
                            onFavourites = { if (backStack.lastOrNull() != FavouritesRoute) backStack.add(FavouritesRoute) },
                            onSignOut = authViewModel::signOut,
                        )
                    } else {
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
                        )
                    }
                }
                else -> error("Unknown navigation key: $key")
            }
        },
    )
}

internal fun accountGateSessionMatches(gateUserId: String?, liveSessionUserId: String?): Boolean =
    gateUserId == liveSessionUserId
