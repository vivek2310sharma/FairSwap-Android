# FairSwap Android Architecture

## Direction

FairSwap Android is a native Kotlin/Compose client for the same Supabase backend used by FairSwap Web. It does not introduce a parallel backend or duplicate database authorization rules.

## Layering

```text
Compose UI
  -> ViewModel / immutable UI state
  -> Repository
  -> Supabase data source / local persistence where justified
  -> PostgreSQL RLS + trusted RPCs
```

The application is structured into the following foundation and feature modules:

**App Layer**
- `app`

**Core Layers**
- `core:network`
- `core:designsystem`
- `core:auth`
- `core:account`
- `core:discovery`
- `core:favourites`
- `core:sellerlistings`
- `core:communication`

**Feature Layers**
- `feature:auth`
- `feature:account`
- `feature:discovery`
- `feature:listingdetail`
- `feature:favourites`
- `feature:sellerlistings`
- `feature:sellerprofile`
- `feature:communication`

Feature modules are added when they contain a real vertical slice.

## Security boundary

- Client credential: Supabase publishable/anon key only.
- User authorization: current Supabase session + PostgreSQL RLS.
