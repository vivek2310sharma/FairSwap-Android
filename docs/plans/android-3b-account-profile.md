# Android-3B My Account and Public Profile Editing

## Scope

Provide a signed-in My Account screen with account email/status, the current public profile summary, profile editing for display name, bio, city, and state, Saved Favourites navigation, and sign out. Profile persistence uses the existing Supabase contract. Avatar editing, account preferences, privacy flows, seller tools, messaging, and backend migrations are excluded.

## Architecture and modules

- `core:account` owns immutable account/profile models, validation, typed repository results, and the Supabase gateway. Compose never performs persistence or chooses a profile ID.
- `feature:account` owns the account ViewModel and compact Compose account/edit screens. It retains successful account data across transient refresh failures and retains form values after failed saves.
- `app` injects the modules and renders the account feature lazily inside the existing AuthRoute `NavEntry` only for `AuthGate.SignedIn`.
- Existing `core:auth` remains authoritative for session, recovery, policy review, and sign-out. No parallel auth system is introduced.

## Backend contract

Use existing `public.profiles` and `public.user_accounts` tables and existing RLS. Read only `id, display_name, bio, city, state, updated_at` from profiles and `user_id, account_status, terms_version` from the current user's account row. Obtain email from the current Supabase auth session. Update only `display_name, bio, city, state` filtered by the captured current user ID. Never update avatar, account, or policy fields. The existing `has_current_policy_acceptance()` RPC gates profile updates. No backend contract gap is currently known; no migration is planned.

## Auth and policy gates

Resolve the authenticated user in the repository. Signed-out operations return `AuthRequired`. Validate locally before mutation, then require current policy acceptance; false returns `PolicyReviewRequired`, and RPC errors fail closed with a generic failure. Existing AuthViewModel refreshes the auth gate when a handoff is needed. Never replay a save after sign-in or policy acceptance; saving again is a user action. RLS remains final authority.

## Profile validation

Trim all fields. Display name is required and must contain 2–50 characters. Bio is optional up to 500 characters; city and state are optional up to 100 characters each. Empty optional values become null. The update input cannot represent profile ID, avatar URL, timestamps, account status, or terms version.

## Cross-account and session isolation

Do not persist account/profile state to disk. Each load captures the current user ID, queries only that user's rows, then verifies the session still has that ID before returning success. Each update captures the ID, validates, gates policy, updates only that ID, and rechecks the current session before applying the response. Stale loads and writes from account A are discarded after a switch or sign-out. ViewModel state is cleared when its authenticated identity changes.

## Account UI integration

Inside the existing AuthRoute entry, retain the AuthViewModel and render the account feature only while the gate is `SignedIn`. Signed-out, confirmation, recovery, policy-review, restricted, and failure gates keep their existing Auth UI. Keep `AccountViewModel` creation inside the NavEntry. Show email, account status, public profile summary, Edit Public Profile, Saved Favourites, and Sign Out. Keep existing favourites navigation and do not add future-milestone items.

## Failure and offline handling

Use bounded remote operations of about 15 seconds. Initial load failure is terminal with retry; failed refresh keeps the previous successful summary. Save failures retain form values and current summary, show a generic error, and never claim success. Cancellation and timeout clear loading/saving state. No offline write queue or local persistence is added.

## Tests

Cover repository identity capture/recheck, stale load and update results, current-user query/update filters, narrow field mapping, generic failures, policy/auth gates, exact update allowlist, validation boundaries and normalization, ViewModel load/refresh/save/cancel/timeout behavior, and AuthRoute gate/navigation integration where practical. Keep existing auth callback, Android-2A/2B/3A tests green.

## Emulator acceptance

Manual FairSwapTest staging acceptance passed. Checklist: sign in and inspect My Account email/status/profile; edit each public field and confirm persisted values after reload; validate required/length limits and trimmed/blank optional values; trigger policy review and confirm save is not replayed; verify signed-out, restricted, confirmation, and recovery flows remain intact; open Saved Favourites and return; sign out; switch between two test accounts and confirm no prior account profile/form is shown; refresh seller profile for an owner with a public listing and confirm updated public fields. No production backend mutation.

## Exclusions

No avatar upload/edit, notification or contact preferences, password redesign, privacy export/delete, blocking/reports, seller CRUD, messaging, production backend changes, or changes to Android-4+ parity rows.
