package com.fairswap.feature.sellerlistings

import com.fairswap.core.sellerlistings.CategoryAttributeMetadata
import com.fairswap.core.sellerlistings.PincodeLookupResponseDto
import com.fairswap.core.sellerlistings.SellerListingGateway
import com.fairswap.core.sellerlistings.SellerListingInsertDto
import com.fairswap.core.sellerlistings.SellerListingRepository
import com.fairswap.core.sellerlistings.SellerListingRowDto
import com.fairswap.core.sellerlistings.SellerListingStatus
import com.fairswap.core.sellerlistings.SellerListingUpdateDto
import com.fairswap.core.sellerlistings.SellerMediaGateway
import com.fairswap.core.sellerlistings.SellerMediaGetSignedUrlResult
import com.fairswap.core.sellerlistings.SellerMediaGetSnapshotResult
import com.fairswap.core.sellerlistings.SellerMediaItem
import com.fairswap.core.sellerlistings.SellerMediaListingSnapshot
import com.fairswap.core.sellerlistings.SellerMediaMutationAcknowledgement
import com.fairswap.core.sellerlistings.SellerMediaMutationResult
import com.fairswap.core.sellerlistings.SellerMediaProcessingStatus
import com.fairswap.core.sellerlistings.SellerMediaRepository
import com.fairswap.core.sellerlistings.SellerMediaSignedUrl
import com.fairswap.core.sellerlistings.SellerMediaSnapshot
import com.fairswap.core.sellerlistings.SellerMediaUploadFile
import com.fairswap.core.sellerlistings.SellerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private val validListingId = "11111111-2222-3333-4444-555555555555"
private val validMediaId = "99999999-8888-7777-6666-555555555555"
private val validCityId = "66666666-7777-8888-9999-000000000000"
private val validCategoryId = "77777777-8888-9999-0000-111111111111"

private class FakeMediaGateway : SellerMediaGateway {
    var snapshotResult: SellerMediaGetSnapshotResult = SellerMediaGetSnapshotResult.Success(
        SellerMediaSnapshot(
            listing = SellerMediaListingSnapshot(validListingId, SellerListingStatus.DRAFT, 1, null),
            media = emptyList(),
        )
    )

    override suspend fun getSnapshot(listingId: String) = snapshotResult

    override suspend fun uploadMedia(
        listingId: String,
        file: SellerMediaUploadFile,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
        return SellerMediaMutationResult.Success(
            SellerMediaMutationAcknowledgement.Upload(
                committed = true,
                        refreshRequired = false,


                snapshot = (snapshotResult as? SellerMediaGetSnapshotResult.Success)?.snapshot,
                uploadedMediaId = validMediaId,
                processingStatus = SellerMediaProcessingStatus.READY,
            )
        )
    }

    override suspend fun deleteMedia(
        listingId: String,
        mediaId: String,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Delete> {
        return SellerMediaMutationResult.Success(
            SellerMediaMutationAcknowledgement.Delete(
                committed = true,
                        refreshRequired = false,


                snapshot = (snapshotResult as? SellerMediaGetSnapshotResult.Success)?.snapshot,
                deleted = true,
                deletedMediaId = mediaId,
                newCoverId = null,
            )
        )
    }

    override suspend fun reorderMedia(
        listingId: String,
        mediaIds: List<String>,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Reorder> {
        return SellerMediaMutationResult.Success(
            SellerMediaMutationAcknowledgement.Reorder(
                committed = true,
                        refreshRequired = false,


                snapshot = (snapshotResult as? SellerMediaGetSnapshotResult.Success)?.snapshot,
                changed = true,
            )
        )
    }

    override suspend fun setCoverPhoto(
        listingId: String,
        mediaId: String,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Cover> {
        return SellerMediaMutationResult.Success(
            SellerMediaMutationAcknowledgement.Cover(
                committed = true,
                        refreshRequired = false,


                snapshot = (snapshotResult as? SellerMediaGetSnapshotResult.Success)?.snapshot,
                coverPhotoId = mediaId,
            )
        )
    }

    override suspend fun getSignedDerivativeUrl(
        listingId: String,
        mediaId: String,
        size: String,
    ): SellerMediaGetSignedUrlResult {
        return SellerMediaGetSignedUrlResult.Success(SellerMediaSignedUrl("https://example.com/signed.webp", 60))
    }
}

private class FakeListingGateway : SellerListingGateway {
    override suspend fun currentUserId() = "u1"
    override suspend fun fetchCategories() = listOf(com.fairswap.core.discovery.Category(validCategoryId, "Category 1", "cat1"))
    override suspend fun fetchCities() = listOf(com.fairswap.core.discovery.City(validCityId, "District1"))
    override suspend fun fetchLocalities(cityId: String) = emptyList<com.fairswap.core.discovery.Locality>()
    override suspend fun fetchSellerListings(sellerId: String, limit: Int) = emptyList<SellerListingRowDto>()
    override suspend fun fetchReadyMediaCounts(listingIds: List<String>) = mapOf(validListingId to 1)
    override suspend fun fetchSellerListingDetail(listingId: String, sellerId: String): SellerListingRowDto {
        return SellerListingRowDto(
            id = listingId,
            sellerId = sellerId,
            title = "Title",
            categoryId = validCategoryId,
            cityId = validCityId,
            status = "draft",
        )
    }
    override suspend fun fetchSellerPrivateLocation(listingId: String) = com.fairswap.core.sellerlistings.SellerPrivateLocationDto(listingId, "302001", "Landmark")
    override suspend fun fetchCategoryAttributes(categoryId: String) = emptyList<CategoryAttributeMetadata>()
    override suspend fun insertListing(insertDto: SellerListingInsertDto) = SellerListingRowDto(validListingId, "u1", "New Guitar", validCategoryId, validCityId)
    override suspend fun updateListing(listingId: String, sellerId: String, updateDto: SellerListingUpdateDto) = SellerListingRowDto(listingId, sellerId, "New Guitar", validCategoryId, validCityId)
    override suspend fun submitListing(listingId: String) {}
    override suspend fun transitionListing(listingId: String, action: String) {}
    override suspend fun lookupPincode(pinCode: String) = PincodeLookupResponseDto(
        success = true,
        pincode = pinCode,
        state = "State1",
        district = "District1",
        city = "City 1",
        cityId = validCityId,
    )
    override suspend fun policyAccepted() = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class SellerListingEditorMediaTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createDraft emits Created event and does not pop route`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)
        val mediaGateway = FakeMediaGateway()
        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session = listingRepo.sessionOwner.updateSession("u1")!!

        viewModel.bind(session, listingId = null, entryId = "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateTitle("New Guitar")
        viewModel.updateCategory(validCategoryId)
        viewModel.updatePinCode("302001")
        testDispatcher.scheduler.advanceUntilIdle()

        val eventDeferred = async { viewModel.events.first() }

        assertTrue("isSaveReady should be true", viewModel.state.value.isSaveReady)
        viewModel.saveDraft("entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        val capturedEvent = eventDeferred.await()
        assertTrue(capturedEvent is SellerListingEditorEvent.Created)
        assertEquals(validListingId, (capturedEvent as SellerListingEditorEvent.Created).listingId)
    }

    @Test
    fun `bind Edit route loads media snapshot and signed URLs`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)
        val mediaGateway = FakeMediaGateway()
        mediaGateway.snapshotResult = SellerMediaGetSnapshotResult.Success(
            SellerMediaSnapshot(
                listing = SellerMediaListingSnapshot(validListingId, SellerListingStatus.DRAFT, 1, validMediaId),
                media = listOf(
                    SellerMediaItem(validMediaId, validListingId, 100, 100, 0, SellerMediaProcessingStatus.READY, "", true)
                )
            )
        )
        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session = listingRepo.sessionOwner.updateSession("u1")!!

        viewModel.bind(session, listingId = validListingId, entryId = "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("State: ${viewModel.state.value}", 1, viewModel.state.value.mediaSnapshot?.media?.size)
        assertEquals("https://example.com/signed.webp", viewModel.state.value.signedUrls[validMediaId])
        assertEquals(1, viewModel.state.value.readyMediaCount)
    }

    @Test
    fun `serial queue processes one at a time and stops on failure`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)

        var uploadCallCount = 0
        var currentConcurrentUploads = 0
        var maxConcurrentUploads = 0
        var failureOnNext = false
        val completedUploads = mutableListOf<String>()
        var uploadStartBarrier = kotlinx.coroutines.CompletableDeferred<Unit>()
        var uploadResumeBarrier = kotlinx.coroutines.CompletableDeferred<Unit>()

        val mediaGateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(
                    listing = SellerMediaListingSnapshot(validListingId, SellerListingStatus.DRAFT, 1, null),
                    media = emptyList(),
                )
            )

            override suspend fun uploadMedia(
                listingId: String,
                file: SellerMediaUploadFile,
            ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
                uploadCallCount++
                currentConcurrentUploads++
                if (currentConcurrentUploads > maxConcurrentUploads) {
                    maxConcurrentUploads = currentConcurrentUploads
                }

                uploadStartBarrier.complete(Unit)
                uploadResumeBarrier.await()

                currentConcurrentUploads--

                if (failureOnNext) {
                    failureOnNext = false
                    return SellerMediaMutationResult.Failure(com.fairswap.core.sellerlistings.SellerMediaFailure.MediaServiceUnavailable)
                }

                completedUploads.add(file.fileName)

                return SellerMediaMutationResult.Success(
                    SellerMediaMutationAcknowledgement.Upload(
                        committed = true,
                        refreshRequired = false,

                        snapshot = SellerMediaSnapshot(
                            listing = SellerMediaListingSnapshot(validListingId, SellerListingStatus.DRAFT, 1, null),
                            media = emptyList(),
                        ),
                        uploadedMediaId = "new",
                        processingStatus = SellerMediaProcessingStatus.READY,
                    )
                )
            }
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }

        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session = listingRepo.sessionOwner.updateSession("u1")!!
        viewModel.bind(session, listingId = validListingId, entryId = "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        val fileA = SellerMediaUploadFile("A", ByteArray(0), "image/jpeg")
        val fileB = SellerMediaUploadFile("B", ByteArray(0), "image/jpeg")

        // Start queue
        viewModel.queueUploads(listOf(fileA, fileB))

        // Let A reach the barrier
        uploadStartBarrier.await()
        assertEquals(1, uploadCallCount)
        assertEquals(1, currentConcurrentUploads)
        assertEquals(1, maxConcurrentUploads)
        assertTrue(completedUploads.isEmpty())

        // Reset start barrier for B
        uploadStartBarrier = kotlinx.coroutines.CompletableDeferred<Unit>()

        // Resume A
        val resumeA = uploadResumeBarrier
        uploadResumeBarrier = kotlinx.coroutines.CompletableDeferred<Unit>()
        resumeA.complete(Unit)

        // Wait for B to start
        uploadStartBarrier.await()
        assertEquals(2, uploadCallCount)
        assertEquals(1, currentConcurrentUploads)
        assertEquals(1, maxConcurrentUploads)
        assertEquals(listOf("A"), completedUploads)

        // Resume B
        uploadResumeBarrier.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("A", "B"), completedUploads)

        // Now test stop on failure
        completedUploads.clear()
        uploadCallCount = 0
        failureOnNext = true
        uploadStartBarrier = kotlinx.coroutines.CompletableDeferred<Unit>()
        uploadResumeBarrier = kotlinx.coroutines.CompletableDeferred<Unit>()

        viewModel.queueUploads(listOf(fileA, fileB))

        uploadStartBarrier.await()
        assertEquals(1, uploadCallCount)
        uploadResumeBarrier.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Since A failed, B should never have started
        assertEquals(1, uploadCallCount)
        assertTrue(completedUploads.isEmpty())
    }

    @Test
    fun `stale upload queue stops after session replacement`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)

        var uploadCallCount = 0
        val uploadStartBarrier = kotlinx.coroutines.CompletableDeferred<Unit>()
        val uploadResumeBarrier = kotlinx.coroutines.CompletableDeferred<Unit>()

        val mediaGateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(
                    listing = SellerMediaListingSnapshot(validListingId, SellerListingStatus.DRAFT, 1, null),
                    media = emptyList(),
                )
            )

            override suspend fun uploadMedia(
                listingId: String,
                file: SellerMediaUploadFile,
            ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
                uploadCallCount++
                uploadStartBarrier.complete(Unit)
                uploadResumeBarrier.await()

                return SellerMediaMutationResult.Success(
                    SellerMediaMutationAcknowledgement.Upload(
                        committed = true,
                        refreshRequired = false,
                        snapshot = SellerMediaSnapshot(
                            listing = SellerMediaListingSnapshot(validListingId, SellerListingStatus.DRAFT, 1, null),
                            media = listOf(SellerMediaItem("m_stale", validListingId, 100, 100, 0, SellerMediaProcessingStatus.READY, "", false)),
                        ),
                        uploadedMediaId = "m_stale",
                        processingStatus = SellerMediaProcessingStatus.READY,
                    )
                )
            }
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }

        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session1 = listingRepo.sessionOwner.updateSession("u1")!!
        viewModel.bind(session1, listingId = validListingId, entryId = "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        val fileA = SellerMediaUploadFile("A", ByteArray(0), "image/jpeg")
        val fileB = SellerMediaUploadFile("B", ByteArray(0), "image/jpeg")

        viewModel.queueUploads(listOf(fileA, fileB))

        uploadStartBarrier.await()
        assertEquals(1, uploadCallCount)

        // Re-bind to session2 (new generation / route identity)
        val session2 = listingRepo.sessionOwner.updateSession("u1")!! // incremented generation
        viewModel.bind(session2, listingId = validListingId, entryId = "entry2")
        testDispatcher.scheduler.advanceUntilIdle()

        // Release A
        uploadResumeBarrier.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify B was never called
        assertEquals(1, uploadCallCount)
        // Verify stale A snapshot was NOT applied to new session2 state
        assertEquals(0, viewModel.state.value.readyMediaCount)
    }

    @Test
    fun `private image failure refreshes signed url only once`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)

        var signedUrlCalls = 0
        val mediaGateway = object : SellerMediaGateway {
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String): SellerMediaGetSignedUrlResult {
                signedUrlCalls++
                return SellerMediaGetSignedUrlResult.Success(SellerMediaSignedUrl("https://example.com/fresh.webp", 60))
            }
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(SellerMediaSnapshot(SellerMediaListingSnapshot(validListingId, SellerListingStatus.DRAFT, 1, null), listOf(SellerMediaItem(validMediaId, validListingId, 100, 100, 0, SellerMediaProcessingStatus.READY, "", false))))
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
        }

        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session = listingRepo.sessionOwner.updateSession("u1")!!
        viewModel.bind(session, validListingId, "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Reset count after initial snapshot load
        signedUrlCalls = 0

        // First image load failure -> triggers refresh
        viewModel.onPrivateImageLoadFailed(validMediaId, "thumb")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, signedUrlCalls)
        assertEquals("https://example.com/fresh.webp", viewModel.state.value.signedUrls[validMediaId])

        // Second image load failure for same key -> no further request
        viewModel.onPrivateImageLoadFailed(validMediaId, "thumb")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, signedUrlCalls)
    }

    @Test
    fun `session generation change clears refresh attempt state`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)

        var signedUrlCalls = 0
        val mediaGateway = object : SellerMediaGateway {
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String): SellerMediaGetSignedUrlResult {
                signedUrlCalls++
                return SellerMediaGetSignedUrlResult.Success(SellerMediaSignedUrl("https://example.com/fresh.webp", 60))
            }
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(SellerMediaSnapshot(SellerMediaListingSnapshot(validListingId, SellerListingStatus.DRAFT, 1, null), listOf(SellerMediaItem(validMediaId, validListingId, 100, 100, 0, SellerMediaProcessingStatus.READY, "", false))))
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
        }

        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session1 = listingRepo.sessionOwner.updateSession("u1")!!
        viewModel.bind(session1, validListingId, "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        signedUrlCalls = 0
        viewModel.onPrivateImageLoadFailed(validMediaId, "thumb")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, signedUrlCalls)

        // New session generation
        val session2 = listingRepo.sessionOwner.updateSession("u1")!! // generation = 2
        viewModel.bind(session2, validListingId, "entry2")
        testDispatcher.scheduler.advanceUntilIdle()

        signedUrlCalls = 0
        viewModel.onPrivateImageLoadFailed(validMediaId, "thumb")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, signedUrlCalls)
    }

    @Test
    fun `picker mode 0 1 2 8 deterministic tests`() {
        val state0 = com.fairswap.feature.sellerlistings.components.SellerMediaSectionState(
            items = List(8) { SellerMediaItem("m$it", validListingId, 100, 100, it, SellerMediaProcessingStatus.READY, "", false) }
        )
        assertEquals(com.fairswap.feature.sellerlistings.components.SellerMediaPickerMode.DISABLED, state0.pickerMode)

        val state1 = com.fairswap.feature.sellerlistings.components.SellerMediaSectionState(
            items = List(7) { SellerMediaItem("m$it", validListingId, 100, 100, it, SellerMediaProcessingStatus.READY, "", false) }
        )
        assertEquals(com.fairswap.feature.sellerlistings.components.SellerMediaPickerMode.SINGLE, state1.pickerMode)

        val state2 = com.fairswap.feature.sellerlistings.components.SellerMediaSectionState(
            items = List(6) { SellerMediaItem("m$it", validListingId, 100, 100, it, SellerMediaProcessingStatus.READY, "", false) }
        )
        assertEquals(com.fairswap.feature.sellerlistings.components.SellerMediaPickerMode.MULTIPLE, state2.pickerMode)

        val state8 = com.fairswap.feature.sellerlistings.components.SellerMediaSectionState(
            items = emptyList()
        )
        assertEquals(com.fairswap.feature.sellerlistings.components.SellerMediaPickerMode.MULTIPLE, state8.pickerMode)
    }

    @Test
    fun `AuthRequired media failure emits AuthRequired event`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)
        val mediaGateway = object : SellerMediaGateway {
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = SellerMediaMutationResult.Failure(com.fairswap.core.sellerlistings.SellerMediaFailure.AuthRequired)
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Failure(com.fairswap.core.sellerlistings.SellerMediaFailure.AuthRequired)
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session = listingRepo.sessionOwner.updateSession("u1")!!
        viewModel.bind(session, validListingId, "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        val eventDeferred = async { viewModel.events.first() }

        viewModel.queueUploads(listOf(SellerMediaUploadFile("test", ByteArray(0), "image/jpeg")))
        testDispatcher.scheduler.advanceUntilIdle()

        val capturedEvent = eventDeferred.await()
        assertTrue(capturedEvent is SellerListingEditorEvent.AuthRequired)
    }

    @Test
    fun `PolicyReviewRequired media failure emits PolicyReviewRequired event`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)
        val mediaGateway = object : SellerMediaGateway {
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = SellerMediaMutationResult.Failure(com.fairswap.core.sellerlistings.SellerMediaFailure.PolicyReviewRequired)
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Failure(com.fairswap.core.sellerlistings.SellerMediaFailure.AuthRequired)
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session = listingRepo.sessionOwner.updateSession("u1")!!
        viewModel.bind(session, validListingId, "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        val eventDeferred = async { viewModel.events.first() }

        viewModel.queueUploads(listOf(SellerMediaUploadFile("test", ByteArray(0), "image/jpeg")))
        testDispatcher.scheduler.advanceUntilIdle()

        val capturedEvent = eventDeferred.await()
        assertTrue(capturedEvent is SellerListingEditorEvent.PolicyReviewRequired)
    }

    @Test
    fun `AccountRestricted uses explicit restricted account-gate event`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)
        val mediaGateway = object : SellerMediaGateway {
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = SellerMediaMutationResult.Failure(com.fairswap.core.sellerlistings.SellerMediaFailure.AccountRestricted)
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Failure(com.fairswap.core.sellerlistings.SellerMediaFailure.AuthRequired)
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session = listingRepo.sessionOwner.updateSession("u1")!!
        viewModel.bind(session, validListingId, "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        val eventDeferred = async { viewModel.events.first() }

        viewModel.queueUploads(listOf(SellerMediaUploadFile("test", ByteArray(0), "image/jpeg")))
        testDispatcher.scheduler.advanceUntilIdle()

        val capturedEvent = eventDeferred.await()
        assertTrue(capturedEvent is SellerListingEditorEvent.Restricted)
    }

    @Test
    fun `media snapshot preserves listing input pincode and locality state`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)
        val mediaGateway = FakeMediaGateway()
        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session = listingRepo.sessionOwner.updateSession("u1")!!
        viewModel.bind(session, validListingId, "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Mutate local state
        viewModel.updateTitle("Locally Edited")
        viewModel.updatePinCode("110001")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateLocality("loc_123")

        assertEquals("Locally Edited", viewModel.state.value.input.title)
        assertEquals("110001", viewModel.state.value.input.pinCode)
        assertEquals("loc_123", viewModel.state.value.input.localityId)

        // Emulate incoming snapshot
        viewModel.applyMediaSnapshot(
            expectedSession = session,
            expectedEntryId = "entry1",
            expectedListingId = validListingId,
            snapshot = SellerMediaSnapshot(SellerMediaListingSnapshot(validListingId, SellerListingStatus.PENDING_REVIEW, 5, "m99"), emptyList())
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Verification
        assertEquals("Locally Edited", viewModel.state.value.input.title)
        assertEquals("110001", viewModel.state.value.input.pinCode)
        assertEquals("loc_123", viewModel.state.value.input.localityId)
        assertEquals(SellerListingStatus.PENDING_REVIEW, viewModel.state.value.persistedListing?.status)
    }

    @Test
    fun `stale refresh result after identity change is ignored`() = runTest {
        val listingGateway = FakeListingGateway()
        val listingRepo = SellerListingRepository(listingGateway)

        var requestCount = 0
        var suspendBarrier = kotlinx.coroutines.CompletableDeferred<Unit>()
        val mediaGateway = object : SellerMediaGateway {
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String): SellerMediaGetSignedUrlResult {
                requestCount++
                suspendBarrier.await()
                return SellerMediaGetSignedUrlResult.Success(SellerMediaSignedUrl("https://example.com/late", 60))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(SellerMediaSnapshot(SellerMediaListingSnapshot(validListingId, SellerListingStatus.DRAFT, 1, null), listOf(SellerMediaItem("m1", validListingId, 100, 100, 0, SellerMediaProcessingStatus.READY, "", false))))
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
        }
        val mediaRepo = SellerMediaRepository(mediaGateway, listingRepo)
        val viewModel = SellerListingEditorViewModel(listingRepo, mediaRepo)

        val session1 = listingRepo.sessionOwner.updateSession("u1")!!
        viewModel.bind(session1, validListingId, "entry1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Wait for it to suspend in requestSignedUrl
        assertEquals(1, requestCount)

        // Change session identity
        val session2 = listingRepo.sessionOwner.updateSession("u2")!!
        viewModel.bind(session2, validListingId, "entry2")
        testDispatcher.scheduler.advanceUntilIdle()

        // Release the first request
        suspendBarrier.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert the stale URL was NOT applied to the new state
        assertNull(viewModel.state.value.signedUrls["m1"])
    }
}
