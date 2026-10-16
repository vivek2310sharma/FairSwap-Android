# Android Communication & Notifications Milestone

This document tracks the completion of the Android Messaging & Notifications milestone (Android-6A, Android-6B, and Android-6C).

---

## Milestone Acceptance Checklist (C01 - C10)

### C01 — NOTIFICATIONS USER FLOW
- [x] AccountScreen provides a functional `onNotifications` entry.
- [x] NotificationsRoute contains no sensitive notification content in arguments/key.
- [x] NotificationsScreen includes working Back, Refresh, and Mark All Read actions.
- [x] Renders loading, empty ("No notifications yet."), and safe error feedback states.
- [x] Refresh failure preserves previously loaded notification rows and readAt timestamps.
- [x] Title, body, timestamp, and read/unread dot render correctly (`unreadCount` where `readAt == null`).
- [x] Safe timestamp formatting handles malformed dates without throwing exceptions.
- [x] Safe user-facing feedback (`CommunicationUiText`) is displayed without exposing raw backend error strings.

*Evidence*: `NotificationsScreen.kt`, `NotificationsViewModel.kt`, `NotificationsViewModelTest::refreshFailureRetainsPreviouslySuccessfulNotifications`

### C02 — SESSION AND PRIVATE-STATE ISOLATION
- [x] All asynchronous operations hold the originating `CommunicationSession` (and generation).
- [x] On session or user change, previous account rows, errors, mutating IDs, and in-flight jobs are immediately cleared/cancelled.
- [x] Late old-session or old-generation results/events are rejected.
- [x] Old operation finalizers verify operation identity before clearing pending states, preventing an old job from clearing a new job's state.

*Evidence*: `NotificationsViewModel.kt` (`cancelAllWork()`, lazy job registration with identity checks), `NotificationsViewModelTest::sameUserNewerGenerationRejectsStaleOldGenerationResults`, `NotificationsViewModelTest::rebindWithSuspendedRowMutationsCancelsOldJobsAndClearsPendingState`

### C03 — AUTHORITATIVE RESULTS AND RESPONSE ORDERING
- [x] `processFetchResult` maps results into explicit `FetchOutcome` (`AppliedSuccess`, `SupersededSuccess`, `RetryableFailure`, `OtherFailure`, `AuthFailure`, `StaleOrInvalidSession`) paired with `AcceptedResultRecord` tracking session, revision, and outcome kind.
- [x] Request revisions (`requestRevision` / `lastAcceptedRecord`) prevent older fetches from overwriting newer accepted snapshots.
- [x] Response ordering applies to failures as well (an obsolete error cannot overwrite a newer accepted success or auth handoff).
- [x] Failed post-mutation reloads retain existing rows and `readAt` timestamps without fabricating state.
- [x] Safe user-facing feedback is displayed on retryable failure, suppressing ordinary destination events.

*Evidence*: `NotificationsViewModel.kt` (`FetchOutcome`, `processFetchResult`, `AcceptedResultRecord`), `NotificationsViewModelTest::partA1_olderRefreshFailureCannotOverwriteNewerAcceptedSuccess`, `NotificationsViewModelTest::partA5_markReadSuccessWithReloadFailureRetainsRowsAndSuppressesDestinationEvent`

### C04 — VISIBLE-ONLY REALTIME WITH COMPLETE TEARDOWN
- [x] `bind(session)` alone creates zero Realtime collectors.
- [x] `onVisible()` before `bind(session)` is remembered and starts the visible lifecycle when bound.
- [x] Authoritative reconciliation completes before Realtime subscription starts.
- [x] `ReadyForBackfill` and matching-user `Inserted` events trigger authoritative reconciliation.
- [x] Hiding screen (`onNotVisible()`) immediately cancels the visible lifecycle and closes the Realtime collector.
- [x] Mutex-serialized job execution (`visibleLifecycleMutex.withLock`) ensures old collector teardown completes before a new collector starts (`peakActiveNotificationCollectorsCount <= 1`).
- [x] Hidden screen events cause zero fetches, zero state updates, and zero navigation events.

*Evidence*: `NotificationsViewModel.kt` (`startVisibleLifecycle()`, `visibleLifecycleMutex`), `NotificationsViewModelTest::activeRealtimeCollectorsFollowLifecycleZeroOneZeroOne`, `NotificationsViewModelTest::partB1_actualCollectorTeardownIsAwaitedBeforeReplacementStarts`, `NotificationsViewModelTest::hideDuringSuspendedVisibleFetchCancelsFetchAndClearsLoading`

### C05 — SINGLE AND BULK MARK-READ CORRECTNESS
- [x] Lazy job registration (`CoroutineStart.LAZY`) prevents immediate non-suspending completions from leaving completed-job locks in `markReadJobs`.
- [x] Duplicate taps on an in-flight row ID cause zero extra RPC calls.
- [x] Concurrent row operations on different IDs execute independently without cancelling each other.
- [x] Tapping an already-read notification issues zero `markNotificationRead` mutations and navigates directly.
- [x] Mark All Read checks for unread rows (`hasUnread`), locks against duplicate calls (`isMarkingAllRead`), and reloads authoritatively upon completion.
- [x] Calling Mark All Read with 0 unread rows issues zero repository calls.
- [x] Late completion across different accounts and same-user generations are rejected safely, ensuring old cleanup does not clear a newer same-ID action.

*Evidence*: `NotificationsViewModel.kt` (`onNotificationTapped`, `markAllRead`), `NotificationsViewModelTest::immediateCompletionClearsPendingStateWithoutLeavingCompletedJobLock`, `NotificationsViewModelTest::concurrentMarkReadForDifferentIdsAreIndependentAndDoNotCancelEachOther`, `NotificationsViewModelTest::partF01_sessionChangeRejectsOldEventDelivery`

### C06 — DESTINATION ROUTING AND AUTH HANDOFFS
- [x] Uses `NotificationDestinationParser` as sole link parser.
- [x] Valid `new_message` links route to `NavigateToConversation`.
- [x] Valid `listing_rejected` links route to `NavigateToMyListings`.
- [x] Malformed UUIDs, unsupported types, null links, and blank links produce zero navigation events.
- [x] Events carry only necessary session and ID data without raw links or notification text.
- [x] `canConsumeNotificationEvent` in `FairSwapApp` validates active session, live user ID, and `NotificationsRoute` top-route before consuming navigation events.
- [x] `AuthRequired`, `PolicyReviewRequired`, and `AccountRestricted` failures emit guarded `NavigateToAuth(session)` handoff.

*Evidence*: `NotificationsViewModel.kt` (`emitDestinationEvent`), `FairSwapApp.kt` (`canConsumeNotificationEvent`), `FairSwapAppTest::notificationEventGuardMatrixTest`, `NotificationsViewModelTest::destinationRoutingForMyListingsAndMalformedLinks`

### C07 — ACCEPTED MESSAGING REMAINS INTACT
- [x] All accepted messaging flows (Inbox, New Inquiry, Thread) and tests remain intact and passing.
- [x] Inbox filters (All / Buying / Selling) do not make extra network calls on filter toggle.
- [x] Thread visible-only Realtime and keyset pagination remain intact.
- [x] Non-null persisted message request ID invariant maintained.

*Evidence*: `InboxViewModelTest.kt`, `ThreadViewModelTest.xml`, `NewInquiryViewModelTest.xml`

### C08 — SECURITY, PRIVACY, AND CANCELLATION
- [x] ViewModels use `CommunicationRepository`, never `SupabaseClient` directly.
- [x] No custom Bearer headers, manual Authorization tokens, or raw table mutations in ViewModels.
- [x] No notification or message title/body text in `SavedStateHandle`, `Room`, `DataStore`, `SharedPreferences`, or navigation routes.
- [x] No app-global Realtime subscriptions or background polling services.
- [x] `CancellationException` is rethrown as cancellation, never mapped to UI error.

*Evidence*: `NotificationsViewModel.kt`, `NotificationsViewModelTest::cancellationExceptionRethrownAndNeverMappedToFailure`

### C09 — DETERMINISTIC EVIDENCE FOR ACTUAL BEHAVIOR
- [x] Fake test fixture (`FakeFeatureCommunicationRepository` in `InboxViewModelTest.kt`) records `FetchInvocationRecord` (`entered`, `returned`, `canceled`), `MutationInvocationRecord`, `CollectorInvocationRecord`, invocation-specific gates/results, active collectors, peak active collectors, and teardown gates.
- [x] All 42 `NotificationsViewModelTest` and 17 `NotificationsViewModelMutationTest` cases (59 notification tests in total) pass deterministically on `UnconfinedTestDispatcher` and `StandardTestDispatcher`.

*Evidence*: `InboxViewModelTest.kt`, `NotificationsViewModelTest.kt`, `NotificationsViewModelMutationTest.kt`, `TEST-com.fairswap.feature.communication.NotificationsViewModelTest.xml`

### C10 — COMPLETE GATES, INTEGRITY, AND REVIEW HANDOFF
- [x] All unit tests across all communication and app modules pass (177 distinct identifiers across 183 recorded report entries, including 88 in feature/communication across 17 suites). Device and live staging acceptance explicitly NOT RUN.
- [x] All lint checks across communication, account, and listingdetail modules pass.
- [x] Mandatory full repository gate (`:app:assembleStagingDebug test lint`) passes.
- [x] Index empty (`git diff --cached --name-only` empty), `gradle.properties` fingerprint preserved (`733fd7876404b21974689f8973d8fe2fff364eb6dc1e96a4e3ce6c9b025a33b7`).
- [x] Mechanical Python evidence export generated.

*Evidence*: Gate logs, `/var/folders/n7/8vxckxws3d74nxdfmg1s65vm0000gn/T/fairswap_verify_run_7mxb_y0l/`

---

## Affected Files Implementation Summary

1. `feature/communication/src/main/java/com/fairswap/feature/communication/NotificationsViewModel.kt`
   - Production ViewModel managing notifications UI state, lazy job registration, request revision counter (`requestRevision` / `lastAcceptedRecord`), visible lifecycle ownership (`visibleLifecycleMutex`, `loadingOwnerToken`), `FetchOutcome` mapping, mark-one, mark-all, and destination routing.
2. `feature/communication/src/main/java/com/fairswap/feature/communication/NotificationsScreen.kt`
   - Composable UI rendering top app bar, Back, Mark all read, Refresh / Retry, title/body, safe formatted timestamps, read/unread dots, loading/empty/error states.
3. `feature/communication/src/main/java/com/fairswap/feature/communication/CommunicationUiText.kt`
   - Failure mapping helper mapping notification failures (`InvalidInput` -> `"Invalid request."`) without changing messaging UI text globally.
4. `feature/communication/src/test/java/com/fairswap/feature/communication/NotificationsViewModelTest.kt`
   - 42 unit tests covering bind, session isolation, immediate completion retry lock, independent row mutations, older fetch rejection (R1), superseded visible lifecycle cancellation (R2), retry restoring Realtime (R3), hide-during-fetch cleanup (H1-H4), mark-all read, destination routing, and auth handoffs.
5. `feature/communication/src/test/java/com/fairswap/feature/communication/NotificationsViewModelMutationTest.kt`
   - 17 unit tests covering row/bulk 4 retry combinations (Network/Service), cancellation late results, same-session R3 job cancellation, canceled row failure regressions, distinct account late results, and overlapping cleanup.
6. `feature/communication/src/test/java/com/fairswap/feature/communication/InboxViewModelTest.kt`
   - Shared test fake `FakeFeatureCommunicationRepository` with invocation records, `MutationInvocationRecord`, `CollectorInvocationRecord`, invocation-specific gates/results, active collector tracking, peak collector tracking, and teardown gate.
7. `app/src/main/java/com/fairswap/app/ui/FairSwapApp.kt`
   - Root composable wiring `NotificationsRoute`, handling navigation events from `NotificationsViewModel.events`, and enforcing `canConsumeNotificationEvent` event guard matrix with signed-out Back UI.
8. `docs/ANDROID_COMMUNICATION_MILESTONE.md`
   - Milestone tracking and acceptance contract documentation.

---

## Non-Blocking Backlog Items
- Optional Compose UI animation polish for mark-all-read row transitions.
- Additional locale-specific date formatting options.

---

## Blockers
- None. All mandatory acceptance criteria supported and local gates passing.
