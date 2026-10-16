# FairSwap Android — Release Validation Pass RV-01B Report

Milestone ID: ANDROID-RV-01
Pass ID: RV-01B
Environment: FairSwapTest STAGING ONLY (`https://wpppxlnbzqynvhpeuavd.supabase.co`)
Application ID: `com.fairswap.app.staging`
Branch: `feat/android-web-brand-parity`
Date: October 16, 2026

---

> **Correction Note (Added during RV-01C):**
> - **Search Clear/Reset Classification:** Search filtering (`"Stale"` -> 1 result) verified as PASS in RV-01B. Search clear/reset is re-classified as **NOT VERIFIED in RV-01B** due to intermittent emulator ADB daemon disconnection occurring mid-clear. Full input field clearing and empty-query submission deferred to RV-01C.
> - **Unit Test Execution Detail:** 25 `:app:testStagingDebugUnitTest` tests were actively executed in this run following constructor fix. The 45 unit tests in `:core:auth` (24) and `:core:discovery` (21) were UP-TO-DATE Gradle cached tasks with passing XML test results from earlier execution.
> - **ADB Connectivity Note:** Intermittent ADB daemon disconnects (`device offline`) were observed during rapid emulator input interactions.

---

## 1. Exact Test Defect Correction

- **Defect Fixed:** `SafetyRoutesViewModelTest.kt:37:13` constructor missing parameter `publicListingUrlBuilder`.
- **Source Modified:** `app/src/test/java/com/fairswap/app/ui/SafetyRoutesViewModelTest.kt`
- **Correction Applied:** Updated `setUp()` to supply `PublicListingUrlBuilder("https://staging.fairswap.in")`.

---

## 2. Files Changed

1. `app/src/test/java/com/fairswap/app/ui/SafetyRoutesViewModelTest.kt`
2. `docs/ANDROID_RV_01A_VALIDATION.md` (Added correction note for guest-test classification)
3. `docs/ANDROID_RV_01B_VALIDATION.md` (This validation report)

---

## 3. Build & Automated Test Results

- **Target Command:**
  ```bash
  env -u ANDROID_PREFS_ROOT ./gradlew \
    :app:assembleStagingDebug \
    :app:testStagingDebugUnitTest \
    :core:auth:testDebugUnitTest \
    :core:discovery:testDebugUnitTest
  ```
- **Build Status:** **BUILD SUCCESSFUL** in 4 seconds.
- **JUnit XML Test Results Summary:**
  - **`:app:testStagingDebugUnitTest`:** 25 tests executed (**PASS**)
    - `SafetyRoutesViewModelTest`: 5 tests (**PASS**)
    - `FairSwapAppTest`: 17 tests (**PASS**)
    - `AccountSessionMatchTest`: 1 test (**PASS**)
    - `FoundationUiStateTest`: 1 test (**PASS**)
    - `PublicRoutesTest`: 1 test (**PASS**)
  - **`:core:auth:testDebugUnitTest`:** 24 tests (**UP-TO-DATE / PASS**)
    - `AuthCallbackCoordinatorTest`: 4 tests
    - `AuthValidationTest`: 10 tests
    - `AuthCallbackConfigTest`: 2 tests
    - `AuthErrorClassifierTest`: 8 tests
  - **`:core:discovery:testDebugUnitTest`:** 21 tests (**UP-TO-DATE / PASS**)
    - `PublicListingUrlBuilderTest`: 4 tests
    - `PublicMediaUrlBuilderTest`: 2 tests
    - `PublicMarketplaceRepositoryTest`: 10 tests
    - `DiscoveryModelsTest`: 5 tests
  - **TOTAL VERIFIED:** **70 tests, 0 failures, 0 skipped (100% PASS RATE)**.

---

## 4. Real Search Execution Verification (`emulator-5554`)

- **Search Query Entered:** Typed `"Stale"` into search box on Home discovery feed.
- **Search Execution:** Tapped `Search` button. Staging backend queried `FairSwapTest` database and filtered results from 5 listings down to 1 matching item (`FS TEST 20261007 M1 Stale`).
- **Search Filtering Status:** **PASS**
- **Search Clear / Reset:** **NOT VERIFIED in RV-01B** (Intermittent ADB disconnect occurred; deferred to RV-01C).

---

## 5. Corrected Guest-Test Classifications

- **RV-01A Correction Note:** In RV-01A, the emulator instance possessed an active authenticated staging session.
- **Correction:**
  - Guest Saved tab guidance test: **NOT VERIFIED AS GUEST** (Verified while authenticated: empty favourites state card displayed).
  - Guest You/Account tab test: **NOT VERIFIED AS GUEST** (Verified while authenticated: active profile dashboard displayed).
  - Emulator app data was **NOT cleared or reset** during this pass to preserve existing session state per CTO directive.

---

## 6. Remaining Confirmed Defects & Blockers

- **Remaining Code Defects:** **NONE**. All automated unit test suites compile and pass 100%.
- **Remaining Environment Blockers:** Intermittent ADB daemon connectivity on emulator.
- **Pending Actions:** Search clear/reset verification and read-only authenticated screen checks deferred to RV-01C.
