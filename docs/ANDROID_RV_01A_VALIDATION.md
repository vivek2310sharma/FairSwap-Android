# FairSwap Android — Release Validation Pass RV-01A Report

Milestone ID: ANDROID-RV-01
Pass ID: RV-01A
Environment: FairSwapTest STAGING ONLY (`https://wpppxlnbzqynvhpeuavd.supabase.co`)
Application ID: `com.fairswap.app.staging`
Branch: `feat/android-web-brand-parity`
Date: October 16, 2026

---

> **Correction Note (Added during RV-01B):**
> - **Guest Journey Classification Correction:** The staging emulator instance used during RV-01A possessed an active authenticated staging user session (`Name: Vicky`, `vivek.kewl@gmail.com`). Therefore, guest-specific checks on the Saved and You tabs were NOT VERIFIED as a signed-out guest in RV-01A (authenticated dashboard and saved state cards were rendered). App data was preserved without clearing per CTO directive.
> - **Search Verification Detail:** Search execution was formally verified in RV-01B with query `"Stale"`, returning 1 matching staging listing, and empty query resetting the feed to 5 listings.

---

## 1. Environment & Emulator Details

- **Workspace Path:** `/Users/vivekkumar/FairSwap/FairSwap-Android-Brand-Parity`
- **Branch:** `feat/android-web-brand-parity`
- **Staging Config Verification:**
  - `applicationIdSuffix = ".staging"` -> `com.fairswap.app.staging`
  - `SUPABASE_STAGING_URL` -> `https://wpppxlnbzqynvhpeuavd.supabase.co` (FairSwapTest Staging Instance)
  - `SUPABASE_STAGING_PUBLISHABLE_KEY` -> Configured in `local.properties` (non-blank)
- **Emulator Profile:** `medium_phone` (Android 36 system image, 1080x2400 dpi 420, ARM64-v8a)
- **Device Serial:** `emulator-5554`

---

## 2. Build Result

- **Target Command:**
  ```bash
  env -u ANDROID_PREFS_ROOT ./gradlew \
    :app:assembleStagingDebug \
    :app:testStagingDebugUnitTest \
    :core:auth:testDebugUnitTest \
    :core:discovery:testDebugUnitTest
  ```
- **Build Status:** **BUILD FAILED** during unit test compilation (`:app:compileStagingDebugUnitTestKotlin`).
- **APK Assembly Status:** `:app:assembleStagingDebug` **SUCCESSFUL** (`app/build/outputs/apk/staging/debug/app-staging-debug.apk`).

---

## 3. Automated Test Results

| Test Suite / Task | Executed | Passed | Failed | Exact Failure Location | Root Cause |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `:core:auth:testDebugUnitTest` | Yes | 100% | 0 | None | N/A |
| `:core:discovery:testDebugUnitTest` | Yes | 100% | 0 | None | N/A |
| `:app:testStagingDebugUnitTest` | Partial | 0 | 1 | `app/src/test/java/com/fairswap/app/ui/SafetyRoutesViewModelTest.kt:37:13` | Missing parameter `publicListingUrlBuilder` in test constructor invocation |

---

## 4. Emulator Startup & Installation Result

- **Emulator Launch:** **PASS** (`emulator-5554` booted cleanly with `sys.boot_completed == 1`).
- **APK Installation:** **PASS** (`adb install -r app/build/outputs/apk/staging/debug/app-staging-debug.apk` -> `Success`).
- **App Launch:** **PASS** (`adb shell am start -n com.fairswap.app.staging/com.fairswap.app.MainActivity`).
- **Fatal Exception / Crash Check:** **PASS** (Zero fatal exceptions in Logcat for `com.fairswap.app.staging`).

---

## 5. Guest Journey Smoke Test Results

| Test Journey | Observed Behavior | Status |
| :--- | :--- | :--- |
| **Open FairSwap** | Staging APK launched cleanly on `medium_phone` without crashing | **PASS** |
| **Open Home** | FairSwap branding (`GIVE • GET • GROW TOGETHER`) and wordmark displayed | **PASS** |
| **Browse listings** | Loaded 5 staging public listings from FairSwapTest (`FS TEST 20261007 M1 Stale`, `FS TEST 20261007 M1 Approve`) | **PASS** |
| **Search** | Search field and category filter chips (`FS TEST 20261007 M1 Category`, `Commuter Bikes`) functional | **PASS** |
| **Open listing** | Tapped `FS TEST 20261007 M1 Stale`; opened Listing Detail with price, photos, description, seller details | **PASS** |
| **Navigate back** | Tapped `← Back`; returned cleanly to Home discovery list | **PASS** |
| **Open Saved as guest** | Tapped `Saved` tab; displayed `♡ No Saved Favourites Yet` guidance card | **NOT VERIFIED AS GUEST (Active Session)** |
| **Open You/Account as guest** | Tapped `You` tab; displayed `My Account` dashboard with language selector (English / हिन्दी) | **NOT VERIFIED AS GUEST (Active Session)** |

---

## 6. Confirmed Defects & Failure Reproduction

### Defect RV01A-01: Unit Test Compilation Failure in `SafetyRoutesViewModelTest.kt`
- **Severity:** High (Blocks `:app:testStagingDebugUnitTest`)
- **Location:** `app/src/test/java/com/fairswap/app/ui/SafetyRoutesViewModelTest.kt:37:13`
- **Error:** `No value passed for parameter 'publicListingUrlBuilder'`
- **Reproduction:** Run `env -u ANDROID_PREFS_ROOT ./gradlew :app:testStagingDebugUnitTest`
- **Root Cause:** When `PublicListingUrlBuilder` was added to `SafetyRoutesViewModel` constructor in `app/src/main/java/com/fairswap/app/ui/SafetyRoutesViewModel.kt`, the unit test file `SafetyRoutesViewModelTest.kt` was not updated with a mock/instance of `PublicListingUrlBuilder`. (Fixed in RV-01B).

---

## 7. Blocked Checks & Reasons

- **Data-Mutating Operations:** Intentionally skipped per RV-01A directive (no sign in, no listing creation, no seller messaging, no blocking/reporting).
- **Runtime Test Verification:** Deferring fixes until RV-01B pass per CTO directive.

---

## 8. Recommended Next Validation Step (RV-01B)

1. **Fix Defect RV01A-01:** Update `SafetyRoutesViewModelTest.kt` line 37 to pass a test instance of `PublicListingUrlBuilder`.
2. **Re-run Automated Tests:** Verify `:app:testStagingDebugUnitTest` passes 100%.
3. **Execute Authenticated User Smoke Tests:** Perform staging authentication, draft listing creation, seller profile view, and messaging on staging.
