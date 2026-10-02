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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

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
    onAttributeChange: (String, JsonPrimitive) -> Unit,
    onSaveDraft: () -> Unit,
    onSubmitListing: () -> Unit,
    onClearError: () -> Unit,
    onRetryInitialization: () -> Unit = {},
    onRetryCategoryAttributes: () -> Unit = {},
    onRetryLocalities: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

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
                        TextButton(onClick = onSaveDraft) {
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

                // City Selection
                if (state.citiesEmpty && !state.isEditing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No active locations are currently available for selection.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                } else {
                    var cityExpanded by remember { mutableStateOf(false) }
                    val activeCityMap = state.cities.associate { it.id to it.name }
                    val selectedCityName = formatUnavailableReferenceLabel(
                        selectedId = state.input.cityId,
                        activeItemsNameMap = activeCityMap,
                        defaultPlaceholder = "Select City",
                        typeLabel = "city",
                    )

                    ExposedDropdownMenuBox(
                        expanded = cityExpanded,
                        onExpandedChange = { cityExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedCityName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("City") },
                            isError = state.validationErrors.cityError != null,
                            supportingText = state.validationErrors.cityError?.let { { Text(it) } },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = cityExpanded,
                            onDismissRequest = { cityExpanded = false },
                        ) {
                            state.cities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city.name) },
                                    onClick = {
                                        onCityChange(city.id)
                                        cityExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Locality Selection or Locality Retry UI
                if (state.localityLoadFailed) {
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
                                text = "Unable to load localities for selected city.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            OutlinedButton(onClick = onRetryLocalities) {
                                Text("Retry")
                            }
                        }
                    }
                }

                if (!state.input.cityId.isNullOrBlank() && (state.localities.isNotEmpty() || !state.input.localityId.isNullOrBlank())) {
                    var localityExpanded by remember { mutableStateOf(false) }
                    val activeLocalityMap = state.localities.associate { it.id to it.name }
                    val selectedLocalityName = formatUnavailableReferenceLabel(
                        selectedId = state.input.localityId,
                        activeItemsNameMap = activeLocalityMap,
                        defaultPlaceholder = "Select Locality",
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
                            label = { Text("Locality") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = localityExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = localityExpanded,
                            onDismissRequest = { localityExpanded = false },
                        ) {
                            state.localities.forEach { loc ->
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

                // PIN Code Input
                OutlinedTextField(
                    value = state.input.pinCode,
                    onValueChange = onPinCodeChange,
                    label = { Text("PIN Code (6 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.validationErrors.pinCodeError != null,
                    supportingText = state.validationErrors.pinCodeError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

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
