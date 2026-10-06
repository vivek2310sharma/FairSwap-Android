package com.fairswap.core.sellerlistings

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSellerMediaGateway : SellerMediaGateway {
    var getSnapshotResult: SellerMediaGetSnapshotResult = SellerMediaGetSnapshotResult.Success(
        SellerMediaSnapshot(
            listing = SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null),
            media = emptyList(),
        )
    )

    override suspend fun getSnapshot(listingId: String) = getSnapshotResult

    override suspend fun uploadMedia(
        listingId: String,
        file: SellerMediaUploadFile,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
        return SellerMediaMutationResult.Success(
            SellerMediaMutationAcknowledgement.Upload(
                committed = true,
                refreshRequired = false,
                snapshot = (getSnapshotResult as? SellerMediaGetSnapshotResult.Success)?.snapshot,
                uploadedMediaId = "m1",
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
                snapshot = (getSnapshotResult as? SellerMediaGetSnapshotResult.Success)?.snapshot,
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
                snapshot = (getSnapshotResult as? SellerMediaGetSnapshotResult.Success)?.snapshot,
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
                snapshot = (getSnapshotResult as? SellerMediaGetSnapshotResult.Success)?.snapshot,
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

private class FakeSellerListingGateway : SellerListingGateway {
    override suspend fun currentUserId() = "u1"
    override suspend fun fetchCategories() = emptyList<com.fairswap.core.discovery.Category>()
    override suspend fun fetchCities() = emptyList<com.fairswap.core.discovery.City>()
    override suspend fun fetchLocalities(cityId: String) = emptyList<com.fairswap.core.discovery.Locality>()
    override suspend fun fetchSellerListings(sellerId: String, limit: Int) = emptyList<SellerListingRowDto>()
    override suspend fun fetchReadyMediaCounts(listingIds: List<String>) = emptyMap<String, Int>()
    override suspend fun fetchSellerListingDetail(listingId: String, sellerId: String) = null
    override suspend fun fetchSellerPrivateLocation(listingId: String) = null
    override suspend fun fetchCategoryAttributes(categoryId: String) = emptyList<CategoryAttributeMetadata>()
    override suspend fun insertListing(insertDto: SellerListingInsertDto) = SellerListingRowDto("l1", "u1", "Title", "c1", "city1")
    override suspend fun updateListing(listingId: String, sellerId: String, updateDto: SellerListingUpdateDto) = SellerListingRowDto(listingId, sellerId, "Title", "c1", "city1")
    override suspend fun submitListing(listingId: String) {}
    override suspend fun transitionListing(listingId: String, action: String) {}
    override suspend fun lookupPincode(pinCode: String) = PincodeLookupResponseDto(success = false)
    override suspend fun policyAccepted() = true
}

class SellerMediaRepositoryTest {

    @Test
    fun `reconcileListingWithMediaSnapshot updates readyMediaCount status revision and cover`() {
        val detail = SellerListingDetail(
            id = "l1",
            sellerId = "u1",
            slug = "title",
            categoryId = "c1",
            cityId = "city1",
            localityId = null,
            title = "Title",
            description = "Desc",
            condition = "good",
            priceType = "fixed",
            pricePaise = 10000L,
            formattedPrice = "₹100.00",
            pinCode = "302001",
            landmark = null,
            categoryAttributes = kotlinx.serialization.json.JsonObject(emptyMap()),
            status = SellerListingStatus.ACTIVE,
            revision = 1,
            renewalCount = 0,
            rejectionReason = null,
            removalReason = null,
            coverPhotoId = null,
            readyMediaCount = 0,
            createdAt = "",
            updatedAt = "",
        )

        val snapshot = SellerMediaSnapshot(
            listing = SellerMediaListingSnapshot("l1", SellerListingStatus.PENDING_REVIEW, 2, "m1"),
            media = listOf(
                SellerMediaItem("m1", "l1", 100, 100, 0, SellerMediaProcessingStatus.READY, "", true),
                SellerMediaItem("m2", "l1", 100, 100, 1, SellerMediaProcessingStatus.UPLOADING, "", false),
            )
        )

        val reconciled = reconcileListingWithMediaSnapshot(detail, snapshot)
        assertTrue(reconciled != null)
        assertEquals(SellerListingStatus.PENDING_REVIEW, reconciled!!.status)
        assertEquals(2, reconciled.revision)
        assertEquals("m1", reconciled.coverPhotoId)
        assertEquals(1, reconciled.readyMediaCount) // Counts READY items only
    }

    @Test
    fun `getSnapshot returns AuthRequired when session is inactive`() = runTest {
        val gateway = FakeSellerMediaGateway()
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val repo = SellerMediaRepository(gateway, listingRepo)

        val inactiveSession = SellerSession("u1", 999L)
        val result = repo.getSnapshot("l1", inactiveSession)

        assertTrue(result is SellerMediaGetSnapshotResult.Failure)
        assertEquals(SellerMediaFailure.AuthRequired, (result as SellerMediaGetSnapshotResult.Failure).error)
    }

    @Test
    fun `upload TransportUncertain triggers one GET without replay`() = runTest {
        var uploadCalls = 0
        var getCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
                uploadCalls++
                return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
            }
            override suspend fun getSnapshot(listingId: String): SellerMediaGetSnapshotResult {
                getCalls++
                return SellerMediaGetSnapshotResult.Success(SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), emptyList()))
            }
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.uploadMedia("l1", SellerMediaUploadFile("test", ByteArray(0), "image/jpeg"), activeSession)
        assertEquals(1, uploadCalls)
        assertEquals(1, getCalls)
        assertTrue(result is SellerMediaMutationResult.Failure)
    }

    @Test
    fun `delete TransportUncertain triggers one GET without replay`() = runTest {
        var deleteCalls = 0
        var getCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun deleteMedia(listingId: String, mediaId: String): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Delete> {
                deleteCalls++
                return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
            }
            override suspend fun getSnapshot(listingId: String): SellerMediaGetSnapshotResult {
                getCalls++
                return SellerMediaGetSnapshotResult.Success(SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), emptyList()))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.deleteMedia("l1", "m1", activeSession)
        assertEquals(1, deleteCalls)
        assertEquals(1, getCalls)
        assertTrue(result is SellerMediaMutationResult.Failure)
    }

    @Test
    fun `committed refreshRequired triggers one GET without replay`() = runTest {
        var uploadCalls = 0
        var getCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
                uploadCalls++
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Upload(true, true, null, "m1", SellerMediaProcessingStatus.READY))
            }
            override suspend fun getSnapshot(listingId: String): SellerMediaGetSnapshotResult {
                getCalls++
                return SellerMediaGetSnapshotResult.Success(SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), emptyList()))
            }
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.uploadMedia("l1", SellerMediaUploadFile("test", ByteArray(0), "image/jpeg"), activeSession)
        assertEquals(1, uploadCalls)
        assertEquals(1, getCalls)
        assertTrue(result is SellerMediaMutationResult.Success)
        val ack = (result as SellerMediaMutationResult.Success).value
        assertTrue(ack.snapshot != null)
        assertFalse(ack.refreshRequired)
    }

    @Test
    fun `ProcessingFailed preserves mediaId and triggers one GET retaining snapshot`() = runTest {
        var getCalls = 0
        val returnedSnapshot = SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), listOf(SellerMediaItem("m1", "l1", 100, 100, 0, SellerMediaProcessingStatus.FAILED, "", false)))
        val gateway = object : SellerMediaGateway {
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.ProcessingFailed("m1"))
            }
            override suspend fun getSnapshot(listingId: String): SellerMediaGetSnapshotResult {
                getCalls++
                return SellerMediaGetSnapshotResult.Success(returnedSnapshot)
            }
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.uploadMedia("l1", SellerMediaUploadFile("test", ByteArray(0), "image/jpeg"), activeSession)
        assertEquals(1, getCalls)
        assertTrue(result is SellerMediaMutationResult.Failure)
        val err = (result as SellerMediaMutationResult.Failure).error
        assertTrue(err is SellerMediaFailure.ProcessingFailed)
        assertEquals("m1", (err as SellerMediaFailure.ProcessingFailed).mediaId)
    }

    @Test
    fun `dashboard invalidation authority relies on snapshot`() = runTest {
        var getCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Upload(true, true, null, "m1", SellerMediaProcessingStatus.READY))
            }
            override suspend fun getSnapshot(listingId: String): SellerMediaGetSnapshotResult {
                getCalls++
                return SellerMediaGetSnapshotResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
            }
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val versionBefore = listingRepo.dashboardVersion.value
        val result = repo.uploadMedia("l1", SellerMediaUploadFile("test", ByteArray(0), "image/jpeg"), activeSession)
        assertEquals(1, getCalls)
        assertTrue(result is SellerMediaMutationResult.Success)
        val ack = (result as SellerMediaMutationResult.Success).value
        assertTrue(ack.refreshRequired)
        val versionAfter = listingRepo.dashboardVersion.value
        assertEquals("Dashboard should NOT be invalidated if we don't have authoritative snapshot", versionBefore, versionAfter)
    }

    @Test
    fun `reorder blocks if missing READY membership`() = runTest {
        var reorderCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), listOf(
                    SellerMediaItem("m1", "l1", 100, 100, 0, SellerMediaProcessingStatus.READY, "", false),
                    SellerMediaItem("m2", "l1", 100, 100, 1, SellerMediaProcessingStatus.READY, "", false),
                ))
            )
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Reorder> {
                reorderCalls++
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Reorder(true, false, null, true))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.reorderMedia("l1", listOf("m1"), activeSession) // Missing m2
        assertTrue(result is SellerMediaMutationResult.Failure)
        assertEquals(SellerMediaFailure.InvalidInput, (result as SellerMediaMutationResult.Failure).error)
        assertEquals(0, reorderCalls)
    }

    @Test
    fun `reorder blocks if non-READY media present`() = runTest {
        var reorderCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), listOf(
                    SellerMediaItem("m1", "l1", 100, 100, 0, SellerMediaProcessingStatus.READY, "", false),
                    SellerMediaItem("m2", "l1", 100, 100, 1, SellerMediaProcessingStatus.UPLOADING, "", false),
                ))
            )
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Reorder> {
                reorderCalls++
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Reorder(true, false, null, true))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.reorderMedia("l1", listOf("m1", "m2"), activeSession)
        assertTrue(result is SellerMediaMutationResult.Failure)
        assertEquals(SellerMediaFailure.InvalidInput, (result as SellerMediaMutationResult.Failure).error)
        assertEquals(0, reorderCalls)
    }

    @Test
    fun `reorder blocks if duplicate requested IDs`() = runTest {
        var reorderCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), listOf(
                    SellerMediaItem("m1", "l1", 100, 100, 0, SellerMediaProcessingStatus.READY, "", false),
                ))
            )
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Reorder> {
                reorderCalls++
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Reorder(true, false, null, true))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.reorderMedia("l1", listOf("m1", "m1"), activeSession)
        assertTrue(result is SellerMediaMutationResult.Failure)
        assertEquals(SellerMediaFailure.InvalidInput, (result as SellerMediaMutationResult.Failure).error)
        assertEquals(0, reorderCalls)
    }

    @Test
    fun `reorder blocks if extra requested IDs`() = runTest {
        var reorderCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), listOf(
                    SellerMediaItem("m1", "l1", 100, 100, 0, SellerMediaProcessingStatus.READY, "", false),
                ))
            )
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Reorder> {
                reorderCalls++
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Reorder(true, false, null, true))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.reorderMedia("l1", listOf("m1", "m2"), activeSession)
        assertTrue(result is SellerMediaMutationResult.Failure)
        assertEquals(SellerMediaFailure.InvalidInput, (result as SellerMediaMutationResult.Failure).error)
        assertEquals(0, reorderCalls)
    }

    @Test
    fun `reorder succeeds for exact complete READY membership`() = runTest {
        var reorderCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), listOf(
                    SellerMediaItem("m1", "l1", 100, 100, 0, SellerMediaProcessingStatus.READY, "", false),
                    SellerMediaItem("m2", "l1", 100, 100, 1, SellerMediaProcessingStatus.READY, "", false),
                ))
            )
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Reorder> {
                reorderCalls++
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Reorder(true, false, null, true))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun setCoverPhoto(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.reorderMedia("l1", listOf("m2", "m1"), activeSession)
        assertTrue(result is SellerMediaMutationResult.Success)
        assertEquals(1, reorderCalls)
    }

    @Test
    fun `cover blocks if target not READY`() = runTest {
        var coverCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), listOf(
                    SellerMediaItem("m1", "l1", 100, 100, 0, SellerMediaProcessingStatus.UPLOADING, "", false)
                ))
            )
            override suspend fun setCoverPhoto(listingId: String, mediaId: String): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Cover> {
                coverCalls++
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Cover(true, false, null, mediaId))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.setCoverPhoto("l1", "m1", activeSession) // Target is UPLOADING
        assertTrue(result is SellerMediaMutationResult.Failure)
        assertEquals(SellerMediaFailure.InvalidInput, (result as SellerMediaMutationResult.Failure).error)
        assertEquals(0, coverCalls)
    }

    @Test
    fun `cover blocks if target missing`() = runTest {
        var coverCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), emptyList())
            )
            override suspend fun setCoverPhoto(listingId: String, mediaId: String): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Cover> {
                coverCalls++
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Cover(true, false, null, mediaId))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.setCoverPhoto("l1", "m1", activeSession)
        assertTrue(result is SellerMediaMutationResult.Failure)
        assertEquals(SellerMediaFailure.InvalidInput, (result as SellerMediaMutationResult.Failure).error)
        assertEquals(0, coverCalls)
    }

    @Test
    fun `cover succeeds if target is READY`() = runTest {
        var coverCalls = 0
        val gateway = object : SellerMediaGateway {
            override suspend fun getSnapshot(listingId: String) = SellerMediaGetSnapshotResult.Success(
                SellerMediaSnapshot(SellerMediaListingSnapshot("l1", SellerListingStatus.DRAFT, 1, null), listOf(
                    SellerMediaItem("m1", "l1", 100, 100, 0, SellerMediaProcessingStatus.READY, "", false)
                ))
            )
            override suspend fun setCoverPhoto(listingId: String, mediaId: String): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Cover> {
                coverCalls++
                return SellerMediaMutationResult.Success(SellerMediaMutationAcknowledgement.Cover(true, false, null, mediaId))
            }
            override suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile) = throw NotImplementedError()
            override suspend fun deleteMedia(listingId: String, mediaId: String) = throw NotImplementedError()
            override suspend fun reorderMedia(listingId: String, mediaIds: List<String>) = throw NotImplementedError()
            override suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String) = throw NotImplementedError()
        }
        val listingRepo = SellerListingRepository(FakeSellerListingGateway())
        val activeSession = listingRepo.sessionOwner.updateSession("u1")!!
        val repo = SellerMediaRepository(gateway, listingRepo)

        val result = repo.setCoverPhoto("l1", "m1", activeSession)
        assertTrue(result is SellerMediaMutationResult.Success)
        assertEquals(1, coverCalls)
    }
}
