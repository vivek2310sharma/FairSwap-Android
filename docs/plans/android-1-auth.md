# Android-1 Authentication & Account Foundation

Status: In progress
Branch: `feat/android-auth`
Base: `main`

## Goal

Implement FairSwap Android authentication against the existing FairSwapTest Supabase Auth, RLS, profile/account tables, and legal-policy RPCs without introducing a parallel mobile auth model.

## Backend contract mirrored from FairSwap Web

- Email/password authentication only for the first executable slice.
- Sign-up validation:
  - valid email
  - password minimum 8 characters
  - display name 2-50 characters
  - explicit Terms of Service + Acceptable Use Policy acceptance
  - explicit 18+ confirmation
- Sign-up metadata:
  - `display_name`
  - `terms_accepted = "true"`
  - `terms_version = "2026-09-18"`
  - `privacy_policy_version = "2026-09-18"`
  - `acceptable_use_version = "2026-09-18"`
  - `age_confirmed = "true"`
  - `minimum_age_confirmed = 18`
- Email confirmation remains enabled.
- Account/profile bootstrap reads only through existing authenticated RLS:
  - `public.profiles`
  - `public.user_accounts`
- Policy freshness uses existing RPC `has_current_policy_acceptance`.
- Re-acceptance uses existing RPC `record_policy_reacceptance`.
- No client contains a Supabase secret/service-role credential.

## Architecture

### Core

`core:network`
- Own one configured `SupabaseClient` per app process.
- Keep environment URL/key and future auth callback configuration together.

`core:auth`
- Auth repository and session-state adapter.
- Validation and authoritative Android legal-policy constants.
- Authenticated profile/account bootstrap.
- Policy-current check and re-acceptance.
- No Compose UI.

### Feature

`feature:auth`
- Compose screens and view models for:
  - session/bootstrap gate
  - sign in
  - sign up
  - confirmation pending
  - policy review
  - authenticated account shell

The app module owns top-level Navigation 3 routing.

## Delivery slices

### Android-1A - executable auth path
1. Persistent Supabase client.
2. Session restoration.
3. Email/password sign in.
4. Sign up with required legal metadata.
5. Email-confirmation pending state.
6. Authenticated profile/account bootstrap.
7. Policy freshness check.
8. Policy re-acceptance.
9. Sign out.
10. Unit tests for validation and legal metadata.

### Android-1B - redirect flows
1. Android auth deep-link scheme/host per environment.
2. Supabase deep-link handling.
3. Forgot-password request.
4. Recovery callback and password update.
5. Staging redirect-URL configuration checklist.

### Android-1C - supported Google flow
Add Google sign-in only after email/password + redirect flows are green, preserving the Web rule that Google auth is limited to the supported existing-account behavior.

## Security invariants

- RLS remains authoritative.
- No service-role/secret key in Android, GitHub, or BuildConfig.
- No production Supabase mutation during Android-1.
- Do not bypass `has_current_policy_acceptance`.
- Treat policy-check failures as fail-closed for authenticated mutations.
- Do not log passwords, refresh tokens, access tokens, or full auth errors containing secrets.
- Account status from `user_accounts` is data, not a client-side authorization substitute.

## Verification

Automated gate:

```bash
./gradlew :app:assembleStagingDebug test lint
```

Manual staging gate:
1. cold launch signed out
2. invalid sign-in remains signed out
3. valid confirmed user signs in and restores after process restart
4. sign out clears authenticated navigation state
5. sign-up rejects missing legal/18+ confirmations
6. valid sign-up creates confirmation-pending state
7. confirmed account loads own profile/account under RLS
8. stale-policy account is routed to policy review
9. policy re-acceptance succeeds through the existing RPC

## Database changes

None planned for Android-1A. Any backend contract mismatch discovered during implementation must be handled as a separate reviewed migration rather than patched from the mobile client.
