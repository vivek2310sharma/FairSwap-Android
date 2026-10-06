package com.fairswap.feature.sellerlistings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.Locality
import com.fairswap.core.sellerlistings.CategoryAttributeMetadata
import com.fairswap.core.sellerlistings.CategoryAttributesLoadResult
import com.fairswap.core.sellerlistings.ListingValidationErrors
import com.fairswap.core.sellerlistings.PincodeDistrict
import com.fairswap.core.sellerlistings.PincodeLocality
import com.fairswap.core.sellerlistings.PincodeLookupFailureReason
import com.fairswap.core.sellerlistings.PincodeLookupResult
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

import com.fairswap.core.sellerlistings.SellerMediaGetSignedUrlResult
import com.fairswap.core.sellerlistings.SellerMediaGetSnapshotResult
import com.fairswap.core.sellerlistings.SellerMediaItem
import com.fairswap.core.sellerlistings.SellerMediaFailure
import com.fairswap.core.sellerlistings.SellerMediaMutationAcknowledgement
import com.fairswap.core.sellerlistings.SellerMediaMutationResult
import com.fairswap.core.sellerlistings.SellerMediaRepository
import com.fairswap.core.sellerlistings.SellerMediaSnapshot
import com.fairswap.core.sellerlistings.SellerMediaUploadFile
import com.fairswap.core.sellerlistings.reconcileListingWithMediaSnapshot

sealed interface PincodeUiState {
    val pinCode: String

    data class Idle(override val pinCode: String) : PincodeUiState
    data class Loading(override val pinCode: String) : PincodeUiState
    data class ResolvedSingleDistrict(
        override val pinCode: String,
        val state: String?,
        val district: String?,
        val cityId: String,
        val localities: List<PincodeLocality>,
    ) : PincodeUiState
    data class RequiresDistrictChoice(
        override val pinCode: String,
        val state: String?,
        val districts: List<PincodeDistrict>,
        val allLocalities: List<PincodeLocality>,
        val priorLocalityId: String? = null,
    ) : PincodeUiState
    data class ResolvedDistrict(
        override val pinCode: String,
        val state: String?,
        val district: String?,
        val cityId: String,
        val localities: List<PincodeLocality>,
    ) : PincodeUiState
    data class Error(
        override val pinCode: String,
        val reason: PincodeLookupFailureReason,
        val retryable: Boolean,
    ) : PincodeUiState
}

typealias SellerEditorEntryId = String

sealed interface SellerListingEditorEvent {
    val session: SellerSession
    val entryId: SellerEditorEntryId

    data class AuthRequired(override val session: SellerSession, override val entryId: SellerEditorEntryId) : SellerListingEditorEvent
    data class PolicyReviewRequired(override val session: SellerSession, override val entryId: SellerEditorEntryId) : SellerListingEditorEvent
    data class Restricted(override val session: SellerSession, override val entryId: SellerEditorEntryId) : SellerListingEditorEvent
    data class Created(override val session: SellerSession, override val entryId: SellerEditorEntryId, val listingId: String) : SellerListingEditorEvent
    data class Saved(override val session: SellerSession, override val entryId: SellerEditorEntryId, val listingId: String) : SellerListingEditorEvent
    data class Submitted(override val session: SellerSession, override val entryId: SellerEditorEntryId) : SellerListingEditorEvent
}

data class SellerListingEditorUiState(
    val session: SellerSession? = null,
    val listingId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSubmitting: Boolean = false,
    val isMediaLoading: Boolean = false,
    val isMediaMutating: Boolean = false,
    val mediaSnapshot: SellerMediaSnapshot? = null,
    val signedUrls: Map<String, String> = emptyMap(),
    val mediaErrorMessage: String? = null,
    val persistedListing: SellerListingDetail? = null,
    val input: SellerListingInput = SellerListingInput(),
    val priorLocalityId: String? = null,
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
    val pincodeUiState: PincodeUiState = PincodeUiState.Idle(""),
    val localityReconciliationFailed: Boolean = false,
    val showReReviewWarning: Boolean = false,
) {
    val isEditing: Boolean get() = listingId != null
    val loadedListingStatus: SellerListingStatus? get() = persistedListing?.status

    val mediaSectionState: com.fairswap.feature.sellerlistings.components.SellerMediaSectionState
        get() = com.fairswap.feature.sellerlistings.components.SellerMediaSectionState(
            userId = session?.userId ?: "",
            generation = session?.generation ?: 0L,
            listingId = listingId ?: "",
            isEditing = isEditing,
            canEditMedia = loadedListingStatus?.canEditMedia ?: true,
            isLoading = isMediaLoading,
            isMutating = isMediaMutating,
            items = mediaSnapshot?.media ?: emptyList(),
            signedUrls = signedUrls,
            errorMessage = mediaErrorMessage,
        )
    val requiresReReviewOnEdit: Boolean get() {
        if (loadedListingStatus !in setOf(SellerListingStatus.ACTIVE, SellerListingStatus.RESERVED, SellerListingStatus.PAUSED)) return false
        val p = persistedListing?.toInput() ?: return false
        return input.title != p.title ||
               input.description != p.description ||
               input.categoryId != p.categoryId ||
               input.condition != p.condition ||
               input.priceType != p.priceType ||
               input.priceRupees != p.priceRupees ||
               input.categoryAttributes != p.categoryAttributes
    }
    val isFormDirty: Boolean get() = (persistedListing == null) || (input != persistedListing.toInput())

    val isLocationReady: Boolean get() {
        if (listingId == null) {
            val validCity = !input.cityId.isNullOrBlank()
            val stateResolved = pincodeUiState is PincodeUiState.ResolvedSingleDistrict || pincodeUiState is PincodeUiState.ResolvedDistrict
            val matchingCity = when (pincodeUiState) {
                is PincodeUiState.ResolvedSingleDistrict -> pincodeUiState.cityId == input.cityId
                is PincodeUiState.ResolvedDistrict -> pincodeUiState.cityId == input.cityId
                else -> false
            }
            return validCity && stateResolved && matchingCity
        } else {
            val p = persistedListing ?: return false
            val pinChanged = input.pinCode != p.pinCode
            val cityChanged = input.cityId != p.cityId
            val localityChanged = input.localityId != p.localityId

            val locationIdentityChanged = pinChanged || cityChanged || localityChanged

            if (!locationIdentityChanged) return !input.cityId.isNullOrBlank()

            val validCity = !input.cityId.isNullOrBlank()
            val stateResolved = pincodeUiState is PincodeUiState.ResolvedSingleDistrict || pincodeUiState is PincodeUiState.ResolvedDistrict
            val matchingCity = when (pincodeUiState) {
                is PincodeUiState.ResolvedSingleDistrict -> pincodeUiState.cityId == input.cityId
                is PincodeUiState.ResolvedDistrict -> pincodeUiState.cityId == input.cityId
                else -> false
            }
            return validCity && stateResolved && matchingCity
        }
    }

    val isSaveReady: Boolean get() = !isLoading && !isSaving && !isSubmitting && isLocationReady
}

@HiltViewModel
class SellerListingEditorViewModel @Inject constructor(
    private val repository: SellerListingRepository,
    private val mediaRepository: SellerMediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SellerListingEditorUiState())
    val state: StateFlow<SellerListingEditorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SellerListingEditorEvent>(extraBufferCapacity = 64)
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
        lookupJob?.cancel()
        mediaQueueJob?.cancel()
        refreshAttempts.clear()
        initJob = null
        attrJob = null
        localityJob = null
        saveJob = null
        submitJob = null
        lookupJob = null
        mediaQueueJob = null
        currentEntryId = entryId

        if (session == null) {
            _state.value = SellerListingEditorUiState(session = null, isLoading = false, errorMessage = "Signed out.", signedUrls = emptyMap())
        } else {
            _state.value = SellerListingEditorUiState(session = session, listingId = listingId, isLoading = true, signedUrls = emptyMap())
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
                                        val pin = detail.pinCode
                                        val priorLocality = detail.localityId?.ifBlank { null }
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
                                                priorLocalityId = priorLocality,
                                                categoriesEmpty = categories.isEmpty(),
                                                citiesEmpty = cities.isEmpty(),
                                                pincodeUiState = if (pin?.trim()?.length == 6) PincodeUiState.Loading(pin.trim()) else PincodeUiState.Idle(pin ?: "")
                                            )
                                        }
                                        if (pin?.trim()?.length == 6) {
                                            lookupPincodeForHydration(pin.trim(), priorLocality, detail.cityId)
                                        }
                                        loadMediaSnapshot(detail.id, currentSession)
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
                priorLocalityId = null,
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
        _state.update {
            it.copy(
                priorLocalityId = localityId?.ifBlank { null },
                input = it.input.copy(localityId = localityId),
            )
        }
    }

    fun updatePinCode(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        val currentPin = _state.value.input.pinCode
        if (digitsOnly == currentPin) return

        val currentLocality = _state.value.input.localityId?.ifBlank { null }
        val effectivePriorLocality = currentLocality ?: _state.value.priorLocalityId

        _state.update {
            it.copy(
                priorLocalityId = effectivePriorLocality,
                input = it.input.copy(pinCode = digitsOnly, cityId = null, localityId = null),
                pincodeUiState = PincodeUiState.Idle(digitsOnly)
            )
        }
        if (digitsOnly.length == 6) {
            lookupPincode(digitsOnly)
        }
    }

    private var lookupOpId = 0L
    private var lookupJob: Job? = null

    private fun resolveLocalityId(
        candidateLocalities: List<PincodeLocality>,
        priorLocalityId: String?,
    ): String? {
        val cleanPriorId = priorLocalityId?.ifBlank { null }
        return if (cleanPriorId != null) {
            if (candidateLocalities.any { it.id == cleanPriorId }) {
                cleanPriorId
            } else {
                null
            }
        } else {
            if (candidateLocalities.size == 1) {
                candidateLocalities.first().id
            } else {
                null
            }
        }
    }

    private fun lookupPincodeForHydration(pin: String, persistedLocalityId: String?, persistedCityId: String) {
        val op = ++lookupOpId
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val result = repository.lookupPincode(pin)
            if (lookupOpId != op || _state.value.session != currentSession || currentEntryId != activeEntry) return@launch
            when (result) {
                is PincodeLookupResult.Success -> {
                    if (result.districts.size > 1) {
                        val matchedDistrict = result.districts.firstOrNull { it.cityId != null && it.cityId == persistedCityId }
                        if (matchedDistrict != null) {
                            val localities = filterLocalities(result.localities, matchedDistrict.name)
                            val targetLocalityId = resolveLocalityId(localities, persistedLocalityId)
                            _state.update {
                                it.copy(
                                    pincodeUiState = PincodeUiState.ResolvedDistrict(pin, matchedDistrict.state, matchedDistrict.name, persistedCityId, localities),
                                    input = it.input.copy(cityId = persistedCityId, localityId = targetLocalityId)
                                )
                            }
                        } else {
                            _state.update { it.copy(pincodeUiState = PincodeUiState.RequiresDistrictChoice(pin, result.state, result.districts, result.localities, priorLocalityId = persistedLocalityId)) }
                        }
                    } else {
                        val cityId = result.cityId ?: result.districts.firstOrNull()?.cityId ?: resolveCompatibilityCity(result.district, result.state)
                        if (cityId == null) {
                            _state.update { it.copy(pincodeUiState = PincodeUiState.Error(pin, PincodeLookupFailureReason.COMPATIBILITY_CITY_UNAVAILABLE, false)) }
                            return@launch
                        }
                        val localities = filterLocalities(result.localities, result.district)
                        val targetLocalityId = resolveLocalityId(localities, persistedLocalityId)
                        _state.update {
                            it.copy(
                                pincodeUiState = PincodeUiState.ResolvedSingleDistrict(pin, result.state, result.district, cityId, localities),
                                input = it.input.copy(cityId = cityId, localityId = targetLocalityId)
                            )
                        }
                    }
                }
                is PincodeLookupResult.Failure -> {
                    val retryable = result.reason == PincodeLookupFailureReason.NETWORK_OR_FETCH_FAILURE
                    _state.update { it.copy(pincodeUiState = PincodeUiState.Error(pin, result.reason, retryable)) }
                }
            }
        }
    }

    private fun lookupPincode(pin: String) {
        val op = ++lookupOpId
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId
        val priorLocality = _state.value.priorLocalityId
        _state.update { it.copy(pincodeUiState = PincodeUiState.Loading(pin)) }
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val result = repository.lookupPincode(pin)
            if (lookupOpId != op || _state.value.session != currentSession || currentEntryId != activeEntry) return@launch
            when (result) {
                is PincodeLookupResult.Success -> {
                    if (result.districts.size > 1) {
                        _state.update { it.copy(pincodeUiState = PincodeUiState.RequiresDistrictChoice(pin, result.state, result.districts, result.localities, priorLocalityId = priorLocality)) }
                    } else {
                        val cityId = result.cityId ?: result.districts.firstOrNull()?.cityId ?: resolveCompatibilityCity(result.district, result.state)
                        if (cityId == null) {
                            _state.update { it.copy(pincodeUiState = PincodeUiState.Error(pin, PincodeLookupFailureReason.COMPATIBILITY_CITY_UNAVAILABLE, false)) }
                            return@launch
                        }
                        val localities = filterLocalities(result.localities, result.district)
                        val targetLocalityId = resolveLocalityId(localities, priorLocality)
                        _state.update {
                            it.copy(
                                pincodeUiState = PincodeUiState.ResolvedSingleDistrict(pin, result.state, result.district, cityId, localities),
                                input = it.input.copy(cityId = cityId, localityId = targetLocalityId)
                            )
                        }
                    }
                }
                is PincodeLookupResult.Failure -> {
                    val retryable = result.reason == PincodeLookupFailureReason.NETWORK_OR_FETCH_FAILURE
                    _state.update { it.copy(pincodeUiState = PincodeUiState.Error(pin, result.reason, retryable)) }
                }
            }
        }
    }

    private fun filterLocalities(allLocalities: List<PincodeLocality>, targetDistrict: String?): List<PincodeLocality> {
        if (targetDistrict.isNullOrBlank()) return allLocalities
        return allLocalities.filter { it.district.isNullOrBlank() || it.district.equals(targetDistrict, ignoreCase = true) }
    }

    private fun resolveCompatibilityCity(district: String?, state: String?): String? {
        if (district.isNullOrBlank()) return null
        return _state.value.cities.firstOrNull { it.name.equals(district, ignoreCase = true) && (it.state.isNullOrBlank() || it.state.equals(state, ignoreCase = true)) }?.id
    }

    fun selectDistrict(district: PincodeDistrict) {
        val state = _state.value
        val uiState = state.pincodeUiState as? PincodeUiState.RequiresDistrictChoice ?: return
        val cityId = district.cityId ?: resolveCompatibilityCity(district.name, district.state ?: uiState.state)
        if (cityId == null) {
            _state.update { it.copy(pincodeUiState = PincodeUiState.Error(uiState.pinCode, PincodeLookupFailureReason.COMPATIBILITY_CITY_UNAVAILABLE, false)) }
            return
        }
        val priorLocality = uiState.priorLocalityId ?: state.priorLocalityId
        val localities = filterLocalities(uiState.allLocalities, district.name)
        val targetLocalityId = resolveLocalityId(localities, priorLocality)
        _state.update {
            it.copy(
                pincodeUiState = PincodeUiState.ResolvedDistrict(uiState.pinCode, district.state ?: uiState.state, district.name, cityId, localities),
                input = it.input.copy(cityId = cityId, localityId = targetLocalityId)
            )
        }
    }

    fun updateLandmark(value: String) {
        if (value.length <= 200) {
            _state.update { it.copy(input = it.input.copy(landmark = value)) }
        }
    }

    fun retryPincodeLookup() {
        val pin = _state.value.input.pinCode
        if (pin.length == 6) {
            lookupPincode(pin)
        }
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
        if (_state.value.isSaving || _state.value.isSubmitting || !_state.value.isSaveReady) return
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
                            if (currentId == null) {
                                _events.emit(SellerListingEditorEvent.Created(currentSession, activeEntry, detail.id))
                            } else {
                                _events.emit(SellerListingEditorEvent.Saved(currentSession, activeEntry, detail.id))
                            }
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

    fun setMediaErrorMessage(message: String) {
        _state.update { it.copy(mediaErrorMessage = message) }
    }

    fun loadMediaSnapshot(listingId: String, session: SellerSession) {
        val activeEntry = currentEntryId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isMediaLoading = true, mediaErrorMessage = null) }
            when (val result = mediaRepository.getSnapshot(listingId, session)) {
                is SellerMediaGetSnapshotResult.Success -> {
                    applyMediaSnapshot(session, activeEntry, listingId, result.snapshot)
                }
                is SellerMediaGetSnapshotResult.Failure -> {
                    if (_state.value.session == session && currentEntryId == activeEntry) {
                        _state.update { it.copy(isMediaLoading = false, mediaErrorMessage = "Unable to load media.") }
                    }
                }
            }
        }
    }

    fun applyMediaSnapshot(
        expectedSession: SellerSession,
        expectedEntryId: SellerEditorEntryId,
        expectedListingId: String,
        snapshot: SellerMediaSnapshot,
    ) {
        if (_state.value.session != expectedSession || currentEntryId != expectedEntryId) return
        if (snapshot.listing.id != expectedListingId) return

        val currentDetail = _state.value.persistedListing
        val reconciledDetail = if (currentDetail != null) {
            reconcileListingWithMediaSnapshot(currentDetail, snapshot) ?: currentDetail
        } else null

        val currentSignedUrls = _state.value.signedUrls.toMutableMap()
        val validMediaIds = snapshot.media.map { it.id }.toSet()
        currentSignedUrls.keys.retainAll(validMediaIds)

        _state.update {
            it.copy(
                isMediaLoading = false,
                isMediaMutating = false,
                mediaSnapshot = snapshot,
                persistedListing = reconciledDetail ?: it.persistedListing,
                readyMediaCount = snapshot.media.count { item -> item.processingStatus == com.fairswap.core.sellerlistings.SellerMediaProcessingStatus.READY },
                signedUrls = currentSignedUrls,
                mediaErrorMessage = null,
            )
        }

        snapshot.media.filter { it.processingStatus == com.fairswap.core.sellerlistings.SellerMediaProcessingStatus.READY && !currentSignedUrls.containsKey(it.id) }
            .forEach { item ->
                requestSignedUrl(expectedListingId, item.id, "thumb", expectedSession)
            }
    }

    private var mediaQueueJob: Job? = null

    fun queueUploads(files: List<SellerMediaUploadFile>) {
        if (files.isEmpty()) return
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId ?: return
        val listingId = _state.value.listingId ?: return

        if (_state.value.isMediaMutating) return

        _state.update { it.copy(isMediaMutating = true, mediaErrorMessage = null) }

        mediaQueueJob?.cancel()
        mediaQueueJob = viewModelScope.launch {
            try {
                for (file in files) {
                    if (_state.value.session != currentSession || currentEntryId != activeEntry || _state.value.listingId != listingId) break

                    val result = mediaRepository.uploadMedia(listingId, file, currentSession)

                    if (_state.value.session != currentSession || currentEntryId != activeEntry || _state.value.listingId != listingId) break

                    when (result) {
                        is SellerMediaMutationResult.Success -> {
                            val ack = result.value
                            if (ack.snapshot != null) {
                                applyMediaSnapshot(currentSession, activeEntry, listingId, ack.snapshot!!)
                            } else {
                                loadMediaSnapshot(listingId, currentSession)
                            }
                        }
                        is SellerMediaMutationResult.Failure -> {
                            val msg = when (result.error) {
                                SellerMediaFailure.FileTooLarge -> "File size exceeds 5 MB limit."
                                SellerMediaFailure.UnsupportedFormat -> "Unsupported image format. Use JPEG, PNG, or WebP."
                                SellerMediaFailure.PhotoLimitReached -> "Maximum 8 photos allowed."
                                else -> "Upload failed. Please try again."
                            }
                            when (result.error) {
                                SellerMediaFailure.AuthRequired -> _events.emit(SellerListingEditorEvent.AuthRequired(currentSession, activeEntry))
                                SellerMediaFailure.PolicyReviewRequired -> _events.emit(SellerListingEditorEvent.PolicyReviewRequired(currentSession, activeEntry))
                                SellerMediaFailure.AccountRestricted -> _events.emit(SellerListingEditorEvent.Restricted(currentSession, activeEntry))
                                else -> _state.update { it.copy(mediaErrorMessage = msg) }
                            }
                            break // Stop the queue
                        }
                    }
                }
            } finally {
                if (_state.value.session == currentSession && currentEntryId == activeEntry && _state.value.listingId == listingId) {
                    _state.update { it.copy(isMediaMutating = false) }
                }
            }
        }
    }

    fun deleteMedia(mediaId: String) {
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId ?: return
        val listingId = _state.value.listingId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isMediaMutating = true, mediaErrorMessage = null) }
            when (val result = mediaRepository.deleteMedia(listingId, mediaId, currentSession)) {
                is SellerMediaMutationResult.Success -> {
                    val ack = result.value
                    if (ack.snapshot != null) {
                        applyMediaSnapshot(currentSession, activeEntry, listingId, ack.snapshot!!)
                    } else {
                        loadMediaSnapshot(listingId, currentSession)
                    }
                }
                is SellerMediaMutationResult.Failure -> {
                    if (_state.value.session == currentSession && currentEntryId == activeEntry) {
                        _state.update { it.copy(isMediaMutating = false, mediaErrorMessage = "Failed to delete photo.") }
                    }
                }
            }
        }
    }

    fun setCoverPhoto(mediaId: String) {
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId ?: return
        val listingId = _state.value.listingId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isMediaMutating = true, mediaErrorMessage = null) }
            when (val result = mediaRepository.setCoverPhoto(listingId, mediaId, currentSession)) {
                is SellerMediaMutationResult.Success -> {
                    val ack = result.value
                    if (ack.snapshot != null) {
                        applyMediaSnapshot(currentSession, activeEntry, listingId, ack.snapshot!!)
                    } else {
                        loadMediaSnapshot(listingId, currentSession)
                    }
                }
                is SellerMediaMutationResult.Failure -> {
                    if (_state.value.session == currentSession && currentEntryId == activeEntry) {
                        _state.update { it.copy(isMediaMutating = false, mediaErrorMessage = "Failed to set cover photo.") }
                    }
                }
            }
        }
    }

    fun moveMediaLeft(mediaId: String) {
        val currentItems = _state.value.mediaSnapshot?.media ?: return
        val index = currentItems.indexOfFirst { it.id == mediaId }
        if (index <= 0) return
        val reordered = currentItems.toMutableList()
        val item = reordered.removeAt(index)
        reordered.add(index - 1, item)
        reorderMedia(reordered.map { it.id })
    }

    fun moveMediaRight(mediaId: String) {
        val currentItems = _state.value.mediaSnapshot?.media ?: return
        val index = currentItems.indexOfFirst { it.id == mediaId }
        if (index < 0 || index >= currentItems.lastIndex) return
        val reordered = currentItems.toMutableList()
        val item = reordered.removeAt(index)
        reordered.add(index + 1, item)
        reorderMedia(reordered.map { it.id })
    }

    private fun reorderMedia(mediaIds: List<String>) {
        val currentSession = _state.value.session ?: return
        val activeEntry = currentEntryId ?: return
        val listingId = _state.value.listingId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isMediaMutating = true, mediaErrorMessage = null) }
            when (val result = mediaRepository.reorderMedia(listingId, mediaIds, currentSession)) {
                is SellerMediaMutationResult.Success -> {
                    val ack = result.value
                    if (ack.snapshot != null) {
                        applyMediaSnapshot(currentSession, activeEntry, listingId, ack.snapshot!!)
                    } else {
                        loadMediaSnapshot(listingId, currentSession)
                    }
                }
                is SellerMediaMutationResult.Failure -> {
                    if (_state.value.session == currentSession && currentEntryId == activeEntry) {
                        _state.update { it.copy(isMediaMutating = false, mediaErrorMessage = "Failed to reorder photos.") }
                    }
                }
            }
        }
    }

    private data class RefreshAttemptKey(
        val generation: Long,
        val listingId: String,
        val mediaId: String,
        val size: String,
    )

    private val refreshAttempts = mutableSetOf<RefreshAttemptKey>()

    fun onPrivateImageLoadFailed(mediaId: String, size: String = "thumb") {
        val currentSession = _state.value.session ?: return
        val listingId = _state.value.listingId ?: return
        val key = RefreshAttemptKey(
            generation = currentSession.generation,
            listingId = listingId,
            mediaId = mediaId,
            size = size,
        )
        if (refreshAttempts.contains(key)) return
        refreshAttempts.add(key)

        requestSignedUrl(listingId, mediaId, size, currentSession)
    }

    fun requestSignedUrl(listingId: String, mediaId: String, size: String, expectedSession: SellerSession) {
        val expectedEntryId = currentEntryId
        viewModelScope.launch {
            when (val result = mediaRepository.getSignedDerivativeUrl(listingId, mediaId, size, expectedSession)) {
                is SellerMediaGetSignedUrlResult.Success -> {
                    if (_state.value.session == expectedSession && currentEntryId == expectedEntryId && _state.value.listingId == listingId) {
                        val currentSnapshot = _state.value.mediaSnapshot
                        if (currentSnapshot != null && currentSnapshot.media.any { it.id == mediaId }) {
                            _state.update {
                                val current = it.signedUrls.toMutableMap()
                                current[mediaId] = result.signedUrl.url
                                it.copy(signedUrls = current)
                            }
                        }
                    }
                }
                is SellerMediaGetSignedUrlResult.Failure -> {}
            }
        }
    }

    fun retryMediaSnapshot() {
        val listingId = _state.value.listingId ?: return
        val session = _state.value.session ?: return
        loadMediaSnapshot(listingId, session)
    }
}
