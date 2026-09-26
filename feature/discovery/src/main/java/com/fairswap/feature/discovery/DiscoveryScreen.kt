package com.fairswap.feature.discovery

import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fairswap.core.discovery.ListingCard
import com.fairswap.core.discovery.ListingCondition
import com.fairswap.core.discovery.ListingSort
import com.fairswap.core.discovery.SearchFilters
import com.fairswap.core.discovery.ResultErrorKind

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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("FairSwap", style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = onAccount) { Text("Account / Sign in") }
            }
        }
        item {
            OutlinedTextField(
                value = state.draft.query,
                onValueChange = { value -> onEdit { it.copy(query = value) } },
                label = { Text("Search listings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Selector(
                label = "Category",
                selected = state.categories.firstOrNull { it.id == state.draft.categoryId }?.name ?: "All categories",
                options = listOf(null to "All categories") + state.categories.map { it.id to it.name },
                onSelect = { id -> onEdit { it.copy(categoryId = id) } },
            )
        }
        item {
            Selector(
                label = "City",
                selected = state.cities.firstOrNull { it.id == state.draft.cityId }?.name ?: "All cities",
                options = listOf(null to "All cities") + state.cities.map { it.id to it.name },
                onSelect = onCitySelected,
            )
        }
        if (state.draft.cityId != null) item {
            Selector(
                label = "Locality",
                selected = state.localities.firstOrNull { it.id == state.draft.localityId }?.name ?: "All localities",
                options = listOf(null to "All localities") + state.localities.map { it.id to it.name },
                onSelect = { id -> onEdit { it.copy(localityId = id) } },
            )
        }
        item {
            Selector(
                label = "Condition",
                selected = state.draft.condition?.label ?: "Any condition",
                options = listOf(null to "Any condition") + ListingCondition.entries.map { it to it.label },
                onSelect = { choice -> onEdit { it.copy(condition = choice) } },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.draft.minRupees,
                    onValueChange = { value -> onEdit { it.copy(minRupees = value) } },
                    label = { Text("Min ₹") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.draft.maxRupees,
                    onValueChange = { value -> onEdit { it.copy(maxRupees = value) } },
                    label = { Text("Max ₹") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Selector(
                label = "Sort",
                selected = state.draft.sort.label,
                options = ListingSort.entries.map { it to it.label },
                onSelect = { choice -> onEdit { it.copy(sort = choice) } },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApply, enabled = !state.loadingFirstPage) { Text("Search / Apply") }
                OutlinedButton(onClick = onClear) { Text("Clear filters") }
                OutlinedButton(onClick = onRefresh, enabled = !state.loadingFirstPage) { Text("Refresh") }
            }
        }
        state.inputError?.let { message -> item { ErrorText(message) } }
        state.metadataError?.let { message ->
            item {
                Column {
                    ErrorText(message)
                    OutlinedButton(onClick = onRetryMetadata) { Text("Retry filters") }
                }
            }
        }
        if (state.loadingMetadata || state.loadingFirstPage) item { CircularProgressIndicator() }
        item {
            val stale = if (state.results.isStale) " · Showing previous results" else ""
            Text("${state.results.totalCount} results$stale", style = MaterialTheme.typography.titleMedium)
        }
        state.results.error?.let { message ->
            item {
                Column {
                    ErrorText(message)
                    OutlinedButton(
                        onClick = if (state.results.errorKind == ResultErrorKind.NEXT_PAGE) onLoadMore else onRefresh,
                    ) { Text("Retry") }
                }
            }
        }
        if (!state.loadingFirstPage && state.results.items.isEmpty() && state.results.error == null) {
            item { Text("No listings match these filters.") }
        }
        items(state.results.items, key = { it.id }) { listing -> ListingCardView(listing) }
        if (state.results.canLoadMore && state.results.errorKind != ResultErrorKind.NEXT_PAGE) item {
            Button(onClick = onLoadMore, enabled = !state.loadingMore && !state.loadingFirstPage) {
                if (state.loadingMore) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Load more")
            }
        }
        if (state.results.items.isNotEmpty() && !state.results.canLoadMore) {
            item { Text("End of results", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun ListingCardView(listing: ListingCard) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (listing.thumbnailUrl == null) {
                PhotoPlaceholder()
            } else {
                AsyncImage(
                    model = listing.thumbnailUrl,
                    contentDescription = "Photo of ${listing.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Text(listing.title, style = MaterialTheme.typography.titleMedium)
            Text(listing.price, style = MaterialTheme.typography.titleSmall)
            Text("${listing.condition} · ${listing.category}")
            Text(listing.location, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhotoPlaceholder() {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Text("No photo", color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}
