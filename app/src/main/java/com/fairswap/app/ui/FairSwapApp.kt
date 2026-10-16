package com.fairswap.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.saveable.rememberSaveable
import com.fairswap.app.ui.AppTab
import com.fairswap.app.ui.AppBottomBar
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.fairswap.feature.account.safety.PrivacyScreen
import com.fairswap.feature.account.safety.PrivacyViewModel
import com.fairswap.feature.account.safety.ReportScreen
import com.fairswap.feature.account.safety.ReportViewModel
import com.fairswap.core.account.ReportTargetType
import com.fairswap.core.account.SafetySession
import com.fairswap.feature.account.safety.BlockedUsersScreen
import com.fairswap.feature.account.safety.BlockedUsersViewModel
import com.fairswap.feature.account.safety.PrivacyEvent
import com.fairswap.feature.account.safety.ReportEvent
import com.fairswap.feature.account.safety.BlockedUsersEvent
import com.fairswap.app.ui.SafetyRoutesViewModel
import android.widget.Toast
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.fairswap.core.designsystem.components.FairSwapComingSoonScreen
import com.fairswap.feature.account.AccountUiState
import com.fairswap.feature.account.AccountViewModel
import com.fairswap.feature.auth.AuthScreen
import com.fairswap.feature.auth.AuthViewModel
import com.fairswap.feature.communication.CommunicationNavigationEvent
import com.fairswap.feature.communication.InboxScreen
import com.fairswap.feature.communication.InboxViewModel
import com.fairswap.feature.communication.NewInquiryScreen
import com.fairswap.feature.communication.NewInquiryViewModel
import com.fairswap.feature.communication.NotificationsScreen
import com.fairswap.feature.communication.NotificationsViewModel
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
internal data object AuthRoute : NavKey

@Serializable
private data object DiscoveryRoute : NavKey

@Serializable
data class ListingDetailRoute(val listingId: String) : NavKey

@Serializable
data class SellerProfileRoute(val sellerId: String) : NavKey

@Serializable
data object FavouritesRoute : NavKey

@Serializable
data class ReportRoute(val targetType: String, val targetId: String) : NavKey

@Serializable
data object BlockedUsersRoute : NavKey

@Serializable
data object PrivacyRoute : NavKey

@Serializable
data object SafetyCenterRoute : NavKey

@Serializable
data object MyListingsRoute : NavKey

@Serializable
data object MessagesRoute : NavKey

@Serializable
data object NotificationsRoute : NavKey

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

@Serializable
data class ComingSoonRoute(
    val featureTitle: String,
    val featureDescription: String
) : NavKey

internal fun canConsumeSellerSessionEvent(
    eventSession: SellerSession,
    activeSession: SellerSession?,
    liveUserId: String?,
): Boolean {
    if (activeSession == null || liveUserId == null) return false
    return eventSession == activeSession && activeSession.userId == liveUserId
}

internal fun handleMyListingsEvent(
    event: MyListingsEvent,
    activeSession: SellerSession?,
    liveUserId: String?,
    topRoute: NavKey?,
    backStack: MutableList<NavKey>,
) {
    if (!canConsumeSellerSessionEvent(event.session, activeSession, liveUserId)) {
        return
    }
    when (event) {
        is MyListingsEvent.AuthRequired,
        is MyListingsEvent.PolicyReviewRequired -> {
            if (topRoute != AuthRoute && backStack.lastOrNull() != AuthRoute) {
                backStack.add(AuthRoute)
            }
        }
    }
}

internal fun handleSellerEditorEvent(
    event: SellerListingEditorEvent,
    activeSession: SellerSession?,
    liveUserId: String?,
    topRoute: NavKey?,
    backStack: MutableList<NavKey>,
) {
    if (!canConsumeSellerEditorEvent(event.session, event.entryId, activeSession, liveUserId, topRoute)) {
        return
    }
    when (event) {
        is SellerListingEditorEvent.AuthRequired,
        is SellerListingEditorEvent.PolicyReviewRequired,
        is SellerListingEditorEvent.Restricted -> {
            if (topRoute != AuthRoute && backStack.lastOrNull() != AuthRoute) {
                backStack.add(AuthRoute)
            }
        }
        is SellerListingEditorEvent.Created -> {
            if (topRoute is CreateSellerListingRoute) {
                backStack.removeLastOrNull()
                backStack.add(EditSellerListingRoute(listingId = event.listingId, entryId = UUID.randomUUID().toString()))
            }
        }
        is SellerListingEditorEvent.Saved,
        is SellerListingEditorEvent.Submitted -> {
            if (topRoute is CreateSellerListingRoute || topRoute is EditSellerListingRoute) {
                backStack.removeLastOrNull()
            }
        }
    }
}

internal data class SellerEditorCallbacks(
    val onRetryInitialization: () -> Unit,
    val onRetryCategoryAttributes: () -> Unit,
    val onRetryLocalities: () -> Unit,
    val onValidatedPhotos: (List<com.fairswap.core.sellerlistings.SellerMediaUploadFile>) -> Unit,
    val onSetCover: (String) -> Unit,
    val onDeleteMedia: (String) -> Unit,
    val onMoveLeft: (String) -> Unit,
    val onMoveRight: (String) -> Unit,
    val onRetryMedia: () -> Unit,
    val onImageError: (String, String) -> Unit,
    val onLocalMediaError: (String) -> Unit,
)

internal fun createSellerEditorCallbacks(
    onRetryInitialization: () -> Unit,
    onRetryCategoryAttributes: () -> Unit,
    onRetryLocalities: () -> Unit,
    onValidatedPhotos: (List<com.fairswap.core.sellerlistings.SellerMediaUploadFile>) -> Unit,
    onSetCover: (String) -> Unit,
    onDeleteMedia: (String) -> Unit,
    onMoveLeft: (String) -> Unit,
    onMoveRight: (String) -> Unit,
    onRetryMedia: () -> Unit,
    onImageError: (String, String) -> Unit,
    onLocalMediaError: (String) -> Unit,
): SellerEditorCallbacks = SellerEditorCallbacks(
    onRetryInitialization = onRetryInitialization,
    onRetryCategoryAttributes = onRetryCategoryAttributes,
    onRetryLocalities = onRetryLocalities,
    onValidatedPhotos = onValidatedPhotos,
    onSetCover = onSetCover,
    onDeleteMedia = onDeleteMedia,
    onMoveLeft = onMoveLeft,
    onMoveRight = onMoveRight,
    onRetryMedia = onRetryMedia,
    onImageError = onImageError,
    onLocalMediaError = onLocalMediaError,
)

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

internal fun canConsumeNotificationEvent(
    eventSession: CommunicationSession,
    activeSession: CommunicationSession?,
    liveUserId: String?,
    topRoute: NavKey?,
): Boolean {
    if (activeSession == null || liveUserId == null) return false
    if (eventSession != activeSession || activeSession.userId != liveUserId) return false
    return topRoute is NotificationsRoute
}

@Composable
fun FairSwapApp(
    callbacks: AuthCallbackCoordinator,
    favouritesRepository: FavouritesRepository,
    favouriteStore: FavouriteStateStore,
    sellerRepository: SellerListingRepository,
    communicationSessionOwner: CommunicationSessionOwner,
    supabase: SupabaseClient,
    selectedLanguage: String = "en",
    onLanguageSelected: (String) -> Unit = {},
) {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }

    val homeBackStack = rememberNavBackStack(DiscoveryRoute)
    val savedBackStack = rememberNavBackStack(FavouritesRoute)
    val sellBackStack = rememberNavBackStack(MyListingsRoute)
    val chatsBackStack = rememberNavBackStack(MessagesRoute)
    val youBackStack = rememberNavBackStack(AuthRoute)

    val backStack = when (currentTab) {
        AppTab.HOME -> homeBackStack
        AppTab.SAVED -> savedBackStack
        AppTab.SELL -> sellBackStack
        AppTab.CHATS -> chatsBackStack
        AppTab.YOU -> youBackStack
    }

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
            if (youBackStack.lastOrNull() != AuthRoute) youBackStack.add(AuthRoute)
            currentTab = AppTab.YOU
            callbacks.acknowledgeNavigation(sequence)
        }
    }

    val saveableStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    Scaffold(
        bottomBar = {
            AppBottomBar(
                currentTab = currentTab,
                onTabSelected = { tab -> currentTab = tab }
            )
        }
    ) { innerPadding ->
        saveableStateHolder.SaveableStateProvider(currentTab) {
            val activeBackStack = backStack
            NavDisplay(
                backStack = activeBackStack,
                modifier = Modifier.padding(innerPadding),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                onBack = { 
                    if (activeBackStack.size > 1) {
                        activeBackStack.removeLastOrNull()
                    } else if (currentTab != AppTab.HOME) {
                        currentTab = AppTab.HOME
                    }
                },
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
                                onRecentSearchSelected = discoveryViewModel::selectRecentSearch,
                                onClearRecentSearches = discoveryViewModel::clearRecentSearches,
                                onLoadMore = discoveryViewModel::loadMore,
                                onRetryMetadata = discoveryViewModel::retryMetadata,
                                onAccount = { 
                                    if (youBackStack.lastOrNull() != AuthRoute) youBackStack.add(AuthRoute)
                                    currentTab = AppTab.YOU
                                },
                                onNotifications = {
                                    if (youBackStack.lastOrNull() != NotificationsRoute) youBackStack.add(NotificationsRoute)
                                    currentTab = AppTab.YOU
                                },
                                onListingSelected = { id -> activeBackStack.add(ListingDetailRoute(id)) },
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
                    val safetyRoutesViewModel: SafetyRoutesViewModel = hiltViewModel()
                    val context = LocalContext.current
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
                        onReportListing = { id -> activeBackStack.add(ReportRoute("listing", id)) },
                        onMakeOffer = { activeBackStack.add(ComingSoonRoute("Make Offer", "Price negotiation and offers will be available in a future update.")) },
                        onBuyNow = { activeBackStack.add(ComingSoonRoute("Protected Payments", "FairSwap protected payments via UPI escrow are currently in development.")) },
                        onDelivery = { activeBackStack.add(ComingSoonRoute("Delivery Tracking", "Integrated shipping and delivery tracking will be available soon.")) },
                        onDispute = { activeBackStack.add(ComingSoonRoute("Disputes", "Post-trade dispute resolution is in development. Contact Support for immediate assistance.")) },
                        onShareListing = { listingId, title ->
                            val url = safetyRoutesViewModel.publicListingUrlBuilder.buildListingUrl(listingId)
                            if (url != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                                }
                                runCatching { context.startActivity(Intent.createChooser(shareIntent, "Share Listing")) }.onFailure {
                                    Toast.makeText(context, "Cannot open sharing app.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Cannot share: Public link unavailable.", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
                is SellerProfileRoute -> NavEntry(key) {
                    val viewModel: SellerProfileViewModel = hiltViewModel()
                    val safetyRoutesViewModel: SafetyRoutesViewModel = hiltViewModel()
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    val blockState = safetyRoutesViewModel.blockState.collectAsStateWithLifecycle().value
                    LaunchedEffect(key.sellerId) { viewModel.load(key.sellerId) }
                    SellerProfileScreen(
                        state = state,
                        onBack = { activeBackStack.removeLastOrNull() },
                        onRetry = viewModel::retry,
                        onListing = { listingId -> activeBackStack.add(ListingDetailRoute(listingId)) },
                        onReportUser = { userId -> activeBackStack.add(ReportRoute("user", userId)) },
                        onBlockUser = { userId -> 
                            safetyRoutesViewModel.dispatchBlockUser(
                                originatingSession = communicationSessionOwner.currentSession(),
                                originatingRoute = key,
                                getLiveSession = { communicationSessionOwner.currentSession() },
                                getLiveTopRoute = { activeBackStack.lastOrNull() },
                                getLiveUserId = { liveSessionUserId },
                                counterpartyId = userId,
                                onSuccess = { activeBackStack.removeLastOrNull() }
                            )
                        },
                        blockActionPending = blockState.pendingCounterpartyId == key.sellerId,
                        blockActionError = blockState.error.takeIf { blockState.pendingCounterpartyId == null },
                        onClearBlockError = safetyRoutesViewModel::clearMessages,
                        onReviews = { activeBackStack.add(ComingSoonRoute("Seller Reviews", "Seller reviews and ratings will be available in a future update.")) },
                        onFollow = { activeBackStack.add(ComingSoonRoute("Follow Seller", "Seller following and saved-search alerts will be available soon.")) },
                        onVerification = { activeBackStack.add(ComingSoonRoute("Seller Verification", "Identity verification badges and trust scores are in development.")) },
                    )
                }
                FavouritesRoute -> NavEntry(key) {
                    val viewModel: FavouritesViewModel = hiltViewModel()
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    LaunchedEffect(favouriteSnapshot.userId, favouriteSnapshot.generation) { viewModel.sessionChanged() }
                    FavouritesScreen(
                        state = state,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else currentTab = AppTab.HOME },
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        onClearUnavailable = viewModel::clearUnavailable,
                        onBrowse = { currentTab = AppTab.HOME },
                        onListing = { id -> activeBackStack.add(ListingDetailRoute(id)) },
                        onAuth = { if (youBackStack.lastOrNull() != AuthRoute) youBackStack.add(AuthRoute); currentTab = AppTab.YOU },
                        mutatingIds = favouriteSnapshot.mutating,
                        onRemoveFavourite = { id ->
                            scope.launch {
                                when (favouritesRepository.setFavourite(id, false)) {
                                    FavouriteMutationResult.AuthRequired -> { favouriteError = null; if (youBackStack.lastOrNull() != AuthRoute) youBackStack.add(AuthRoute); currentTab = AppTab.YOU }
                                    FavouriteMutationResult.PolicyReviewRequired -> { favouriteError = null; if (youBackStack.lastOrNull() != AuthRoute) youBackStack.add(AuthRoute); currentTab = AppTab.YOU }
                                    is FavouriteMutationResult.Updated -> favouriteError = null
                                    else -> favouriteError = "Unable to update saved favourite. Retry."
                                }
                            }
                        },
                        favouriteError = favouriteError,
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
                NotificationsRoute -> NavEntry(key) {
                    val viewModel: NotificationsViewModel = hiltViewModel()
                    val state = viewModel.uiState.collectAsStateWithLifecycle().value
                    val currentSession = activeCommunicationSession
                    val currentLiveUserId by rememberUpdatedState(liveSessionUserId)

                    LaunchedEffect(viewModel, currentSession) {
                        viewModel.bind(currentSession)
                    }

                    LaunchedEffect(viewModel) {
                        viewModel.events.collect { event ->
                            val latestSession = communicationSessionOwner.currentSession()
                            when (event) {
                                is CommunicationNavigationEvent.NavigateToConversation -> {
                                    if (canConsumeNotificationEvent(event.session, latestSession, currentLiveUserId, backStack.lastOrNull())) {
                                        backStack.add(ConversationRoute(event.conversationId))
                                    }
                                }
                                is CommunicationNavigationEvent.NavigateToMyListings -> {
                                    if (canConsumeNotificationEvent(event.session, latestSession, currentLiveUserId, backStack.lastOrNull())) {
                                        backStack.add(MyListingsRoute)
                                    }
                                }
                                is CommunicationNavigationEvent.NavigateToAuth -> {
                                    if (canConsumeNotificationEvent(event.session, latestSession, currentLiveUserId, backStack.lastOrNull())) {
                                        if (backStack.lastOrNull() != AuthRoute) backStack.add(AuthRoute)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }

                    // Render loading state while rebinding or if there's no active session
                    // Ensure we only render the UI when the session matches the current live session
                    val isSessionAligned = currentSession != null && state.boundSession == currentSession && currentSession.userId == liveSessionUserId

                    if (!isSessionAligned) {
                        Scaffold(
                            topBar = {
                                @OptIn(ExperimentalMaterial3Api::class)
                                TopAppBar(
                                    title = { Text(stringResource(id = com.fairswap.core.designsystem.R.string.notifications_title)) },
                                    navigationIcon = { TextButton(onClick = { backStack.removeLastOrNull() }) { Text(stringResource(id = com.fairswap.core.designsystem.R.string.action_back)) } },
                                )
                            }
                        ) { paddingValues ->
                            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                                if (liveSessionUserId == null) {
                                    Text(stringResource(id = com.fairswap.core.designsystem.R.string.notifications_sign_in_required))
                                } else {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    } else {
                        NotificationsScreen(
                            state = state,
                            onVisible = viewModel::onVisible,
                            onNotVisible = viewModel::onNotVisible,
                            onRefresh = viewModel::refresh,
                            onNotificationTapped = viewModel::onNotificationTapped,
                            onMarkAllRead = viewModel::markAllRead,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
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
                SafetyCenterRoute -> NavEntry(key) {
                    com.fairswap.feature.account.safety.SafetyCenterScreen(
                        onBack = { activeBackStack.removeLastOrNull() }
                    )
                }
                AuthRoute -> NavEntry(key) {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val uriHandler = LocalUriHandler.current
                    val context = LocalContext.current
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
                            onFavourites = { currentTab = AppTab.SAVED },
                            onMyListings = { currentTab = AppTab.SELL },
                            onMessages = { currentTab = AppTab.CHATS },
                            onNotifications = { activeBackStack.add(NotificationsRoute) },
                            onBlockedUsers = { activeBackStack.add(BlockedUsersRoute) },
                            onPrivacyRequests = { activeBackStack.add(PrivacyRoute) },
                            onSupport = { runCatching { uriHandler.openUri("https://help.fairswap.in/en") }.onFailure { Toast.makeText(context, "Cannot open browser.", Toast.LENGTH_SHORT).show() } },
                            onSafety = { activeBackStack.add(SafetyCenterRoute) },
                            onTerms = { runCatching { uriHandler.openUri("https://fairswap.in/terms") }.onFailure { Toast.makeText(context, "Cannot open browser.", Toast.LENGTH_SHORT).show() } },
                            onPrivacyPolicy = { runCatching { uriHandler.openUri("https://fairswap.in/privacy") }.onFailure { Toast.makeText(context, "Cannot open browser.", Toast.LENGTH_SHORT).show() } },
                            selectedLanguage = selectedLanguage,
                            onLanguageSelected = onLanguageSelected,
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
                            onTerms = { runCatching { uriHandler.openUri("https://fairswap.in/terms") }.onFailure { android.widget.Toast.makeText(context, "Cannot open browser.", android.widget.Toast.LENGTH_SHORT).show() } },
                            onPrivacyPolicy = { runCatching { uriHandler.openUri("https://fairswap.in/privacy") }.onFailure { android.widget.Toast.makeText(context, "Cannot open browser.", android.widget.Toast.LENGTH_SHORT).show() } },
                            onSupport = { runCatching { uriHandler.openUri("https://help.fairswap.in/en") }.onFailure { android.widget.Toast.makeText(context, "Cannot open browser.", android.widget.Toast.LENGTH_SHORT).show() } },
                            selectedLanguage = selectedLanguage,
                            onLanguageSelected = onLanguageSelected,
                        )
                    }
                }
                MyListingsRoute -> NavEntry(key) {
                    val myListingsViewModel: MyListingsViewModel = hiltViewModel()
                    val state = myListingsViewModel.state.collectAsStateWithLifecycle().value
                    val currentLiveUserState = rememberUpdatedState(liveSessionUserId)
                    val currentActiveSessionState = rememberUpdatedState(activeSellerSession)

                    LaunchedEffect(myListingsViewModel, activeSellerSession) {
                        myListingsViewModel.bind(activeSellerSession)
                        if (liveSessionUserId == null && backStack.lastOrNull() != AuthRoute) {
                            backStack.add(AuthRoute)
                        }
                    }

                    LaunchedEffect(myListingsViewModel) {
                        myListingsViewModel.events.collect { event ->
                            handleMyListingsEvent(
                                event = event,
                                activeSession = currentActiveSessionState.value,
                                liveUserId = currentLiveUserState.value,
                                topRoute = backStack.lastOrNull(),
                                backStack = backStack,
                            )
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
                    val currentTopEntryState = rememberUpdatedState(backStack.lastOrNull())
                    val currentLiveUserState = rememberUpdatedState(liveSessionUserId)
                    val currentActiveSessionState = rememberUpdatedState(activeSellerSession)

                    LaunchedEffect(editorViewModel, activeSellerSession, key.entryId) {
                        editorViewModel.bind(activeSellerSession, null, key.entryId)
                        if (liveSessionUserId == null && backStack.lastOrNull() != AuthRoute) {
                            backStack.add(AuthRoute)
                        }
                    }

                    LaunchedEffect(editorViewModel) {
                        editorViewModel.events.collect { event ->
                            handleSellerEditorEvent(
                                event = event,
                                activeSession = currentActiveSessionState.value,
                                liveUserId = currentLiveUserState.value,
                                topRoute = currentTopEntryState.value,
                                backStack = backStack,
                            )
                        }
                    }

                    if (!canRenderSellerState(state.session, activeSellerSession, liveSessionUserId)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val callbacks = createSellerEditorCallbacks(
                            onRetryInitialization = editorViewModel::retryInitialization,
                            onRetryCategoryAttributes = editorViewModel::retryCategoryAttributes,
                            onRetryLocalities = editorViewModel::retryLocalities,
                            onValidatedPhotos = editorViewModel::queueUploads,
                            onSetCover = editorViewModel::setCoverPhoto,
                            onDeleteMedia = editorViewModel::deleteMedia,
                            onMoveLeft = editorViewModel::moveMediaLeft,
                            onMoveRight = editorViewModel::moveMediaRight,
                            onRetryMedia = editorViewModel::retryMediaSnapshot,
                            onImageError = editorViewModel::onPrivateImageLoadFailed,
                            onLocalMediaError = editorViewModel::setMediaErrorMessage,
                        )
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
                            onRetryInitialization = callbacks.onRetryInitialization,
                            onRetryCategoryAttributes = callbacks.onRetryCategoryAttributes,
                            onRetryLocalities = callbacks.onRetryLocalities,
                            onValidatedPhotos = callbacks.onValidatedPhotos,
                            onSetCover = callbacks.onSetCover,
                            onDeleteMedia = callbacks.onDeleteMedia,
                            onMoveLeft = callbacks.onMoveLeft,
                            onMoveRight = callbacks.onMoveRight,
                            onRetryMedia = callbacks.onRetryMedia,
                            onImageError = callbacks.onImageError,
                            onLocalMediaError = callbacks.onLocalMediaError,
                        )
                    }
                }
                is EditSellerListingRoute -> NavEntry(key) {
                    val editorViewModel: SellerListingEditorViewModel = hiltViewModel()
                    val state = editorViewModel.state.collectAsStateWithLifecycle().value
                    val currentTopEntryState = rememberUpdatedState(backStack.lastOrNull())
                    val currentLiveUserState = rememberUpdatedState(liveSessionUserId)
                    val currentActiveSessionState = rememberUpdatedState(activeSellerSession)

                    LaunchedEffect(editorViewModel, activeSellerSession, key.listingId, key.entryId) {
                        editorViewModel.bind(activeSellerSession, key.listingId, key.entryId)
                        if (liveSessionUserId == null && backStack.lastOrNull() != AuthRoute) {
                            backStack.add(AuthRoute)
                        }
                    }

                    LaunchedEffect(editorViewModel) {
                        editorViewModel.events.collect { event ->
                            handleSellerEditorEvent(
                                event = event,
                                activeSession = currentActiveSessionState.value,
                                liveUserId = currentLiveUserState.value,
                                topRoute = currentTopEntryState.value,
                                backStack = backStack,
                            )
                        }
                    }

                    if (!canRenderSellerState(state.session, activeSellerSession, liveSessionUserId)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val callbacks = createSellerEditorCallbacks(
                            onRetryInitialization = editorViewModel::retryInitialization,
                            onRetryCategoryAttributes = editorViewModel::retryCategoryAttributes,
                            onRetryLocalities = editorViewModel::retryLocalities,
                            onValidatedPhotos = editorViewModel::queueUploads,
                            onSetCover = editorViewModel::setCoverPhoto,
                            onDeleteMedia = editorViewModel::deleteMedia,
                            onMoveLeft = editorViewModel::moveMediaLeft,
                            onMoveRight = editorViewModel::moveMediaRight,
                            onRetryMedia = editorViewModel::retryMediaSnapshot,
                            onImageError = editorViewModel::onPrivateImageLoadFailed,
                            onLocalMediaError = editorViewModel::setMediaErrorMessage,
                        )
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
                            onRetryInitialization = callbacks.onRetryInitialization,
                            onRetryCategoryAttributes = callbacks.onRetryCategoryAttributes,
                            onRetryLocalities = callbacks.onRetryLocalities,
                            onValidatedPhotos = callbacks.onValidatedPhotos,
                            onSetCover = callbacks.onSetCover,
                            onDeleteMedia = callbacks.onDeleteMedia,
                            onMoveLeft = callbacks.onMoveLeft,
                            onMoveRight = callbacks.onMoveRight,
                            onRetryMedia = callbacks.onRetryMedia,
                            onImageError = callbacks.onImageError,
                            onLocalMediaError = callbacks.onLocalMediaError,
                        )
                    }
                }
                is ComingSoonRoute -> NavEntry(key) {
                    FairSwapComingSoonScreen(
                        title = key.featureTitle,
                        description = key.featureDescription,
                        statusLabel = "IN DEVELOPMENT",
                        backLabel = "← Go Back",
                        onBack = { activeBackStack.removeLastOrNull() }
                    )
                }
                else -> NavEntry(key) { Box(Modifier.fillMaxSize()) }
            }
        }
    )
        }
    }
}

fun accountGateSessionMatches(gateUserId: String?, liveSessionUserId: String?): Boolean {
    if (gateUserId == null || liveSessionUserId == null) return false
    return gateUserId == liveSessionUserId
}
