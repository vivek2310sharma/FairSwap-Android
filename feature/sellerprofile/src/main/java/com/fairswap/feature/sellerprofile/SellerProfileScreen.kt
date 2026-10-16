package com.fairswap.feature.sellerprofile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.fairswap.core.designsystem.R as DesignR
import com.fairswap.core.discovery.publicDate

@Composable
fun SellerProfileScreen(
    state: SellerProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onListing: (String) -> Unit,
    onReportUser: (String) -> Unit = {},
    onBlockUser: (String) -> Unit = {},
    onReviews: () -> Unit = {},
    onFollow: () -> Unit = {},
    onVerification: () -> Unit = {},
    blockActionPending: Boolean = false,
    blockActionError: String? = null,
    onClearBlockError: () -> Unit = {},
) {
    var showBlockDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text(stringResource(id = DesignR.string.action_back)) } }
        if (state.loading && state.profile == null) item { CircularProgressIndicator() }
        if (state.unavailable) item { Text(stringResource(id = DesignR.string.seller_profile_unavailable), style = MaterialTheme.typography.headlineSmall) }
        blockActionError?.let { err -> item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearBlockError) { Text(stringResource(id = DesignR.string.seller_profile_dismiss)) }
            }
        } }
        state.error?.let { error -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text(stringResource(id = DesignR.string.action_retry)) }
            }
        } }
        state.profile?.let { profile ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile.displayName.take(1).uppercase(), style = MaterialTheme.typography.displayMedium)
                    Text(profile.displayName, style = MaterialTheme.typography.headlineMedium)
                    profile.bio?.let { Text(it) }
                    listOfNotNull(profile.city, profile.state).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(", "))
                    }
                    publicDate(profile.createdAt)?.let { Text("Member since $it") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onVerification) { Text("Identity Verification — Coming soon") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onReviews) { Text("Seller Reviews — Coming soon") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onFollow) { Text("Follow Seller — Coming soon") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onReportUser(profile.id) }) { Text(stringResource(id = DesignR.string.listing_detail_report)) }
                        OutlinedButton(onClick = { showBlockDialog = true }, enabled = !blockActionPending) { 
                            Text(if (blockActionPending) "Blocking..." else "Block") 
                        }
                    }
                    Text(stringResource(id = DesignR.string.seller_profile_listings_shown, profile.displayedListingCount), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(profile.listings, key = { it.id }) { listing ->
                Card(Modifier.fillMaxWidth().clickable { onListing(listing.id) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(listing.title, style = MaterialTheme.typography.titleMedium)
                        Text(listing.price)
                        Text("${listing.condition} · ${listing.category}")
                        Text(listing.location)
                    }
                }
            }
            if (state.loading) item { Row { CircularProgressIndicator() } }
        }
    }

    if (showBlockDialog && state.profile != null) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(stringResource(id = DesignR.string.seller_profile_block_dialog_title)) },
            text = { Text(stringResource(id = DesignR.string.seller_profile_block_dialog_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockDialog = false
                    onBlockUser(state.profile.id)
                }) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text(stringResource(id = DesignR.string.action_cancel))
                }
            }
        )
    }
}
