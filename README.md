# FairSwap Android

Native Android client for FairSwap, sharing the existing Supabase backend with `FairSwap-Web`.

## Status

**Android-0 Foundation - in progress.**

This milestone establishes a staging-first Android baseline: Gradle/Compose, typed Navigation 3, Hilt, Supabase Kotlin, an anonymous RLS connectivity probe, CI, and project guardrails.

## Stack

- Kotlin / Jetpack Compose / Material 3
- Android Gradle Plugin 9.2.1 / Gradle 9.4.1 / JDK 17
- Kotlin 2.2.10 built-in through AGP; KSP 2.3.12
- compileSdk/targetSdk 37, minSdk 26
- Navigation 3
- Hilt
- Supabase Kotlin + Ktor OkHttp

## Staging

FairSwapTest project ref: `wpppxlnbzqynvhpeuavd`

Create `local.properties` from `local.properties.example` and add the staging publishable key only. Never put a Supabase service-role key in Android.

## Verify

macOS/Linux:

```bash
./gradlew :app:assembleStagingDebug test lint
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleStagingDebug test lint
```

See `docs/ARCHITECTURE.md`, `docs/BACKEND_CONTRACT.md`, and `docs/PARITY_MATRIX.md`.
