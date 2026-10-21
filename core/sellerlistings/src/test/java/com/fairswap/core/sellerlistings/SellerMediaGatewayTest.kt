package com.fairswap.core.sellerlistings

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerMediaGatewayTest {

    private val config = SellerMediaConfig("https://fairswap.example.com")
    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://example.supabase.co",
        supabaseKey = "sbp_fake_key",
    ) {
    }

    @Test
    fun `getSnapshot sets Bearer header and Accept application json`() = runTest {
        var capturedAuthHeader: String? = null
        var capturedAcceptHeader: String? = null

        val mockEngine = MockEngine { request ->
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            capturedAcceptHeader = request.headers[HttpHeaders.Accept]
            respond(
                content = """{"listing":{"id":"l1","status":"draft","revision":1},"media":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = httpClient,
            tokenProvider = { "fake_access_token" },
        )

        val result = gateway.getSnapshot("l1")
        assertTrue(result is SellerMediaGetSnapshotResult.Success)
        assertEquals("Bearer fake_access_token", capturedAuthHeader)
        assertEquals("application/json", capturedAcceptHeader)
    }

    /**
     * V2 B4: signed derivatives keep the native JSON contract, including when
     * Storage is on an independently credentialed host.
     * Seller Bearer tokens are sent ONLY to FairSwap-Web.
     */
    @Test
    fun `B4 sealed derivative keeps signed URL JSON contract and Bearer on Web only`() = runTest {
        val token = "b4_private_seller_access_token"
        val signed = "https://sealed-media.example.in/storage/v1/object/sign/listing-derivatives/l1/m1/v2/asset/sha/thumb.webp?token=b4_signed"
        var requestPath: String? = null
        var requestAuth: String? = null
        var requestAccept: String? = null

        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine { request ->
                requestPath = request.url.encodedPath
                requestAuth = request.headers[HttpHeaders.Authorization]
                requestAccept = request.headers[HttpHeaders.Accept]
                respond(
                    content = """{"url":"$signed","expiresIn":60}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }) { followRedirects = false },
            tokenProvider = { token },
        )

        val result = gateway.getSignedDerivativeUrl("l1", "m1", "thumb")
        assertTrue(result is SellerMediaGetSignedUrlResult.Success)
        val url = (result as SellerMediaGetSignedUrlResult.Success).signedUrl
        assertEquals(signed, url.url)
        assertEquals(60, url.expiresInSeconds)
        assertEquals("/api/listings/l1/media/m1/thumb", requestPath)
        assertEquals("Bearer $token", requestAuth)
        assertEquals("application/json", requestAccept)
        assertFalse(url.url.contains(token))
    }

    @Test
    fun `B4 signed derivative refuses cross-host HTTP redirect`() = runTest {
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine {
                respond(
                    "",
                    HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://untrusted.example.net/collect"),
                )
            }) { followRedirects = false },
            tokenProvider = { "secret_to_web_only" },
        )

        val result = gateway.getSignedDerivativeUrl("l1", "m1", "medium")
        assertTrue(result is SellerMediaGetSignedUrlResult.Failure)
        assertEquals(
            SellerMediaFailure.MediaServiceUnavailable,
            (result as SellerMediaGetSignedUrlResult.Failure).error,
        )
    }

    @Test
    fun `getSignedDerivativeUrl forbids original size`() = runTest {
        val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
        val httpClient = HttpClient(mockEngine)
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = httpClient,
            tokenProvider = { "fake_access_token" },
        )

        val result = gateway.getSignedDerivativeUrl("l1", "m1", "original")
        assertTrue(result is SellerMediaGetSignedUrlResult.Failure)
        assertEquals(SellerMediaFailure.InvalidInput, (result as SellerMediaGetSignedUrlResult.Failure).error)
    }

    @Test
    fun `canEditMedia matrix allows valid statuses and denies terminal statuses`() {
        assertTrue(SellerListingStatus.DRAFT.canEditMedia)
        assertTrue(SellerListingStatus.REJECTED.canEditMedia)
        assertTrue(SellerListingStatus.ACTIVE.canEditMedia)
        assertTrue(SellerListingStatus.RESERVED.canEditMedia)
        assertTrue(SellerListingStatus.PAUSED.canEditMedia)
        assertTrue(SellerListingStatus.PENDING_REVIEW.canEditMedia)

        assertFalse(SellerListingStatus.SOLD.canEditMedia)
        assertFalse(SellerListingStatus.EXPIRED.canEditMedia)
        assertFalse(SellerListingStatus.REMOVED.canEditMedia)
        assertFalse(SellerListingStatus.UNKNOWN.canEditMedia)
    }

    @Test
    fun `token CancellationException propagates`() = runTest {
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
            tokenProvider = { throw kotlinx.coroutines.CancellationException() },
        )
        try {
            gateway.getSnapshot("l1")
            org.junit.Assert.fail("Expected CancellationException")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Success
        }
    }

    @Test
    fun `token provider per request`() = runTest {
        var callCount = 0
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine { respond("""{"listing":{"id":"l1","status":"draft","revision":1},"media":[]}""", HttpStatusCode.OK) }),
            tokenProvider = { callCount++; "token" },
        )
        gateway.getSnapshot("l1")
        gateway.getSnapshot("l1")
        assertEquals(2, callCount)
    }

    @Test
    fun `config rejects invalid urls`() {
        var thrown = false
        try { SellerMediaConfig("") } catch(e: IllegalArgumentException) { thrown = true }
        assertTrue(thrown)

        thrown = false
        try { SellerMediaConfig("api/listings") } catch(e: IllegalArgumentException) { thrown = true }
        assertTrue(thrown)

        thrown = false
        try { SellerMediaConfig("http://example.com") } catch(e: IllegalArgumentException) { thrown = true }
        assertTrue(thrown)

        thrown = false
        try { SellerMediaConfig("https://user:pass@example.com") } catch(e: IllegalArgumentException) { thrown = true }
        assertTrue(thrown)

        thrown = false
        try { SellerMediaConfig("https://example.com#frag") } catch(e: IllegalArgumentException) { thrown = true }
        assertTrue(thrown)

        // Valid
        val valid = SellerMediaConfig("https://example.com/api/")
        assertEquals("https://example.com/api", valid.normalizedWebBaseUrl)
    }

    @Test
    fun `unknown 403 fail closed to MediaServiceUnavailable`() = runTest {
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine { respond("""{"code":"RANDOM"}""", HttpStatusCode.Forbidden) }),
            tokenProvider = { "token" },
        )
        val result = gateway.getSnapshot("l1")
        assertTrue(result is SellerMediaGetSnapshotResult.Failure)
        assertEquals(SellerMediaFailure.MediaServiceUnavailable, (result as SellerMediaGetSnapshotResult.Failure).error)
    }

    @Test
    fun `null code 403 fail closed to MediaServiceUnavailable`() = runTest {
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine { respond("""{}""", HttpStatusCode.Forbidden) }),
            tokenProvider = { "token" },
        )
        val result = gateway.getSnapshot("l1")
        assertTrue(result is SellerMediaGetSnapshotResult.Failure)
        assertEquals(SellerMediaFailure.MediaServiceUnavailable, (result as SellerMediaGetSnapshotResult.Failure).error)
    }

    @Test
    fun `unknown 401 returns AuthRequired`() = runTest {
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine { respond("""{}""", HttpStatusCode.Unauthorized) }),
            tokenProvider = { "token" },
        )
        val result = gateway.getSnapshot("l1")
        assertTrue(result is SellerMediaGetSnapshotResult.Failure)
        assertEquals(SellerMediaFailure.AuthRequired, (result as SellerMediaGetSnapshotResult.Failure).error)
    }

    @Test
    fun `derivative 404 concealed as MediaNotFound`() = runTest {
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }),
            tokenProvider = { "token" },
        )
        val result = gateway.getSignedDerivativeUrl("l1", "m1", "thumb")
        assertTrue(result is SellerMediaGetSignedUrlResult.Failure)
        assertEquals(SellerMediaFailure.MediaNotFound, (result as SellerMediaGetSignedUrlResult.Failure).error)
    }

    @Test
    fun `upload network failure returns TransportUncertain`() = runTest {
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine { throw java.io.IOException("Network down") }),
            tokenProvider = { "token" },
        )
        val result = gateway.uploadMedia("l1", SellerMediaUploadFile("test", ByteArray(0), "image/jpeg"))
        assertTrue(result is SellerMediaMutationResult.Failure)
        assertEquals(SellerMediaFailure.TransportUncertain, (result as SellerMediaMutationResult.Failure).error)
    }

    @Test
    fun `structural mutation network failure returns TransportUncertain`() = runTest {
        val gateway = KtorSellerMediaGateway(
            config = config,
            supabase = supabase,
            httpClient = HttpClient(MockEngine { throw java.io.IOException("Network down") }),
            tokenProvider = { "token" },
        )
        val result = gateway.deleteMedia("l1", "m1")
        assertTrue(result is SellerMediaMutationResult.Failure)
        assertEquals(SellerMediaFailure.TransportUncertain, (result as SellerMediaMutationResult.Failure).error)
    }
}
