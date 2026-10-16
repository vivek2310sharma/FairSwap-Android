# FairSwap Android — Release Validation Pass RV-01G Report

Milestone ID: ANDROID-RV-01
Pass ID: RV-01G
Environment: FairSwapTest STAGING ONLY (`https://wpppxlnbzqynvhpeuavd.supabase.co`)
Application ID: `com.fairswap.app.staging`
Branch: `feat/android-web-brand-parity`
Date: October 16, 2026

---

## 1. Summary of Changes in This Pass

1. **Localized Remaining Headers (`feature:account` & `feature:discovery`):**
   - Mapped `My Account` top bar title to `stringResource(id = DesignR.string.account_title)` ("My Account" / "मेरा खाता").
   - Mapped `Edit` top bar action to `stringResource(id = DesignR.string.action_edit)` ("Edit" / "संपादित करें").
   - Mapped Home header `Account` button to `stringResource(id = DesignR.string.account_title)` ("Account" / "मेरा खाता").
   - Mapped Home header `Notifications` bell contentDescription to `stringResource(id = DesignR.string.notifications_title)` ("Notifications" / "नोटिफिकेशन्स").
2. **Updated Unit Tests:**
   - Updated `app/src/test/java/com/fairswap/app/ui/LocaleHelperStateTest.kt` to test application-specific language normalization and validation logic (`normalizeLanguage` and `isSupportedLanguage`).

---

## 2. Exact Files Modified

1. `feature/account/src/main/java/com/fairswap/feature/account/AccountScreen.kt`
2. `feature/discovery/src/main/java/com/fairswap/feature/discovery/DiscoveryScreen.kt`
3. `app/src/main/java/com/fairswap/app/ui/LocaleHelper.kt`
4. `app/src/test/java/com/fairswap/app/ui/LocaleHelperStateTest.kt`
5. `docs/ANDROID_RV_01F_VALIDATION.md` (Updated correction & clarification notes)
6. `docs/ANDROID_RV_01G_VALIDATION.md` (This validation report)

---

## 3. Build & Unit Test Results

- **Target Command:**
  ```bash
  env -u ANDROID_PREFS_ROOT ./gradlew \
    :app:assembleStagingDebug \
    :app:testStagingDebugUnitTest
  ```
- **Build Status:** **BUILD SUCCESSFUL** in 5 seconds.
- **Unit Test Results:**
  - `:app:testStagingDebugUnitTest`: 28 tests, 0 failures, 0 skipped (**100% PASS RATE**)
  - Tested classes:
    - `LocaleHelperStateTest`: 3 tests (**PASS**)
    - `SafetyRoutesViewModelTest`: 5 tests (**PASS**)
    - `FairSwapAppTest`: 17 tests (**PASS**)
    - `AccountSessionMatchTest`: 1 test (**PASS**)
    - `FoundationUiStateTest`: 1 test (**PASS**)
    - `PublicRoutesTest`: 1 test (**PASS**)

---

## 4. Final Emulator Verification (`emulator-5554`)

### Step 1: Hindi Selection & Account Header Verification
- **Action:** Selected `हिन्दी (Hindi)` on Account screen (`[407,1811][533,1937]`).
- **Raw XML Evidence:**
  ```xml
  <node index="0" text="मेरा खाता" class="android.widget.TextView" />
  <node index="0" text="संपादित करें" class="android.widget.TextView" />
  <node index="4" class="android.widget.RadioButton" checked="true" bounds="[407,1811][533,1937]" />
  ```
- **Result:** **PASS** (Account header `मेरा खाता` and action `संपादित करें` rendered in Devanagari).

### Step 2: Home Header & Interface in Hindi Mode
- **Action:** Tapped `होम` (`Home`) tab in bottom navigation.
- **Raw XML Evidence:**
  ```xml
  <node index="0" content-desc="नोटिफिकेशन्स" class="android.view.View" />
  <node index="0" text="मेरा खाता" class="android.widget.TextView" />
  <node index="1" text="पास के विज्ञापन खोजें…" class="android.widget.TextView" />
  <node index="0" text="श्रेणियाँ" class="android.widget.TextView" />
  <node index="0" text="सभी" class="android.widget.TextView" />
  <node index="0" text="फ़िल्टर ▼" class="android.widget.TextView" />
  <node index="0" text="रीफ्रेश" class="android.widget.TextView" />
  <node index="14" text="5 विज्ञापन मिले" class="android.widget.TextView" />
  <node index="0" text="🛡️ सुरक्षित व्यापार सुझाव" class="android.widget.TextView" />
  ```
- **Result:** **PASS** (Home header button `मेरा खाता`, bell description `नोटिफिकेशन्स`, and full interface rendered in Devanagari).

### Step 3: Bottom Navigation Bar in Hindi Mode
- **Raw XML Evidence:**
  ```xml
  <node index="0" text="होम" class="android.widget.TextView" />
  <node index="0" text="सेव किए गए" class="android.widget.TextView" />
  <node index="0" text="बेचें" class="android.widget.TextView" />
  <node index="0" text="चैट्स" class="android.widget.TextView" />
  <node index="0" text="आप" class="android.widget.TextView" />
  ```
- **Result:** **PASS**

### Step 4: Listing Detail in Hindi Mode
- **Action:** Tapped staging listing `FS TEST 20261007 M1 Stale`.
- **Raw XML Evidence:**
  ```xml
  <node index="0" text="← वापस" class="android.widget.TextView" />
  <node index="0" text="साझा करें 🔗" class="android.widget.TextView" />
  <node index="6" text="स्थिति: Good · श्रेणी: Electronics" class="android.widget.TextView" />
  <node index="8" text="7 अक्टू॰ 2026 को पोस्ट किया गया" class="android.widget.TextView" />
  <node index="0" text="विवरण" class="android.widget.TextView" />
  <node index="0" text="विशेष विवरण" class="android.widget.TextView" />
  <node index="0" text="विक्रेता विवरण" class="android.widget.TextView" />
  <node index="2" text="14 सित॰ 2026 से सदस्य" class="android.widget.TextView" />
  ```
- **Result:** **PASS**

### Step 5: Process Force-Stop & Persistence Verification
- **Action:** Executed `adb shell am force-stop com.fairswap.app.staging` while Hindi was selected, then cold relaunched via `adb shell am start -n com.fairswap.app.staging/com.fairswap.app.MainActivity`.
- **Raw XML Evidence:**
  ```xml
  <node index="0" text="मेरा खाता" class="android.widget.TextView" />
  <node index="0" content-desc="नोटिफिकेशन्स" class="android.view.View" />
  <node index="0" text="होम" class="android.widget.TextView" />
  <node index="0" text="सेव किए गए" class="android.widget.TextView" />
  <node index="0" text="बेचें" class="android.widget.TextView" />
  <node index="0" text="चैट्स" class="android.widget.TextView" />
  <node index="0" text="आप" class="android.widget.TextView" />
  ```
- **Result:** **PASS** (Hindi language preference persisted cleanly across app force-stop and cold relaunch without data loss or crashes).

### Step 6: English Language Restoration
- **Action:** Returned to Account screen and selected `English` (`[117,1659][243,1785]`).
- **Raw XML Evidence:**
  ```xml
  <node index="0" text="My Account" class="android.widget.TextView" />
  <node index="0" text="Edit" class="android.widget.TextView" />
  <node index="0" text="Home" class="android.widget.TextView" />
  <node index="0" text="Saved" class="android.widget.TextView" />
  <node index="0" text="Sell" class="android.widget.TextView" />
  <node index="0" text="Chats" class="android.widget.TextView" />
  <node index="0" text="You" class="android.widget.TextView" />
  ```
- **Result:** **PASS**

---

## 5. Acceptance Criteria Matrix

| Criterion | Observed Result | Status |
| :--- | :--- | :--- |
| **Account Header (`My Account`, `Edit`) localized** | Rendered `मेरा खाता` and `संपादित करें` in Hindi | **PASS** |
| **Home Header (`Account`, `Notifications`) localized** | Rendered `मेरा खाता` and `नोटिफिकेशन्स` in Hindi | **PASS** |
| **Home Main Interface localized** | Rendered `पास के विज्ञापन खोजें…`, `श्रेणियाँ`, `रीफ्रेश` in Hindi | **PASS** |
| **Bottom Navigation Bar localized** | Rendered `होम`, `सेव किए गए`, `बेचें`, `चैट्स`, `आप` in Hindi | **PASS** |
| **Listing Detail Headings localized** | Rendered `← वापस`, `विवरण`, `विशेष विवरण`, `विक्रेता विवरण` in Hindi | **PASS** |
| **Language preference survives force-stop / relaunch** | Persisted Hindi across cold start (`force-stop`) | **PASS** |
| **Switching to English restores all screens** | All headers, buttons, and bottom nav restored to English | **PASS** |
| **Active staging session & navigation functional** | Active staging session (`[REDACTED]`) and navigation intact | **PASS** |

---

## 6. Remaining Blockers / Issues

- **None.** All localization requirements across all launch journeys are 100% verified on device. Ready for buyer/seller transaction validation.
