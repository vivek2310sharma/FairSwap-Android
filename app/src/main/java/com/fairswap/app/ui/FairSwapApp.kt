package com.fairswap.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.fairswap.core.auth.AuthGate
import com.fairswap.feature.discovery.DiscoveryScreen
import com.fairswap.feature.discovery.DiscoveryViewModel
import com.fairswap.feature.listingdetail.ListingDetailScreen
import com.fairswap.feature.listingdetail.ListingDetailViewModel
import com.fairswap.feature.sellerprofile.SellerProfileScreen
import com.fairswap.feature.sellerprofile.SellerProfileViewModel
import com.fairswap.feature.sellerlistings.MyListingsScreen
import com.fairswap.feature.sellerlistings.MyListingsViewModel
import com.fairswap.feature.sellerlistings.MyListingsEvent
import com.fairswap.feature.sellerlistings.SellerListingEditorScreen
import com.fairswap.feature.sellerlistings.SellerListingEditorViewModel
import com.fairswap.feature.sellerlistings.SellerListingEditorEvent
import com.fairswap.core.sellerlistings.SellerListingRepository
import com.fairswap.core.sellerlistings.SellerSession
import com.fairswap.core.sellerlistings.canRenderSellerState
import com.fairswap.feature.account.AccountEvent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import java.util.UUID
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

@Serializable
data object MyListingsRoute : NavKey

@Serializable
data class CreateSellerListingRoute(val entryId: String) : NavKey

@Serializable
data class EditSellerListingRoute(
    val listingId: String,
    val entryId: String,
) : NavKey

internal fun canConsumeSellerEditorEvent(
    eventSession: SellerSession,
    eventEntryId: String,
    activeSession: SellerSession?,
    liveUserId: String?,
    topRoute: NavKey?,
): Boolean {
    if (activeSession == null || liveUserId == null) return false
    if (eventSession != activeSession || activeSession.userId != liveUserId) return false
    return when (topRoute) {
        is CreateSellerListingRoute -> topRoute.entryId == eventEntryId
        is EditSellerListingRoute -> topRoute.entryId == eventEntryId
        else -> false
    }
}

@Composable
fun FairSwapApp(
    callbacks: AuthCallbackCoordinator,
    favouritesRepository: FavouritesRepository,
    favouriteStore: FavouriteStateStore,
    sellerRepository: SellerListingRepository,
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
    val activeSellerSession = sellerRepository.sessionOwner.session.collectAsStateWithLifecycle().value

    val scope = rememberCoroutineScope()
    val favouriteSnapshot = favouriteStore.state.collectAsStateWithLifecycle().value
    var favouriteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(supabase) {
        supabase.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val userId = status.session.user?.id
                    favouriteStore.reconcile(userId)
                    sellerRepository.sessionOwner.updateSession(userId)
                }
                is SessionStatus.NotAuthenticated, SessionStatus.Initializing -> {
                    favouriteStore.reconcile(null)
                    sellerRepository.sessionOwner.updateSession(null)
                }
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
                        is AuthGate.SignedIn -> gate.account.userId
                        is AuthGate.PolicyReviewRequired -> gate.account.userId
                        else -> null
                    }
                    LaunchedEffect(currentUser) {
                        favouriteStore.reconcile(currentUser)
                    }
                    val identityGate = authState.gate is AuthGate.SignedIn ||
                        authState.gate is AuthGate.PolicyReviewRequired
                    val sessionAligned = !identityGate || accountGateSessionMatches(currentUser, liveSessionUserId)
                    LaunchedEffect(authViewModel, currentUser, authState.gate, authState.isLoading, authState.isInitializing, sessionStatus) {
                        if (authState.isLoading || authState.isInitializing) return@LaunchedEffect
                        val mismatch = when (authState.gate) {
                            is AuthGate.SignedIn,
                            is AuthGate.PolicyReviewRequired -> !accountGateSessionMatches(currentUser, liveSessionUserId)
                            AuthGate.SignedOut -> liveSessionUserId != null
                            else -> false
                        }
                        if (mismatch) authViewModel.refresh()
                    }
                    if (!sessionAligned) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (authState.gate is AuthGate.SignedIn && currentUser != null) {
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
                                    AccountEvent.AuthRequired,
                                    AccountEvent.PolicyReviewRequired,
                                    AccountEvent.Restricted -> authViewModel.refresh()
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
                            onMyListings = { if (backStack.lastOrNull() != MyListingsRoute) backStack.add(MyListingsRoute) },
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
                MyListingsRoute -> NavEntry(key) {
                    val viewModel: MyListingsViewModel = hiltViewModel()
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    LaunchedEffect(activeSellerSession) {
                        viewModel.bind(activeSellerSession)
                        if (liveSessionUserId == null && backStack.lastOrNull() != AuthRoute) {
                            backStack.add(AuthRoute)
                        }
                    }
                    LaunchedEffect(viewModel, activeSellerSession) {
                        viewModel.events.collect { event ->
                            if (event.session == activeSellerSession && activeSellerSession?.userId == liveSessionUserId) {
                                when (event) {
                                    is MyListingsEvent.AuthRequired,
                                    is MyListingsEvent.PolicyReviewRequired -> if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                }
                            }
                        }
                    }
                    if (!canRenderSellerState(state.session, activeSellerSession, liveSessionUserId)) {
                        Box(Modifier.fillMaxSize())
                    } else {
                        MyListingsScreen(
                            state = state,
                            onBack = { backStack.removeLastOrNull() },
                            onCreateListing = { backStack.add(CreateSellerListingRoute(entryId = UUID.randomUUID().toString())) },
                            onEditListing = { id -> backStack.add(EditSellerListingRoute(listingId = id, entryId = UUID.randomUUID().toString())) },
                            onRefresh = viewModel::refresh,
                            onLifecycleAction = viewModel::executeLifecycleAction,
                            onClearMessages = viewModel::clearMessages,
                        )
                    }
                }
                is CreateSellerListingRoute -> NavEntry(key) {
                    val viewModel: SellerListingEditorViewModel = hiltViewModel()
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    val currentTopEntryState = rememberUpdatedState(backStack.lastOrNull())
                    val currentLiveUserState = rememberUpdatedState(liveSessionUserId)
                    val currentActiveSessionState = rememberUpdatedState(activeSellerSession)

                    LaunchedEffect(activeSellerSession, key.entryId) {
                        viewModel.bind(activeSellerSession, listingId = null, entryId = key.entryId)
                        if (liveSessionUserId == null && backStack.lastOrNull() != AuthRoute) {
                            backStack.add(AuthRoute)
                        }
                    }
                    LaunchedEffect(viewModel) {
                        viewModel.events.collect { event ->
                            if (canConsumeSellerEditorEvent(
                                    event.session,
                                    event.entryId,
                                    currentActiveSessionState.value,
                                    currentLiveUserState.value,
                                    currentTopEntryState.value,
                                )
                            ) {
                                when (event) {
                                    is SellerListingEditorEvent.AuthRequired,
                                    is SellerListingEditorEvent.PolicyReviewRequired -> if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                    is SellerListingEditorEvent.Saved,
                                    is SellerListingEditorEvent.Submitted -> backStack.removeLastOrNull()
                                }
                            }
                        }
                    }
                    if (!canRenderSellerState(state.session, activeSellerSession, liveSessionUserId)) {
                        Box(Modifier.fillMaxSize())
                    } else {
                        SellerListingEditorScreen(
                            state = state,
                            onBack = { backStack.removeLastOrNull() },
                            onTitleChange = viewModel::updateTitle,
                            onCategoryChange = viewModel::updateCategory,
                            onDescriptionChange = viewModel::updateDescription,
                            onConditionChange = viewModel::updateCondition,
                            onPriceTypeChange = viewModel::updatePriceType,
                            onPriceRupeesChange = viewModel::updatePriceRupees,
                            onCityChange = viewModel::updateCity,
                            onLocalityChange = viewModel::updateLocality,
                            onPinCodeChange = viewModel::updatePinCode,
                            onAttributeChange = viewModel::updateCategoryAttribute,
                            onSaveDraft = { viewModel.saveDraft(entryId = key.entryId) },
                            onSubmitListing = { viewModel.submitListing(entryId = key.entryId) },
                            onClearError = viewModel::clearErrorMessage,
                            onRetryInitialization = viewModel::retryInitialization,
                            onRetryCategoryAttributes = viewModel::retryCategoryAttributes,
                            onRetryLocalities = viewModel::retryLocalities,
                        )
                    }
                }
                is EditSellerListingRoute -> NavEntry(key) {
                    val viewModel: SellerListingEditorViewModel = hiltViewModel()
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    val currentTopEntryState = rememberUpdatedState(backStack.lastOrNull())
                    val currentLiveUserState = rememberUpdatedState(liveSessionUserId)
                    val currentActiveSessionState = rememberUpdatedState(activeSellerSession)

                    LaunchedEffect(activeSellerSession, key.entryId) {
                        viewModel.bind(activeSellerSession, listingId = key.listingId, entryId = key.entryId)
                        if (liveSessionUserId == null && backStack.lastOrNull() != AuthRoute) {
                            backStack.add(AuthRoute)
                        }
                    }
                    LaunchedEffect(viewModel) {
                        viewModel.events.collect { event ->
                            if (canConsumeSellerEditorEvent(
                                    event.session,
                                    event.entryId,
                                    currentActiveSessionState.value,
                                    currentLiveUserState.value,
                                    currentTopEntryState.value,
                                )
                            ) {
                                when (event) {
                                    is SellerListingEditorEvent.AuthRequired,
                                    is SellerListingEditorEvent.PolicyReviewRequired -> if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                    is SellerListingEditorEvent.Saved,
                                    is SellerListingEditorEvent.Submitted -> backStack.removeLastOrNull()
                                }
                            }
                        }
                    }
                    if (!canRenderSellerState(state.session, activeSellerSession, liveSessionUserId)) {
                        Box(Modifier.fillMaxSize())
                    } else {
                        SellerListingEditorScreen(
                            state = state,
                            onBack = { backStack.removeLastOrNull() },
                            onTitleChange = viewModel::updateTitle,
                            onCategoryChange = viewModel::updateCategory,
                            onDescriptionChange = viewModel::updateDescription,
                            onConditionChange = viewModel::updateCondition,
                            onPriceTypeChange = viewModel::updatePriceType,
                            onPriceRupeesChange = viewModel::updatePriceRupees,
                            onCityChange = viewModel::updateCity,
                            onLocalityChange = viewModel::updateLocality,
                            onPinCodeChange = viewModel::updatePinCode,
                            onAttributeChange = viewModel::updateCategoryAttribute,
                            onSaveDraft = { viewModel.saveDraft(entryId = key.entryId) },
                            onSubmitListing = { viewModel.submitListing(entryId = key.entryId) },
                            onClearError = viewModel::clearErrorMessage,
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
