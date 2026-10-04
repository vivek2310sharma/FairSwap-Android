package com.fairswap.feature.auth

import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.core.auth.AuthGate
import com.fairswap.core.auth.AuthRepository
import com.fairswap.core.auth.EmailOtpRequestResult
import com.fairswap.core.auth.EmailOtpVerificationResult
import com.fairswap.core.auth.SignUpInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeAuthRepository : AuthRepository {
    var signInCalled = false
    var signUpCalled = false
    var requestOtpCalled = false
    var verifyOtpCalled = false
    var signOutCalled = false
    var lastSignInEmail: String? = null
    var requestOtpCallCount = 0
    var verifyOtpCallCount = 0
    var signOutCallCount = 0

    var nextBootstrapGate: AuthGate = AuthGate.SignedOut
    var nextOtpRequestResult: EmailOtpRequestResult = EmailOtpRequestResult.Acknowledged
    var nextOtpVerifyResult: EmailOtpVerificationResult = EmailOtpVerificationResult.Authenticated(AuthGate.SignedIn(com.fairswap.core.auth.ProfileSummary("id", "name"), com.fairswap.core.auth.AccountSummary("id", "active")))

    var requestOtpStarted: CompletableDeferred<Unit>? = null
    var requestOtpRelease: CompletableDeferred<Unit>? = null
    var requestOtpSurviveCancellation: Boolean = false
    var verifyOtpStarted: CompletableDeferred<Unit>? = null
    var verifyOtpRelease: CompletableDeferred<Unit>? = null
    var verifyOtpSurviveCancellation: Boolean = false
    var signOutStarted: CompletableDeferred<Unit>? = null
    var signOutRelease: CompletableDeferred<Unit>? = null
    var signOutSurviveCancellation: Boolean = false

    override suspend fun bootstrap() = nextBootstrapGate
    override suspend fun signIn(email: String, password: String): AuthGate {
        signInCalled = true
        lastSignInEmail = email
        return AuthGate.SignedOut
    }
    override suspend fun signUp(input: SignUpInput): AuthGate {
        signUpCalled = true
        return AuthGate.SignedOut
    }
    override suspend fun requestPasswordReset(email: String) = AuthGate.SignedOut
    override suspend fun setNewPassword(password: String, confirmation: String) = AuthGate.SignedOut
    override suspend fun reacceptPolicies() = AuthGate.SignedOut
    override suspend fun signOut(): AuthGate {
        signOutCalled = true
        signOutCallCount++
        signOutStarted?.complete(Unit)
        if (signOutSurviveCancellation) {
            withContext(NonCancellable) {
                signOutRelease?.await()
            }
        } else {
            signOutRelease?.await()
        }
        return AuthGate.SignedOut
    }
    override suspend fun requestEmailOtp(email: String): EmailOtpRequestResult {
        requestOtpCalled = true
        requestOtpCallCount++
        requestOtpStarted?.complete(Unit)
        if (requestOtpSurviveCancellation) {
            withContext(NonCancellable) {
                requestOtpRelease?.await()
            }
        } else {
            requestOtpRelease?.await()
        }
        return nextOtpRequestResult
    }
    override suspend fun verifyEmailOtp(email: String, token: String): EmailOtpVerificationResult {
        verifyOtpCalled = true
        verifyOtpCallCount++
        verifyOtpStarted?.complete(Unit)
        if (verifyOtpSurviveCancellation) {
            withContext(NonCancellable) {
                verifyOtpRelease?.await()
            }
        } else {
            verifyOtpRelease?.await()
        }
        return nextOtpVerifyResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

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
    fun `initial state is Email step`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SignInStep.Email, viewModel.uiState.signInStep)
    }

    @Test
    fun `valid email advances to Password step`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")
        assertEquals(SignInStep.Password, viewModel.uiState.signInStep)
        assertEquals("test@example.com", viewModel.uiState.normalizedEmail)
    }

    @Test
    fun `invalid email stays put and shows error`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("invalid")
        assertEquals(SignInStep.Email, viewModel.uiState.signInStep)
        assertEquals("Please enter a valid email address.", viewModel.uiState.error)
    }

    @Test
    fun `switchToOtp requests code and shows acknowledgement only on success`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")

        repo.nextOtpRequestResult = EmailOtpRequestResult.Acknowledged
        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SignInStep.Otp, viewModel.uiState.signInStep)
        assertTrue(viewModel.uiState.otpAcknowledged)
        assertTrue(repo.requestOtpCalled)
    }

    @Test
    fun `OTP request failure does not show acknowledgement`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")

        repo.nextOtpRequestResult = EmailOtpRequestResult.RateLimited
        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SignInStep.Password, viewModel.uiState.signInStep)
        assertFalse(viewModel.uiState.otpAcknowledged)
        assertEquals("Too many requests. Please wait a moment before trying again.", viewModel.uiState.error)
    }

    @Test
    fun `switching to password clears OTP state`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")
        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.switchToPassword()
        assertEquals(SignInStep.Password, viewModel.uiState.signInStep)
        assertFalse(viewModel.uiState.otpAcknowledged)
    }

    @Test
    fun `same email method switching preserves cooldown without another request`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")
        repo.nextOtpRequestResult = EmailOtpRequestResult.Acknowledged
        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SignInStep.Otp, viewModel.uiState.signInStep)
        assertEquals(1, repo.requestOtpCallCount)
        val capturedCooldown = viewModel.uiState.resendAvailableAt
        assertTrue(capturedCooldown > System.currentTimeMillis())

        viewModel.switchToPassword()
        assertEquals(SignInStep.Password, viewModel.uiState.signInStep)

        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.requestOtpCallCount)
        assertEquals(SignInStep.Otp, viewModel.uiState.signInStep)
        assertEquals(capturedCooldown, viewModel.uiState.resendAvailableAt)
        assertTrue(viewModel.uiState.otpAcknowledged)
    }

    @Test
    fun `strengthened A to B to A cooldown test`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        // Email A
        viewModel.submitEmail("test1@example.com")
        repo.nextOtpRequestResult = EmailOtpRequestResult.Acknowledged
        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SignInStep.Otp, viewModel.uiState.signInStep)
        assertEquals(1, repo.requestOtpCallCount)
        val cooldownA = viewModel.uiState.resendAvailableAt
        assertTrue(cooldownA > System.currentTimeMillis())

        // Change Email to B
        viewModel.changeEmail()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.submitEmail("test2@example.com")
        repo.nextOtpRequestResult = EmailOtpRequestResult.Acknowledged
        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SignInStep.Otp, viewModel.uiState.signInStep)
        assertEquals(2, repo.requestOtpCallCount)
        val cooldownB = viewModel.uiState.resendAvailableAt
        assertTrue(cooldownB > System.currentTimeMillis())

        // Back to Email A
        viewModel.changeEmail()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.submitEmail("test1@example.com")

        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, repo.requestOtpCallCount)
        assertEquals(SignInStep.Otp, viewModel.uiState.signInStep)
        assertEquals(cooldownA, viewModel.uiState.resendAvailableAt)
        assertTrue(viewModel.uiState.otpAcknowledged)
    }

    @Test
    fun `signOut failure does not create blank AuthGate Failure UI`() = runTest {
        val repo = object : AuthRepository {
            override suspend fun bootstrap() = AuthGate.SignedOut
            override suspend fun signIn(email: String, password: String) = AuthGate.SignedOut
            override suspend fun signUp(input: SignUpInput) = AuthGate.SignedOut
            override suspend fun requestPasswordReset(email: String) = AuthGate.SignedOut
            override suspend fun setNewPassword(password: String, confirmation: String) = AuthGate.SignedOut
            override suspend fun reacceptPolicies() = AuthGate.SignedOut
            override suspend fun signOut() = AuthGate.Failure("Safe sign out error")
            override suspend fun requestEmailOtp(email: String) = EmailOtpRequestResult.Acknowledged
            override suspend fun verifyEmailOtp(email: String, token: String) = EmailOtpVerificationResult.InvalidOrExpiredCode
        }
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Safe sign out error", viewModel.uiState.error)
        assertEquals(AuthGate.SignedOut, viewModel.uiState.gate)
        assertFalse(viewModel.uiState.gate is AuthGate.Failure)
        assertFalse(viewModel.uiState.isLoading)
    }

    @Test
    fun `strong stale OTP request assertions`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        repo.requestOtpStarted = CompletableDeferred()
        repo.requestOtpRelease = CompletableDeferred()
        repo.requestOtpSurviveCancellation = true

        viewModel.submitEmail("first@example.com")
        viewModel.switchToOtp()
        runCurrent()

        repo.requestOtpStarted!!.await()
        assertEquals(SignInStep.Password, viewModel.uiState.signInStep)

        viewModel.changeEmail()
        viewModel.submitEmail("second@example.com")
        runCurrent()

        repo.nextOtpRequestResult = EmailOtpRequestResult.Acknowledged
        repo.requestOtpRelease!!.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("second@example.com", viewModel.uiState.normalizedEmail)
        assertEquals(SignInStep.Password, viewModel.uiState.signInStep)
        assertFalse(viewModel.uiState.otpAcknowledged)
        assertEquals(AuthGate.SignedOut, viewModel.uiState.gate)
        assertNull(viewModel.uiState.error)
        assertFalse(viewModel.uiState.isLoading)
        assertEquals(0L, viewModel.uiState.resendAvailableAt)
        assertEquals(1, repo.requestOtpCallCount)
    }

    @Test
    fun `strong stale OTP verification assertions`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("old@example.com")
        repo.nextOtpRequestResult = EmailOtpRequestResult.Acknowledged
        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()

        repo.verifyOtpStarted = CompletableDeferred()
        repo.verifyOtpRelease = CompletableDeferred()
        repo.verifyOtpSurviveCancellation = true

        viewModel.verifyOtp("123456")
        runCurrent()
        repo.verifyOtpStarted!!.await()

        viewModel.changeEmail()
        runCurrent()

        repo.nextOtpVerifyResult = EmailOtpVerificationResult.Authenticated(
            AuthGate.SignedIn(
                com.fairswap.core.auth.ProfileSummary("id", "name"),
                com.fairswap.core.auth.AccountSummary("id", "active")
            )
        )
        repo.verifyOtpRelease!!.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SignInStep.Email, viewModel.uiState.signInStep)
        assertEquals(AuthGate.SignedOut, viewModel.uiState.gate)
        assertEquals("old@example.com", viewModel.uiState.normalizedEmail)
        assertNull(viewModel.uiState.error)
        assertFalse(viewModel.uiState.isLoading)
    }

    @Test
    fun `strong sign-out race assertions`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")
        repo.requestOtpStarted = CompletableDeferred()
        repo.requestOtpRelease = CompletableDeferred()
        repo.requestOtpSurviveCancellation = true

        viewModel.switchToOtp()
        runCurrent()
        repo.requestOtpStarted!!.await()

        viewModel.signOut()
        runCurrent()

        assertEquals(1, repo.signOutCallCount)
        assertEquals(AuthGate.SignedOut, viewModel.uiState.gate)

        repo.requestOtpRelease!!.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthGate.SignedOut, viewModel.uiState.gate)
        assertNotEquals(SignInStep.Otp, viewModel.uiState.signInStep)
        assertFalse(viewModel.uiState.gate is AuthGate.SignedIn)
        assertNull(viewModel.uiState.error)
        assertFalse(viewModel.uiState.isLoading)
    }

    @Test
    fun `AuthGate Failure after OTP is a safe error`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")
        repo.nextOtpVerifyResult = EmailOtpVerificationResult.Authenticated(AuthGate.Failure("Safe failure"))
        viewModel.verifyOtp("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Safe failure", viewModel.uiState.error)
        assertEquals(AuthGate.SignedOut, viewModel.uiState.gate)
        assertFalse(viewModel.uiState.gate is AuthGate.Failure)
        assertFalse(viewModel.uiState.isLoading)
    }

    @Test
    fun `duplicate initial OTP request is suppressed`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")
        repo.requestOtpStarted = CompletableDeferred()
        repo.requestOtpRelease = CompletableDeferred()

        viewModel.switchToOtp()
        runCurrent()
        repo.requestOtpStarted!!.await()

        viewModel.switchToOtp()
        runCurrent()

        assertEquals(1, repo.requestOtpCallCount)

        repo.requestOtpRelease!!.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SignInStep.Otp, viewModel.uiState.signInStep)
        assertEquals(1, repo.requestOtpCallCount)
    }

    @Test
    fun `normalization and password signin`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail(" User@Example.COM ")
        assertEquals("user@example.com", viewModel.uiState.normalizedEmail)

        viewModel.signIn(" User@Example.COM ", "password")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("user@example.com", repo.lastSignInEmail)
    }

    @Test
    fun `successful OTP verification updates auth gate to PolicyReviewRequired`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")
        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()

        repo.nextOtpVerifyResult = EmailOtpVerificationResult.Authenticated(
            AuthGate.PolicyReviewRequired(
                com.fairswap.core.auth.ProfileSummary("id", "name"),
                com.fairswap.core.auth.AccountSummary("id", "active")
            )
        )
        viewModel.verifyOtp("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.gate is AuthGate.PolicyReviewRequired)
    }

    @Test
    fun `successful OTP verification updates auth gate to Restricted`() = runTest {
        val repo = FakeAuthRepository()
        val coordinator = AuthCallbackCoordinator()
        val viewModel = AuthViewModel(repo, coordinator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEmail("test@example.com")
        viewModel.switchToOtp()
        testDispatcher.scheduler.advanceUntilIdle()

        repo.nextOtpVerifyResult = EmailOtpVerificationResult.Authenticated(AuthGate.Restricted("suspended"))
        viewModel.verifyOtp("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.gate is AuthGate.Restricted)
        assertEquals("suspended", (viewModel.uiState.gate as AuthGate.Restricted).accountStatus)
    }
}
