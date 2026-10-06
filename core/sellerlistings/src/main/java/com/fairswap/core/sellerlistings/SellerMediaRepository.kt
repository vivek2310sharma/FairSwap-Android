package com.fairswap.core.sellerlistings

import kotlinx.coroutines.CancellationException

class SellerMediaRepository(
    private val gateway: SellerMediaGateway,
    private val listingRepository: SellerListingRepository,
) {
    suspend fun getSnapshot(
        listingId: String,
        session: SellerSession,
    ): SellerMediaGetSnapshotResult {
        if (!listingRepository.isSessionActive(session)) {
            return SellerMediaGetSnapshotResult.Failure(SellerMediaFailure.AuthRequired)
        }

        val result = gateway.getSnapshot(listingId)
        if (!listingRepository.isSessionActive(session)) {
            return SellerMediaGetSnapshotResult.Failure(SellerMediaFailure.AuthRequired)
        }

        return result
    }

    suspend fun uploadMedia(
        listingId: String,
        file: SellerMediaUploadFile,
        session: SellerSession,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
        if (!listingRepository.isSessionActive(session)) {
            return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
        }

        val lockToken = listingRepository.acquireMutationLock(listingId, session)
        if (lockToken == null) {
            return SellerMediaMutationResult.Failure(SellerMediaFailure.OperationInProgress)
        }

        try {
            val result = gateway.uploadMedia(listingId, file)
            if (!listingRepository.isSessionActive(session)) {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
            }

            return when (result) {
                is SellerMediaMutationResult.Success -> {
                    val ack = result.value
                    val reconciledAck = if (ack.committed && (ack.refreshRequired || ack.snapshot == null)) {
                        when (val refreshRes = gateway.getSnapshot(listingId)) {
                            is SellerMediaGetSnapshotResult.Success -> {
                                ack.copy(snapshot = refreshRes.snapshot, refreshRequired = false)
                            }
                            is SellerMediaGetSnapshotResult.Failure -> {
                                ack.copy(refreshRequired = true)
                            }
                        }
                    } else {
                        ack
                    }
                    if (reconciledAck.committed && reconciledAck.snapshot != null && !reconciledAck.refreshRequired) {
                        listingRepository.notifyDashboardInvalidated(session)
                    }
                    SellerMediaMutationResult.Success(reconciledAck)
                }
                is SellerMediaMutationResult.Failure -> {
                    if (result.error is SellerMediaFailure.ProcessingFailed && result.error.mediaId != null) {
                        when (val refreshRes = gateway.getSnapshot(listingId)) {
                            is SellerMediaGetSnapshotResult.Success -> {
                                listingRepository.notifyDashboardInvalidated(session)
                                // We could attach the snapshot to Failure, but domain currently has no place for it, or wait...
                                // We need to retain the snapshot if possible. Let's just update the ViewModel logic to do the snapshot if it needs it. Or we can just ensure GET is called here, which it is.
                            }
                            else -> {}
                        }
                    } else if (result.error is SellerMediaFailure.TransportUncertain) {
                        val reconciliation = gateway.getSnapshot(listingId)
                        if (reconciliation is SellerMediaGetSnapshotResult.Success) {
                            listingRepository.notifyDashboardInvalidated(session)
                        }
                    }
                    result
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val reconciliation = gateway.getSnapshot(listingId)
            if (reconciliation is SellerMediaGetSnapshotResult.Success) {
                listingRepository.notifyDashboardInvalidated(session)
            }
            return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
        } finally {
            listingRepository.releaseMutationLock(lockToken)
        }
    }

    suspend fun deleteMedia(
        listingId: String,
        mediaId: String,
        session: SellerSession,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Delete> {
        if (!listingRepository.isSessionActive(session)) {
            return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
        }

        val lockToken = listingRepository.acquireMutationLock(listingId, session)
        if (lockToken == null) {
            return SellerMediaMutationResult.Failure(SellerMediaFailure.OperationInProgress)
        }

        try {
            val result = gateway.deleteMedia(listingId, mediaId)
            if (!listingRepository.isSessionActive(session)) {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
            }

            return when (result) {
                is SellerMediaMutationResult.Success -> {
                    val ack = result.value
                    val reconciledAck = if (ack.committed && (ack.refreshRequired || ack.snapshot == null)) {
                        when (val refreshRes = gateway.getSnapshot(listingId)) {
                            is SellerMediaGetSnapshotResult.Success -> {
                                ack.copy(snapshot = refreshRes.snapshot, refreshRequired = false)
                            }
                            is SellerMediaGetSnapshotResult.Failure -> {
                                ack.copy(refreshRequired = true)
                            }
                        }
                    } else {
                        ack
                    }
                    if (reconciledAck.committed && reconciledAck.snapshot != null && !reconciledAck.refreshRequired) {
                        listingRepository.notifyDashboardInvalidated(session)
                    }
                    SellerMediaMutationResult.Success(reconciledAck)
                }
                is SellerMediaMutationResult.Failure -> {
                    if (result.error is SellerMediaFailure.TransportUncertain) {
                        val reconciliation = gateway.getSnapshot(listingId)
                        if (reconciliation is SellerMediaGetSnapshotResult.Success) {
                            listingRepository.notifyDashboardInvalidated(session)
                        }
                    }
                    result
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val reconciliation = gateway.getSnapshot(listingId)
            if (reconciliation is SellerMediaGetSnapshotResult.Success) {
                listingRepository.notifyDashboardInvalidated(session)
            }
            return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
        } finally {
            listingRepository.releaseMutationLock(lockToken)
        }
    }

    suspend fun reorderMedia(
        listingId: String,
        mediaIds: List<String>,
        session: SellerSession,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Reorder> {
        if (!listingRepository.isSessionActive(session)) {
            return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
        }

        val lockToken = listingRepository.acquireMutationLock(listingId, session)
        if (lockToken == null) {
            return SellerMediaMutationResult.Failure(SellerMediaFailure.OperationInProgress)
        }

        try {
            val snapshotResult = gateway.getSnapshot(listingId)
            if (snapshotResult is SellerMediaGetSnapshotResult.Success) {
                val hasNonReady = snapshotResult.snapshot.media.any { it.processingStatus != SellerMediaProcessingStatus.READY }
                if (hasNonReady) {
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.InvalidInput)
                }
                val readyIds = snapshotResult.snapshot.media.map { it.id }.toSet()
                val requestedIds = mediaIds.toSet()
                if (readyIds != requestedIds || mediaIds.size != requestedIds.size) {
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.InvalidInput)
                }
            } else {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
            }

            val result = gateway.reorderMedia(listingId, mediaIds)
            if (!listingRepository.isSessionActive(session)) {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
            }

            return when (result) {
                is SellerMediaMutationResult.Success -> {
                    val ack = result.value
                    val reconciledAck = if (ack.committed && (ack.refreshRequired || ack.snapshot == null)) {
                        when (val refreshRes = gateway.getSnapshot(listingId)) {
                            is SellerMediaGetSnapshotResult.Success -> {
                                ack.copy(snapshot = refreshRes.snapshot, refreshRequired = false)
                            }
                            is SellerMediaGetSnapshotResult.Failure -> {
                                ack.copy(refreshRequired = true)
                            }
                        }
                    } else {
                        ack
                    }
                    if (reconciledAck.committed && reconciledAck.changed && reconciledAck.snapshot != null && !reconciledAck.refreshRequired) {
                        listingRepository.notifyDashboardInvalidated(session)
                    }
                    SellerMediaMutationResult.Success(reconciledAck)
                }
                is SellerMediaMutationResult.Failure -> {
                    if (result.error is SellerMediaFailure.TransportUncertain) {
                        val reconciliation = gateway.getSnapshot(listingId)
                        if (reconciliation is SellerMediaGetSnapshotResult.Success) {
                            listingRepository.notifyDashboardInvalidated(session)
                        }
                    }
                    result
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val reconciliation = gateway.getSnapshot(listingId)
            if (reconciliation is SellerMediaGetSnapshotResult.Success) {
                listingRepository.notifyDashboardInvalidated(session)
            }
            return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
        } finally {
            listingRepository.releaseMutationLock(lockToken)
        }
    }

    suspend fun setCoverPhoto(
        listingId: String,
        mediaId: String,
        session: SellerSession,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Cover> {
        if (!listingRepository.isSessionActive(session)) {
            return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
        }

        val lockToken = listingRepository.acquireMutationLock(listingId, session)
        if (lockToken == null) {
            return SellerMediaMutationResult.Failure(SellerMediaFailure.OperationInProgress)
        }

        try {
            val snapshotResult = gateway.getSnapshot(listingId)
            if (snapshotResult is SellerMediaGetSnapshotResult.Success) {
                val target = snapshotResult.snapshot.media.firstOrNull { it.id == mediaId }
                if (target == null || target.processingStatus != SellerMediaProcessingStatus.READY) {
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.InvalidInput)
                }
            } else {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
            }

            val result = gateway.setCoverPhoto(listingId, mediaId)
            if (!listingRepository.isSessionActive(session)) {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
            }

            return when (result) {
                is SellerMediaMutationResult.Success -> {
                    val ack = result.value
                    val reconciledAck = if (ack.committed && (ack.refreshRequired || ack.snapshot == null)) {
                        when (val refreshRes = gateway.getSnapshot(listingId)) {
                            is SellerMediaGetSnapshotResult.Success -> {
                                ack.copy(snapshot = refreshRes.snapshot, refreshRequired = false)
                            }
                            is SellerMediaGetSnapshotResult.Failure -> {
                                ack.copy(refreshRequired = true)
                            }
                        }
                    } else {
                        ack
                    }
                    if (reconciledAck.committed && reconciledAck.snapshot != null && !reconciledAck.refreshRequired) {
                        listingRepository.notifyDashboardInvalidated(session)
                    }
                    SellerMediaMutationResult.Success(reconciledAck)
                }
                is SellerMediaMutationResult.Failure -> {
                    if (result.error is SellerMediaFailure.TransportUncertain) {
                        val reconciliation = gateway.getSnapshot(listingId)
                        if (reconciliation is SellerMediaGetSnapshotResult.Success) {
                            listingRepository.notifyDashboardInvalidated(session)
                        }
                    }
                    result
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val reconciliation = gateway.getSnapshot(listingId)
            if (reconciliation is SellerMediaGetSnapshotResult.Success) {
                listingRepository.notifyDashboardInvalidated(session)
            }
            return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
        } finally {
            listingRepository.releaseMutationLock(lockToken)
        }
    }

    suspend fun getSignedDerivativeUrl(
        listingId: String,
        mediaId: String,
        size: String,
        session: SellerSession,
    ): SellerMediaGetSignedUrlResult {
        if (!listingRepository.isSessionActive(session)) {
            return SellerMediaGetSignedUrlResult.Failure(SellerMediaFailure.AuthRequired)
        }

        return gateway.getSignedDerivativeUrl(listingId, mediaId, size)
    }
}
