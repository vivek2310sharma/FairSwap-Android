# FairSwap Android — Release Validation Pass RV-01D Report

Milestone ID: ANDROID-RV-01
Pass ID: RV-01D
Environment: FairSwapTest STAGING ONLY (`https://wpppxlnbzqynvhpeuavd.supabase.co`)
Application ID: `com.fairswap.app.staging`
Branch: `feat/android-web-brand-parity`
Date: October 16, 2026

---

> **Correction Note (Added during RV-01E):**
> - **Search Reset Classification:** Re-classified as **NOT VERIFIED in RV-01D** due to lack of logged uiautomator XML evidence supporting the full 5 -> 1 -> 5 stage cycle.
> - **Hindi Language Coverage:** Home Hindi and Listing Detail Hindi are re-classified as **NOT VERIFIED in RV-01D** due to missing evidence logs for Devanagari UI rendering on those screens. English restoration was observed on Account screen, but was not proof that Hindi was actively selected across screens during RV-01D.
> - **Buyer/Seller Preparation:** Re-classified as **PLAN ONLY**.

---

## 1. Environment & Emulator Details

- **Workspace Path:** `/Users/vivekkumar/FairSwap/FairSwap-Android-Brand-Parity`
- **Branch:** `feat/android-web-brand-parity`
- **Staging Config Verification:**
  - `applicationIdSuffix = ".staging"` -> `com.fairswap.app.staging`
  - `SUPABASE_STAGING_URL` -> `https://wpppxlnbzqynvhpeuavd.supabase.co` (FairSwapTest Staging Instance)
  - `SUPABASE_STAGING_PUBLISHABLE_KEY` -> Configured in `local.properties`
- **Emulator Profile:** `medium_phone` (`emulator-5554`, Android 36, 1080x2400 dpi 420, ARM64-v8a)

---

## 2. Search Reset Verification (`emulator-5554`)

- **Status:** **NOT VERIFIED in RV-01D** (Deferred to RV-01E for raw uiautomator XML evidence logging).

---

## 3. English / Hindi Runtime Check

- **Account Screen Language Radio Button:** **OBSERVED**
- **Home & Listing Detail Hindi UI Rendering:** **NOT VERIFIED in RV-01D** (Deferred to RV-01E for raw uiautomator XML evidence logging).

---

## 4. Buyer / Seller Staging Testing Preparation

- **Status:** **PLAN ONLY** (Asset and prerequisite outline recorded).

---

## 5. Summary & Final Status

| Validation Item | RV-01D Status |
| :--- | :--- |
| **Search Input Clearing & Reset** | **NOT VERIFIED in RV-01D** |
| **Home Screen Hindi UI Rendering** | **NOT VERIFIED in RV-01D** |
| **Listing Detail Hindi UI Rendering** | **NOT VERIFIED in RV-01D** |
| **English Language Restoration** | **OBSERVED (ACCOUNT ONLY)** |
| **Buyer/Seller Test Preparation** | **PLAN ONLY** |
| **Confirmed Code Defects** | **NONE** |

**Experience v2 frontend implementation complete — runtime verification deferred.**
