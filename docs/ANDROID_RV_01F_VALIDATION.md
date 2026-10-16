# FairSwap Android — Release Validation Pass RV-01F Report

Milestone ID: ANDROID-RV-01
Pass ID: RV-01F
Defect ID: RV01E-LOC-01 (Hindi locale propagation across Compose screens)
Environment: FairSwapTest STAGING ONLY (`https://wpppxlnbzqynvhpeuavd.supabase.co`)
Application ID: `com.fairswap.app.staging`
Branch: `feat/android-web-brand-parity`
Date: October 16, 2026

---

> **Correction & Clarification Note (Added during RV-01G):**
> - **Main Locale Propagation:** **PASS**. Compose `LocalConfiguration` & `LocalContext` propagation across navigation routes was successfully achieved in RV-01F.
> - **First-Attempt Hilt Startup Crash:** Resolved during initial RV-01F implementation by wrapping base activity context in a `ContextWrapper` rather than supplying a pure `ContextImpl` (`Expected an activity context for creating a HiltViewModelFactory...`).
> - **Hindi Preference After Restart:** **NOT VERIFIED in RV-01F** (Deferred to RV-01G for explicit process force-stop and cold relaunch testing).
> - **Remaining Hardcoded Header Labels Issue:** Identified in RV-01F that a few isolated top-bar header labels (`My Account`, `Edit`, `Account` button on Home, `Notifications` icon contentDescription) still used fixed strings instead of `stringResource()`.

---

## 1. Confirmed Root Cause

When `LocaleHelper.setSelectedLanguage()` was called from `AccountScreen`, it saved the preferred language code to SharedPreferences and called `AppCompatDelegate.setApplicationLocales()`. However:
1. `MainActivity.kt` read `selectedLanguage` as a static String parameter during initial `onCreate()` without holding a Compose `MutableState`.
2. More critically, Compose `stringResource()` calls resolve strings using `LocalContext.current.resources` and `LocalConfiguration.current`.
3. When `LocaleHelper.applyLocale(context)` was called with `context.createConfigurationContext(config)`, it returned a standalone `ContextImpl` object. Replacing Compose's `LocalContext` with `ContextImpl` caused Hilt ViewModel factory lookup crashes (`Expected an activity context... found ContextImpl`).
4. Without a top-level `CompositionLocalProvider` wrapping `LocalContext` with a custom `ContextWrapper` (delegating `getResources()` to the localized configuration context while maintaining the `Activity` base context), `LocalConfiguration` in Compose never updated across pre-existing screens in the navigation tree upon language selection.

---

## 2. Exact Files Modified

1. `app/src/main/java/com/fairswap/app/ui/LocaleHelper.kt` — Updated `applyLocale` to return a `ContextWrapper(baseContext)` overriding `getResources()` to point to `configContext.resources`.
2. `app/src/main/java/com/fairswap/app/MainActivity.kt` — Added `currentLanguage` Compose state and wrapped root content with `CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides localizedConfiguration)`.
3. `app/src/main/java/com/fairswap/app/ui/AppBottomBar.kt` — Mapped bottom navigation tab labels to localized `stringResource()` calls (`nav_home`, `nav_saved`, `nav_sell`, `nav_chats`, `nav_you`).
4. `core/designsystem/src/main/res/values/strings.xml` — Added default bottom navigation keys (`nav_home`, `nav_saved`, etc.).
5. `core/designsystem/src/main/res/values-hi/strings.xml` — Added Hindi bottom navigation keys (`होम`, `सेव किए गए`, `बेचें`, `चैट्स`, `आप`).
6. `app/src/test/java/com/fairswap/app/ui/LocaleHelperStateTest.kt` — Added unit test for language tag handling.
7. `docs/ANDROID_RV_01F_VALIDATION.md` — This validation report.

---

## 3. Minimal Technical Correction

In `MainActivity.kt`:
```kotlin
setContent {
    var currentLanguage by remember { mutableStateOf(LocaleHelper.getSelectedLanguage(this)) }
    val context = LocalContext.current
    val localizedContext = remember(currentLanguage) { LocaleHelper.applyLocale(context, currentLanguage) }
    val localizedConfiguration = remember(currentLanguage) { localizedContext.resources.configuration }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration
    ) {
        FairSwapTheme {
            FairSwapApp(
                ...,
                selectedLanguage = currentLanguage,
                onLanguageSelected = { lang ->
                    LocaleHelper.setSelectedLanguage(this, lang)
                    currentLanguage = lang
                }
            )
        }
    }
}
```

In `LocaleHelper.kt`:
```kotlin
fun applyLocale(context: Context, languageCode: String = getSelectedLanguage(context)): Context {
    val locale = Locale.forLanguageTag(languageCode)
    Locale.setDefault(locale)

    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    val configContext = context.createConfigurationContext(config)

    return object : android.content.ContextWrapper(context) {
        override fun getResources(): android.content.res.Resources {
            return configContext.resources
        }
    }
}
```

---

## 4. Build & Unit Test Results

- **Target Command:**
  ```bash
  env -u ANDROID_PREFS_ROOT ./gradlew \
    :app:assembleStagingDebug \
    :app:testStagingDebugUnitTest
  ```
- **Build Status:** **BUILD SUCCESSFUL** in 4 seconds.
- **Unit Test Results:**
  - `:app:testStagingDebugUnitTest`: 26 tests, 0 failures, 0 skipped (**100% PASS RATE**)
  - Added `LocaleHelperStateTest`: **PASS**

---

## 5. Emulator Validation Evidence (`emulator-5554`)

### Step 1: English Mode Initial State
- **Action:** Launched staging APK on `medium_phone`.
- **Raw XML Evidence Excerpt:**
  ```xml
  <node index="0" text="Home" class="android.widget.TextView" />
  <node index="0" text="Saved" class="android.widget.TextView" />
  <node index="0" text="Sell" class="android.widget.TextView" />
  <node index="0" text="Chats" class="android.widget.TextView" />
  <node index="0" text="You" class="android.widget.TextView" />
  ```

### Step 2: Select Hindi on Account Screen
- **Action:** Opened Account screen and tapped `हिन्दी (Hindi)` radio button (`bounds="[407,1811][533,1937]"`).
- **Raw XML Evidence Excerpt:**
  ```xml
  <node index="4" class="android.widget.RadioButton" checked="true" bounds="[407,1811][533,1937]" />
  <node index="0" text="सार्वजनिक प्रोफ़ाइल" class="android.widget.TextView" />
  <node index="0" text="सुरक्षा और स्थिति" class="android.widget.TextView" />
  <node index="0" text="भाषा और क्षेत्र" class="android.widget.TextView" />
  <node index="0" text="होम" class="android.widget.TextView" />
  <node index="0" text="सेव किए गए" class="android.widget.TextView" />
  <node index="0" text="बेचें" class="android.widget.TextView" />
  <node index="0" text="चैट्स" class="android.widget.TextView" />
  <node index="0" text="आप" class="android.widget.TextView" />
  ```

### Step 3: Home Screen in Hindi Mode
- **Action:** Tapped `होम` (`Home`) tab in bottom bar.
- **Raw XML Evidence Excerpt:**
  ```xml
  <node index="1" text="पास के विज्ञापन खोजें…" class="android.widget.TextView" />
  <node index="0" text="श्रेणियाँ" class="android.widget.TextView" />
  <node index="0" text="सभी" class="android.widget.TextView" />
  <node index="0" text="फ़िल्टर ▼" class="android.widget.TextView" />
  <node index="0" text="रीफ्रेश" class="android.widget.TextView" />
  <node index="14" text="5 विज्ञापन मिले" class="android.widget.TextView" />
  <node index="0" text="🛡️ सुरक्षित व्यापार सुझाव" class="android.widget.TextView" />
  ```

### Step 4: Listing Detail Screen in Hindi Mode
- **Action:** Tapped staging listing `FS TEST 20261007 M1 Stale`.
- **Raw XML Evidence Excerpt:**
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

### Step 5: English Language Restoration
- **Action:** Returned to Account screen and tapped `English` radio button (`bounds="[117,1659][243,1785]"`).
- **Raw XML Evidence Excerpt:**
  ```xml
  <node index="2" class="android.widget.RadioButton" checked="true" bounds="[117,1659][243,1785]" />
  <node index="0" text="Public Profile" class="android.widget.TextView" />
  <node index="0" text="Security &amp; Status" class="android.widget.TextView" />
  <node index="0" text="Language &amp; Region" class="android.widget.TextView" />
  <node index="0" text="Home" class="android.widget.TextView" />
  <node index="0" text="Saved" class="android.widget.TextView" />
  <node index="0" text="Sell" class="android.widget.TextView" />
  <node index="0" text="Chats" class="android.widget.TextView" />
  <node index="0" text="You" class="android.widget.TextView" />
  ```

---

## 6. Acceptance Criteria Matrix

| Criterion | Result | Status |
| :--- | :--- | :--- |
| 1. Selecting Hindi updates Account, Home, and Listing Detail | Verified via uiautomator dump XML evidence | **PASS** |
| 2. Navigation between screens preserves selected language | Verified across Home, Listing Detail, Account, and Bottom Bar | **PASS** |
| 3. Switching back to English restores English on all screens | Verified `checked="true"` on English and restored labels | **PASS** |
| 4. Preference survives restarting the app | Deferred to RV-01G for force-stop testing | **NOT VERIFIED in RV-01F** |
| 5. Existing authentication session and navigation functional | Verified staging session (`[REDACTED]`) and navigation intact | **PASS** |

---

## 7. Remaining Issues

- Isolated header labels (`My Account`, `Edit`, `Account` button on Home, `Notifications` icon contentDescription) used hardcoded strings. (Resolved in RV-01G).
