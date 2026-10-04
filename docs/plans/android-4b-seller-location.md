# Android 4B: Seller Location Parity

## Overview
This document outlines the PIN-first architecture implemented for seller location editing in the Android application. This fulfills the requirement to decouple safe persistence from complete address structures while preserving user privacy and conforming to the existing Supabase backend schema.

## PIN-first architecture
The Android application relies exclusively on a "PIN-first" flow where the user begins by entering exactly six digits.
- The `pinCode` acts as the primary resolution driver.
- The backend RPC `lookup_pincode` resolves the geographical data (District, State, City).
- A valid six-digit PIN is strictly required for any *new* listing before it can be persisted to the database.

## Database persistence constraints
- `city_id` (UUID) is **NOT NULL**.
- `locality_id` (UUID) is optional (nullable).
- `pin_code` (Text) and `landmark` (Text) are nullable, but when populated, `pin_code` must strictly contain exactly six numeric digits (checked by Postgres constraints), and `landmark` must not exceed 200 characters.

## Local editor state versus persistable state
The client distinguishes cleanly between the local `SellerListingInput` Form State and Persistence-Ready State.
- Incomplete forms (blank PIN, <6 digit PIN, unresolved district) can be held locally.
- New listings are blocked from `Save Draft` or `Submit` until they possess a valid six-digit PIN and a fully resolved `city_id`.
- Existing listings without PIN changes remain persistable as long as they retain their previously resolved `city_id`.

## Single-district flow
When `lookup_pincode` returns exactly one matching District for the entered PIN:
- The `city_id` is automatically deduced based on district and state.
- The locality picker is populated with matching areas for that district.
- If only one locality exists, it may be auto-selected (if there was no previously persisted locality).

## Multi-district flow
When `lookup_pincode` returns multiple matching Districts (e.g. crossing borders):
- The `RequiresDistrictChoice` UI state triggers.
- The seller must explicitly select a district from a dropdown.
- Once selected, the flow proceeds similarly to the single-district flow, with localities filtered by the explicit district choice.

## Private location hydration
To protect privacy, public/safe DTO projections do not include `pin_code` or `landmark`.
Instead, during Edit Hydration, the client invokes `get_seller_listing_location_private(listing_id)` to recover these fields.

## Privacy boundary
- Only approximate area (`city` and `locality`) is rendered on the public marketplace.
- `pin_code` and `landmark` are structurally excluded from public visibility and read-paths outside the authenticated listing owner.

## Stale/session response protection
Network fetches are carefully isolated from one another via `opId` counters, ensuring that rapid user edits (or session switching) invalidate and discard stale `lookup_pincode` responses, preventing data races or cross-session leakage.

## Optional locality semantics
- An existing locality remains unchanged and is preserved when the location has not been altered.
- A changed PIN causes automatic invalidation of the old derived location, clearing out the old city and locality.
- Locality remains completely optional throughout the flow.

## Location-edit moderation follow-up
Edits to location variables (PIN, Locality, City, Landmark) are not currently classified as "material" for moderation purposes. They do not independently trigger a false "re-review" UI warning or transition the listing to `PENDING_REVIEW` if other material content is untouched.

## Automated test coverage
Thorough coverage of all these components exists in `core:sellerlistings` and `feature:sellerlistings` test modules.

## Staging accepted
The feature is fully implemented, verified, and accepted on the Staging environment (`FairSwapTest`).

Acceptance Evidence:
- Partial PIN `30100` blocked persistence until 6th digit was completed.
- PIN `301001` resolved State = `Rajasthan` and District = `Alwar`.
- Real draft save succeeded via Android UI (`Android 4B Location Acceptance A1`).
- Private PIN `301001` and landmark `Android 4B private landmark A1` were absent from public My Listings cards.
- Private edit hydration successfully recovered PIN `301001`, landmark, State `Rajasthan`, and District `Alwar`.
- Multi-district PIN `110003` exercised explicit district selection without guessing.
- Unsaved multi-district change was discarded cleanly without corrupting the persisted draft.
- Backend status remained `draft` with valid `city_id` and `submitted_at = null`.
- Reviewer/submission email count = 0 (verified via `public.email_outbox`).
- Scalar JSONB RPC decoding verified using `.decodeAs<PincodeLookupResponseDto>()`.
