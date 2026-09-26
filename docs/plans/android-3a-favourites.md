# Android-3A Favourites Plan

## Scope

Add authenticated saved-favourite mutations, public-only saved listing cards, paged favourites, unavailable-item cleanup, and shared in-memory heart state to the native Android app. Use FairSwapTest first. Do not add a backend migration unless a verified contract gap requires one.

## Modules and files

- `core:favourites`: Supabase favourites gateway/repository, typed mutation/list outcomes, bounded pagination, per-user in-memory favourite coordinator, and repository tests. Depend on `core:discovery` for public listing models/cards and media URL building; use the existing Supabase client and authentication session.
- `feature:favourites`: route-scoped ViewModel, immutable state, screen, and state-machine tests.
- `core:discovery`: add a safe batch public-card lookup if needed. It must check `is_listing_publicly_visible` before detail reads and again before card emission.
- `feature:discovery` and `feature:listingdetail`: render favourite state and request auth navigation without blocking public content.
- `feature:auth`: add a Saved Favourites action to the existing account summary and expose `PolicyReviewRequired` for stale-policy return flows.
- `app`: Hilt bindings and typed Navigation 3 route wiring; preserve NavEntry decorators and route-scoped ViewModels.
- `docs/PARITY_MATRIX.md`: mark 2B complete, 3A complete only after verification, and 3B profile editing pending.

## Auth and policy boundary

All add/remove/clear operations resolve the current Supabase user inside the repository; caller-supplied user IDs are forbidden. Signed-out actions return `AuthRequired`. Before either mutation, call `has_current_policy_acceptance`; false returns `PolicyReviewRequired`, and RPC failure fails closed. The existing Auth route handles sign-in and policy reacceptance. Do not automatically replay a blocked mutation. PostgreSQL RLS remains authoritative.

## Favourites RLS contract

Use the existing `public.favourites(user_id, listing_id, created_at)` table and composite key. Read and delete remain limited to `auth.uid()`. Add uses the current user's ID and is guarded by account-active and `is_listing_publicly_visible` RLS checks. Add performs an explicit public-visibility RPC check first. Remove and unavailable cleanup do not require the listing to remain visible. No service role or policy weakening.

## Public listing visibility

Treat favourites as public marketplace content even when the owner can read a private listing through expanded RLS. For each bounded page, collect own favourite IDs, check visibility with concurrency at most four, fetch public card data only for visible IDs, then check visibility again before emitting cards. A false or changed visibility result becomes an unavailable ID with no listing fields. Reuse Android-2B mapping/media rules and Web `/thumb` derivative URLs.

## Session state

Keep favourite state in process memory only, keyed by current authenticated user and listing ID. Reconcile the user ID on repository calls and clear the coordinator on sign-out or account change; signed-out UI uses neutral hearts. No disk persistence and no dependency from `core:auth` to `core:favourites`.

## Navigation

Add serializable `FavouritesRoute`. Open it from signed-in AccountHome and return to Account. Discovery/detail heart taps while signed out navigate to Auth while retaining the underlying route. Keep `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`; create `FavouritesViewModel` only inside its NavEntry.

## Pagination and unavailable cleanup

Fetch favourite rows by `created_at DESC` in pages of 20. Visibility-check each bounded page with concurrency at most four. Track visible cards, unavailable IDs/count, and `hasMore` independently so unavailable rows never block later pages. Clear only IDs classified unavailable by the repository for the current user, bounded to the loaded unavailable set. Failed cleanup preserves state.

## Offline and errors

Use approximately 15-second operation timeouts. No offline mutation queue or Room. Keep last successful pages on transient refresh or next-page failure; expose generic retry messages and stale state. Cancellation and exceptions always release loading. Raw backend errors never reach Compose. Mutations use explicit desired state, prevent concurrent duplicate listing mutations, and update shared state only after backend success (or roll back an explicitly optimistic update).

## Tests

Cover auth/policy mutation gates, UUID validation, visibility and owner-RLS regressions, idempotence/concurrency, current-user ownership, public card double-check, media URL boundaries, pagination and deduplication, unavailable cleanup, shared-state account isolation, decoration failures, ViewModel timeout/cancellation/retry/stale behavior, and existing Android-1/2A/2B regression suites.

## Emulator acceptance

Manual FairSwapTest staging acceptance passed.

On FairSwapTest with two test accounts and public/private listings: browse signed out; heart tap opens Auth and returns to the same screen; sign in and save/remove; stale policy opens policy review without mutation; open Saved Favourites from Account; verify pagination and unavailable-only rows; clear unavailable without changing visible saves; switch accounts/sign out and verify no stale hearts; navigate saved card to detail; confirm details/media never appear for a private or unavailable listing. Record emulator/device availability separately from automated test results.

## Exclusions

No backend migration without a proven contract gap, no production data mutation, no service-role credential, no persistent/offline queue, no profile editor (Android-3B), no seller CRUD/media upload, no Google auth, and no broad Account redesign.
