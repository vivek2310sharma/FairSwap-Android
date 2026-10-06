package com.fairswap.core.sellerlistings

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

interface SellerMediaGateway {
    suspend fun getSnapshot(listingId: String): SellerMediaGetSnapshotResult
    suspend fun uploadMedia(listingId: String, file: SellerMediaUploadFile): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload>
    suspend fun deleteMedia(listingId: String, mediaId: String): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Delete>
    suspend fun reorderMedia(listingId: String, mediaIds: List<String>): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Reorder>
    suspend fun setCoverPhoto(listingId: String, mediaId: String): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Cover>
    suspend fun getSignedDerivativeUrl(listingId: String, mediaId: String, size: String): SellerMediaGetSignedUrlResult
}

class KtorSellerMediaGateway(
    private val config: SellerMediaConfig,
    private val supabase: SupabaseClient,
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val tokenProvider: suspend () -> String? = {
        try {
            supabase.auth.awaitInitialization()
            supabase.auth.currentSessionOrNull()?.accessToken
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw IllegalStateException("Auth initialization failed", e)
        }
    },
) : SellerMediaGateway {

    private suspend fun getBearerToken(): String? {
        return tokenProvider()
    }

    private fun buildUrl(path: String): String =
        "${config.normalizedWebBaseUrl}$path"

    private suspend fun handleHttpError(response: HttpResponse): SellerMediaFailure {
        val status = response.status.value
        val bodyText = try { response.bodyAsText() } catch (_: Exception) { "" }
        val errorDto = try {
            json.decodeFromString<MediaErrorBodyDto>(bodyText)
        } catch (_: Exception) {
            null
        }

        val code = SellerMediaApiErrorCode.fromWire(errorDto?.code)
        return SellerMediaErrorMapper.map(
            errorCode = code,
            failedMediaId = errorDto?.media?.id,
            httpStatus = status,
        )
    }

    override suspend fun getSnapshot(listingId: String): SellerMediaGetSnapshotResult {
        val token = try {
            getBearerToken() ?: return SellerMediaGetSnapshotResult.Failure(SellerMediaFailure.AuthRequired)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return SellerMediaGetSnapshotResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
        }
        val url = buildUrl("/api/listings/$listingId/media")

        return try {
            val response: HttpResponse = httpClient.get(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.Accept, "application/json")
                }
            }

            if (response.status.value in 301..308) {
                return SellerMediaGetSnapshotResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
            }

            if (response.status.isSuccess()) {
                val bodyText = response.bodyAsText()
                val snapshotDto = json.decodeFromString<SafeMediaSnapshotDto>(bodyText)
                SellerMediaGetSnapshotResult.Success(snapshotDto.toDomain())
            } else {
                val failure = handleHttpError(response)
                SellerMediaGetSnapshotResult.Failure(failure)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SellerMediaGetSnapshotResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
        }
    }

    override suspend fun uploadMedia(
        listingId: String,
        file: SellerMediaUploadFile,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Upload> {
        val token = try {
            getBearerToken() ?: return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
        }
        val url = buildUrl("/api/listings/$listingId/media")

        return try {
            val response: HttpResponse = httpClient.submitFormWithBinaryData(
                url = url,
                formData = formData {
                    append(
                        key = "file",
                        value = file.bytes,
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, file.detectedFormat)
                            append(HttpHeaders.ContentDisposition, "filename=\"${file.fileName}\"")
                        }
                    )
                }
            ) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.Accept, "application/json")
                }
            }

            if (response.status.value in 301..308) {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
            }

            if (response.status.isSuccess()) {
                val bodyText = try { response.bodyAsText() } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
                }
                val uploadDto = try {
                    json.decodeFromString<UploadResponseDto>(bodyText)
                } catch (e: Exception) {
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
                }
                val ack = SellerMediaMutationAcknowledgement.Upload(
                    committed = uploadDto.committed,
                    refreshRequired = uploadDto.refreshRequired,
                    snapshot = uploadDto.snapshot?.toDomain(),
                    uploadedMediaId = uploadDto.uploadedMediaId,
                    processingStatus = SellerMediaProcessingStatus.fromWire(uploadDto.processingStatus),
                )
                SellerMediaMutationResult.Success(ack)
            } else {
                val failure = handleHttpError(response)
                SellerMediaMutationResult.Failure(failure)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
        }
    }

    override suspend fun deleteMedia(
        listingId: String,
        mediaId: String,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Delete> {
        val token = try {
            getBearerToken() ?: return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
        }
        val url = buildUrl("/api/listings/$listingId/media/$mediaId")

        return try {
            val response: HttpResponse = httpClient.delete(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.Accept, "application/json")
                }
            }

            if (response.status.value in 301..308) {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
            }

            if (response.status.isSuccess()) {
                val bodyText = try { response.bodyAsText() } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
                }
                val deleteDto = try {
                    json.decodeFromString<DeleteResponseDto>(bodyText)
                } catch (e: Exception) {
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
                }
                val ack = SellerMediaMutationAcknowledgement.Delete(
                    committed = deleteDto.committed,
                    refreshRequired = deleteDto.refreshRequired,
                    snapshot = deleteDto.snapshot?.toDomain(),
                    deleted = deleteDto.deleted,
                    deletedMediaId = deleteDto.deletedMediaId,
                    newCoverId = deleteDto.newCoverId,
                )
                SellerMediaMutationResult.Success(ack)
            } else {
                val failure = handleHttpError(response)
                SellerMediaMutationResult.Failure(failure)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
        }
    }

    override suspend fun reorderMedia(
        listingId: String,
        mediaIds: List<String>,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Reorder> {
        val token = try {
            getBearerToken() ?: return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
        }
        val url = buildUrl("/api/listings/$listingId/media/reorder")

        return try {
            val payload = json.encodeToString(ReorderRequestBodyDto.serializer(), ReorderRequestBodyDto(mediaIds))
            val response: HttpResponse = httpClient.post(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.ContentType, "application/json")
                    append(HttpHeaders.Accept, "application/json")
                }
                setBody(payload)
            }

            if (response.status.value in 301..308) {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
            }

            if (response.status.isSuccess()) {
                val bodyText = try { response.bodyAsText() } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
                }
                val reorderDto = try {
                    json.decodeFromString<ReorderResponseDto>(bodyText)
                } catch (e: Exception) {
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
                }
                val ack = SellerMediaMutationAcknowledgement.Reorder(
                    committed = reorderDto.committed,
                    refreshRequired = reorderDto.refreshRequired,
                    snapshot = reorderDto.snapshot?.toDomain(),
                    changed = reorderDto.changed,
                )
                SellerMediaMutationResult.Success(ack)
            } else {
                val failure = handleHttpError(response)
                SellerMediaMutationResult.Failure(failure)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
        }
    }

    override suspend fun setCoverPhoto(
        listingId: String,
        mediaId: String,
    ): SellerMediaMutationResult<SellerMediaMutationAcknowledgement.Cover> {
        val token = try {
            getBearerToken() ?: return SellerMediaMutationResult.Failure(SellerMediaFailure.AuthRequired)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
        }
        val url = buildUrl("/api/listings/$listingId/media/cover")

        return try {
            val payload = json.encodeToString(SetCoverRequestBodyDto.serializer(), SetCoverRequestBodyDto(mediaId))
            val response: HttpResponse = httpClient.put(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.ContentType, "application/json")
                    append(HttpHeaders.Accept, "application/json")
                }
                setBody(payload)
            }

            if (response.status.value in 301..308) {
                return SellerMediaMutationResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
            }

            if (response.status.isSuccess()) {
                val bodyText = try { response.bodyAsText() } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
                }
                val coverDto = try {
                    json.decodeFromString<CoverResponseDto>(bodyText)
                } catch (e: Exception) {
                    return SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
                }
                val ack = SellerMediaMutationAcknowledgement.Cover(
                    committed = coverDto.committed,
                    refreshRequired = coverDto.refreshRequired,
                    snapshot = coverDto.snapshot?.toDomain(),
                    coverPhotoId = coverDto.coverPhotoId,
                )
                SellerMediaMutationResult.Success(ack)
            } else {
                val failure = handleHttpError(response)
                SellerMediaMutationResult.Failure(failure)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SellerMediaMutationResult.Failure(SellerMediaFailure.TransportUncertain)
        }
    }

    override suspend fun getSignedDerivativeUrl(
        listingId: String,
        mediaId: String,
        size: String,
    ): SellerMediaGetSignedUrlResult {
        if (size == "original") {
            return SellerMediaGetSignedUrlResult.Failure(SellerMediaFailure.InvalidInput)
        }
        val token = try {
            getBearerToken() ?: return SellerMediaGetSignedUrlResult.Failure(SellerMediaFailure.AuthRequired)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return SellerMediaGetSignedUrlResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
        }
        val url = buildUrl("/api/listings/$listingId/media/$mediaId/$size")

        return try {
            val response: HttpResponse = httpClient.get(url) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.Accept, "application/json")
                }
            }

            if (response.status.value in 301..308) {
                return SellerMediaGetSignedUrlResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
            }

            if (response.status.isSuccess()) {
                val bodyText = response.bodyAsText()
                val signedUrlDto = json.decodeFromString<SignedUrlResponseDto>(bodyText)
                SellerMediaGetSignedUrlResult.Success(signedUrlDto.toDomain())
            } else {
                if (response.status.value == 404) {
                    // Concealed media/listing unavailable
                    SellerMediaGetSignedUrlResult.Failure(SellerMediaFailure.MediaNotFound)
                } else {
                    val failure = handleHttpError(response)
                    SellerMediaGetSignedUrlResult.Failure(failure)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SellerMediaGetSignedUrlResult.Failure(SellerMediaFailure.MediaServiceUnavailable)
        }
    }
}
