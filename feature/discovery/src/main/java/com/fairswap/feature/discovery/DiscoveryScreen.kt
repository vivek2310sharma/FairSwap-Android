package com.fairswap.feature.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.fairswap.core.designsystem.R as DesignR
import com.fairswap.core.designsystem.components.FairSwapChip
import com.fairswap.core.designsystem.components.FairSwapWordmark
import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.ListingCard
import com.fairswap.core.discovery.ListingCondition
import com.fairswap.core.discovery.ListingSort
import com.fairswap.core.discovery.ResultErrorKind
import com.fairswap.core.discovery.SearchFilters
import com.fairswap.core.favourites.FavouriteValue
import java.util.Locale

private val FairSwapGreen = Color(0xFF008C45)
private val FairSwapMint = Color(0xFFE8F9E8)
private val FairSwapBlue = Color(0xFF0878F9)
private val Ink = Color(0xFF0C1D43)
private val MutedInk = Color(0xFF607089)
private val SoftBorder = Color(0xFFE2E8F0)

private data class Shortcut(val label: String, val icon: ImageVector, val slug: String)

@Composable
fun DiscoveryScreen(
    state: DiscoveryUiState,
    isAuthenticated: Boolean = false,
    onEdit: ((SearchFilters) -> SearchFilters) -> Unit,
    onCitySelected: (String?) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryMetadata: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit = {},
    onFavourites: () -> Unit = {},
    onListingSelected: (String) -> Unit,
    favouriteValues: Map<String, FavouriteValue> = emptyMap(),
    mutatingIds: Set<String> = emptySet(),
    onFavourite: (String, Boolean) -> Unit = { _, _ -> },
    favouriteError: String? = null,
    onRecentSearchSelected: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var activeHowItWorksStep by remember { mutableIntStateOf(0) }

    if (showLocationDialog) {
        LocationSelectionDialog(
            cities = state.cities,
            currentCityId = state.draft.cityId,
            onDismissRequest = { showLocationDialog = false },
            onCitySelected = { cityId ->
                onCitySelected(cityId)
                onApply()
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFCFDFD)),
        verticalArrangement = Arrangement.spacedBy(17.dp),
        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 150.dp),
    ) {
        item {
            val currentCityName = state.cities.firstOrNull { it.id == state.draft.cityId }?.name ?: "All India"
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FairSwapWordmark()
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF0FAF2),
                    border = BorderStroke(1.dp, Color(0xFFD9F0DF)),
                    modifier = Modifier.clickable { showLocationDialog = true }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = FairSwapGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(currentCityName, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAuthenticated) {
                        IconButton(onClick = onNotifications, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.NotificationsNone, stringResource(id = DesignR.string.notifications_title), tint = Ink, modifier = Modifier.size(23.dp))
                        }
                    }
                    IconButton(onClick = onAccount, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.PersonOutline, stringResource(id = DesignR.string.account_title), tint = FairSwapGreen, modifier = Modifier.size(25.dp))
                    }
                }
            }
        }

        // Hero from the agreed reference screenshot: pale mint green container, top branding row, handshake characters, and pill search bar.
        item {
            Surface(
                color = FairSwapMint,
                shape = RoundedCornerShape(27.dp),
                border = BorderStroke(1.dp, Color(0xFFD5F2D7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Top branding row inside hero container
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Logo + Wordmark + Tagline
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(DesignR.drawable.fairswap_logo_mark),
                                        contentDescription = "FairSwap Logo",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Fair", color = Ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    Text("Swap", color = FairSwapGreen, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                }
                                Text(
                                    "Buy • Sell • Swap • Share Locally",
                                    color = MutedInk,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Thin Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(28.dp)
                                .background(Color(0xFFCBEED0))
                        )

                        // Right: Trust Badge (2 lines)
                        Surface(
                            color = Color(0xFFE0F2E2),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBEED0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = FairSwapGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = "Your Local",
                                        color = FairSwapGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        lineHeight = 11.sp
                                    )
                                    Text(
                                        text = "Community Marketplace",
                                        color = FairSwapGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Main Content: Dedicated Non-Overlapping Layout Areas
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        if (maxWidth >= 380.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(155.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Dedicated Left Column: Headline & Subhead
                                Column(
                                    modifier = Modifier.weight(0.58f).padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.discovery_hero_headline),
                                        color = Ink,
                                        fontSize = 20.sp,
                                        lineHeight = 23.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(id = R.string.discovery_hero_subhead),
                                        color = Color(0xFF243650),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Dedicated Right Area: Handshake Characters
                                Box(
                                    modifier = Modifier.weight(0.42f).height(155.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.discovery_handshake_hero),
                                        contentDescription = "Two FairSwap community members greeting each other",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = stringResource(id = R.string.discovery_hero_headline),
                                        color = Ink,
                                        fontSize = 19.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = stringResource(id = R.string.discovery_hero_subhead),
                                        color = Color(0xFF243650),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(140.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.discovery_handshake_hero),
                                        contentDescription = "Two FairSwap community members greeting each other",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // Large White Pill Search Bar
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, Color(0xFFE7ECE9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(start = 13.dp, end = 5.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = FairSwapGreen, modifier = Modifier.size(23.dp))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f).padding(vertical = 9.dp)) {
                                if (state.draft.query.isBlank()) {
                                    Text("Search for bikes, phones, furniture...", color = MutedInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                BasicTextField(
                                    value = state.draft.query,
                                    onValueChange = { value -> onEdit { it.copy(query = value) } },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink, fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Button(
                                onClick = onApply,
                                enabled = !state.loadingFirstPage,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp)
                            ) { Text(stringResource(id = R.string.discovery_search_button), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        if (state.recentSearches.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(id = R.string.discovery_recent_searches), style = MaterialTheme.typography.labelMedium, color = MutedInk)
                        Text(stringResource(id = R.string.discovery_clear), style = MaterialTheme.typography.labelSmall, color = FairSwapGreen, modifier = Modifier.clickable { onClearRecentSearches() })
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.recentSearches.forEach { query -> FairSwapChip(text = query, onClick = { onRecentSearchSelected(query) }) }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(id = R.string.discovery_categories), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                val shortcuts = listOf(
                    Shortcut("Mobiles", Icons.Default.Devices, "electronics"),
                    Shortcut("Laptops", Icons.Default.Laptop, "laptop"),
                    Shortcut("Bikes", Icons.Default.PedalBike, "tw-commuter-bikes"),
                    Shortcut("Furniture", Icons.Default.HomeWork, "furniture"),
                    Shortcut("Fashion", Icons.Default.Checkroom, "hobbies"),
                    Shortcut("Cars", Icons.Default.DirectionsCar, "car-hatchback"),
                    Shortcut("All", Icons.Default.Category, "__all__"),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    shortcuts.forEachIndexed { index, shortcut ->
                        val matchingCat = if (shortcut.slug == "__all__") null else state.categories.firstOrNull {
                            it.slug.contains(shortcut.slug) || it.name.lowercase().contains(shortcut.slug)
                        }
                        val selected = if (shortcut.slug == "__all__") state.draft.categoryId == null else matchingCat != null && state.draft.categoryId == matchingCat.id
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).clickable {
                                if (shortcut.slug == "__all__") { onEdit { it.copy(categoryId = null) }; onApply() }
                                else if (matchingCat != null) { onEdit { it.copy(categoryId = matchingCat.id) }; onApply() }
                                else { onEdit { it.copy(query = shortcut.label) }; onApply() }
                            }.padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val tint = when (index % 6) {
                                0 -> Color(0xFF0878F9); 1 -> Color(0xFF009E7A); 2 -> Color(0xFF00A66A)
                                3 -> Color(0xFFC58A00); 4 -> Color(0xFF008C45); else -> Color(0xFF7C3AED)
                            }
                            Box(Modifier.size(44.dp).clip(CircleShape).background(if (selected) Color(0xFFE0F8E3) else tint.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                                Icon(shortcut.icon, shortcut.label, tint = if (selected) FairSwapGreen else tint, modifier = Modifier.size(24.dp))
                            }
                            Text(shortcut.label, color = if (selected) FairSwapGreen else Ink, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 2, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val currentCityName = state.cities.firstOrNull { it.id == state.draft.cityId }?.name ?: "All India"
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0FAF2),
                    border = BorderStroke(1.dp, Color(0xFFD9F0DF)),
                    modifier = Modifier.clickable { showLocationDialog = true }
                ) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = FairSwapGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(currentCityName, color = FairSwapGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(
                        onClick = { showFilterSheet = !showFilterSheet },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (showFilterSheet) "Hide ▲" else "Filters ▼", fontSize = 12.sp)
                    }
                    IconButton(onClick = onRefresh, enabled = !state.loadingFirstPage, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Refresh, stringResource(id = R.string.discovery_refresh), tint = FairSwapGreen, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (showFilterSheet) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, SoftBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
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
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = state.draft.maxRupees,
                                onValueChange = { value -> onEdit { it.copy(maxRupees = value) } },
                                label = { Text(stringResource(id = R.string.discovery_max_price)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Selector(
                            label = stringResource(id = R.string.discovery_sort),
                            selected = state.draft.sort.label,
                            options = ListingSort.entries.map { it to it.label },
                            onSelect = { choice -> onEdit { it.copy(sort = choice) } },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onApply(); showFilterSheet = false }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White), modifier = Modifier.weight(1f)) { Text(stringResource(id = R.string.discovery_apply_filters)) }
                            OutlinedButton(onClick = onClear, shape = RoundedCornerShape(12.dp)) { Text(stringResource(id = R.string.discovery_clear)) }
                        }
                    }
                }
            }
        }

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
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FairSwapGreen) }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.draft.query.isBlank() && state.draft.categoryId == null) "Fresh Listings Near You" else "Search Results",
                    color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
                )
                Text("View all  →", color = FairSwapGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onClear() })
            }
        }

        item {
            val staleText = if (state.results.isStale) stringResource(id = R.string.discovery_results_stale) else ""
            Text(
                stringResource(id = R.string.discovery_results_found, state.results.totalCount) + staleText,
                style = MaterialTheme.typography.labelSmall,
                color = MutedInk
            )
        }

        state.results.error?.let { message ->
            item {
                Column {
                    ErrorText(message)
                    OutlinedButton(onClick = if (state.results.errorKind == ResultErrorKind.NEXT_PAGE) onLoadMore else onRefresh) { Text(stringResource(id = R.string.discovery_retry)) }
                }
            }
        }

        if (!state.loadingFirstPage && state.results.items.isEmpty() && state.results.error == null) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, SoftBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(id = R.string.discovery_no_listings), style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(stringResource(id = R.string.discovery_no_listings_hint), style = MaterialTheme.typography.bodyMedium, color = MutedInk, textAlign = TextAlign.Center)
                        OutlinedButton(onClick = onClear) { Text(stringResource(id = R.string.discovery_clear_all_filters)) }
                    }
                }
            }
        }

        val chunkedItems = state.results.items.chunked(3)
        items(chunkedItems, key = { chunk -> chunk.first().id }) { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (listing in rowItems) {
                    Box(Modifier.weight(1f)) {
                        RefinedListingCard(
                            listing = listing,
                            onClick = { onListingSelected(listing.id) },
                            saved = favouriteValues[listing.id] == FavouriteValue.FAVOURITED,
                            mutating = listing.id in mutatingIds,
                            onFavourite = { onFavourite(listing.id, favouriteValues[listing.id] != FavouriteValue.FAVOURITED) },
                        )
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        if (state.results.canLoadMore && state.results.errorKind != ResultErrorKind.NEXT_PAGE) item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = onLoadMore, enabled = !state.loadingMore && !state.loadingFirstPage, colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White)) {
                    if (state.loadingMore) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White)
                    else Text(stringResource(id = R.string.discovery_load_more))
                }
            }
        }

        // How FairSwap Works Section
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SoftBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text(stringResource(id = R.string.discovery_how_it_works_title), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(id = R.string.discovery_how_it_works_subtitle), style = MaterialTheme.typography.bodySmall, color = MutedInk)

                    val steps = listOf(
                        Triple(0, stringResource(id = R.string.discovery_step1_title), Icons.Default.Search),
                        Triple(1, stringResource(id = R.string.discovery_step2_title), Icons.Default.Forum),
                        Triple(2, stringResource(id = R.string.discovery_step3_title), Icons.Default.Visibility),
                        Triple(3, stringResource(id = R.string.discovery_step4_title), Icons.Default.CurrencyRupee),
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        steps.forEach { (index, title, icon) ->
                            val isSelected = activeHowItWorksStep == index
                            Surface(
                                onClick = { activeHowItWorksStep = index },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) FairSwapMint else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isSelected) FairSwapGreen else Color.Transparent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(icon, title, tint = if (isSelected) FairSwapGreen else MutedInk, modifier = Modifier.size(19.dp))
                                    Text(title, color = if (isSelected) FairSwapGreen else Ink, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                }
                            }
                        }
                    }

                    val activeDesc = when (activeHowItWorksStep) {
                        0 -> stringResource(id = R.string.discovery_step1_desc)
                        1 -> stringResource(id = R.string.discovery_step2_desc)
                        2 -> stringResource(id = R.string.discovery_step3_desc)
                        else -> stringResource(id = R.string.discovery_step4_desc)
                    }

                    Surface(color = FairSwapMint, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(activeDesc, color = Color(0xFF0F3818), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        // Comprehensive Indian Marketplace Safety Section
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SoftBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text(stringResource(id = R.string.discovery_safety_section_title), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                    Surface(
                        color = Color(0xFFFFF1F2),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("🚨 " + stringResource(id = R.string.discovery_safety_rule_upi_title), color = Color(0xFFBE123C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(stringResource(id = R.string.discovery_safety_rule_upi_desc), color = Color(0xFF881337), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                        }
                    }

                    Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("🛡️ " + stringResource(id = R.string.discovery_safety_rule_army_title), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(stringResource(id = R.string.discovery_safety_rule_army_desc), color = MutedInk, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }

                    Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("🔗 " + stringResource(id = R.string.discovery_safety_rule_courier_title), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(stringResource(id = R.string.discovery_safety_rule_courier_desc), color = MutedInk, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(id = R.string.discovery_safety_tip_1), color = MutedInk, fontSize = 11.sp)
                        Text(stringResource(id = R.string.discovery_safety_tip_2), color = MutedInk, fontSize = 11.sp)
                        Text(stringResource(id = R.string.discovery_safety_tip_5), color = FairSwapGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun requestDeviceCity(
    context: Context,
    cities: List<City>,
    locationDisabledMsg: String,
    locationPermissionDeniedMsg: String,
    locationNotFoundMsg: String,
    onCityMatched: (City?) -> Unit,
    onError: (String) -> Unit
) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onError(locationDisabledMsg)
        return
    }
    val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    if (!isGpsEnabled && !isNetworkEnabled) {
        onError(locationDisabledMsg)
        return
    }

    val lastLocation = try {
        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    } catch (_: SecurityException) {
        onError(locationPermissionDeniedMsg)
        return
    }

    if (lastLocation == null) {
        onError(locationNotFoundMsg)
        return
    }

    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lastLocation.latitude, lastLocation.longitude, 1) { addresses ->
                val cityName = addresses.firstOrNull()?.locality
                    ?: addresses.firstOrNull()?.subAdminArea
                    ?: addresses.firstOrNull()?.adminArea
                val matchedCity = cities.firstOrNull { city ->
                    cityName != null && (city.name.contains(cityName, ignoreCase = true) || cityName.contains(city.name, ignoreCase = true))
                }
                if (matchedCity != null) {
                    onCityMatched(matchedCity)
                } else {
                    onError(locationNotFoundMsg)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lastLocation.latitude, lastLocation.longitude, 1)
            val cityName = addresses?.firstOrNull()?.locality
                ?: addresses?.firstOrNull()?.subAdminArea
                ?: addresses?.firstOrNull()?.adminArea
            val matchedCity = cities.firstOrNull { city ->
                cityName != null && (city.name.contains(cityName, ignoreCase = true) || cityName.contains(city.name, ignoreCase = true))
            }
            if (matchedCity != null) {
                onCityMatched(matchedCity)
            } else {
                onError(locationNotFoundMsg)
            }
        }
    } catch (_: Exception) {
        onError(locationNotFoundMsg)
    }
}

@Composable
private fun LocationSelectionDialog(
    cities: List<City>,
    currentCityId: String?,
    onDismissRequest: () -> Unit,
    onCitySelected: (String?) -> Unit,
) {
    val context = LocalContext.current
    val locationDisabledMsg = stringResource(id = R.string.discovery_location_disabled)
    val locationPermissionDeniedMsg = stringResource(id = R.string.discovery_location_permission_denied)
    val locationNotFoundMsg = stringResource(id = R.string.discovery_location_not_found)

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isAcquiringLocation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isAcquiringLocation = true
            statusMessage = null
            requestDeviceCity(
                context = context,
                cities = cities,
                locationDisabledMsg = locationDisabledMsg,
                locationPermissionDeniedMsg = locationPermissionDeniedMsg,
                locationNotFoundMsg = locationNotFoundMsg,
                onCityMatched = { city ->
                    isAcquiringLocation = false
                    if (city != null) {
                        onCitySelected(city.id)
                        onDismissRequest()
                    } else {
                        statusMessage = locationNotFoundMsg
                    }
                },
                onError = { err ->
                    isAcquiringLocation = false
                    statusMessage = err
                }
            )
        } else {
            statusMessage = locationPermissionDeniedMsg
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.discovery_location_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismissRequest, modifier = Modifier.size(32.dp)) {
                        Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MutedInk)
                    }
                }

                statusMessage?.let { msg ->
                    Text(text = msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                // Option 1: Use Current Location
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FairSwapGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAcquiringLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(id = R.string.discovery_location_use_current),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Option 2: All India
                OutlinedButton(
                    onClick = {
                        onCitySelected(null)
                        onDismissRequest()
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (currentCityId == null) FairSwapGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.discovery_location_all_india),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (currentCityId == null) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (currentCityId == null) FairSwapGreen else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Option 3: Select City
                if (cities.isNotEmpty()) {
                    Text(
                        text = stringResource(id = R.string.discovery_location_select_city),
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedInk
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        cities.forEach { city ->
                            val isSelected = city.id == currentCityId
                            Surface(
                                onClick = {
                                    onCitySelected(city.id)
                                    onDismissRequest()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) FairSwapMint else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, if (isSelected) FairSwapGreen else Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = city.name + (city.state?.let { " ($it)" } ?: ""),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) FairSwapGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Text("✓", color = FairSwapGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryIcon(category: Category): ImageVector {
    val iconKey = category.icon?.lowercase() ?: ""
    val nameLower = category.name.lowercase()
    val slugLower = category.slug.lowercase()

    return when {
        iconKey == "laptop" || nameLower.contains("electronic") || slugLower.contains("electronic") -> Icons.Default.Devices
        iconKey == "armchair" || nameLower.contains("furniture") || slugLower.contains("furniture") -> Icons.Default.HomeWork
        iconKey == "refrigerator" || nameLower.contains("appliance") || slugLower.contains("appliance") -> Icons.Default.Kitchen
        iconKey == "bike" || nameLower.contains("bike") || nameLower.contains("vehicle") || nameLower.contains("car") || slugLower.startsWith("tw-") || slugLower.startsWith("car-") -> Icons.Default.DirectionsCar
        iconKey == "book-open" || nameLower.contains("book") || slugLower.contains("book") -> Icons.AutoMirrored.Filled.MenuBook
        iconKey == "palette" || nameLower.contains("hobby") || slugLower.contains("hobby") -> Icons.Default.Palette
        nameLower.contains("fashion") || nameLower.contains("cloth") -> Icons.Default.Checkroom
        nameLower.contains("sport") || nameLower.contains("fitness") -> Icons.Default.FitnessCenter
        else -> Icons.Default.Category
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
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
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
private fun RefinedListingCard(
    listing: ListingCard,
    onClick: () -> Unit,
    saved: Boolean,
    mutating: Boolean,
    onFavourite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.02f).clip(RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp)).background(Color(0xFFF3F6F9))
            ) {
                if (listing.thumbnailUrl == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Photo unavailable", color = MutedInk, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    AsyncImage(
                        model = listing.thumbnailUrl,
                        contentDescription = listing.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    onClick = onFavourite,
                    enabled = !mutating,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(29.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (saved) "Remove from favourites" else "Add to favourites",
                            tint = if (saved) Color(0xFFE11D48) else MutedInk,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp)
                ) {
                    Text(listing.condition, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(listing.price, color = FairSwapGreen, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(listing.title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                Text(listing.location, color = MutedInk, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}
