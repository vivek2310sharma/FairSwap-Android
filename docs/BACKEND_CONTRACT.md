# FairSwap Android Backend Contract

The canonical backend implementation currently lives with FairSwap Web/Supabase. Android consumes the same schema and preserves its security model.

## Accounts
- Supabase Auth email/password with mandatory confirmation.
- Google sign-in is for existing matching verified FairSwap accounts.
- Current policy acceptance and 18+ confirmation are database-enforced requirements for protected customer actions.

## Listings

Known lifecycle states:

```text
draft
pending_review
active
paused
reserved
sold
expired
rejected
removed
```

Use existing RPCs for lifecycle operations instead of direct status manipulation.

## Discovery

Use `search_public_listings(...)` for bounded public search/filter/sort behavior rather than rebuilding the search contract independently in Android.

## Messaging

Use the trusted RPC boundary:

```text
start_conversation(listing_id, content, client_request_id)
send_message(conversation_id, content, client_request_id)
mark_conversation_read(conversation_id, message_id)
```

Messages are published through Supabase Realtime. The `client_request_id` supports idempotent retry design.

## Notifications

`notifications` is published through Supabase Realtime. Device push is a separate mobile-backend concern and is not part of Android-0.

## Blocking/reporting

Use the existing block/unblock/report RPCs and RLS semantics.

## Media exception

Listing originals and derivatives are private. The Web app currently performs trusted server-side validation, image processing/metadata removal, derivative generation, and service-role Storage operations.

Android must not bypass this by uploading directly to privileged buckets. Cross-platform listing media requires a reviewed mobile-safe server endpoint or deliberately redesigned trusted media service.
