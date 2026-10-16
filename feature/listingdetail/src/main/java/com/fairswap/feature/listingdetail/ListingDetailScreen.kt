package com.fairswap.feature.listingdetail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
    isOwner: Boolean = false,
    onMessageSeller: () -> Unit = {},
    onReportListing: (String) -> Unit = {},
    onMakeOffer: () -> Unit = {},
    onBuyNow: () -> Unit = {},
    onDelivery: () -> Unit = {},
    onDispute: () -> Unit = {},
    onShareListing: (listingId: String, title: String) -> Unit = { _, _ -> },
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        // Top Navigation Header
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onBack) { Text("← " + stringResource(id = DesignR.string.action_back)) }
                state.detail?.let { detail ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { onFavourite(favourite != FavouriteValue.FAVOURITED) }, enabled = !favouriteMutating) {
                            Text(if (favourite == FavouriteValue.FAVOURITED) "♥" else "♡", style = MaterialTheme.typography.headlineSmall, color = if (favourite == FavouriteValue.FAVOURITED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                        OutlinedButton(onClick = { onShareListing(detail.id, detail.title) }) {
                            Text(stringResource(id = DesignR.string.listing_detail_share) + " 🔗")
                        }
                    }
                }
            }
        }

        if (state.loading && state.detail == null) item {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        if (state.unavailable) item {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(id = DesignR.string.listing_detail_unavailable_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(id = DesignR.string.listing_detail_unavailable_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        state.error?.let { error -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text(stringResource(id = DesignR.string.action_retry)) }
            }
        } }

        state.detail?.let { detail ->
            favouriteError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }

            // Owner Banner
            if (isOwner) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(id = DesignR.string.listing_detail_you_own),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Photo Gallery
            item {
                if (detail.media.isEmpty() || detail.media.all { it.mediumUrl == null }) {
                    Box(
                        Modifier.fillMaxWidth().height(240.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(id = DesignR.string.listing_detail_no_photo), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(detail.media, key = { it.id }) { media ->
                            if (media.mediumUrl == null) {
                                Box(
                                    Modifier.width(300.dp).height(240.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) { Text(stringResource(id = DesignR.string.listing_detail_no_photo), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            } else {
                                AsyncImage(
                                    model = media.mediumUrl,
                                    contentDescription = "Photo of ${detail.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(300.dp).height(240.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                        }
                    }
                }
            }

            // Price, Title & Location Info
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = detail.formattedPrice,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        if (detail.priceType == "negotiable") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = stringResource(id = DesignR.string.listing_detail_negotiable),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(detail.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(id = DesignR.string.listing_detail_condition_category, detail.condition, detail.category), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("📍 " + listOfNotNull(detail.locality, detail.city).joinToString(", "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    publicDate(detail.publishedAt ?: detail.createdAt)?.let { Text(stringResource(id = DesignR.string.listing_detail_posted_on, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            // Description
            detail.description?.let { description -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(id = DesignR.string.listing_detail_description), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } }

            // Specifications
            if (detail.specifications.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(id = DesignR.string.listing_detail_specifications), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        detail.specifications.forEach { spec ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(spec.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                Text(spec.value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Seller Card & Actions
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(id = DesignR.string.listing_detail_seller_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(detail.seller.displayName, style = MaterialTheme.typography.titleMedium)
                        publicDate(detail.seller.createdAt)?.let { Text(stringResource(id = DesignR.string.listing_detail_member_since, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { onSeller(detail.sellerId) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(id = DesignR.string.listing_detail_seller_profile))
                            }
                            if (!isOwner) {
                                Button(
                                    onClick = onMessageSeller,
                                    enabled = !state.isMessagingActionLoading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (state.isMessagingActionLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.height(16.dp).width(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Text(stringResource(id = DesignR.string.listing_detail_message_seller))
                                    }
                                }
                                OutlinedButton(onClick = { onReportListing(detail.id) }) {
                                    Text(stringResource(id = DesignR.string.listing_detail_report))
                                }
                            }
                        }
                        state.messagingActionError?.let { err ->
                            Text(err, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (!isOwner) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(id = DesignR.string.listing_detail_planned_features), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(id = DesignR.string.listing_detail_planned_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = onMakeOffer, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(id = DesignR.string.planned_feature_make_offer))
                                }
                                OutlinedButton(onClick = onBuyNow, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(id = DesignR.string.planned_feature_buy_now))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = onDelivery, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(id = DesignR.string.planned_feature_delivery))
                                }
                                OutlinedButton(onClick = onDispute, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(id = DesignR.string.planned_feature_disputes))
                                }
                            }
                        }
                    }
                }
            }

            // Safety Advice Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(id = DesignR.string.safety_advice_buyers), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(id = DesignR.string.safety_advice_1), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(id = DesignR.string.safety_advice_2), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(id = DesignR.string.safety_advice_3), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(stringResource(id = DesignR.string.safety_advice_4), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(id = DesignR.string.safety_advice_5), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
