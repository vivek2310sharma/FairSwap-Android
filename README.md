# FairSwap Android

Native Android client for FairSwap, sharing the existing Supabase backend with `FairSwap-Web`.

## Status

**Experience v2 (Brand Parity) - COMPLETED.**
**Release Validation (RV-01) - COMPLETED.**
**Release Validation (RV-02A) - BLOCKED (Awaiting staging test accounts).**

This repository implements the native Android application using modern, declarative UI patterns.
It connects exclusively to the `FairSwapTest` staging backend by default.

## Stack

- **Platform:** Native Android (minSdk 26, targetSdk 37)
- **Language:** Kotlin 1.9+
- **UI Framework:** Jetpack Compose, Material 3
- **Navigation:** Compose Navigation 3
- **Dependency Injection:** Hilt
- **Backend SDK:** Supabase Kotlin + Ktor OkHttp
- **IDE:** Android Studio

## Local Setup

To run this project locally, ensure you have the following configured:

1. **Android Studio** (Koala or newer recommended).
2. **JDK 17**.
3. **Emulator/Device** running API 26 or higher.

### Staging Configuration

**CRITICAL: Do not commit secrets to Git.**

Add the following environment variables to your local `local.properties` file in the project root:

```properties
# local.properties (Do not commit)
SUPABASE_STAGING_URL=https://wpppxlnbzqynvhpeuavd.supabase.co
SUPABASE_STAGING_PUBLISHABLE_KEY=<your_anon_key>
```
If you encounter CI errors related to the `SUPABASE_STAGING_PUBLISHABLE_KEY`, ensure the repository has GitHub Actions repository secrets configured for `SUPABASE_STAGING_PUBLISHABLE_KEY`.

## Application IDs

- Production: `com.fairswap.app`
- Staging: `com.fairswap.app.staging`

*Note: Android is not yet production-release approved. Wait for RV-02 and RV-03 validation closure before preparing Play Store builds.*
