# Android-0 Foundation Plan

Status: BUILD AUTHORIZED by project owner on 2026-09-26.

## Goal

Create the smallest production-quality Android baseline that can build as a native application, remain staging-first, preserve FairSwap's Supabase security boundaries, and prove an anonymous RLS-protected connection to FairSwapTest.

## Scope

1. Gradle/Android project with JDK 17, minSdk 26, compile/target SDK 37.
2. Staging and production product flavors; only staging is configured for active development.
3. Compose Material 3 design-system module.
4. Navigation 3 with typed serializable keys.
5. Hilt baseline.
6. Supabase Kotlin modules with Ktor OkHttp.
7. Foundation screen with bounded anonymous `categories` query when a staging publishable key is configured.
8. GitHub Actions build/test/lint workflow.
9. Security/architecture/parity documentation and agent rules.

## Explicit exclusions

- No production Supabase mutation.
- No Auth UI yet.
- No listing mutation or media upload.
- No messaging implementation.
- No Room/WorkManager yet.
- No service-role/elevated secret in Android.
- No RLS/Storage policy changes.

## Verification gate

```bash
./gradlew :app:assembleStagingDebug test lint
```

With a valid FairSwapTest publishable key, run `stagingDebug` and execute **Check staging connection**. Expected outcome: the bounded anonymous category query succeeds under existing backend permissions.
