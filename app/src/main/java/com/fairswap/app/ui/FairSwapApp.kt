package com.fairswap.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.core.auth.AuthGate
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.CommunicationSessionOwner
import com.fairswap.core.favourites.FavouriteMutationResult
import com.fairswap.core.favourites.FavouriteStateStore
import com.fairswap.core.favourites.FavouriteValue
import com.fairswap.core.favourites.FavouritesRepository
import com.fairswap.core.sellerlistings.SellerListingRepository
import com.fairswap.core.sellerlistings.SellerSession
import com.fairswap.core.sellerlistings.canRenderSellerState
import com.fairswap.feature.account.AccountEvent
import com.fairswap.feature.account.AccountScreen
import com.fairswap.feature.account.AccountUiState
import com.fairswap.feature.account.AccountViewModel
import com.fairswap.feature.auth.AuthScreen
import com.fairswap.feature.auth.AuthViewModel
import com.fairswap.feature.communication.CommunicationNavigationEvent
import com.fairswap.feature.communication.InboxScreen
import com.fairswap.feature.communication.InboxViewModel
import com.fairswap.feature.communication.NewInquiryScreen
import com.fairswap.feature.communication.NewInquiryViewModel
import com.fairswap.feature.communication.ThreadScreen
import com.fairswap.feature.communication.ThreadViewModel
import com.fairswap.feature.discovery.DiscoveryScreen
import com.fairswap.feature.discovery.DiscoveryViewModel
import com.fairswap.feature.favourites.FavouritesScreen
import com.fairswap.feature.favourites.FavouritesViewModel
import com.fairswap.feature.listingdetail.ListingDetailMessagingEvent
import com.fairswap.feature.listingdetail.ListingDetailScreen
import com.fairswap.feature.listingdetail.ListingDetailViewModel
import com.fairswap.feature.sellerlistings.MyListingsEvent
import com.fairswap.feature.sellerlistings.MyListingsScreen
import com.fairswap.feature.sellerlistings.MyListingsViewModel
import com.fairswap.feature.sellerlistings.SellerListingEditorEvent
import com.fairswap.feature.sellerlistings.SellerListingEditorScreen
import com.fairswap.feature.sellerlistings.SellerListingEditorViewModel
import com.fairswap.feature.sellerprofile.SellerProfileScreen
import com.fairswap.feature.sellerprofile.SellerProfileViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

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
data object MessagesRoute : NavKey

@Serializable
data class NewInquiryRoute(val listingId: String) : NavKey

@Serializable
data class ConversationRoute(val conversationId: String) : NavKey

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

internal fun canConsumeMessagingEvent(
    eventSession: CommunicationSession,
    activeSession: CommunicationSession?,
    liveUserId: String?,
    eventListingId: String?,
    topRoute: NavKey?,
): Boolean {
    if (activeSession == null || liveUserId == null) return false
    if (eventSession != activeSession || activeSession.userId != liveUserId) return false
    if (eventListingId != null) {
        val topListingId = when (topRoute) {
            is ListingDetailRoute -> topRoute.listingId
            is NewInquiryRoute -> topRoute.listingId
            else -> null
        }
        if (topListingId != eventListingId) return false
    }
    return true
}

@Composable
fun FairSwapApp(
    callbacks: AuthCallbackCoordinator,
    favouritesRepository: FavouritesRepository,
    favouriteStore: FavouriteStateStore,
    sellerRepository: SellerListingRepository,
    communicationSessionOwner: CommunicationSessionOwner,
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
    val activeCommunicationSession = communicationSessionOwner.session.collectAsStateWithLifecycle().value
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
                    communicationSessionOwner.updateSession(userId)
                }
                is SessionStatus.NotAuthenticated, SessionStatus.Initializing -> {
                    favouriteStore.reconcile(null)
                    sellerRepository.sessionOwner.updateSession(null)
                    communicationSessionOwner.updateSession(null)
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
                                when (favouritesRepository.setFavourite(id, desired)) {
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

                    LaunchedEffect(viewModel) {
                        viewModel.messagingEvents.collect { event ->
                            val currentSession = communicationSessionOwner.currentSession()
                            when (event) {
                                is ListingDetailMessagingEvent.NavigateToConversation -> {
                                    if (canConsumeMessagingEvent(event.session, currentSession, liveSessionUserId, event.listingId, backStack.lastOrNull())) {
                                        backStack.add(ConversationRoute(event.conversationId))
                                    }
                                }
                                is ListingDetailMessagingEvent.NavigateToNewInquiry -> {
                                    if (canConsumeMessagingEvent(event.session, currentSession, liveSessionUserId, event.listingId, backStack.lastOrNull())) {
                                        backStack.add(NewInquiryRoute(event.listingId))
                                    }
                                }
                                is ListingDetailMessagingEvent.NavigateToAuth -> {
                                    if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                }
                            }
                        }
                    }

                    val detail = state.detail
                    val isOwner = detail != null && activeCommunicationSession?.userId == detail.sellerId

                    ListingDetailScreen(
                        state = state,
                        onBack = { backStack.removeLastOrNull() },
                        onRetry = viewModel::retry,
                        onSeller = { sellerId -> backStack.add(SellerProfileRoute(sellerId)) },
                        favourite = favouriteSnapshot.values[key.listingId] ?: FavouriteValue.UNKNOWN,
                        favouriteMutating = key.listingId in favouriteSnapshot.mutating,
                        onFavourite = { desired ->
                            scope.launch {
                                when (favouritesRepository.setFavourite(key.listingId, desired)) {
                                    FavouriteMutationResult.AuthRequired -> { favouriteError = null; if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute) }
                                    FavouriteMutationResult.PolicyReviewRequired -> { favouriteError = null; if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute) }
                                    is FavouriteMutationResult.Updated -> favouriteError = null
                                    else -> favouriteError = "Unable to update saved favourite. Retry."
                                }
                            }
                        },
                        favouriteError = favouriteError,
                        isOwner = isOwner,
                        onMessageSeller = { viewModel.onMessageSellerClicked(communicationSessionOwner.currentSession()) },
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
                MessagesRoute -> NavEntry(key) {
                    val viewModel: InboxViewModel = hiltViewModel()
                    val state = viewModel.uiState.collectAsStateWithLifecycle().value

                    LaunchedEffect(viewModel, activeCommunicationSession) {
                        viewModel.bindSession(activeCommunicationSession)
                    }

                    LaunchedEffect(viewModel) {
                        viewModel.events.collect { event ->
                            when (event) {
                                is CommunicationNavigationEvent.NavigateToAuth -> {
                                    if (canConsumeMessagingEvent(event.session, communicationSessionOwner.currentSession(), liveSessionUserId, null, backStack.lastOrNull())) {
                                        if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }

                    InboxScreen(
                        state = state,
                        onFilterSelected = viewModel::setFilter,
                        onRefresh = viewModel::refresh,
                        onConversationClick = { convId -> backStack.add(ConversationRoute(convId)) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                is NewInquiryRoute -> NavEntry(key) {
                    val viewModel: NewInquiryViewModel = hiltViewModel()
                    val state = viewModel.uiState.collectAsStateWithLifecycle().value

                    LaunchedEffect(viewModel, activeCommunicationSession, key.listingId) {
                        viewModel.bind(activeCommunicationSession, key.listingId)
                    }

                    LaunchedEffect(viewModel) {
                        viewModel.events.collect { event ->
                            val currentSession = communicationSessionOwner.currentSession()
                            when (event) {
                                is CommunicationNavigationEvent.NavigateToConversation -> {
                                    val targetListingId = event.listingId ?: key.listingId
                                    if (canConsumeMessagingEvent(event.session, currentSession, liveSessionUserId, targetListingId, backStack.lastOrNull())) {
                                        if (backStack.lastOrNull() is NewInquiryRoute) {
                                            backStack.removeLastOrNull()
                                        }
                                        backStack.add(ConversationRoute(event.conversationId))
                                    }
                                }
                                is CommunicationNavigationEvent.NavigateToAuth -> {
                                    if (canConsumeMessagingEvent(event.session, currentSession, liveSessionUserId, key.listingId, backStack.lastOrNull())) {
                                        if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }

                    NewInquiryScreen(
                        state = state,
                        onTextChanged = viewModel::onComposerTextChanged,
                        onSend = viewModel::sendInquiry,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                is ConversationRoute -> NavEntry(key) {
                    val viewModel: ThreadViewModel = hiltViewModel()
                    val state = viewModel.uiState.collectAsStateWithLifecycle().value

                    LaunchedEffect(viewModel, activeCommunicationSession, key.conversationId) {
                        viewModel.bind(activeCommunicationSession, key.conversationId)
                    }

                    LaunchedEffect(viewModel) {
                        viewModel.events.collect { event ->
                            when (event) {
                                is CommunicationNavigationEvent.NavigateToAuth -> {
                                    if (canConsumeMessagingEvent(event.session, communicationSessionOwner.currentSession(), liveSessionUserId, null, backStack.lastOrNull())) {
                                        if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }

                    ThreadScreen(
                        state = state,
                        onVisible = viewModel::onVisible,
                        onNotVisible = viewModel::onNotVisible,
                        onComposerTextChanged = viewModel::onComposerTextChanged,
                        onSend = viewModel::sendMessage,
                        onLoadOlder = viewModel::loadOlderMessages,
                        onBack = { backStack.removeLastOrNull() },
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
                            onRetry = accountViewModel::refresh,
                            onFavourites = { backStack.add(FavouritesRoute) },
                            onMyListings = { backStack.add(MyListingsRoute) },
                            onMessages = { backStack.add(MessagesRoute) },
                            onSignOut = authViewModel::signOut,
                        )
                    } else {
                        AuthScreen(
                            uiState = authState,
                            onBrowse = { backStack.clear(); backStack.add(DiscoveryRoute) },
                            onShowSignIn = authViewModel::showSignIn,
                            onShowSignUp = authViewModel::showSignUp,
                            onShowForgotPassword = authViewModel::showForgotPassword,
                            onSubmitEmail = authViewModel::submitEmail,
                            onChangeEmail = authViewModel::changeEmail,
                            onSwitchToPassword = authViewModel::switchToPassword,
                            onSwitchToOtp = authViewModel::switchToOtp,
                            onRequestOtp = authViewModel::requestOtp,
                            onVerifyOtp = authViewModel::verifyOtp,
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
                    val myListingsViewModel: MyListingsViewModel = hiltViewModel()
                    val state = myListingsViewModel.state.collectAsStateWithLifecycle().value

                    LaunchedEffect(myListingsViewModel, activeSellerSession) {
                        myListingsViewModel.bind(activeSellerSession)
                    }

                    LaunchedEffect(myListingsViewModel) {
                        myListingsViewModel.events.collect { event ->
                            when (event) {
                                is MyListingsEvent.AuthRequired,
                                is MyListingsEvent.PolicyReviewRequired -> {
                                    if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                }
                            }
                        }
                    }

                    if (!canRenderSellerState(state.session, activeSellerSession, liveSessionUserId)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        MyListingsScreen(
                            state = state,
                            onBack = { backStack.removeLastOrNull() },
                            onCreateListing = { backStack.add(CreateSellerListingRoute(UUID.randomUUID().toString())) },
                            onEditListing = { listingId -> backStack.add(EditSellerListingRoute(listingId, UUID.randomUUID().toString())) },
                            onRefresh = myListingsViewModel::refresh,
                            onLifecycleAction = myListingsViewModel::executeLifecycleAction,
                            onClearMessages = myListingsViewModel::clearMessages,
                        )
                    }
                }
                is CreateSellerListingRoute -> NavEntry(key) {
                    val editorViewModel: SellerListingEditorViewModel = hiltViewModel()
                    val state = editorViewModel.state.collectAsStateWithLifecycle().value

                    LaunchedEffect(editorViewModel, activeSellerSession, key.entryId) {
                        editorViewModel.bind(activeSellerSession, null, key.entryId)
                    }

                    LaunchedEffect(editorViewModel) {
                        editorViewModel.events.collect { event ->
                            val activeSession = sellerRepository.sessionOwner.session.value
                            when (event) {
                                is SellerListingEditorEvent.Created -> {
                                    if (canConsumeSellerEditorEvent(event.session, event.entryId, activeSession, liveSessionUserId, backStack.lastOrNull())) {
                                        if (backStack.lastOrNull() is CreateSellerListingRoute) {
                                            backStack.removeLastOrNull()
                                        }
                                        backStack.add(ListingDetailRoute(event.listingId))
                                    }
                                }
                                is SellerListingEditorEvent.Saved -> {
                                    if (canConsumeSellerEditorEvent(event.session, event.entryId, activeSession, liveSessionUserId, backStack.lastOrNull())) {
                                        if (backStack.lastOrNull() is CreateSellerListingRoute) {
                                            backStack.removeLastOrNull()
                                        }
                                        backStack.add(ListingDetailRoute(event.listingId))
                                    }
                                }
                                is SellerListingEditorEvent.Submitted -> {
                                    if (canConsumeSellerEditorEvent(event.session, event.entryId, activeSession, liveSessionUserId, backStack.lastOrNull())) {
                                        backStack.removeLastOrNull()
                                    }
                                }
                                is SellerListingEditorEvent.AuthRequired,
                                is SellerListingEditorEvent.PolicyReviewRequired,
                                is SellerListingEditorEvent.Restricted -> {
                                    if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                }
                            }
                        }
                    }

                    if (!canRenderSellerState(state.session, activeSellerSession, liveSessionUserId)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        SellerListingEditorScreen(
                            state = state,
                            onBack = { backStack.removeLastOrNull() },
                            onTitleChange = editorViewModel::updateTitle,
                            onCategoryChange = editorViewModel::updateCategory,
                            onDescriptionChange = editorViewModel::updateDescription,
                            onConditionChange = editorViewModel::updateCondition,
                            onPriceTypeChange = editorViewModel::updatePriceType,
                            onPriceRupeesChange = editorViewModel::updatePriceRupees,
                            onCityChange = editorViewModel::updateCity,
                            onLocalityChange = editorViewModel::updateLocality,
                            onPinCodeChange = editorViewModel::updatePinCode,
                            onLandmarkChange = editorViewModel::updateLandmark,
                            onSelectDistrict = editorViewModel::selectDistrict,
                            onAttributeChange = editorViewModel::updateCategoryAttribute,
                            onSaveDraft = { editorViewModel.saveDraft(key.entryId) },
                            onSubmitListing = { editorViewModel.submitListing(key.entryId) },
                            onClearError = editorViewModel::clearErrorMessage,
                        )
                    }
                }
                is EditSellerListingRoute -> NavEntry(key) {
                    val editorViewModel: SellerListingEditorViewModel = hiltViewModel()
                    val state = editorViewModel.state.collectAsStateWithLifecycle().value

                    LaunchedEffect(editorViewModel, activeSellerSession, key.listingId, key.entryId) {
                        editorViewModel.bind(activeSellerSession, key.listingId, key.entryId)
                    }

                    LaunchedEffect(editorViewModel) {
                        editorViewModel.events.collect { event ->
                            val activeSession = sellerRepository.sessionOwner.session.value
                            when (event) {
                                is SellerListingEditorEvent.Created -> {
                                    if (canConsumeSellerEditorEvent(event.session, event.entryId, activeSession, liveSessionUserId, backStack.lastOrNull())) {
                                        if (backStack.lastOrNull() is EditSellerListingRoute) {
                                            backStack.removeLastOrNull()
                                        }
                                        backStack.add(ListingDetailRoute(event.listingId))
                                    }
                                }
                                is SellerListingEditorEvent.Saved -> {
                                    if (canConsumeSellerEditorEvent(event.session, event.entryId, activeSession, liveSessionUserId, backStack.lastOrNull())) {
                                        if (backStack.lastOrNull() is EditSellerListingRoute) {
                                            backStack.removeLastOrNull()
                                        }
                                        backStack.add(ListingDetailRoute(event.listingId))
                                    }
                                }
                                is SellerListingEditorEvent.Submitted -> {
                                    if (canConsumeSellerEditorEvent(event.session, event.entryId, activeSession, liveSessionUserId, backStack.lastOrNull())) {
                                        backStack.removeLastOrNull()
                                    }
                                }
                                is SellerListingEditorEvent.AuthRequired,
                                is SellerListingEditorEvent.PolicyReviewRequired,
                                is SellerListingEditorEvent.Restricted -> {
                                    if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                }
                            }
                        }
                    }

                    if (!canRenderSellerState(state.session, activeSellerSession, liveSessionUserId)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        SellerListingEditorScreen(
                            state = state,
                            onBack = { backStack.removeLastOrNull() },
                            onTitleChange = editorViewModel::updateTitle,
                            onCategoryChange = editorViewModel::updateCategory,
                            onDescriptionChange = editorViewModel::updateDescription,
                            onConditionChange = editorViewModel::updateCondition,
                            onPriceTypeChange = editorViewModel::updatePriceType,
                            onPriceRupeesChange = editorViewModel::updatePriceRupees,
                            onCityChange = editorViewModel::updateCity,
                            onLocalityChange = editorViewModel::updateLocality,
                            onPinCodeChange = editorViewModel::updatePinCode,
                            onLandmarkChange = editorViewModel::updateLandmark,
                            onSelectDistrict = editorViewModel::selectDistrict,
                            onAttributeChange = editorViewModel::updateCategoryAttribute,
                            onSaveDraft = { editorViewModel.saveDraft(key.entryId) },
                            onSubmitListing = { editorViewModel.submitListing(key.entryId) },
                            onClearError = editorViewModel::clearErrorMessage,
                        )
                    }
                }
                else -> NavEntry(key) { Box(Modifier.fillMaxSize()) }
            }
        }
    )
}

internal fun accountGateSessionMatches(gateUserId: String?, liveSessionUserId: String?): Boolean {
    if (gateUserId == null || liveSessionUserId == null) return false
    return gateUserId == liveSessionUserId
}
