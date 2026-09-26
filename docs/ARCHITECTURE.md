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

Android-0 contains only the modules needed for a working foundation:

```text
app
core:network
core:designsystem
```

Feature modules are added when they contain a real vertical slice.

## Security boundary

- Client credential: Supabase publishable/anon key only.
- User authorization: current Supabase session + PostgreSQL RLS.
- State transitions/business invariants: existing database constraints, triggers and RPCs.
- Elevated operations: server-side only; never moved into the APK.

## Environment boundary

`staging` and `production` are Gradle product flavors. Android Phase 1 uses `staging` and FairSwapTest. Production credentials remain unset unless explicitly authorized.

## Navigation

Navigation 3 uses serializable `NavKey` types.

## Offline policy

Offline behavior is feature-specific. Read caches and idempotent outboxes can be added when required. Security-sensitive mutations must not report success until the authoritative backend accepts them.
