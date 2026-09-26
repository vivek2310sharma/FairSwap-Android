# Android-2B: public listing detail and seller profile

## Scope

Add public listing detail, public seller profile, a bounded set of the seller's public listings, and navigation between these screens and discovery. Keep Android-1 auth and Android-2A search behavior intact. Stage against FairSwapTest.

## Files and modules

- Extend `core:discovery` with public detail/profile DTOs, domain models, repository methods, visibility checks, safe attributes, and derivative media URLs.
- Add `feature:listingdetail` and `feature:sellerprofile` with route-specific ViewModels, immutable UI state, and Compose screens.
- Connect typed Navigation 3 routes in `app`, and make discovery cards tappable.
- Add focused repository/mapping/ViewModel tests and update the parity matrix.

## Public visibility and queries

Validate route UUIDs before any request. For detail, call `is_listing_publicly_visible` before data access and again after all reads. Fetch only display columns from listings, categories, cities, localities, profiles, and ready listing_media. Treat a false visibility result, missing listing, or missing public profile as unavailable. For seller listings, fetch a bounded set of active listing IDs and display fields, then require the same RPC to return true for each item before emitting it. Check at most 20 candidates with bounded concurrency; report the count of displayed public items. RLS and the trusted RPC remain the authority; no client clock or owner preview.

## Media

Use the existing HTTPS Web-origin builder for `/thumb`, `/medium`, and `/large` derivative routes. Gallery uses medium images. Missing/invalid origin or media IDs produce placeholders. Never read Storage paths, request originals, or create signed URLs.

## Navigation and state

Discovery card → typed listing route → typed seller route; seller card → listing route. Route ViewModels are created only inside their NavEntry, with state collected inside the entry. `LaunchedEffect(route ID)` starts each load. Back stack retains prior screen state. Each remote load has a 15-second timeout, generic error/retry state, distinct unavailable state, and terminal loading cleanup. Successful data may remain visible during retry.

## Tests and emulator acceptance

Test invalid IDs, false public visibility (including owner-readable data), DTO mapping, safe attributes, derivative URLs, media order, bounded seller filtering, ViewModel success/error/timeout/retry, and retained data. On FairSwapTest emulator, open discovery, detail, seller, another listing, and navigate back; confirm public-only data, gallery placeholders/medium images, generic errors and retry, and unchanged auth callback behavior.

## Exclusions

No favourites, messaging, contact, seller CRUD, uploads, reports/blocking, notifications, Room, production configuration, or backend/RLS changes.
