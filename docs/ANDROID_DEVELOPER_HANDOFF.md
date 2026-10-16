# FairSwap Android — Developer Handoff

## 1. Architecture & Environment
- **Native Android:** Minimum SDK 26, Target SDK 37, Kotlin 1.9+.
- **UI Framework:** Jetpack Compose with Material 3.
- **Navigation:** `androidx.navigation3` (Compose Navigation 3) with `hiltViewModel()`.
- **Backend:** Supabase Kotlin (GoTrue, PostgREST, Storage, Realtime).
- **Environment:** `FairSwapTest` is configured as the active STAGING backend (`applicationId = "com.fairswap.app.staging"`).

## 2. Milestone Progress
- **Experience v2 (ANDROID-WEB-BRAND-PARITY):** **COMPLETED**.
  - All 9 frontend requirements are implemented at the source level.
  - Features include: Bottom Navigation, Home Discovery feed (with categories & text search), Favourites, Listing Details, Profile Dashboard, Sell/My Listings form skeleton, Chats inbox, and localized Hindi/English switching.
- **Release Validation (ANDROID-RV-01):**
  - **RV-01A to RV-01G:** Read-only tests completed. 
  - Validated staging emulator startup, guest journey search queries, full text Hindi localization propagation across all compose screens (via `LocaleHelper`), and 100% unit-test pass rate (28 total tests).
  - *Known Limitations:* Some non-core features (payments, disputes, advanced messaging) display "In Development" placeholders to strictly adhere to Web brand parity milestones.

## 3. Latest Test & Build Results
- **Command:** `env -u ANDROID_PREFS_ROOT ./gradlew :app:assembleStagingDebug :app:testStagingDebugUnitTest`
- **Result:** `BUILD SUCCESSFUL`
- **Unit Tests:** 100% Pass Rate (28 executed tests in `app`, `core:auth`, and `core:discovery` combined, as of pass RV-01G).

## 4. Pending Validation Blocker (RV-02A)
- **Status:** RV-02A (Buyer & Seller Journey Validation) is **BLOCKED**.
- **Reason:** Requires dedicated, non-personal staging seller and buyer test accounts. The active emulator (`medium_phone`) possessed a personal session, preventing data-mutating operations like creating a synthetic listing, messaging, and blocking users.

## 5. Local Setup Instructions
To safely configure the staging environment without leaking credentials:
1. Do **NOT** commit `local.properties`.
2. Add the following to your `/Users/username/FairSwap/FairSwap-Android-Brand-Parity/local.properties`:
   ```properties
   SUPABASE_STAGING_URL=https://wpppxlnbzqynvhpeuavd.supabase.co
   SUPABASE_STAGING_PUBLISHABLE_KEY=<your_provided_anon_key>
   ```

## 6. Recommended Next Steps
- **Next Milestone:** Resume `ANDROID-RV-02A` once dedicated staging test accounts are available.
- Execute synthetic listing creation (`FS-RV02A-TEST-X`), favourite toggling, and buyer-seller chat transactions.

## 7. Strict Rules
- **No Production Operations.** Connect to `FairSwapTest` only.
- **No Unapproved Migrations.** Do not execute or commit backend changes to the Supabase database.
- **No PII Exposure.** Test with completely synthetic information.
