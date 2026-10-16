# FairSwap Android Experience v2 Closure Matrix
Milestone: ANDROID-V2-CLOSE-01

| Requirement ID | Status | Source evidence | Remaining blocker |
|---|---|---|---|
| **V2-01: Official branding and Home, Saved, Sell, Chats, You navigation** | Implemented | `app/src/main/java/com/fairswap/app/ui/FairSwapApp.kt`, `core/designsystem/src/main/java/com/fairswap/core/designsystem/components/Wordmark.kt`, `AppBottomBar.kt` | None |
| **V2-02: Home discovery, search, filters and listing navigation** | Implemented | `feature/discovery/src/main/java/com/fairswap/feature/discovery/DiscoveryScreen.kt` | None |
| **V2-03: Listing Detail with real messaging, sharing, saving, reporting and clearly separated future features** | Implemented | `feature/listingdetail/src/main/java/com/fairswap/feature/listingdetail/ListingDetailScreen.kt` | None |
| **V2-04: Sell and My Listings with existing listing lifecycle actions** | Implemented | `feature/sellerlistings/src/main/java/com/fairswap/feature/sellerlistings/MyListingsScreen.kt` | None |
| **V2-05: Saved Favourites with supported mutation and error feedback** | Implemented | `feature/favourites/src/main/java/com/fairswap/feature/favourites/FavouritesScreen.kt` | None |
| **V2-06: Chats and Notifications using existing functional routes** | Implemented | `FairSwapApp.kt`, `ThreadScreen.kt` | None |
| **V2-07: Account, Authentication and public legal/help navigation** | Implemented | `AccountScreen.kt`, `AuthScreen.kt` | None |
| **V2-08: Seller reporting/blocking and bilingual India-specific Safety Center** | Implemented | `SafetyCenterScreen.kt`, `SellerProfileScreen.kt` | None |
| **V2-09: English/Hindi localization for essential launch journeys** | Implemented | Localized hardcoded strings mapped to `core/designsystem/src/main/res/values/strings.xml` and `values-hi/strings.xml` across all launch screens. Final corrections applied to `ListingDetailScreen.kt`, `ThreadScreen.kt`, `NotificationsScreen.kt`, and `FairSwapApp.kt`. | None |

## Final V2-09 Integration Evidence (Final Session Corrections)

1. **Listing Detail Planned Features Localized:**
   - Source: `feature/listingdetail/src/main/java/com/fairswap/feature/listingdetail/ListingDetailScreen.kt`
   - Resources: `planned_feature_make_offer`, `planned_feature_buy_now`, `planned_feature_delivery`, `planned_feature_disputes` in `core/designsystem` (`values/strings.xml` and `values-hi/strings.xml`).
2. **Chat Conversation Fallback Title Localized:**
   - Source: `feature/communication/src/main/java/com/fairswap/feature/communication/ThreadScreen.kt`
   - Resources: `chats_title` ("Messages" / "संदेश") in `core/designsystem` (`values/strings.xml` and `values-hi/strings.xml`).
3. **Notifications Screen Localized:**
   - Source: `feature/communication/src/main/java/com/fairswap/feature/communication/NotificationsScreen.kt` & `FairSwapApp.kt`
   - Resources: `notifications_title`, `notifications_mark_all_read`, `notifications_empty` in `core/designsystem` (`values/strings.xml` and `values-hi/strings.xml`).
4. **Notifications Signed-Out State Localized:**
   - Source: `app/src/main/java/com/fairswap/app/ui/FairSwapApp.kt`
   - Resources: `notifications_sign_in_required` ("Sign in to view notifications." / "नोटिफिकेशन देखने के लिए साइन इन करें।") in `core/designsystem` (`values/strings.xml` and `values-hi/strings.xml`).

## Final Status
**Experience v2 frontend implementation complete — runtime verification deferred.**

All nine frozen frontend requirements are implemented at the source level. Unsupported capabilities are honestly represented as "In Development". The staging APK compiles successfully.
