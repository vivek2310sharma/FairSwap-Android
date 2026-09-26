# FairSwap Android agent rules

## Authority and workflow
- Inspect current files before editing.
- Work one explicitly authorized Android milestone at a time.
- Use PLAN -> BUILD -> VERIFY -> REVIEW. Do not self-accept or silently start the next milestone.
- Keep changes small and reviewable.

## Backend and security
- Android and Web share the existing FairSwap Supabase backend contract.
- Staging is FairSwapTest (`wpppxlnbzqynvhpeuavd`) unless the owner explicitly authorizes another environment.
- Never put a Supabase `service_role` key, database password, signing secret, or other privileged credential in Android source, BuildConfig, logs, tests, or documentation.
- Android uses only a Supabase publishable/anon client credential. PostgreSQL RLS and trusted RPCs are the authorization boundary.
- Never weaken RLS, Storage policies, database constraints, or RPC guards to make Android integration easier.
- Prefer existing FairSwap RPCs and state transitions over client-side reconstruction of business rules.
- Do not mutate production Supabase from Android development or tests.

## Media invariant
- Listing originals and derivatives remain private.
- Android must not upload directly to privileged listing Storage paths just to simplify the client.
- Preserve the trusted server-side media validation, metadata removal, derivative generation, and elevated Storage boundary.

## Android engineering
- Kotlin + Jetpack Compose + Material 3.
- Navigation 3 with serializable typed keys.
- MVVM/UDF; immutable UI state and coroutine/Flow-based data access.
- Hilt for dependency injection.
- Supabase Kotlin + Ktor OkHttp.
- Add Room/WorkManager only for a concrete offline/retry requirement.

## Verification
- After each coherent change run the narrowest relevant tests.
- Before review run `./gradlew :app:assembleStagingDebug test lint`.
- Unavailable credentials, SDKs, emulators, or services are NOT RUN/BLOCKED, never PASS.
- Do not fabricate test results.
