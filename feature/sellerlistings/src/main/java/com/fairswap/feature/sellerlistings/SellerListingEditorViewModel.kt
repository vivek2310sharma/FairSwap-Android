package com.fairswap.feature.sellerlistings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.Locality
import com.fairswap.core.sellerlistings.CategoryAttributeMetadata
import com.fairswap.core.sellerlistings.CategoryAttributesLoadResult
import com.fairswap.core.sellerlistings.ListingValidationErrors
import com.fairswap.core.sellerlistings.ReferenceDataResult
import com.fairswap.core.sellerlistings.SellerDetailResult
import com.fairswap.core.sellerlistings.SellerListingDetail
import com.fairswap.core.sellerlistings.SellerListingInput
import com.fairswap.core.sellerlistings.SellerListingRepository
import com.fairswap.core.sellerlistings.SellerListingStatus
import com.fairswap.core.sellerlistings.SellerMutationResult
import com.fairswap.core.sellerlistings.SellerSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

typealias SellerEditorEntryId = String

sealed interface SellerListingEditorEvent {
    val session: SellerSession
    val entryId: SellerEditorEntryId

    data class AuthRequired(override val session: SellerSession, override val entryId: SellerEditorEntryId) : SellerListingEditorEvent
    data class PolicyReviewRequired(override val session: SellerSession, override val entryId: SellerEditorEntryId) : SellerListingEditorEvent
    data class Saved(override val session: SellerSession, override val entryId: SellerEditorEntryId, val listingId: String) : SellerListingEditorEvent
    data class Submitted(override val session: SellerSession, override val entryId: SellerEditorEntryId) : SellerListingEditorEvent
}

data class SellerListingEditorUiState(
    val session: SellerSession? = null,
    val listingId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSubmitting: Boolean = false,
    val persistedListing: SellerListingDetail? = null,
    val input: SellerListingInput = SellerListingInput(),
    val validationErrors: ListingValidationErrors = ListingValidationErrors(),
    val categories: List<Category> = emptyList(),
    val cities: List<City> = emptyList(),
    val localities: List<Locality> = emptyList(),
    val attributeMetadata: List<CategoryAttributeMetadata> = emptyList(),
    val metadataLoadFailed: Boolean = false,
    val localityLoadFailed: Boolean = false,
    val initializationError: String? = null,
    val categoriesEmpty: Boolean = false,
    val citiesEmpty: Boolean = false,
    val readyMediaCount: Int = 0,
    val errorMessage: String? = null,
) {
    val isEditing: Boolean get() = listingId != null
    val loadedListingStatus: SellerListingStatus? get() = persistedListing?.status
    val requiresReReviewOnEdit: Boolean get() = loadedListingStatus in setOf(
        SellerListingStatus.ACTIVE,
        SellerListingStatus.RESERVED,
        SellerListingStatus.PAUSED,
    )
    val isFormDirty: Boolean get() = (persistedListing == null) || (input != persistedListing.toInput())
}

@HiltViewModel
class SellerListingEditorViewModel @Inject constructor(
    private val repository: SellerListingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SellerListingEditorUiState())
    val state: StateFlow<SellerListingEditorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SellerListingEditorEvent>()
    val events: SharedFlow<SellerListingEditorEvent> = _events.asSharedFlow()

    private var currentEntryId: SellerEditorEntryId? = null
    private var initJob: Job? = null
    private var attrJob: Job? = null
    private var localityJob: Job? = null
    private var saveJob: Job? = null
    private var submitJob: Job? = null
    private var currentSaveOpId = 0L
    private var currentSubmitOpId = 0L

    fun bind(session: SellerSession?, listingId: String?, entryId: SellerEditorEntryId) {
        val previousSession = _state.value.session
        val previousListingId = _state.value.listingId
        val previousEntryId = currentEntryId

        val sameSession = session != null && session == previousSession
        val sameRoute = listingId == previousListingId
        val sameEntry = entryId == previousEntryId

        if (sameSession && sameRoute && sameEntry) {
            return
        }

        initJob?.cancel()
        attrJob?.cancel()
        localityJob?.cancel()
        saveJob?.cancel()
        submitJob?.cancel()
        initJob = null
        attrJob = null
        localityJob = null
        saveJob = null
        submitJob = null
        currentEntryId = entryId

        if (session == null) {
            _state.value = SellerListingEditorUiState(session = null, isLoading = false, errorMessage = "Signed out.")
        } else {
            _state.value = SellerListingEditorUiState(session = session, listingId = listingId, isLoading = true)
            initialize(session, listingId, entryId)
        }
    }

    fun initialize(session: SellerSession? = _state.value.session, listingId: String? = _state.value.listingId, entryId: SellerEditorEntryId? = currentEntryId) {
        val currentSession = session ?: _state.value.session ?: return
        val activeEntry = entryId ?: currentEntryId
        initJob?.cancel()
        initJob = viewModelScope.launch {
            _state.update { it.copy(session = currentSession, listingId = listingId, isLoading = true, initializationError = null, errorMessage = null) }
            try {
                withTimeout(15_000) {
                    coroutineScope {
                        val categoriesDeferred = async { repository.loadCategories() }
                        val citiesDeferred = async { repository.loadCities() }

                        val catRes = categoriesDeferred.await()
                        val cityRes = citiesDeferred.await()

                        if (_state.value.session != currentSession || currentEntryId != activeEntry) return@coroutineScope

                        if (catRes is ReferenceDataResult.Failure) {
                            _state.update { it.copy(isLoading = false, initializationError = catRes.message) }
                            return@coroutineScope
                        }
                        if (cityRes is ReferenceDataResult.Failure) {
                            _state.update { it.copy(isLoading = false, initializationError = cityRes.message) }
                            return@coroutineScope
                        }

                        val categories = (catRes as ReferenceDataResult.Success).value
                        val cities = (cityRes as ReferenceDataResult.Success).value

                        if (listingId == null) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    categories = categories,
                                    cities = cities,
                                    categoriesEmpty = categories.isEmpty(),
                                    citiesEmpty = cities.isEmpty(),
                                )
                            }
                        } else {
                            when (val detailResult = repository.loadDetail(listingId, currentSession)) {
                                is SellerDetailResult.Success -> {
                                    val detail = detailResult.listing
                                    val cityId = detail.cityId
                                    val localitiesRes = if (!cityId.isNullOrBlank()) {
                                        repository.loadLocalities(cityId)
                                    } else ReferenceDataResult.Success(emptyList())

                                    val (attributes, attrFailed) = when (val attrRes = repository.loadCategoryAttributes(detail.categoryId)) {
                                        is CategoryAttributesLoadResult.Success -> attrRes.attributes to false
                                        is CategoryAttributesLoadResult.Failure -> emptyList<CategoryAttributeMetadata>() to true
                                    }

                                    val (localities, localityFailed) = when (localitiesRes) {
                                        is ReferenceDataResult.Success -> localitiesRes.value to false
                                        is ReferenceDataResult.Failure -> emptyList<Locality>() to true
                                    }

                                    if (_state.value.session == currentSession && currentEntryId == activeEntry) {
                                        _state.update {
                                            it.copy(
                                                isLoading = false,
                                                categories = categories,
                                                cities = cities,
                                                localities = localities,
                                                attributeMetadata = attributes,
                                                metadataLoadFailed = attrFailed,
                                                localityLoadFailed = localityFailed,
                                                persistedListing = detail,
                                                readyMediaCount = detail.readyMediaCount,
                                                input = detail.toInput(),
                                                categoriesEmpty = categories.isEmpty(),
                                                citiesEmpty = cities.isEmpty(),
                                            )
                                        }
                                    }
                                }
                                SellerDetailResult.AuthRequired -> {
                                    if (_state.value.session == currentSession && currentEntryId == activeEntry) {
                                        _state.update { it.copy(isLoading = false) }
                                        if (activeEntry != null) {
                                            _events.emit(SellerListingEditorEvent.AuthRequired(currentSession, activeEntry))
                                        }
                                    }
                                }
                                SellerDetailResult.NotFound -> {
                                    if (_state.value.session == currentSession && currentEntryId == activeEntry) {
                                        _state.update { it.copy(isLoading = false, initializationError = "Listing not found.") }
                                    }
                                }
                                is SellerDetailResult.Failure -> {
                                    if (_state.value.session == currentSession && currentEntryId == activeEntry) {
                                        _state.update { it.copy(isLoading = false, initializationError = detailResult.message) }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (_state.value.session == currentSession && currentEntryId == activeEntry) {
                    _state.update { it.copy(isLoading = false, initializationError = "Loading listing options timed out. Retry.") }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (_state.value.session == currentSession && currentEntryId == activeEntry) {
                    _state.update { it.copy(isLoading = false, initializationError = "Unable to load listing options. Retry.") }
                }
            }
        }
    }

    fun retryInitialization() {
        initialize()
    }

    fun updateTitle(value: String) {
        _state.update { it.copy(input = it.input.copy(title = value)) }
    }

    fun updateCategory(categoryId: String) {
        if (categoryId == _state.value.input.categoryId) return
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId
        _state.update {
            it.copy(
                session = currentSession,
                input = it.input.copy(categoryId = categoryId, categoryAttributes = JsonObject(emptyMap())),
                metadataLoadFailed = false,
            )
        }
        attrJob?.cancel()
        attrJob = viewModelScope.launch {
            when (val result = repository.loadCategoryAttributes(categoryId)) {
                is CategoryAttributesLoadResult.Success -> {
                    if (_state.value.session == currentSession &&
                        currentEntryId == activeEntry &&
                        _state.value.input.categoryId == categoryId
                    ) {
                        _state.update { it.copy(attributeMetadata = result.attributes, metadataLoadFailed = false) }
                    }
                }
                is CategoryAttributesLoadResult.Failure -> {
                    if (_state.value.session == currentSession &&
                        currentEntryId == activeEntry &&
                        _state.value.input.categoryId == categoryId
                    ) {
                        _state.update {
                            it.copy(
                                attributeMetadata = emptyList(),
                                metadataLoadFailed = true,
                                errorMessage = result.message,
                            )
                        }
                    }
                }
            }
        }
    }

    fun retryCategoryAttributes() {
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId
        val catId = _state.value.input.categoryId
        if (catId.isBlank()) return

        attrJob?.cancel()
        attrJob = viewModelScope.launch {
            when (val result = repository.loadCategoryAttributes(catId)) {
                is CategoryAttributesLoadResult.Success -> {
                    if (_state.value.session == currentSession &&
                        currentEntryId == activeEntry &&
                        _state.value.input.categoryId == catId
                    ) {
                        _state.update {
                            it.copy(
                                attributeMetadata = result.attributes,
                                metadataLoadFailed = false,
                                errorMessage = null,
                            )
                        }
                    }
                }
                is CategoryAttributesLoadResult.Failure -> {
                    if (_state.value.session == currentSession &&
                        currentEntryId == activeEntry &&
                        _state.value.input.categoryId == catId
                    ) {
                        _state.update {
                            it.copy(
                                metadataLoadFailed = true,
                                errorMessage = result.message,
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateDescription(value: String) {
        _state.update { it.copy(input = it.input.copy(description = value)) }
    }

    fun updateCondition(condition: String?) {
        _state.update { it.copy(input = it.input.copy(condition = condition)) }
    }

    fun updatePriceType(type: String?) {
        _state.update {
            val updatedPriceStr = if (type == "free") "" else it.input.priceRupees
            it.copy(input = it.input.copy(priceType = type, priceRupees = updatedPriceStr))
        }
    }

    fun updatePriceRupees(value: String) {
        _state.update { it.copy(input = it.input.copy(priceRupees = value)) }
    }

    fun updateCity(cityId: String?) {
        if (cityId == _state.value.input.cityId) return
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId
        _state.update {
            it.copy(
                session = currentSession,
                input = it.input.copy(cityId = cityId, localityId = null),
                localityLoadFailed = false,
            )
        }
        localityJob?.cancel()
        localityJob = viewModelScope.launch {
            val localitiesRes = if (!cityId.isNullOrBlank()) {
                repository.loadLocalities(cityId)
            } else ReferenceDataResult.Success(emptyList())

            if (_state.value.session == currentSession &&
                currentEntryId == activeEntry &&
                _state.value.input.cityId == cityId
            ) {
                when (localitiesRes) {
                    is ReferenceDataResult.Success -> {
                        _state.update {
                            it.copy(localities = localitiesRes.value, localityLoadFailed = false)
                        }
                    }
                    is ReferenceDataResult.Failure -> {
                        _state.update {
                            it.copy(localities = emptyList(), localityLoadFailed = true)
                        }
                    }
                }
            }
        }
    }

    fun retryLocalities() {
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId
        val cityId = _state.value.input.cityId
        if (cityId.isNullOrBlank()) return

        localityJob?.cancel()
        localityJob = viewModelScope.launch {
            val localitiesRes = repository.loadLocalities(cityId)

            if (_state.value.session == currentSession &&
                currentEntryId == activeEntry &&
                _state.value.input.cityId == cityId
            ) {
                when (localitiesRes) {
                    is ReferenceDataResult.Success -> {
                        _state.update {
                            it.copy(
                                localities = localitiesRes.value,
                                localityLoadFailed = false,
                            )
                        }
                    }
                    is ReferenceDataResult.Failure -> {
                        _state.update {
                            it.copy(
                                localityLoadFailed = true,
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateLocality(localityId: String?) {
        _state.update { it.copy(input = it.input.copy(localityId = localityId)) }
    }

    fun updatePinCode(value: String) {
        _state.update { it.copy(input = it.input.copy(pinCode = value)) }
    }

    fun updateCategoryAttribute(key: String, value: JsonElement) {
        _state.update { state ->
            val currentMap = state.input.categoryAttributes.toMutableMap()
            if (value is JsonNull) {
                currentMap.remove(key)
            } else {
                currentMap[key] = value
            }
            state.copy(input = state.input.copy(categoryAttributes = JsonObject(currentMap)))
        }
    }

    fun saveDraft(entryId: SellerEditorEntryId? = currentEntryId) {
        if (_state.value.isSaving || _state.value.isSubmitting) return
        val currentSession = _state.value.session ?: return
        val activeEntry = entryId ?: currentEntryId ?: return
        val capturedInput = _state.value.input // SYNCHRONOUS TAP-TIME CAPTURE
        val currentId = _state.value.listingId
        val opId = ++currentSaveOpId

        _state.update { it.copy(session = currentSession, isSaving = true, errorMessage = null) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            try {
                val result = if (currentId == null) {
                    repository.createDraft(capturedInput, currentSession)
                } else {
                    repository.saveEdit(currentId, capturedInput, currentSession)
                }

                if (currentSaveOpId == opId && _state.value.session == currentSession) {
                    when (result) {
                        is SellerMutationResult.Success -> {
                            val detail = result.value
                            _state.update {
                                it.copy(
                                    listingId = detail.id,
                                    persistedListing = detail,
                                    input = detail.toInput(),
                                    validationErrors = ListingValidationErrors(),
                                )
                            }
                            _events.emit(SellerListingEditorEvent.Saved(currentSession, activeEntry, detail.id))
                        }
                        SellerMutationResult.AuthRequired -> {
                            _events.emit(SellerListingEditorEvent.AuthRequired(currentSession, activeEntry))
                        }
                        SellerMutationResult.PolicyReviewRequired -> {
                            _events.emit(SellerListingEditorEvent.PolicyReviewRequired(currentSession, activeEntry))
                        }
                        is SellerMutationResult.ValidationError -> {
                            _state.update { it.copy(validationErrors = result.errors) }
                        }
                        is SellerMutationResult.Failure -> {
                            _state.update { it.copy(errorMessage = result.message) }
                        }
                    }
                }
            } finally {
                if (currentSaveOpId == opId && _state.value.session == currentSession) {
                    _state.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    fun submitListing(entryId: SellerEditorEntryId? = currentEntryId) {
        if (_state.value.isSaving || _state.value.isSubmitting) return
        val currentState = _state.value
        val currentSession = currentState.session ?: return
        val activeEntry = entryId ?: currentEntryId ?: return
        if (currentState.isFormDirty || currentState.persistedListing == null) {
            _state.update { it.copy(errorMessage = "Save changes before submitting for review.") }
            return
        }
        if (currentState.metadataLoadFailed) {
            _state.update { it.copy(errorMessage = "Cannot submit because category specifications failed to load.") }
            return
        }

        val opId = ++currentSubmitOpId
        _state.update { it.copy(session = currentSession, isSubmitting = true, errorMessage = null) }
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            try {
                val listingId = currentState.persistedListing.id
                val attributes = currentState.attributeMetadata

                val result = repository.submitListing(listingId, attributes, currentSession)
                if (currentSubmitOpId == opId && _state.value.session == currentSession) {
                    when (result) {
                        is SellerMutationResult.Success -> {
                            val detail = result.value
                            _state.update {
                                it.copy(
                                    persistedListing = detail,
                                    input = detail.toInput(),
                                )
                            }
                            _events.emit(SellerListingEditorEvent.Submitted(currentSession, activeEntry))
                        }
                        SellerMutationResult.AuthRequired -> {
                            _events.emit(SellerListingEditorEvent.AuthRequired(currentSession, activeEntry))
                        }
                        SellerMutationResult.PolicyReviewRequired -> {
                            _events.emit(SellerListingEditorEvent.PolicyReviewRequired(currentSession, activeEntry))
                        }
                        is SellerMutationResult.ValidationError -> {
                            _state.update { it.copy(validationErrors = result.errors) }
                        }
                        is SellerMutationResult.Failure -> {
                            _state.update { it.copy(errorMessage = result.message) }
                        }
                    }
                }
            } finally {
                if (currentSubmitOpId == opId && _state.value.session == currentSession) {
                    _state.update { it.copy(isSubmitting = false) }
                }
            }
        }
    }

    fun clearErrorMessage() {
        _state.update { it.copy(errorMessage = null) }
    }
}
