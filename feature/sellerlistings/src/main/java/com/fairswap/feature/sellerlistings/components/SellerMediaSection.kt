package com.fairswap.feature.sellerlistings.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import com.fairswap.core.sellerlistings.SellerMediaItem
import com.fairswap.core.sellerlistings.SellerMediaProcessingStatus

enum class SellerMediaPickerMode {
    DISABLED,
    SINGLE,
    MULTIPLE
}

data class SellerMediaSectionState(
    val userId: String = "",
    val generation: Long = 0L,
    val listingId: String = "",
    val isEditing: Boolean = false,
    val canEditMedia: Boolean = true,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val items: List<SellerMediaItem> = emptyList(),
    val signedUrls: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
) {
    val totalCount: Int get() = items.size
    val remainingSlots: Int get() = (8 - totalCount).coerceIn(0, 8)
    val hasUnreadyMedia: Boolean get() = items.any { it.processingStatus != SellerMediaProcessingStatus.READY }
    val pickerMode: SellerMediaPickerMode
        get() = when {
            remainingSlots <= 0 -> SellerMediaPickerMode.DISABLED
            remainingSlots == 1 -> SellerMediaPickerMode.SINGLE
            else -> SellerMediaPickerMode.MULTIPLE
        }
}

@Composable
fun SellerMediaSection(
    state: SellerMediaSectionState,
    onPhotosSelected: (List<Uri>) -> Unit,
    onSetCover: (String) -> Unit,
    onDeleteMedia: (String) -> Unit,
    onMoveLeft: (String) -> Unit,
    onMoveRight: (String) -> Unit,
    onRetry: () -> Unit,
    onImageError: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val pickerLauncherMultiple = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = if (state.remainingSlots > 1) state.remainingSlots else 2),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onPhotosSelected(uris.take(state.remainingSlots))
            }
        }
    )

    val pickerLauncherSingle = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && state.remainingSlots > 0) {
                onPhotosSelected(listOf(uri))
            }
        }
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Photos (${state.totalCount}/8)",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.isEditing && state.canEditMedia && state.pickerMode != SellerMediaPickerMode.DISABLED && !state.isMutating) {
                    Button(
                        onClick = {
                            if (state.pickerMode == SellerMediaPickerMode.SINGLE) {
                                pickerLauncherSingle.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } else if (state.pickerMode == SellerMediaPickerMode.MULTIPLE) {
                                pickerLauncherMultiple.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                        enabled = true,
                    ) {
                        Text("Add Photos")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!state.isEditing) {
                Text(
                    text = "Save draft first to add photos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.errorMessage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            } else if (state.items.isEmpty()) {
                Text(
                    text = "No photos added yet. At least 1 ready photo is required before submitting for review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        MediaItemCard(
                            item = item,
                            signedUrl = state.signedUrls[item.id],
                            cacheKey = "seller_private_${state.userId}_${state.generation}_${state.listingId}_${item.id}_thumb",
                            isFirst = index == 0,
                            isLast = index == state.items.lastIndex,
                            canReorder = state.canEditMedia && !state.hasUnreadyMedia && !state.isMutating && state.items.size > 1,
                            canEdit = state.canEditMedia && !state.isMutating,
                            onSetCover = { onSetCover(item.id) },
                            onDelete = { onDeleteMedia(item.id) },
                            onMoveLeft = { onMoveLeft(item.id) },
                            onMoveRight = { onMoveRight(item.id) },
                            onImageError = { onImageError(item.id, "thumb") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaItemCard(
    item: SellerMediaItem,
    signedUrl: String?,
    cacheKey: String,
    isFirst: Boolean,
    isLast: Boolean,
    canReorder: Boolean,
    canEdit: Boolean,
    onSetCover: () -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onImageError: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .padding(vertical = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (signedUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(signedUrl)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .memoryCacheKey(cacheKey)
                            .build(),
                        contentDescription = "Photo ${item.displayOrder + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                        onError = { onImageError() },
                    )
                } else if (item.processingStatus == SellerMediaProcessingStatus.PROCESSING || item.processingStatus == SellerMediaProcessingStatus.UPLOADING) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else if (item.processingStatus == SellerMediaProcessingStatus.FAILED) {
                    Text(
                        text = "Upload failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                if (item.isCover) {
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    ) {
                        Text("Cover")
                    }
                }
            }

            if (canEdit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (canReorder && !isFirst) {
                        TextButton(onClick = onMoveLeft) { Text("<") }
                    }
                    if (!item.isCover && item.processingStatus == SellerMediaProcessingStatus.READY) {
                        TextButton(onClick = onSetCover) { Text("Cover") }
                    }
                    if (canReorder && !isLast) {
                        TextButton(onClick = onMoveRight) { Text(">") }
                    }
                }
                TextButton(
                    onClick = onDelete,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text("Delete")
                }
            }
        }
    }
}
