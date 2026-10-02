# FairSwap Android-4A: Seller Listings + Listing Lifecycle Plan

## Scope
Milestone Android-4A implements seller listing management and lifecycle in the FairSwap Android application.

### Key Included Features:
- My Listings dashboard for authenticated sellers
- Create draft listing
- Load/edit seller-owned listing
- Category, city, locality selection
- Dynamic category attributes rendering and editing
- Draft and submission validation
- Submit existing eligible draft/rejected listing via RPC
- Lifecycle actions via RPC (`pause`, `resume`, `reserve`, `unreserve`, `mark_sold`, `renew`)
- Rejection, removal, status, and revision information display
- Ready-media count and status explanation for submission eligibility
- Account -> My Listings navigation integration

### Explicit Exclusions (Deferred to Android-5 or Non-Mobile Scope):
- Image picking and media upload
- Direct Supabase Storage uploads
- Web service-role capabilities
- Media reordering, deletion, or cover photo mutation (`cover_photo_id`)
- Avatar upload
- Hard deleting listings (seller hard-delete is not supported by backend RLS)

---

## Module & File Plan

### 1. New Module: `:core:sellerlistings`
- `core/sellerlistings/build.gradle.kts`
- `core/sellerlistings/src/main/java/com/fairswap/core/sellerlistings/SellerListingModels.kt`:
  - Domain models: `SellerListing`, `SellerListingSummary`, `SellerListingDetail`, `CategoryAttributeMetadata`, `SellerLifecycleAction`, `SellerMutationResult`, `SellerListingsResult`, `SellerListingDetailResult`.
  - Wire DTOs for Supabase queries and RPCs.
  - Validation rules for drafts and submissions.
- `core/sellerlistings/src/main/java/com/fairswap/core/sellerlistings/SellerListingGateway.kt`:
  - Direct PostgREST table queries and RPC calls (`seller_submit_listing`, `seller_transition_listing`, `has_current_policy_acceptance`).
- `core/sellerlistings/src/main/java/com/fairswap/core/sellerlistings/SellerListingRepository.kt`:
  - Auth resolution, policy verification, session matching, and data mapping.

### 2. New Module: `:feature:sellerlistings`
- `feature/sellerlistings/build.gradle.kts`
- `feature/sellerlistings/src/main/java/com/fairswap/feature/sellerlistings/MyListingsScreen.kt`:
  - Dashboard UI showing seller's listings, status badges, price, revision, rejection/removal reasons, and action buttons.
- `feature/sellerlistings/src/main/java/com/fairswap/feature/sellerlistings/MyListingsViewModel.kt`:
  - Manages dashboard state, refreshes, lifecycle action mutations, and cross-account isolation.
- `feature/sellerlistings/src/main/java/com/fairswap/feature/sellerlistings/SellerListingEditorScreen.kt`:
  - Form UI for creating/editing listing (title, category, description, condition, price type, price, city, locality, pin code, dynamic category attributes).
- `feature/sellerlistings/src/main/java/com/fairswap/feature/sellerlistings/SellerListingEditorViewModel.kt`:
  - Handles reference metadata loading, form input state, validation, saving draft, submitting, and confirmation dialogs.

### 3. Updates to Existing Files:
- `settings.gradle.kts`: Include `:core:sellerlistings` and `:feature:sellerlistings`.
- `app/build.gradle.kts`: Add dependencies on `:core:sellerlistings` and `:feature:sellerlistings`.
- `app/src/main/java/com/fairswap/app/di/AppModule.kt`: Provide `SellerListingRepository` and gateway Hilt dependencies.
- `app/src/main/java/com/fairswap/app/ui/FairSwapApp.kt`: Add `MyListingsRoute`, `CreateSellerListingRoute`, and `EditSellerListingRoute` with navigation state decorators.
- `feature/account/src/main/java/com/fairswap/feature/account/AccountScreen.kt`: Add "My Listings" entry button.
- `docs/PARITY_MATRIX.md`: Update milestone entry upon completion.

---

## Backend Contract & Security Controls

### 1. Security & Insert/Update Grants
- **INSERT**: Authenticated seller INSERT permits ONLY:
  `category_id`, `city_id`, `locality_id`, `title`, `description`, `condition`, `price_type`, `price_paise`, `pin_code`, `category_attributes`.
  - Trigger sets `seller_id = auth.uid()`, `slug`, `status = draft`, `revision = 1`, `renewal_count = 0`, `cover_photo_id = null`.
  - Android NEVER sends restricted/generated fields on INSERT.
- **UPDATE**: Authenticated seller UPDATE permits content fields.
  - Android-4A NEVER mutates `cover_photo_id`.
  - UPDATE queries are strictly constrained by `id = listingId AND seller_id = current authenticated user` via RLS.
  - Updates allowed only when status is `draft`, `active`, `reserved`, `paused`, `rejected`.
  - Edits disallowed for `pending_review`, `sold`, `expired`, `removed`.

### 2. Database-Enforced Edit Transitions
- Material edits to `active`, `reserved`, or `paused` listings automatically trigger backend transitions to `pending_review`, incrementing `revision` and updating `submitted_at`.
  - UI displays a clear notice: "Saving these changes will send this listing back for review."
- Material edits to `rejected` listings automatically transition status to `draft`, incrementing `revision` and clearing `rejection_reason`.
- Authoritative state is reloaded from Supabase after every edit.

### 3. RPC Contracts
- **Submission**: `seller_submit_listing(listing_id)`
  - Direct status UPDATE for submission is forbidden.
  - Validates authenticated user, active account, ownership, minimum field lengths, and presence of at least 1 ready photo (`processing_status = 'ready'`).
- **Lifecycle Action**: `seller_transition_listing(listing_id, action)`
  - Allowed actions: `pause`, `resume`, `reserve`, `unreserve`, `mark_sold`, `renew`.
  - Passed via typed Kotlin enum/sealed representation.

---

## Listing Lifecycle Matrix

| Current Status | Available UI Actions | Notes & Transitions |
|---|---|---|
| `draft` | Edit, Submit for Review | Submission requires ready media photo in backend. |
| `pending_review` | Read-only | Under review by moderators. No content edit or lifecycle RPC. |
| `active` | Edit, Pause, Reserve, Mark Sold | Edit warns seller that status will revert to `pending_review`. |
| `reserved` | Edit, Unreserve, Mark Sold | Edit warns seller that status will revert to `pending_review`. |
| `paused` | Edit, Resume, Mark Sold | Resume requires listing was previously published & not expired. |
| `rejected` | Edit, Submit for Review | Displays rejection reason. Edits reset status to `draft`. |
| `expired` | Renew | Renew allowed only if `renewal_count < 3`. Resets to `pending_review`. |
| `sold` | Read-only | Immutable terminal seller state. |
| `removed` | Read-only | Displays moderation removal reason. |

---

## Auth & Policy Boundaries
Every mutation (`create`, `edit`, `submit`, `lifecycle transition`) follows a strict 7-step sequence:
1. Resolve current authenticated Supabase user.
2. If signed out -> return `SellerMutationResult.AuthRequired`.
3. Check `has_current_policy_acceptance()`.
4. If false -> return `SellerMutationResult.PolicyReviewRequired`.
5. If policy check fails -> return generic `SellerMutationResult.Failure`.
6. Perform owner-scoped PostgREST/RPC operation.
7. Re-check current authenticated user against starting user ID. If changed -> discard result and return `AuthRequired`.

---

## Seller Ownership & Session Isolation
- All dashboard loads and editor operations capture `currentUserId`.
- If user switches account (A -> B) or signs out while an operation is in flight, the completed operation is discarded.
- Sign-out immediately purges seller dashboard and editor state.
- Account A's listings, drafts, or rejection reasons will never appear in Account B's UI.

---

## Validation Rules

| Field | Draft Validation | Submit Validation |
|---|---|---|
| `title` | 3..150 chars | 3..150 chars |
| `description` | Optional, max 5000 chars | Optional (or >=10 if provided / required by backend rules) |
| `category_id` | Required valid UUID | Required valid UUID |
| `city_id` | Optional valid UUID | Required valid UUID |
| `locality_id` | Optional valid UUID (must belong to city) | Optional valid UUID (must belong to city) |
| `condition` | Optional | Required (`new`, `like_new`, `good`, `fair`, `poor`) |
| `price_type` | Optional | Required (`fixed`, `negotiable`, `free`) |
| `price_paise` | 0..100,000,000 | > 0 if non-free; 0 if free; max 100,000,000 |
| `pin_code` | Optional (6 digits if present) | 6 digits if present |
| `category_attributes` | Valid JSON object | Required fields specified in category metadata must be present |
| `ready_media` | Not required for draft | At least 1 ready photo required for submission |

---

## Media Dependency & Explanation
- Android-4A does NOT perform file picking or uploads.
- Seller listing queries select minimal media metadata (`id`, `processing_status`, `display_order`) from `listing_media`.
- If `ready_media_count == 0`, Submit button shows an explanatory message: "At least one photo is required to submit for review. Mobile media upload will be available in Android-5."

---

## Navigation Flow
- `AccountScreen` -> Click "My Listings" -> `MyListingsRoute`
- `MyListingsRoute` -> Click "Create Listing" -> `CreateSellerListingRoute`
- `MyListingsRoute` -> Click "Edit" on a listing -> `EditSellerListingRoute(listingId)`
- Back navigation from editor returns to `MyListingsRoute`, preserving dashboard list state.

---

## Test Strategy
- **Core Unit Tests**:
  - `SellerListingValidationTest`: Title lengths, price conversions, PIN codes, category attributes, locality city matching.
  - `SellerListingRepositoryTest`: Policy/auth gating, user isolation, insert payload hygiene, edit transitions, RPC mapping, error handling.
- **Feature Unit Tests**:
  - `MyListingsViewModelTest`: Dashboard load, refresh, lifecycle actions, A -> B session isolation.
  - `SellerListingEditorViewModelTest`: Form validation, create draft, save edit, submit eligibility check.
- **App Integration Tests**:
  - Navigation route keys, session matching, account screen integration.

---

## Verification & Acceptance Checklist
1. `./gradlew :app:assembleStagingDebug test lint` (Passed)
2. `git diff --check` (Clean)
3. Manual staging acceptance on FairSwapTest emulator (Passed):
   - Gate 5D Renew PASS
   - One Android UI Renew execution
   - Status transitioned `expired` -> `pending_review`
   - Revision 1 retained
   - Renewal count updated 0 -> 1
   - `listing.renewed` audit log verified
   - `listing_submitted_for_review` outbox record verified
   - Staging email dispatch sent
   - Unrelated listing ("Bike") unchanged
