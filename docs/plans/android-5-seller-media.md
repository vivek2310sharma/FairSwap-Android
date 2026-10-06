# Android 5: Seller Listing Media Management

## Authoritative Web Contract (Issue #103 / PR #104)
The FairSwap-Web seller-media API provides a platform-neutral server contract (Migration 33) enforcing:
- Server-mediated private Storage (`listing-originals` and `listing-derivatives`).
- Unified Bearer token authentication (`Authorization: Bearer <token>`).
- Serialized 8-photo limit with parent listing row locking (`FOR UPDATE`).
- Transactional reorder (`public.seller_reorder_listing_media`).
- Transactional cover photo selection (`public.seller_set_listing_cover_photo`).
- Idempotent transactional deletion with automatic cover photo fallback (`public.seller_delete_listing_media`).
- Hardened upload and delete lifecycle preventing premature moderation re-reviews (re-review triggered only when processing_status becomes `ready`).
- Native JSON mode derivative delivery returning short-lived (60s) signed URLs to prevent credential forwarding to Storage origins.

## Android Architecture
The Android client consumes Web HTTP endpoints via a Ktor + OkHttp gateway inside `:core:sellerlistings`:
- **Gateway**: `SellerMediaGateway` (`KtorSellerMediaGateway`) sends requests to Web API routes using `BuildConfig.FAIRSWAP_WEB_BASE_URL`.
- **Auth Security Boundary**: `Authorization: Bearer <accessToken>` is attached at request time using `supabase.auth.currentSessionOrNull()?.accessToken`. `followRedirects` is disabled (`false`) on the HTTP client to prevent token forwarding on unexpected redirects.
- **Signed URL Delivery**: Private seller media derivative URLs are fetched in native JSON mode (`Accept: application/json`). Coil loads image binaries directly from the signed Storage URL without Supabase Authorization headers. Coil `diskCachePolicy` is set to `DISABLED`.

## Concurrency & Locking
- All structural media mutations acquire the shared per-listing lock `SellerListingRepository.acquireMutationLock(listingId, session)` returning a `SellerMutationLockToken`.
- The acquired lock token is released in `finally` via `releaseMutationLock(lockToken)`.
- Mutations are mutually exclusive with listing saves, submissions, and lifecycle actions.
- Uploads are processed serially (one POST request at a time).

## Media Editability Rule
Media editing is allowed for listings in statuses: `DRAFT`, `REJECTED`, `ACTIVE`, `RESERVED`, `PAUSED`, `PENDING_REVIEW`. Denied for `SOLD`, `EXPIRED`, `REMOVED`, `UNKNOWN`.
This rule is exposed via `SellerListingStatus.canEditMedia`.

## Photo Picker & Validation
- System Photo Picker via `ActivityResultContracts.PickMultipleVisualMedia`.
- Zero broad storage permissions (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` not requested).
- Bounded 5 MiB file size check during Uri streaming.
- Magic-byte validation for JPEG (`FF D8 FF`), PNG (`89 50 4E 47`), and WebP (`52 49 46 46 ... 57 41 56 45`).
- HEIC/HEIF and unsupported formats are rejected locally in Phase 1 with a safe user error message.

## Navigation & Editor Route Lifecycle
- Unsaved draft listings (`listingId == null`) show an informational message that draft must be saved first.
- On `CreateSellerListingRoute`, successful draft creation emits `SellerListingEditorEvent.Created(session, entryId, listingId)`.
- `FairSwapApp` replaces `CreateSellerListingRoute` on the backstack with `EditSellerListingRoute(listingId = newId)`, seamlessly enabling media management for the persisted draft.

## Dashboard Invalidation & Snapshot Reconciliation
- Snapshot reconciliation (`reconcileListingWithMediaSnapshot`) updates `status`, `revision`, `coverPhotoId`, and `readyMediaCount` (`snapshot.media.count { processingStatus == READY }`).
- Committed mutations call `SellerListingRepository.notifyDashboardInvalidated(session)` to refresh My Listings.

## Testing & Staging
- Gateway unit tests cover Bearer header injection, `Accept: application/json`, `followRedirects = false`, DTO parsing, error code mapping, and 404 concealment using Ktor `MockEngine`.
- Repository unit tests cover per-listing mutation locking, snapshot reconciliation, serial upload queueing, cover fallback, and idempotent deletion.
- ViewModel unit tests cover draft creation navigation events, photo picker capacity clamping, and stale response generation suppression.
- Staging acceptance on package `com.fairswap.app.staging` is pending.
