# Android 4C: Email-First Password + Email OTP Auth Parity

## Authoritative Web Contract (#80, #82)
The Web interface implemented a stepped, email-first sign-in flow that eliminates Google OAuth while providing a secure Email OTP alternative to password authentication.

## Android Architecture
The Android client leverages the exact `supabase-kt` 3.8.0 API surface:
- **OTP Request**: `supabase.auth.signInWith(OTP, redirectUrl = null) { email = normalizedEmail; createUser = false }`
- **OTP Verification**: `supabase.auth.verifyEmailOtp(OtpType.Email.EMAIL, normalizedEmail, token)`

The `AuthRepository` abstracts the upstream network state into two strict domain models: `EmailOtpRequestResult` and `EmailOtpVerificationResult`.

## Domain Error Mapping
Account-sensitive errors (`user_not_found`, `otp_disabled`, `email_not_confirmed`, `user_banned`, `identity_not_found`) map securely to a unified `Acknowledged` response. Raw errors are not passed to the UI. Specific configurations are assigned distinct results (e.g. `RateLimited`, `ServiceUnavailable`, `ProviderUnavailable`).

## Enumeration Boundary
Android uses `DIRECT_SUPABASE_KT_WITH_SAFE_DOMAIN_MAPPING`.
- Account-sensitive OTP request failures are normalized to the same application-visible `Acknowledged` result.
- Compose never receives raw provider account-existence errors.
- This reduces account enumeration through the FairSwap Android UI.
- This does NOT prevent direct probing of the public Supabase Auth endpoint.
- This does NOT eliminate upstream timing, network, HTTP, or provider characteristics.
- Backend/provider rate limiting remains authoritative.

## State Machine
The `AuthUiState` utilizes `SignInStep` (`Email`, `Password`, `Otp`) to drive a step-by-step Compose flow. State such as active normalized email and cooldown tracks independently of UI switches.

## OTP Secret Handling
The 6-digit OTP is processed exclusively in-memory. It is not printed, persisted to disk, stored in `SavedStateHandle`, or incorporated into navigation routes.

## Cooldown Behavior
A 60-second client-side cooldown is applied per normalized email address. This countdown ignores navigation step changes (e.g., OTP to Password and back).

## Stale-Response Protection
Operations rely on an incrementing `authGenerationId` stored within the ViewModel. Async repository completions are rejected if the user transitions backward, switches emails, or changes modes in the interim.

## Session Semantics
Successful OTP completion triggers `supabase.auth.retrieveUserForCurrentSession(...)`. If the verified user matches the request parameter, standard `bootstrap()` policy verification resumes. Invalid sessions result in local `supabase.auth.signOut()` calls without impacting global state.

## Signup & Recovery Regression Boundary
- Existing signup, confirmation, password recovery, reset continuation, callback coordination, and policy flows were preserved by the 4C implementation.
- Existing project regression coverage remains in place.
- Android-4C-specific tests primarily cover the new email-first, OTP, cooldown, stale-response, error-mapping, and AuthGate behavior.

## Testing
`core:auth` validates regex logic, generic mapping, and repository error classification via `AuthErrorClassifierTest`. `feature:auth` utilizes deterministic testing across `AuthViewModelTest.kt` verifying flow transitions, invalid attempts, state persistence, NonCancellable suspended race conditions, and cancellation sequences.

## Staging Acceptance
Staging acceptance on package `com.fairswap.app.staging` has PASSED.

Acceptance Evidence:
- Password login passed against existing `FairSwapTest` account.
- Password session persistence passed across app force-stop and relaunch.
- Password logout passed and remained signed out across relaunch.
- OTP request and neutral acknowledgement passed.
- Human 6-digit OTP verification passed.
- OTP session persistence passed across force-stop and relaunch.
- OTP logout passed and remained signed out across relaunch.
- Unknown-email neutral acknowledgement passed for `android4c-unknown-e54845c8@example.com`.
- Unknown-email `auth.users` count remained 0 before and after request.
- Google sign-in UI remained absent.
- Backend schema, RLS, and Auth provider configuration remained unchanged.

## Google Exclusion
There is no Google SDK implementation. No hosted provider modifications. Google OAuth is explicitly retired for the Android client platform.

## Android-5 Sequencing
Following this cycle, Android-5 will execute the native seller media implementation according to Web issue #103, PR #104, and migration 33.
