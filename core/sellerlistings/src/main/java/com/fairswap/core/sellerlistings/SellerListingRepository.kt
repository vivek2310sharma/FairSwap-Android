package com.fairswap.core.sellerlistings

import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.Locality
import com.fairswap.core.discovery.Rupees
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout

data class SellerSession(val userId: String, val generation: Long)

data class SellerMutationLockToken(
    val listingId: String,
    val session: SellerSession,
    val operationId: Long,
)

class SellerListingRepository(
    private val gateway: SellerListingGateway,
    val sessionOwner: SellerSessionOwner = SellerSessionOwner(),
) {
    private val activeMutationLocks = mutableMapOf<String, SellerMutationLockToken>()
    private val lockOperationCounter = AtomicLong(1L)

    private val _dashboardVersion = MutableStateFlow<Long>(0L)
    val dashboardVersion: StateFlow<Long> = _dashboardVersion.asStateFlow()

    fun notifyDashboardInvalidated(session: SellerSession) {
        if (isSessionActive(session)) {
            _dashboardVersion.update { it + 1 }
        }
    }

    fun getCurrentSession(): SellerSession? = sessionOwner.currentSession()

    fun validateSession(expectedSession: SellerSession?): SellerSession? {
        if (expectedSession == null) return null
        val current = sessionOwner.currentSession() ?: return null
        if (current.userId == expectedSession.userId && current.generation == expectedSession.generation) {
            return current
        }
        return null
    }

    fun isSessionActive(session: SellerSession): Boolean {
        return validateSession(session) != null
    }

    private suspend fun sessionStillMatches(expectedSession: SellerSession): Boolean {
        val currentSession = sessionOwner.currentSession() ?: return false
        if (currentSession != expectedSession) return false
        val liveUser = gateway.currentUserId() ?: return false
        return liveUser == expectedSession.userId
    }

    @Synchronized
    fun acquireMutationLock(listingId: String, session: SellerSession): SellerMutationLockToken? {
        if (!isSessionActive(session)) return null
        if (activeMutationLocks.containsKey(listingId)) return null
        val token = SellerMutationLockToken(listingId, session, lockOperationCounter.incrementAndGet())
        activeMutationLocks[listingId] = token
        return token
    }

    @Synchronized
    fun releaseMutationLock(token: SellerMutationLockToken?) {
        if (token == null) return
        if (activeMutationLocks[token.listingId] == token) {
            activeMutationLocks.remove(token.listingId)
        }
    }

    suspend fun currentUserId(): String? = gateway.currentUserId()

    suspend fun loadCategories(): ReferenceDataResult<List<Category>> = try {
        withTimeout(15_000) {
            val list = gateway.fetchCategories()
            ReferenceDataResult.Success(list)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        ReferenceDataResult.Failure("Unable to load category specifications. Please try again.")
    }

    suspend fun loadCities(): ReferenceDataResult<List<City>> = try {
        withTimeout(15_000) {
            val list = gateway.fetchCities()
            ReferenceDataResult.Success(list)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        ReferenceDataResult.Failure("Unable to load locations. Please try again.")
    }

    suspend fun loadLocalities(cityId: String): ReferenceDataResult<List<Locality>> {
        if (!SellerListingValidation.isUuid(cityId)) return ReferenceDataResult.Success(emptyList())
        return try {
            withTimeout(15_000) {
                val list = gateway.fetchLocalities(cityId)
                ReferenceDataResult.Success(list)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ReferenceDataResult.Failure("Unable to load localities. Please try again.")
        }
    }

    suspend fun loadDashboard(expectedSession: SellerSession): SellerDashboardResult {
        val session = validateSession(expectedSession) ?: return SellerDashboardResult.AuthRequired
        val userId = gateway.currentUserId() ?: return SellerDashboardResult.AuthRequired
        if (userId != session.userId) return SellerDashboardResult.AuthRequired

        return try {
            withTimeout(15_000) {
                if (!sessionStillMatches(expectedSession)) return@withTimeout SellerDashboardResult.AuthRequired
                val rows = gateway.fetchSellerListings(userId)
                if (!sessionStillMatches(expectedSession)) return@withTimeout SellerDashboardResult.AuthRequired
                val mediaCounts = gateway.fetchReadyMediaCounts(rows.map { it.id })
                if (!sessionStillMatches(expectedSession)) return@withTimeout SellerDashboardResult.AuthRequired
                val summaries = rows.map { row ->
                    row.toSummary(readyMediaCount = mediaCounts[row.id] ?: 0)
                }
                SellerDashboardResult.Success(summaries)
            }
        } catch (_: TimeoutCancellationException) {
            SellerDashboardResult.Failure("Loading dashboard timed out. Please try again.")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SellerDashboardResult.Failure("Unable to load seller listings. Please try again.")
        }
    }

    suspend fun loadDetail(listingId: String, expectedSession: SellerSession): SellerDetailResult {
        if (!SellerListingValidation.isUuid(listingId)) return SellerDetailResult.NotFound
        val session = validateSession(expectedSession) ?: return SellerDetailResult.AuthRequired
        val userId = gateway.currentUserId() ?: return SellerDetailResult.AuthRequired
        if (userId != session.userId) return SellerDetailResult.AuthRequired

        return try {
            withTimeout(15_000) {
                if (!sessionStillMatches(expectedSession)) return@withTimeout SellerDetailResult.AuthRequired
                val row = gateway.fetchSellerListingDetail(listingId, userId)
                    ?: return@withTimeout SellerDetailResult.NotFound
                if (!sessionStillMatches(expectedSession)) return@withTimeout SellerDetailResult.AuthRequired
                val mediaCounts = gateway.fetchReadyMediaCounts(listOf(listingId))
                if (!sessionStillMatches(expectedSession)) return@withTimeout SellerDetailResult.AuthRequired
                val privateLocation = gateway.fetchSellerPrivateLocation(listingId)
                if (!sessionStillMatches(expectedSession)) return@withTimeout SellerDetailResult.AuthRequired
                SellerDetailResult.Success(
                    row.toDetail(
                        readyMediaCount = mediaCounts[listingId] ?: 0,
                        privatePinCode = privateLocation?.pinCode,
                    )
                )
            }
        } catch (_: TimeoutCancellationException) {
            SellerDetailResult.Failure("Loading listing detail timed out. Please try again.")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SellerDetailResult.Failure("Unable to load listing detail. Please try again.")
        }
    }

    suspend fun loadCategoryAttributes(categoryId: String): CategoryAttributesLoadResult {
        if (!SellerListingValidation.isUuid(categoryId)) {
            return CategoryAttributesLoadResult.Success(emptyList())
        }
        return try {
            withTimeout(15_000) {
                val list = gateway.fetchCategoryAttributes(categoryId)
                CategoryAttributesLoadResult.Success(list)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            CategoryAttributesLoadResult.Failure("Unable to load category specifications. Please try again.")
        }
    }

    suspend fun createDraft(
        input: SellerListingInput,
        expectedSession: SellerSession,
    ): SellerMutationResult<SellerListingDetail> {
        val validation = SellerListingValidation.validateDraft(input)
        if (!validation.isValid) {
            return SellerMutationResult.ValidationError(validation)
        }

        if (!sessionStillMatches(expectedSession)) return SellerMutationResult.AuthRequired

        val result = runPolicyGuardedMutation(expectedSession) { userId ->
            if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

            val insertDto = SellerListingInsertDto(
                categoryId = input.categoryId,
                cityId = input.cityId!!,
                localityId = input.localityId?.ifBlank { null },
                title = input.title.trim(),
                description = input.description.trim().ifBlank { null },
                condition = input.condition?.ifBlank { null },
                priceType = input.priceType?.ifBlank { null },
                pricePaise = if (input.priceType == "free") 0L else Rupees.toPaise(input.priceRupees)?.toLong(),
                pinCode = input.pinCode.trim().ifBlank { null },
                categoryAttributes = input.categoryAttributes,
            )

            if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

            val insertedRow = gateway.insertListing(insertDto)

            if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

            val privateLocation = gateway.fetchSellerPrivateLocation(insertedRow.id)

            if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

            SellerMutationResult.Success(
                insertedRow.toDetail(
                    readyMediaCount = 0,
                    privatePinCode = privateLocation?.pinCode,
                )
            )
        }

        if (result is SellerMutationResult.Success) {
            notifyDashboardInvalidated(expectedSession)
        }
        return result
    }

    suspend fun saveEdit(
        listingId: String,
        input: SellerListingInput,
        expectedSession: SellerSession,
    ): SellerMutationResult<SellerListingDetail> {
        if (!SellerListingValidation.isUuid(listingId)) {
            return SellerMutationResult.Failure("Invalid listing ID.")
        }
        val validation = SellerListingValidation.validateDraft(input)
        if (!validation.isValid) {
            return SellerMutationResult.ValidationError(validation)
        }

        if (!sessionStillMatches(expectedSession)) return SellerMutationResult.AuthRequired

        val lockToken = acquireMutationLock(listingId, expectedSession)
            ?: return SellerMutationResult.Failure("An operation is already in progress for this listing.")

        try {
            val result = runPolicyGuardedMutation(expectedSession) { userId ->
                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val existingRow = gateway.fetchSellerListingDetail(listingId, userId)
                    ?: return@runPolicyGuardedMutation SellerMutationResult.Failure("Listing not found.")

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val existingStatus = SellerListingStatus.fromWire(existingRow.status)
                if (!existingStatus.canEdit) {
                    return@runPolicyGuardedMutation SellerMutationResult.Failure(
                        "Listings with status '${existingStatus.label}' cannot be edited."
                    )
                }

                val updateDto = SellerListingUpdateDto(
                    categoryId = input.categoryId,
                    cityId = input.cityId!!,
                    localityId = input.localityId?.ifBlank { null },
                    title = input.title.trim(),
                    description = input.description.trim().ifBlank { null },
                    condition = input.condition?.ifBlank { null },
                    priceType = input.priceType?.ifBlank { null },
                    pricePaise = if (input.priceType == "free") 0L else Rupees.toPaise(input.priceRupees)?.toLong(),
                    pinCode = input.pinCode.trim().ifBlank { null },
                    categoryAttributes = input.categoryAttributes,
                )

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val updatedRow = gateway.updateListing(listingId, userId, updateDto)

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val reloadedRow = gateway.fetchSellerListingDetail(listingId, userId) ?: updatedRow
                val mediaCounts = gateway.fetchReadyMediaCounts(listOf(listingId))

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val privateLocation = gateway.fetchSellerPrivateLocation(listingId)

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                SellerMutationResult.Success(
                    reloadedRow.toDetail(
                        readyMediaCount = mediaCounts[listingId] ?: 0,
                        privatePinCode = privateLocation?.pinCode,
                    )
                )
            }

            if (result is SellerMutationResult.Success) {
                notifyDashboardInvalidated(expectedSession)
            }
            return result
        } finally {
            releaseMutationLock(lockToken)
        }
    }

    suspend fun submitListing(
        listingId: String,
        attributeMetadata: List<CategoryAttributeMetadata> = emptyList(),
        expectedSession: SellerSession,
    ): SellerMutationResult<SellerListingDetail> {
        if (!SellerListingValidation.isUuid(listingId)) {
            return SellerMutationResult.Failure("Invalid listing ID.")
        }

        if (!sessionStillMatches(expectedSession)) return SellerMutationResult.AuthRequired

        val lockToken = acquireMutationLock(listingId, expectedSession)
            ?: return SellerMutationResult.Failure("An operation is already in progress for this listing.")

        try {
            val result = runPolicyGuardedMutation(expectedSession) { userId ->
                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val existingRow = gateway.fetchSellerListingDetail(listingId, userId)
                    ?: return@runPolicyGuardedMutation SellerMutationResult.Failure("Listing not found.")

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val existingPrivateLocation = gateway.fetchSellerPrivateLocation(listingId)

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val existingDetail = existingRow.toDetail(
                    readyMediaCount = 0,
                    privatePinCode = existingPrivateLocation?.pinCode,
                )
                val persistedInput = existingDetail.toInput()

                val submitValidation = SellerListingValidation.validateSubmit(persistedInput, attributeMetadata)
                if (!submitValidation.isValid) {
                    return@runPolicyGuardedMutation SellerMutationResult.ValidationError(submitValidation)
                }

                val status = existingDetail.status
                if (status != SellerListingStatus.DRAFT && status != SellerListingStatus.REJECTED) {
                    return@runPolicyGuardedMutation SellerMutationResult.Failure(
                        "Only drafts or rejected listings can be submitted for review."
                    )
                }

                val mediaCounts = gateway.fetchReadyMediaCounts(listOf(listingId))
                val readyCount = mediaCounts[listingId] ?: 0
                if (readyCount == 0) {
                    return@runPolicyGuardedMutation SellerMutationResult.Failure(
                        "At least one ready photo is required before submitting for review."
                    )
                }

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                gateway.submitListing(listingId)

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val reloadedRow = gateway.fetchSellerListingDetail(listingId, userId)
                    ?: return@runPolicyGuardedMutation SellerMutationResult.Failure("Unable to reload listing after submission.")
                val reloadedPrivateLocation = gateway.fetchSellerPrivateLocation(listingId)

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                SellerMutationResult.Success(
                    reloadedRow.toDetail(
                        readyMediaCount = readyCount,
                        privatePinCode = reloadedPrivateLocation?.pinCode,
                    )
                )
            }

            if (result is SellerMutationResult.Success) {
                notifyDashboardInvalidated(expectedSession)
            }
            return result
        } finally {
            releaseMutationLock(lockToken)
        }
    }

    suspend fun executeLifecycleAction(
        listingId: String,
        action: SellerLifecycleAction,
        expectedSession: SellerSession,
    ): SellerMutationResult<SellerListingDetail> {
        if (!SellerListingValidation.isUuid(listingId)) {
            return SellerMutationResult.Failure("Invalid listing ID.")
        }

        if (!sessionStillMatches(expectedSession)) return SellerMutationResult.AuthRequired

        val lockToken = acquireMutationLock(listingId, expectedSession)
            ?: return SellerMutationResult.Failure("An operation is already in progress for this listing.")

        try {
            val result = runPolicyGuardedMutation(expectedSession) { userId ->
                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val existingRow = gateway.fetchSellerListingDetail(listingId, userId)
                    ?: return@runPolicyGuardedMutation SellerMutationResult.Failure("Listing not found.")

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val currentStatus = SellerListingStatus.fromWire(existingRow.status)
                if (!isActionAllowed(currentStatus, action, existingRow.renewalCount)) {
                    return@runPolicyGuardedMutation SellerMutationResult.Failure(
                        "Action '${action.label}' is not allowed for listing status '${currentStatus.label}'."
                    )
                }

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                gateway.transitionListing(listingId, action.wire)

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                val reloadedRow = gateway.fetchSellerListingDetail(listingId, userId)
                    ?: return@runPolicyGuardedMutation SellerMutationResult.Failure("Unable to reload listing after transition.")
                val mediaCounts = gateway.fetchReadyMediaCounts(listOf(listingId))
                val privateLocation = gateway.fetchSellerPrivateLocation(listingId)

                if (!sessionStillMatches(expectedSession)) return@runPolicyGuardedMutation SellerMutationResult.AuthRequired

                SellerMutationResult.Success(
                    reloadedRow.toDetail(
                        readyMediaCount = mediaCounts[listingId] ?: 0,
                        privatePinCode = privateLocation?.pinCode,
                    )
                )
            }

            if (result is SellerMutationResult.Success) {
                notifyDashboardInvalidated(expectedSession)
            }
            return result
        } finally {
            releaseMutationLock(lockToken)
        }
    }

    private fun isActionAllowed(status: SellerListingStatus, action: SellerLifecycleAction, renewalCount: Int): Boolean {
        return when (action) {
            SellerLifecycleAction.PAUSE -> status == SellerListingStatus.ACTIVE
            SellerLifecycleAction.RESUME -> status == SellerListingStatus.PAUSED
            SellerLifecycleAction.RESERVE -> status == SellerListingStatus.ACTIVE
            SellerLifecycleAction.UNRESERVE -> status == SellerListingStatus.RESERVED
            SellerLifecycleAction.MARK_SOLD -> status in setOf(
                SellerListingStatus.ACTIVE,
                SellerListingStatus.RESERVED,
                SellerListingStatus.PAUSED,
            )
            SellerLifecycleAction.RENEW -> status == SellerListingStatus.EXPIRED && renewalCount < 3
        }
    }

    private suspend fun <T> runPolicyGuardedMutation(
        expectedSession: SellerSession,
        block: suspend (userId: String) -> SellerMutationResult<T>
    ): SellerMutationResult<T> {
        val userId = expectedSession.userId

        return try {
            withTimeout(15_000) {
                if (!sessionStillMatches(expectedSession)) {
                    return@withTimeout SellerMutationResult.AuthRequired
                }
                if (!gateway.policyAccepted()) {
                    return@withTimeout SellerMutationResult.PolicyReviewRequired
                }
                if (!sessionStillMatches(expectedSession)) {
                    return@withTimeout SellerMutationResult.AuthRequired
                }
                val result = block(userId)
                if (!sessionStillMatches(expectedSession)) {
                    return@withTimeout SellerMutationResult.AuthRequired
                }
                result
            }
        } catch (_: TimeoutCancellationException) {
            SellerMutationResult.Failure("Operation timed out. Please try again.")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SellerMutationResult.Failure("Unable to complete request. Please try again.")
        }
    }
}
