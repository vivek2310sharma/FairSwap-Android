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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fairswap.core.discovery.publicDate

@Composable
fun SellerProfileScreen(
    state: SellerProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onListing: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("Back") } }
        if (state.loading && state.profile == null) item { CircularProgressIndicator() }
        if (state.unavailable) item { Text("Seller unavailable", style = MaterialTheme.typography.headlineSmall) }
        state.error?.let { error -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Retry") }
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
                    Text("${profile.displayedListingCount} public listings shown", style = MaterialTheme.typography.titleMedium)
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
}
