package com.fairswap.feature.favourites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fairswap.core.discovery.ListingCard

@Composable
fun FavouritesScreen(
    state: FavouritesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onClearUnavailable: () -> Unit,
    onBrowse: () -> Unit,
    onListing: (String) -> Unit,
    onAuth: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item { OutlinedButton(onClick = onBack) { Text("Back") } }
        item { Text("Saved Favourites", style = MaterialTheme.typography.headlineMedium) }
        if (state.stale) item { Text("Showing saved results from your last successful load.") }
        state.error?.let { message -> item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRefresh) { Text("Retry") }
            }
        } }
        if (state.authRequired) item { OutlinedButton(onClick = onAuth) { Text("Sign in to view saved favourites") } }
        if (state.policyRequired) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Review the current policies before changing saved favourites.")
                OutlinedButton(onClick = onAuth) { Text("Review policies") }
            }
        }
        if (state.loading && state.cards.isEmpty()) item { CircularProgressIndicator() }
        if (state.unavailableCount > 0) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${state.unavailableCount} saved items are no longer available.")
                    Button(onClick = onClearUnavailable, enabled = !state.clearBusy) {
                        if (state.clearBusy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Clear unavailable")
                    }
                }
            }
        }
        items(state.cards, key = { it.id }) { listing -> FavouriteListingCard(listing) { onListing(listing.id) } }
        if (state.page > 0 && state.cards.isEmpty() && state.unavailableCount == 0 && !state.loading && state.error == null) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No saved favourites yet. Tap a heart on a listing to find it here.")
                Button(onClick = onBrowse) { Text("Browse listings") }
            }
        }
        if (state.page == 0 && !state.loading && state.error == null && !state.authRequired) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved items appear here after you tap the heart.")
                Button(onClick = onBrowse) { Text("Browse listings") }
            }
        }
        if (state.hasMore) item {
            Button(onClick = onLoadMore, enabled = !state.loadingMore) {
                if (state.loadingMore) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Load more")
            }
        } else if (state.page > 0) item { Text("End of saved favourites", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun FavouriteListingCard(listing: ListingCard, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (listing.thumbnailUrl == null) {
                Box(Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("No photo") }
            } else AsyncImage(
                model = listing.thumbnailUrl,
                contentDescription = "Photo of ${listing.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Text(listing.title, style = MaterialTheme.typography.titleMedium)
            Text(listing.price, style = MaterialTheme.typography.titleSmall)
            Text("${listing.condition} · ${listing.category}")
            Text(listing.location, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
