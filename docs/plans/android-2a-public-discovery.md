# Android-2A Public Marketplace Discovery

Status: In progress. Branch: `feat/android-public-discovery`.

## Scope and affected files

Add `core:discovery` for public RPC models, validation, repository, price display,
and media URL construction. Add `feature:discovery` for immutable screen state,
view model, and Compose UI. Update app Hilt bindings, Navigation 3 routes,
version catalog, module settings, and parity/backend documents. Auth remains
the existing `AuthRoute`; Google auth, listing detail, and mutations are excluded.

## Backend contract and RLS boundary

Use `search_public_listings(query, category_id, category_slug, city_id,
locality_id, condition, price_min, price_max, sort, page, per_page)` with a
serializable parameter object. Decode `{items, total_count, page, per_page,
total_pages}`. Category/city/locality selectors read active public rows, with
localities filtered by selected city. The existing public RPC, PostgreSQL RLS,
and table policies determine visibility; Android will not reconstruct listing
publication or authorization rules.

## Media read boundary

Only construct `{webBaseUrl}/api/listings/{listingId}/media/{mediaId}/thumb`
for a cover photo. The Web endpoint checks visibility. Private Storage paths
from the RPC are never rendered as URLs. An unset or invalid staging Web origin
produces a deterministic placeholder. No original media URL is constructed.

## Navigation and pagination

`DiscoveryRoute` is the initial serializable Navigation 3 key. An account
action opens the existing `AuthRoute`; users can return to discovery. Explicit
search/filter submission resets results to page 1. Load more requests pages
sequentially at a fixed page size of 20, keeps RPC order, suppresses repeated
listing IDs, and stops at the reported last page. A next-page failure keeps
previous results and can be retried.

## Offline and error behavior

No Room cache. On a failed refresh, retain last successful in-memory cards,
mark them stale, and show a retry action. Never surface raw Supabase errors.
Metadata selector failures show a generic retryable message. No request is
sent for each search-field keystroke.

## Tests

Unit tests cover parameter serialization and validation, money conversion,
response decoding and empty results, filter dependencies, pagination state
transitions, and media URL boundaries. The Gradle gate is
`./gradlew :app:assembleStagingDebug test lint`; run `git diff --check`.

## Manual acceptance on FairSwapTest

Configure a valid staging FairSwap Web base URL and staging publishable key.
Open the staging app signed out, search and filter, change city and verify
locality reset, inspect prices/cards/images, load more, refresh, and retry a
network failure. Verify the account action opens existing auth and Back returns
to discovery, then sign in and repeat discovery. With no Web URL, verify image
placeholders and functional search. Do not connect to production services.
