package com.fairswap.feature.sellerlistings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fairswap.core.sellerlistings.SellerLifecycleAction
import com.fairswap.core.sellerlistings.SellerListingStatus
import com.fairswap.core.sellerlistings.SellerListingSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    state: MyListingsUiState,
    onBack: () -> Unit,
    onCreateListing: () -> Unit,
    onEditListing: (String) -> Unit,
    onRefresh: () -> Unit,
    onLifecycleAction: (String, SellerLifecycleAction) -> Unit,
    onClearMessages: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingActionConfirmation by remember { mutableStateOf<Pair<String, SellerLifecycleAction>?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessages()
        }
    }

    LaunchedEffect(state.actionNotice) {
        state.actionNotice?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Listings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Refresh")
                    }
                    Button(
                        onClick = onCreateListing,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("+ Create Listing")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading && state.listings.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.listings.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (state.errorMessage != null) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onRefresh) {
                                Text("Retry")
                            }
                        } else {
                            Text(
                                text = "No listings found.",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create your first draft listing to sell items on FairSwap.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onCreateListing) {
                                    Text("Create Draft Listing")
                                }
                                OutlinedButton(onClick = onRefresh) {
                                    Text("Refresh")
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(state.listings, key = { it.id }) { item ->
                            SellerListingCard(
                                item = item,
                                isMutating = state.mutatingListingId == item.id,
                                onEdit = { onEditListing(item.id) },
                                onLifecycleAction = { action ->
                                    if (action == SellerLifecycleAction.MARK_SOLD || action == SellerLifecycleAction.RENEW) {
                                        pendingActionConfirmation = item.id to action
                                    } else {
                                        onLifecycleAction(item.id, action)
                                    }
                                },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    pendingActionConfirmation?.let { (listingId, action) ->
        val title = when (action) {
            SellerLifecycleAction.MARK_SOLD -> "Mark as Sold?"
            SellerLifecycleAction.RENEW -> "Renew Expired Listing?"
            else -> "Confirm Action"
        }
        val text = when (action) {
            SellerLifecycleAction.MARK_SOLD -> "Marking this item as sold cannot be undone. It will move to completed sales."
            SellerLifecycleAction.RENEW -> "Renewing this listing will send it back for moderator review."
            else -> "Are you sure you want to perform this action?"
        }

        AlertDialog(
            onDismissRequest = { pendingActionConfirmation = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLifecycleAction(listingId, action)
                        pendingActionConfirmation = null
                    },
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingActionConfirmation = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SellerListingCard(
    item: SellerListingSummary,
    isMutating: Boolean,
    onEdit: () -> Unit,
    onLifecycleAction: (SellerLifecycleAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(status = item.status)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${item.formattedPrice} • ${item.categoryName} • ${item.cityName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Revision #${item.revision} • Renewals: ${item.renewalCount}/3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            val rejection = item.rejectionReason
            if (!rejection.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Rejection Reason:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = rejection,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            val removal = item.removalReason
            if (!removal.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Moderation Removal Reason:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = removal,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            if (item.requiresPhotoForSubmit) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "At least one ready photo is required to submit for review. Photo upload will be available in Android-5.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isMutating) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.canEdit) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("Edit")
                        }
                    }

                    when (item.status) {
                        SellerListingStatus.ACTIVE -> {
                            OutlinedButton(
                                onClick = { onLifecycleAction(SellerLifecycleAction.PAUSE) },
                                modifier = Modifier.padding(end = 8.dp),
                            ) { Text("Pause") }
                            OutlinedButton(
                                onClick = { onLifecycleAction(SellerLifecycleAction.RESERVE) },
                                modifier = Modifier.padding(end = 8.dp),
                            ) { Text("Reserve") }
                            Button(onClick = { onLifecycleAction(SellerLifecycleAction.MARK_SOLD) }) {
                                Text("Mark Sold")
                            }
                        }
                        SellerListingStatus.RESERVED -> {
                            OutlinedButton(
                                onClick = { onLifecycleAction(SellerLifecycleAction.UNRESERVE) },
                                modifier = Modifier.padding(end = 8.dp),
                            ) { Text("Unreserve") }
                            Button(onClick = { onLifecycleAction(SellerLifecycleAction.MARK_SOLD) }) {
                                Text("Mark Sold")
                            }
                        }
                        SellerListingStatus.PAUSED -> {
                            OutlinedButton(
                                onClick = { onLifecycleAction(SellerLifecycleAction.RESUME) },
                                modifier = Modifier.padding(end = 8.dp),
                            ) { Text("Resume") }
                            Button(onClick = { onLifecycleAction(SellerLifecycleAction.MARK_SOLD) }) {
                                Text("Mark Sold")
                            }
                        }
                        SellerListingStatus.EXPIRED -> {
                            if (item.canRenew) {
                                Button(onClick = { onLifecycleAction(SellerLifecycleAction.RENEW) }) {
                                    Text("Renew")
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: SellerListingStatus) {
    val colors = when (status) {
        SellerListingStatus.ACTIVE -> SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        SellerListingStatus.DRAFT -> SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        SellerListingStatus.PENDING_REVIEW -> SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        SellerListingStatus.REJECTED, SellerListingStatus.EXPIRED, SellerListingStatus.REMOVED ->
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                labelColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        else -> SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SuggestionChip(
        onClick = {},
        label = { Text(status.label, style = MaterialTheme.typography.labelMedium) },
        colors = colors,
    )
}
