# AGENTS.md — FitCal (AI Food Nutrition Scanner)

This file is the authoritative instruction set for any agent (AI or human) working in this repository. Read it fully before making changes.

---

## 1. Project Overview

**FitCal** is a Kotlin Multiplatform (KMP) app that scans meal photos with a Vision-Language Model (VLM) and estimates ingredients, weights, and macronutrients with an interactive correction workflow.

- **Modules**: `:shared` (HTTP clients, models, VLM logic), `:FitCal-UI` (Compose Multiplatform UI + ads + quotas), `iosApp/` (native iOS wrapper).
- **Release scope**: **Android only for v1.** iOS code stays in the repo but is not a release target.
- **Backend**: Supabase (Postgres + RLS, Auth). The only AI gateway is the Edge Function `supabase/functions/analyze-meal`; it holds the paid provider keys. (The old Cloudflare Worker was removed.) FitCal has its **own Supabase project** `uedtovphilwstzdpzjrk` (EU, since 2026-10-11; the old shared project `mpsqdaptkkasjoepamwl` also hosts other apps, and its history is in `supabase/legacy-shared-project/`). All tables and RPCs live in schema `fitcal` (baseline: `supabase/migrations/0001_fitcal_baseline.sql`); analytics and feedback are documented in `ANALYTICS.md`.
- **VLM Pipeline** (inside the gateway, since 2026-10-23): OpenRouter `google/gemini-3.1-flash-lite` → Gemini 2.5 Flash direct → Groq (`qwen/qwen3.8-27b`) failover. Chosen by `tools/accuracy-bench` (Nutrition5k); re-run it before changing models.
- **Monetization state**: Google AdMob only (banners, forced interstitial at quota, rewarded); test IDs in debug builds. Daily scan quota system live (`ScanQuotaManager`).

## 2. Non-Negotiable Rules

1. **Do not read or expose `.env`** — it contains production API keys. Never commit it.
2. **Do not change monetization behavior** without following Section 5 of this file.
3. **Do not put ads on the Camera/Scan view.** This is a hard product decision.
4. Build must pass: `./gradlew :FitCal-UI:testDebugUnitTest`.

## 3. Existing Features (Do Not Regress)

- Camera capture + on-device image optimization (resize/compress before upload)
- VLM failover sequencing in the gateway (OpenRouter Gemini 3.1 Flash-Lite → Gemini 2.5 Flash → Groq)
- Interactive corrections & local recalculation (~10x faster than re-upload)
- Local food database grounding + Search-to-Swap overlay
- Daily scan quota (`ScanQuotaManager`): currently 3 free scans/day, +2 bonus scans per rewarded ad, +1 free scan/day for permanent accounts (see 5.1)
- Sign-in is optional and never blocks scanning: guests are anonymous Supabase users; creating an account upgrades the same user in place (`SupabaseAuthService`)
- Ad layer: `AdManager` interface + `AndroidAdManager` (AdMob; test units in debug builds)

## 4. Task List

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | Onboarding scan policy (Week 1 vs Week 2+) | **DONE** | Implemented 5 vs 3 in `ScanQuotaManager` |
| 2 | Forced interstitial on scan #4+ | **DONE** | Scan never blocked; routes to `showScanProcessingAd` |
| 3 | AppLovin MAX mediation | **REMOVED** | Owner decision 2026-10-07: AdMob only for launch; MAX was never configured |
| 4 | App Open Ads | **REMOVED** | Owner decision 2026-10-07: dropped for launch (retention over low-value impressions) |
| 5 | Adaptive banners on secondary tabs only | **DONE** | Dashboard, Settings, Library and Guides (owner decision 2026-10-13; not on sensitive guides such as "When to talk to a professional"); zero ads on Camera/Review |
| 6 | Brand-safety ad filtering | **DONE** | Listed in `AdConfig`; all 11 AdMob sensitive categories blocked in the dashboard on 2026-10-13 |
| 7 | Keep `ScanQuotaManagerTest` green (expanded to 9 tests) | **DONE** | 9/9 passing |
| 8 | Production Architecture: Secure Serverless Gateway | **DONE** | Supabase Edge Function `analyze-meal`: verified JWT on every call, server-side quota and allowance, kill switch, API key isolation |
| 9 | Commercial VLM Provider Migration (Pay-As-You-Go) | **DONE** | Gemini 2.0 Flash primary with x-goog-api-key header isolation, Qwen/Groq automated edge failover |
| 10 | FitCal Premium Subscription Paywall (RevenueCat) | **DEFERRED to v1.1** | Owner decision 2026-10-12: launch without Premium. Server side exists (webhook, `fitcal.entitlements`, `/v1/entitlements`, `SubscriptionManager`); missing: RevenueCat SDK, paywall UI, app user ID = Supabase user ID, `REVENUECAT_WEBHOOK_SECRET`. Do not advertise Premium in store text or policies until it ships. |

## 5. THE MONETIZATION TASK (Canonical Specification)

> **⚠️ Where the full source of truth lives:** Before implementing ANY monetization change, read these two documents in full:
> 1. `C:\Users\ahmed\Documents\Hermes-Workspace\App-Monetization-Masterfile.md` — **Sections 2, 4, 5, 6** (formulas, code plan, developer specs, AdMob-vs-MAX).
> 2. Hermes skill `app-business-financial-engineering` (run `skill_view(name='app-business-financial-engineering')`) — the financial model behind every decision.
>
> Deviating from this specification produces rejected changes. Ask before inventing alternatives.

### 5.1 Onboarding Scan Policy (Task 1)

- **Week 1 (first 7 days after install)**: **5 free AI scans/day**. Goal = habit formation. (Scan interstitials follow 5.2a from the first scan, owner decision 2026-10-22.) **Permanent accounts only** (owner decision 2026-10-21): guests get no week-one bonus.
- **Week 2+ (day 8 onward)**: **3 free AI scans/day**. Guests are on this limit from day one.
- Implement in `ScanQuotaManager`: the free limit is higher for users whose `firstInstallDate < 7 days`. Persist `firstInstallDate` in preferences.
- **Account bonus**: permanent accounts (confirmed email or linked Google/Apple identity) get **+1 free scan/day** on top of the above (so 6 in week 1, 4 after). The server is authoritative: `supabase/functions/analyze-meal` computes the allowance from the verified user and ignores the client's value; `consume_scan` clamps at 6 (migration 0007). The client figure is display-only.

### 5.2 Forced Interstitial on Scan #4+ (Task 2)

When the user has exhausted their free daily quota and attempts another scan:

1. **No prompt dialog. No "watch to continue" button. No rewarded ad.**
2. The scan is **allowed** (quota is not hard-blocked), but an **Interstitial Video/Playable ad** is shown **before** the AI processes the photo:
   ```kotlin
   adManager.showScanProcessingAd {
       processMealWithAI(photo)  // runs only after ad closes
   }
   ```
3. When the ad closes/dismisses → AI analysis runs → results display normally.
4. **Policy compliance**: forced ads must be **Interstitials**, NEVER Rewarded (Rewarded requires explicit user opt-in click).
5. Bonus: the ad plays during the "analysis wait" — UX stays smooth.
6. **Ad formats** (owner decision 2026-10-21): the interstitial ad unit allows video and playable (mini-game) creatives. This is set in the AdMob console, not in code. Rewarded stays opt-in only, for the +2 bonus scans.

### 5.2a Scan ad cadence (owner decisions 2026-10-21, updated 2026-10-22)

Goal: as many interstitials for guests as AdMob allows without being flagged. AdMob disallows interstitials after every user action and unexpected or back-to-back interstitials.

- **Who** (2026-10-22): everyone who can be shown ads, guests and signed-in users alike. Premium users never.
- **Every scan, from the first of the day**: an interstitial plays before the scan, the same `showScanProcessingAd { process }` path as 5.2, but only if **at least 90 s** have passed since the last interstitial shown. Inside the cooldown, the scan proceeds without an ad. Past the free limit, 5.2 applies as before.
- **Never**: on app open, on app exit, on the camera screen itself, or two interstitials back to back.
- **Sign-in prompt**: on first open, guests see a one-time card: "Create a free account: more free scans in your first week, plus 1 extra scan every day", with Create account and Not now.

### 5.3 Ad Network (Task 3)

- **Google AdMob only** for launch (owner decision 2026-10-07). AppLovin MAX mediation was removed; revisit only once there is real traffic and a MAX account.
- Production ad unit IDs are in `AdConfig`; debug builds always use Google's test IDs.
- Ad revenue telemetry comes from AdMob `OnPaidEventListener` → `AdTelemetryManager.trackAdRevenue`.

### 5.4 App Open Ads (Task 4)

- **Removed for launch** (owner decision 2026-10-07). Do not reintroduce without the owner's approval.

### 5.5 Banner Ads (Task 5)

- **Adaptive banners ONLY** on passive screens: Macro History, Profile/Settings, Recipe Detail, Library and Guides. Guides marked `adFree` (health conditions, eating disorders) never show one.
- **NEVER** on the Camera/Scan view or the scan results flow.
- Banners are a low-eCPM baseline floor ($0.50–$2.50); they must never block the primary action.

### 5.6 Brand Safety (Task 6)

- In MAX/AdMob dashboards enable **strict category blocking**: Block Gambling, Dating, Politics, Religion, Cosmetic Surgery, Sexual Health, Unverified Apps, Low-quality Clickbait, Weight Loss (diet pills and rapid-loss offers contradict the guides and plan safety limits).
- Allow only: Fitness, Food & Beverage, Sports, Technology, Productivity, Mobile Games.
- Users must never see disturbing/repulsive ads — this is the explicit failure condition.

### 5.8 Required Production Architecture: The Secure Serverless Gateway (Task 8 & 9)

1. **Zero Client-Side Secrets**: Never embed paid commercial API keys (Gemini, Vertex AI, OpenRouter) inside the mobile client or `BuildConfig`.
2. **Edge Proxy Routing**:
   - `Mobile App (KMP) -> HTTPS POST /v1/analyze-meal -> Supabase Edge Function (analyze-meal) -> Commercial VLM API`.
   - Protects against key scraping via APK decompilation (`jadx`) or network interception (`mitmproxy`).
3. **Abuse Prevention & Device Integrity**:
   - Verify requests with device attestation (Firebase App Check, Google Play Integrity, or Apple App Attest).
   - Enforce server-side quota tracking to prevent client preference tampering.
4. **Semantic Response Caching**:
   - Cache common foods and barcode lookups at the edge to reduce duplicate inference costs to $0.00.
5. **Commercial VLM Migration**:
   - Migrate from Google AI Studio Free Tier (15 RPM / 1,500 RPD, model training on user data, zero SLA) to **Google Cloud Vertex AI** or **Gemini 2.0 Flash Pay-As-You-Go** (enterprise SLA, zero data logging, unlimited concurrency).

### 5.9 Recommended Decisions & Next Steps (Task 8, 9, 10)

1. **Model Stack Recommendation**:
   - **Primary VLM** (owner decision 2026-10-23, from `tools/accuracy-bench`): **Gemini 3.1 Flash-Lite via OpenRouter**. On 100 Nutrition5k dishes: 40% mean calorie error and 38% within ±20%, vs 48% / 28% for Gemini 2.5 Flash; Claude Haiku/Sonnet 5.5 and GPT-5 mini over-estimated by ~38–40%.
   - **Secondary Failover**: **Gemini 2.5 Flash** called directly (x-goog-api-key), then Groq.
2. **Gateway Deployment**:
   - The gateway is the Supabase Edge Function `analyze-meal` (deployed with `verify_jwt = false`; it verifies tokens itself so the RevenueCat webhook can reach it).
3. **Subscription Engine (Task 10)**:
   - Integrate **RevenueCat** for FitCal Premium ($4.99/mo or $39.99/yr) offering ad-free unlimited scans, macro export, and personalized calorie planning.

### 5.10 Definition of Done

- [x] All 5 `ScanQuotaManagerTest` tests pass, plus new tests for: Week-1 limit (5), Week-2 limit (3), forced-interstitial trigger on scan #4+ (no dialog).
- [x] `App.kt` gate logic routes: quota available → process; quota exhausted → `showScanProcessingAd { process }`.
- [x] No ad units on Camera/Scan composable.
- [x] ~~App Open ad respects `sessionCount > 3` + 4h cooldown.~~ (App Open ads removed 2026-10-07)
- [x] Deploy secure serverless gateway proxy (Task 8).
- [x] Upgrade VLM pipeline to Vertex AI / Gemini 2.0 Flash Pay-As-You-Go with zero data retention (Task 9).
- [ ] Integrate RevenueCat subscription paywall for FitCal Premium (Task 10) — deferred to v1.1.
- [ ] Merge via PR with the monetization spec referenced in the description.

---

## 6. Verification

```bash
./gradlew :FitCal-UI:testDebugUnitTest   # must be green
./gradlew :FitCal-UI:installDebug        # smoke-test on device/emulator
```