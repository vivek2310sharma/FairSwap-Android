package com.fairswap.feature.listingdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fairswap.core.discovery.publicDate
import com.fairswap.core.favourites.FavouriteValue

@Composable
fun ListingDetailScreen(
    state: ListingDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSeller: (String) -> Unit,
    favourite: FavouriteValue = FavouriteValue.UNKNOWN,
    favouriteMutating: Boolean = false,
    onFavourite: (Boolean) -> Unit = {},
    favouriteError: String? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("Back") } }
        if (state.loading && state.detail == null) item {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (state.unavailable) item { Text("Listing unavailable", style = MaterialTheme.typography.headlineSmall) }
        state.error?.let { error -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Retry") }
            }
        } }
        state.detail?.let { detail ->
            favouriteError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item {
                IconButton(onClick = { onFavourite(favourite != FavouriteValue.FAVOURITED) }, enabled = !favouriteMutating) {
                    Text(if (favourite == FavouriteValue.FAVOURITED) "♥ Saved" else "♡ Save", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                if (detail.media.isEmpty() || detail.media.all { it.mediumUrl == null }) {
                    Box(Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) { Text("No photo") }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(detail.media, key = { it.id }) { media ->
                            if (media.mediumUrl == null) {
                                Box(Modifier.width(280.dp).height(220.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center) { Text("No photo") }
                            } else {
                                AsyncImage(
                                    model = media.mediumUrl,
                                    contentDescription = "Photo of ${detail.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(280.dp).height(220.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(detail.formattedPrice, style = MaterialTheme.typography.headlineMedium)
                    if (detail.priceType == "negotiable") Text("Negotiable", color = MaterialTheme.colorScheme.primary)
                    Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                    Text("${detail.condition} · ${detail.category}")
                    Text(listOfNotNull(detail.locality, detail.city).joinToString(", "))
                    publicDate(detail.publishedAt ?: detail.createdAt)?.let { Text("Posted $it") }
                }
            }
            detail.description?.let { description -> item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Description", style = MaterialTheme.typography.titleMedium)
                    Text(description)
                }
            } }
            if (detail.specifications.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Specifications", style = MaterialTheme.typography.titleMedium)
                    detail.specifications.forEach { spec -> Text("${spec.label}: ${spec.value}") }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Seller", style = MaterialTheme.typography.titleMedium)
                        Text(detail.seller.displayName)
                        publicDate(detail.seller.createdAt)?.let { Text("Member since $it") }
                        Button(onClick = { onSeller(detail.sellerId) }) { Text("View Seller Profile") }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Stay safe on FairSwap", style = MaterialTheme.typography.titleMedium)
                    Text("Meet in a public place, inspect the item, and avoid paying before you are satisfied.")
                }
            }
            if (state.loading) item { Row { CircularProgressIndicator() } }
        }
    }
}
