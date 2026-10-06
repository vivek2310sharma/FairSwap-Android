package com.fairswap.feature.sellerlistings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fairswap.core.discovery.ListingCondition
import com.fairswap.core.sellerlistings.formatUnavailableReferenceLabel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

import com.fairswap.feature.sellerlistings.components.SellerMediaSection
import android.net.Uri

sealed interface SellerMediaReadResult {
    data class Success(val file: com.fairswap.core.sellerlistings.SellerMediaUploadFile) : SellerMediaReadResult
    data object FileTooLarge : SellerMediaReadResult
    data object UnsupportedFormat : SellerMediaReadResult
    data object Unreadable : SellerMediaReadResult
}

suspend fun readUriToUploadFile(
    context: android.content.Context,
    uri: Uri,
): SellerMediaReadResult {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(5 * 1024 * 1024 + 1)
                var bytesRead = 0
                var read: Int
                while (bytesRead < buffer.size) {
                    read = inputStream.read(buffer, bytesRead, buffer.size - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }

                if (bytesRead > 5 * 1024 * 1024) return@withContext SellerMediaReadResult.FileTooLarge

                val actualBytes = buffer.copyOf(bytesRead)
                val format = com.fairswap.core.sellerlistings.SellerMediaValidation.detectFormatFromMagicBytes(actualBytes) ?: return@withContext SellerMediaReadResult.UnsupportedFormat

                var displayName: String? = null
                try {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                displayName = cursor.getString(nameIndex)
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {}

                val fileName = com.fairswap.core.sellerlistings.SellerMediaValidation.sanitizeFilename(
                    filename = displayName,
                    fallbackExtension = if (format == "image/png") "png" else if (format == "image/webp") "webp" else "jpg",
                )

                val uploadFile = com.fairswap.core.sellerlistings.SellerMediaUploadFile(
                    fileName = fileName,
                    bytes = actualBytes,
                    detectedFormat = format,
                )
                SellerMediaReadResult.Success(uploadFile)
            } ?: SellerMediaReadResult.Unreadable
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            SellerMediaReadResult.Unreadable
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerListingEditorScreen(
    state: SellerListingEditorUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConditionChange: (String?) -> Unit,
    onPriceTypeChange: (String?) -> Unit,
    onPriceRupeesChange: (String) -> Unit,
    onCityChange: (String?) -> Unit,
    onLocalityChange: (String?) -> Unit,
    onPinCodeChange: (String) -> Unit,
    onLandmarkChange: (String) -> Unit,
    onSelectDistrict: (com.fairswap.core.sellerlistings.PincodeDistrict) -> Unit,
    onAttributeChange: (String, JsonPrimitive) -> Unit,
    onSaveDraft: () -> Unit,
    onSubmitListing: () -> Unit,
    onClearError: () -> Unit,
    onRetryInitialization: () -> Unit = {},
    onRetryCategoryAttributes: () -> Unit = {},
    onRetryLocalities: () -> Unit = {},
    onRetryPincodeLookup: () -> Unit = {},
    onValidatedPhotos: (List<com.fairswap.core.sellerlistings.SellerMediaUploadFile>) -> Unit = {},
    onSetCover: (String) -> Unit = {},
    onDeleteMedia: (String) -> Unit = {},
    onMoveLeft: (String) -> Unit = {},
    onMoveRight: (String) -> Unit = {},
    onRetryMedia: () -> Unit = {},
    onImageError: (String, String) -> Unit = { _, _ -> },
    onLocalMediaError: (String) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit Listing" else "Create Listing") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Cancel") }
                },
                actions = {
                    if (state.isSaving || state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                    } else {
                        TextButton(onClick = onSaveDraft, enabled = state.isSaveReady) {
                            Text("Save Draft")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.initializationError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = state.initializationError,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRetryInitialization) {
                        Text("Retry")
                    }
                }
            }
        } else if (state.categoriesEmpty && !state.isEditing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Listings cannot be created because no categories are currently available.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.requiresReReviewOnEdit) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Notice: Saving changes to this active/paused/reserved listing will send it back for moderator review.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = state.input.title,
                    onValueChange = onTitleChange,
                    label = { Text("Title *") },
                    isError = state.validationErrors.titleError != null,
                    supportingText = {
                        Text(state.validationErrors.titleError ?: "${state.input.title.length}/150")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Category Selection
                var categoryExpanded by remember { mutableStateOf(false) }
                val activeCategoryMap = state.categories.associate { it.id to it.name }
                val selectedCategoryName = formatUnavailableReferenceLabel(
                    selectedId = state.input.categoryId,
                    activeItemsNameMap = activeCategoryMap,
                    defaultPlaceholder = "Select Category",
                    typeLabel = "category",
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedCategoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category *") },
                        isError = state.validationErrors.categoryError != null,
                        supportingText = state.validationErrors.categoryError?.let { { Text(it) } },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        state.categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    onCategoryChange(cat.id)
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }

                // Description Input
                OutlinedTextField(
                    value = state.input.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    isError = state.validationErrors.descriptionError != null,
                    supportingText = {
                        Text(state.validationErrors.descriptionError ?: "${state.input.description.length}/5000")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )

                // Photos / Media Section
                com.fairswap.feature.sellerlistings.components.SellerMediaSection(
                    state = state.mediaSectionState,
                    onPhotosSelected = { uris ->
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val files = mutableListOf<com.fairswap.core.sellerlistings.SellerMediaUploadFile>()
                            var hasError = false
                            var errorMsg = ""
                            for (uri in uris) {
                                when (val result = readUriToUploadFile(context, uri)) {
                                    is SellerMediaReadResult.Success -> files.add(result.file)
                                    SellerMediaReadResult.FileTooLarge -> {
                                        hasError = true
                                        errorMsg = "One or more files exceed the 5 MB limit."
                                    }
                                    SellerMediaReadResult.UnsupportedFormat -> {
                                        hasError = true
                                        errorMsg = "Unsupported format. Use JPEG, PNG, or WebP."
                                    }
                                    SellerMediaReadResult.Unreadable -> {
                                        hasError = true
                                        errorMsg = "Unable to read one or more files."
                                    }
                                }
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (hasError) {
                                    onLocalMediaError(errorMsg)
                                }
                                if (files.isNotEmpty()) {
                                    onValidatedPhotos(files)
                                }
                            }
                        }
                    },
                    onSetCover = onSetCover,
                    onDeleteMedia = onDeleteMedia,
                    onMoveLeft = onMoveLeft,
                    onMoveRight = onMoveRight,
                    onRetry = onRetryMedia,
                    onImageError = onImageError,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Condition Selection
                Text("Condition", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ListingCondition.entries.forEach { cond ->
                        FilterChip(
                            selected = state.input.condition == cond.wire,
                            onClick = { onConditionChange(if (state.input.condition == cond.wire) null else cond.wire) },
                            label = { Text(cond.label) },
                        )
                    }
                }
                state.validationErrors.conditionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Price Type & Price Input
                Text("Price Type & Amount", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("fixed" to "Fixed", "negotiable" to "Negotiable", "free" to "Free").forEach { (typeKey, typeLabel) ->
                        FilterChip(
                            selected = state.input.priceType == typeKey,
                            onClick = { onPriceTypeChange(if (state.input.priceType == typeKey) null else typeKey) },
                            label = { Text(typeLabel) },
                        )
                    }
                }

                if (state.input.priceType != "free") {
                    OutlinedTextField(
                        value = state.input.priceRupees,
                        onValueChange = onPriceRupeesChange,
                        label = { Text("Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.validationErrors.priceError != null,
                        supportingText = state.validationErrors.priceError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                Text("Where is the item?", style = MaterialTheme.typography.titleSmall)

                // PIN Code Input
                OutlinedTextField(
                    value = state.input.pinCode,
                    onValueChange = onPinCodeChange,
                    label = { Text("PIN Code (6 digits) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.validationErrors.pinCodeError != null,
                    supportingText = state.validationErrors.pinCodeError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Pincode UI State
                when (val pinState = state.pincodeUiState) {
                    is com.fairswap.feature.sellerlistings.PincodeUiState.Idle -> { }
                    is com.fairswap.feature.sellerlistings.PincodeUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is com.fairswap.feature.sellerlistings.PincodeUiState.Error -> {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (pinState.reason) {
                                        com.fairswap.core.sellerlistings.PincodeLookupFailureReason.INVALID_PINCODE -> "Invalid PIN Code."
                                        com.fairswap.core.sellerlistings.PincodeLookupFailureReason.PINCODE_NOT_FOUND -> "PIN Code not found."
                                        com.fairswap.core.sellerlistings.PincodeLookupFailureReason.CONFLICTING_POSTAL_STATES -> "Conflicting state data for this PIN."
                                        com.fairswap.core.sellerlistings.PincodeLookupFailureReason.PINCODE_ADMIN_DATA_UNAVAILABLE -> "Administrative data unavailable for this PIN."
                                        com.fairswap.core.sellerlistings.PincodeLookupFailureReason.COMPATIBILITY_CITY_UNAVAILABLE -> "No valid city found for this PIN."
                                        com.fairswap.core.sellerlistings.PincodeLookupFailureReason.NETWORK_OR_FETCH_FAILURE -> "Unable to verify PIN Code."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                if (pinState.retryable) {
                                    OutlinedButton(onClick = onRetryPincodeLookup) { Text("Retry") }
                                }
                            }
                        }
                    }
                    is com.fairswap.feature.sellerlistings.PincodeUiState.RequiresDistrictChoice -> {
                        Text("State: ${pinState.state ?: ""}", style = MaterialTheme.typography.bodyMedium)

                        var districtExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = districtExpanded,
                            onExpandedChange = { districtExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = "Select District",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("District *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = districtExpanded,
                                onDismissRequest = { districtExpanded = false },
                            ) {
                                pinState.districts.forEach { dist ->
                                    DropdownMenuItem(
                                        text = { Text(dist.name) },
                                        onClick = {
                                            onSelectDistrict(dist)
                                            districtExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    is com.fairswap.feature.sellerlistings.PincodeUiState.ResolvedSingleDistrict -> {
                        Text("State: ${pinState.state ?: ""}", style = MaterialTheme.typography.bodyMedium)
                        Text("District: ${pinState.district ?: ""}", style = MaterialTheme.typography.bodyMedium)

                        // Locality Selection
                        if (pinState.localities.isNotEmpty()) {
                            var localityExpanded by remember { mutableStateOf(false) }
                            val activeLocalityMap = pinState.localities.associate { it.id to it.name }
                            val selectedLocalityName = formatUnavailableReferenceLabel(
                                selectedId = state.input.localityId,
                                activeItemsNameMap = activeLocalityMap,
                                defaultPlaceholder = "Select Area / Locality (Optional)",
                                typeLabel = "locality",
                            )

                            ExposedDropdownMenuBox(
                                expanded = localityExpanded,
                                onExpandedChange = { localityExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = selectedLocalityName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Area / Locality") },
                                    isError = state.validationErrors.localityError != null,
                                    supportingText = state.validationErrors.localityError?.let { { Text(it) } },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = localityExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                )
                                ExposedDropdownMenu(
                                    expanded = localityExpanded,
                                    onDismissRequest = { localityExpanded = false },
                                ) {
                                    pinState.localities.forEach { loc ->
                                        DropdownMenuItem(
                                            text = { Text(loc.name) },
                                            onClick = {
                                                onLocalityChange(loc.id)
                                                localityExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is com.fairswap.feature.sellerlistings.PincodeUiState.ResolvedDistrict -> {
                        Text("State: ${pinState.state ?: ""}", style = MaterialTheme.typography.bodyMedium)
                        Text("District: ${pinState.district ?: ""}", style = MaterialTheme.typography.bodyMedium)

                        // Locality Selection
                        if (pinState.localities.isNotEmpty()) {
                            var localityExpanded by remember { mutableStateOf(false) }
                            val activeLocalityMap = pinState.localities.associate { it.id to it.name }
                            val selectedLocalityName = formatUnavailableReferenceLabel(
                                selectedId = state.input.localityId,
                                activeItemsNameMap = activeLocalityMap,
                                defaultPlaceholder = "Select Area / Locality (Optional)",
                                typeLabel = "locality",
                            )

                            ExposedDropdownMenuBox(
                                expanded = localityExpanded,
                                onExpandedChange = { localityExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = selectedLocalityName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Area / Locality") },
                                    isError = state.validationErrors.localityError != null,
                                    supportingText = state.validationErrors.localityError?.let { { Text(it) } },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = localityExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                )
                                ExposedDropdownMenu(
                                    expanded = localityExpanded,
                                    onDismissRequest = { localityExpanded = false },
                                ) {
                                    pinState.localities.forEach { loc ->
                                        DropdownMenuItem(
                                            text = { Text(loc.name) },
                                            onClick = {
                                                onLocalityChange(loc.id)
                                                localityExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Landmark
                OutlinedTextField(
                    value = state.input.landmark,
                    onValueChange = onLandmarkChange,
                    label = { Text("Landmark (Optional)") },
                    isError = state.validationErrors.landmarkError != null,
                    supportingText = state.validationErrors.landmarkError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text("Only your approximate area is shown publicly.\nYou can decide the exact pickup location later with the buyer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Dynamic Category Attributes
                if (state.metadataLoadFailed) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Unable to load category specifications.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            OutlinedButton(onClick = onRetryCategoryAttributes) {
                                Text("Retry")
                            }
                        }
                    }
                } else if (state.attributeMetadata.isNotEmpty()) {
                    Text("Category Specifications", style = MaterialTheme.typography.titleSmall)
                    state.attributeMetadata.forEach { meta ->
                        val currentValue = state.input.categoryAttributes[meta.name]
                        val errText = state.validationErrors.attributeErrors[meta.name]

                        when (meta.valueType) {
                            "boolean" -> {
                                val checked = (currentValue as? JsonPrimitive)?.booleanOrNull == true
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(meta.label + if (meta.isRequired) " *" else "")
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { onAttributeChange(meta.name, JsonPrimitive(it)) },
                                    )
                                }
                            }
                            "select" -> {
                                var attrExpanded by remember { mutableStateOf(false) }
                                val selectedText = (currentValue as? JsonPrimitive)?.contentOrNull ?: "Select ${meta.label}"
                                ExposedDropdownMenuBox(
                                    expanded = attrExpanded,
                                    onExpandedChange = { attrExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = selectedText,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(meta.label + if (meta.isRequired) " *" else "") },
                                        isError = errText != null,
                                        supportingText = errText?.let { { Text(it) } },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = attrExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                    )
                                    ExposedDropdownMenu(
                                        expanded = attrExpanded,
                                        onDismissRequest = { attrExpanded = false },
                                    ) {
                                        meta.options.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt) },
                                                onClick = {
                                                    onAttributeChange(meta.name, JsonPrimitive(opt))
                                                    attrExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            "text" -> {
                                val rawText = (currentValue as? JsonPrimitive)?.contentOrNull ?: ""
                                OutlinedTextField(
                                    value = rawText,
                                    onValueChange = { onAttributeChange(meta.name, JsonPrimitive(it)) },
                                    label = { Text(meta.label + if (meta.isRequired) " *" else "") },
                                    isError = errText != null,
                                    supportingText = errText?.let { { Text(it) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                            }
                            "number" -> {
                                val rawText = (currentValue as? JsonPrimitive)?.contentOrNull ?: ""
                                OutlinedTextField(
                                    value = rawText,
                                    onValueChange = { onAttributeChange(meta.name, JsonPrimitive(it)) },
                                    label = { Text(meta.label + if (meta.isRequired) " *" else "") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = errText != null,
                                    supportingText = errText?.let { { Text(it) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                            }
                            else -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "${meta.label}: This specification cannot be edited in this app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Submit Button
                Button(
                    onClick = onSubmitListing,
                    enabled = !state.isSaving && !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Submit for Review")
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
