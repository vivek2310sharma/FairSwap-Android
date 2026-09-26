# Android-1B auth callbacks

Branch: `feat/android-auth-deeplinks` (based on updated `main`)

## Callback contract

Staging uses `fairswap-staging://auth/confirm` and `fairswap-staging://auth/recovery`.
Production builds use the distinct `fairswap://auth/confirm` and
`fairswap://auth/recovery` values. The two paths identify which UI follows a
successful PKCE code exchange. The Android intent filter, `BuildConfig` values,
and Supabase Auth config use the same flavor values.
The app records which email flow it most recently requested and rejects a
callback on the other path. This marker contains no token or auth code.

Before staging email flows can work, add both exact staging URLs to FairSwapTest
Supabase Auth's redirect URL allowlist. Keep the existing email templates using
Supabase confirmation/recovery URLs. Do not change the production project during
Android-1B development. A link must be opened in the same app installation that
requested it, because its PKCE verifier is stored locally.

## Manual FairSwapTest checks

1. Install the staging debug app on a device with a staging publishable key.
2. Sign up using a fresh email. Open the confirmation email on that device and
   verify the app loads the account or required policy review. Restart the app
   and verify the session restores.
3. Sign out, request a password reset, and open its email on the same device.
   Verify the app shows **Set new password**, rejects seven characters and a
   mismatched confirmation, then accepts a valid new password.
4. Sign out and sign in with the new password. Verify the old password fails.
5. Open an expired or already used link, and an incomplete callback URL. Verify
   the app shows a safe error and does not reveal callback parameters.
6. Verify a recovery link goes to the password form and a confirmation link
   does not. Verify a production-scheme link does not open the staging app.

The automated Gradle gate does not exercise email delivery, the FairSwapTest
redirect allowlist, or on-device intent delivery; those require the manual checks.
