package com.fairswap.feature.favourites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.fairswap.core.designsystem.R as DesignR
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
    mutatingIds: Set<String> = emptySet(),
    onRemoveFavourite: (String) -> Unit = {},
    favouriteError: String? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack) { Text("←") }
                    Text(stringResource(id = DesignR.string.favourites_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(id = DesignR.string.action_refresh)) }
            }
        }

        if (state.stale) item {
            Text(stringResource(id = DesignR.string.favourites_showing_stale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        state.error?.let { message ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRefresh) { Text(stringResource(id = DesignR.string.action_retry)) }
                }
            }
        }

        favouriteError?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }

        if (state.authRequired) item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(id = DesignR.string.favourites_sign_in_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(id = DesignR.string.favourites_sign_in_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onAuth) { Text(stringResource(id = DesignR.string.favourites_sign_in_button)) }
                }
            }
        }

        if (state.policyRequired) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(id = DesignR.string.favourites_review_policies))
                OutlinedButton(onClick = onAuth) { Text(stringResource(id = DesignR.string.favourites_review_button)) }
            }
        }

        if (state.loading && state.cards.isEmpty()) item {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        if (state.unavailableCount > 0) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(id = DesignR.string.favourites_unavailable_count, state.unavailableCount), fontWeight = FontWeight.Bold)
                    Button(onClick = onClearUnavailable, enabled = !state.clearBusy) {
                        if (state.clearBusy) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Text(stringResource(id = DesignR.string.favourites_clear_unavailable))
                    }
                }
            }
        }

        // 2-Column Responsive Card Grid
        val chunkedCards = state.cards.chunked(2)
        items(chunkedCards, key = { chunk -> chunk.first().id }) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (listing in rowItems) {
                    Box(modifier = Modifier.weight(1f)) {
                        FavouriteListingCard(
                            listing = listing,
                            isMutating = listing.id in mutatingIds,
                            onRemove = { onRemoveFavourite(listing.id) },
                            onClick = { onListing(listing.id) }
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        if (!state.loading && state.cards.isEmpty() && state.unavailableCount == 0 && state.error == null && !state.authRequired) item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(id = DesignR.string.favourites_no_saved_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(id = DesignR.string.favourites_no_saved_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onBrowse) { Text(stringResource(id = DesignR.string.favourites_browse)) }
                }
            }
        }

        if (state.hasMore) item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = onLoadMore, enabled = !state.loadingMore) {
                    if (state.loadingMore) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text(stringResource(id = DesignR.string.favourites_load_more))
                }
            }
        } else if (state.page > 0) item {
            Text(stringResource(id = DesignR.string.favourites_end), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FavouriteListingCard(
    listing: ListingCard,
    isMutating: Boolean,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                if (listing.thumbnailUrl == null) {
                    Box(
                        Modifier.fillMaxWidth().height(130.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(id = DesignR.string.listing_detail_no_photo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else AsyncImage(
                    model = listing.thumbnailUrl,
                    contentDescription = "Photo of ${listing.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                IconButton(
                    onClick = onRemove,
                    enabled = !isMutating,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    if (isMutating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("♥", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Text(listing.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(listing.price, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("${listing.condition} · ${listing.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("📍 ${listing.location}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
