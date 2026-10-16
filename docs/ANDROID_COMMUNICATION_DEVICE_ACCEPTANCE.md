# FairSwap Android — Communication & Notifications Device Acceptance Plan

> **STATUS**: NOT RUN (Device and live staging acceptance are separate follow-up gates).

## Overview
This document outlines the manual verification procedures for validating Android Messages and Notifications functionality on physical devices or emulators using two authorized test accounts in staging environments.

---

## Prerequisites & Setup
- **App Version**: Staging Debug Build (`:app:assembleStagingDebug`)
- **Test Accounts**:
  - Account A: Primary Seller/Buyer
  - Account B: Counterparty Buyer/Seller
- **Network Tools**: Toggleable Wi-Fi / Cellular or Android Emulator Network Controls.

---

## Test Scenarios & Verification Matrix

### 1. Account Navigation & In-App Entry
- **Steps**:
  1. Launch app on physical device.
  2. Sign in as Account A.
  3. Navigate to `AccountScreen`.
  4. Tap `Messages` -> Verify `InboxScreen` displays conversation lists with filters (All, Buying, Selling).
  5. Return to `AccountScreen`.
  6. Tap `Notifications` -> Verify `NotificationsScreen` loads in-app notifications.
- **Expected Outcome**: Navigation routes load without crash; user items render cleanly with exact read/unread status.

### 2. Notifications Flow (Refresh, Mark Read, Navigation)
- **Steps**:
  1. Open `NotificationsScreen` as Account A.
  2. Tap `Refresh` in top bar -> Verify network reload occurs and clears loading indicator.
  3. Tap an unread notification -> Verify row transitions from unread dot to read state, and navigates to the destination (`ConversationRoute` or `MyListingsRoute`).
  4. Tap `Mark all read` -> Verify all unread indicators update to read state.
  5. Tap `Back` -> Returns to `AccountScreen`.
- **Expected Outcome**: State updates atomically without stale overwrites; link destinations route securely.

### 3. Account Switching & Isolation
- **Steps**:
  1. While on `NotificationsScreen` as Account A, navigate to `AccountScreen` and tap `Sign Out`.
  2. Verify screen renders the signed-out state ("Please sign in to view notifications.") with a working `Sign In` action.
  3. Sign in as Account B.
  4. Open `NotificationsScreen` -> Verify ONLY Account B's notifications render.
- **Expected Outcome**: Zero leak of Account A's notifications or private state to Account B.

### 4. Network Offline & Reconnect Resilience
- **Steps**:
  1. Enable Airplane Mode / disable device network connection.
  2. Tap `Refresh` on `NotificationsScreen` -> Verify user-facing error ("You're offline or the network is unavailable. Try again.") and `Retry` action appear.
  3. Re-enable network connection.
  4. Tap `Retry` -> Verify error clears and notifications reload successfully.
- **Expected Outcome**: App gracefully presents safe feedback when offline and restores full functionality upon reconnect.

### 5. App Foreground / Background & Realtime Lifetime
- **Steps**:
  1. Open `NotificationsScreen` on Account A.
  2. Send a new notification to Account A from Account B (via backend or CLI tool).
  3. Verify notification appears via Realtime backfill without manual refresh.
  4. Press Home button to background the app.
  5. Send another notification from Account B.
  6. Bring app back to foreground -> Verify notification reconciles authoritatively.
- **Expected Outcome**: Realtime observation is strictly active while visible and reconciles authoritatively upon foregrounding.

---

## Summary Checklist
- [ ] Account A / Account B Isolation (NOT RUN)
- [ ] Notifications Flow & Routing (NOT RUN)
- [ ] Offline / Retry Flow (NOT RUN)
- [ ] Realtime Invalidation & Rebind (NOT RUN)
- [ ] Staging Backend Verification (NOT RUN)
