package com.fairswap.feature.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fairswap.core.designsystem.components.FairSwapChip
import com.fairswap.core.designsystem.R as DesignR
import com.fairswap.core.designsystem.components.FairSwapWordmark
import com.fairswap.core.discovery.ListingCard
import com.fairswap.core.discovery.ListingCondition
import com.fairswap.core.discovery.ListingSort
import com.fairswap.core.discovery.ResultErrorKind
import com.fairswap.core.discovery.SearchFilters
import com.fairswap.core.favourites.FavouriteValue

@Composable
fun DiscoveryScreen(
    state: DiscoveryUiState,
    onEdit: ((SearchFilters) -> SearchFilters) -> Unit,
    onCitySelected: (String?) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryMetadata: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit = {},
    onListingSelected: (String) -> Unit,
    favouriteValues: Map<String, FavouriteValue> = emptyMap(),
    mutatingIds: Set<String> = emptySet(),
    onFavourite: (String, Boolean) -> Unit = { _, _ -> },
    favouriteError: String? = null,
    onRecentSearchSelected: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        // Header Row: Compact Wordmark & Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FairSwapWordmark()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onNotifications) {
                        Icon(Icons.Default.Notifications, contentDescription = stringResource(id = DesignR.string.notifications_title))
                    }
                    OutlinedButton(onClick = onAccount) { Text(stringResource(id = DesignR.string.account_title)) }
                }
            }
        }

        // Search Box & Quick Filter Trigger
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.draft.query,
                    onValueChange = { value -> onEdit { it.copy(query = value) } },
                    label = { Text(stringResource(id = R.string.discovery_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onApply, enabled = !state.loadingFirstPage) {
                    Text(stringResource(id = R.string.discovery_search_button))
                }
            }
        }

        // Recent Search Chips
        if (state.recentSearches.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.discovery_recent_searches), style = MaterialTheme.typography.labelMedium)
                        Text(
                            stringResource(id = R.string.discovery_clear),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onClearRecentSearches() }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.recentSearches.forEach { query ->
                            FairSwapChip(
                                text = query,
                                onClick = { onRecentSearchSelected(query) }
                            )
                        }
                    }
                }
            }
        }

        // Category Shortcut Row
        if (state.categories.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(id = R.string.discovery_categories), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FairSwapChip(
                            text = stringResource(id = R.string.discovery_all),
                            onClick = { onEdit { it.copy(categoryId = null) }; onApply() }
                        )
                        state.categories.forEach { cat ->
                            FairSwapChip(
                                text = cat.name,
                                onClick = { onEdit { it.copy(categoryId = cat.id) }; onApply() }
                            )
                        }
                    }
                }
            }
        }

        // Filter Controls Bar (Location / Filter Toggle / Sort)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentCityName = state.cities.firstOrNull { it.id == state.draft.cityId }?.name ?: "All India"
                Text(
                    text = "📍 $currentCityName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showFilterSheet = !showFilterSheet }) {
                        Text(if (showFilterSheet) stringResource(id = R.string.discovery_hide_filters) else stringResource(id = R.string.discovery_show_filters))
                    }
                    OutlinedButton(onClick = onRefresh, enabled = !state.loadingFirstPage) {
                        Text(stringResource(id = R.string.discovery_refresh))
                    }
                }
            }
        }

        // Expandable Filters Panel
        if (showFilterSheet) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Selector(
                            label = stringResource(id = R.string.discovery_city),
                            selected = state.cities.firstOrNull { it.id == state.draft.cityId }?.name ?: stringResource(id = R.string.discovery_all),
                            options = listOf(null to stringResource(id = R.string.discovery_all)) + state.cities.map { it.id to it.name },
                            onSelect = onCitySelected,
                        )
                        if (state.draft.cityId != null) {
                            Selector(
                                label = stringResource(id = R.string.discovery_locality),
                                selected = state.localities.firstOrNull { it.id == state.draft.localityId }?.name ?: stringResource(id = R.string.discovery_all),
                                options = listOf(null to stringResource(id = R.string.discovery_all)) + state.localities.map { it.id to it.name },
                                onSelect = { id -> onEdit { it.copy(localityId = id) } },
                            )
                        }
                        Selector(
                            label = stringResource(id = R.string.discovery_condition),
                            selected = state.draft.condition?.label ?: stringResource(id = R.string.discovery_all),
                            options = listOf(null to stringResource(id = R.string.discovery_all)) + ListingCondition.entries.map { it to it.label },
                            onSelect = { choice -> onEdit { it.copy(condition = choice) } },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.draft.minRupees,
                                onValueChange = { value -> onEdit { it.copy(minRupees = value) } },
                                label = { Text(stringResource(id = R.string.discovery_min_price)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.draft.maxRupees,
                                onValueChange = { value -> onEdit { it.copy(maxRupees = value) } },
                                label = { Text(stringResource(id = R.string.discovery_max_price)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Selector(
                            label = stringResource(id = R.string.discovery_sort),
                            selected = state.draft.sort.label,
                            options = ListingSort.entries.map { it to it.label },
                            onSelect = { choice -> onEdit { it.copy(sort = choice) } },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onApply(); showFilterSheet = false }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(id = R.string.discovery_apply_filters))
                            }
                            OutlinedButton(onClick = onClear) {
                                Text(stringResource(id = R.string.discovery_clear))
                            }
                        }
                    }
                }
            }
        }

        // Validation & Metadata Errors
        state.inputError?.let { message -> item { ErrorText(message) } }
        favouriteError?.let { message -> item { ErrorText(message) } }
        state.metadataError?.let { message ->
            item {
                Column {
                    ErrorText(message)
                    OutlinedButton(onClick = onRetryMetadata) { Text(stringResource(id = R.string.discovery_retry_metadata)) }
                }
            }
        }
        if (state.loadingMetadata || state.loadingFirstPage) item {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // Result Count Summary Header
        item {
            val stale = if (state.results.isStale) stringResource(id = R.string.discovery_results_stale) else ""
            Text(stringResource(id = R.string.discovery_results_found, state.results.totalCount) + stale, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        state.results.error?.let { message ->
            item {
                Column {
                    ErrorText(message)
                    OutlinedButton(
                        onClick = if (state.results.errorKind == ResultErrorKind.NEXT_PAGE) onLoadMore else onRefresh,
                    ) { Text(stringResource(id = R.string.discovery_retry)) }
                }
            }
        }

        if (!state.loadingFirstPage && state.results.items.isEmpty() && state.results.error == null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(id = R.string.discovery_no_listings), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(id = R.string.discovery_no_listings_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onClear) { Text(stringResource(id = R.string.discovery_clear_all_filters)) }
                    }
                }
            }
        }

        // Modern 2-Column Listing Cards
        val chunkedItems = state.results.items.chunked(2)
        items(chunkedItems, key = { chunk -> chunk.first().id }) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (listing in rowItems) {
                    Box(modifier = Modifier.weight(1f)) {
                        ListingCardView(
                            listing = listing,
                            onClick = { onListingSelected(listing.id) },
                            saved = favouriteValues[listing.id] == FavouriteValue.FAVOURITED,
                            mutating = listing.id in mutatingIds,
                            onFavourite = { onFavourite(listing.id, favouriteValues[listing.id] != FavouriteValue.FAVOURITED) },
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        if (state.results.canLoadMore && state.results.errorKind != ResultErrorKind.NEXT_PAGE) item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = onLoadMore, enabled = !state.loadingMore && !state.loadingFirstPage) {
                    if (state.loadingMore) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text(stringResource(id = R.string.discovery_load_more))
                }
            }
        }

        // Safety Tip Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(id = R.string.discovery_safety_tip_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                    Text(stringResource(id = R.string.discovery_safety_tip_1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(stringResource(id = R.string.discovery_safety_tip_2), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(stringResource(id = R.string.discovery_safety_tip_3), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(stringResource(id = R.string.discovery_safety_tip_4), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(stringResource(id = R.string.discovery_safety_tip_5), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun <T> Selector(
    label: String,
    selected: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    expanded = false
                    onSelect(value)
                })
            }
        }
    }
}

@Composable
private fun ListingCardView(
    listing: ListingCard,
    onClick: () -> Unit,
    saved: Boolean,
    mutating: Boolean,
    onFavourite: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                if (listing.thumbnailUrl == null) {
                    PhotoPlaceholder()
                } else {
                    AsyncImage(
                        model = listing.thumbnailUrl,
                        contentDescription = "Photo of ${listing.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(130.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                IconButton(
                    onClick = onFavourite,
                    enabled = !mutating,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(if (saved) "♥" else "♡", style = MaterialTheme.typography.titleLarge, color = if (saved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(listing.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(listing.price, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("${listing.condition} · ${listing.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(listing.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhotoPlaceholder() {
    Box(
        modifier = Modifier.fillMaxWidth().height(130.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Text("No photo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}
