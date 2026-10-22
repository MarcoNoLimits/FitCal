# FitCal — where to pick up

## Sugar and fibre: hidden for launch (owner decision 2026-10-23)
Built but switched off with `SHOW_SUGAR_AND_FIBRE = false` (`nutrition/DailyLimits.kt`). The app
still stores sugar and fibre per food when a barcode label has them; nothing shows. The gateway
prompt does not ask the AI for them (the sugar/fibre prompt was reverted, so v5 stays as deployed).
Research behind the decision: photo apps under-estimate calories and fat (NIH, 102 meals, 2026);
no study validates sugar from photos; fibre had one small promising study. Before turning it on:
- [ ] Label test: scan ~20 packaged foods by photo and compare sugar and fibre with the labels.
- [ ] Restore the gateway prompt fields (`git show 6fc4d43 -- supabase/functions/analyze-meal/index.ts`) and deploy.
- [ ] Mark photo/typed values "≈ est."; barcode values stay exact.
- [ ] Diabetes line in the guide and the card: an estimate for general awareness; rely on your
      glucose meter and care team. Never market FitCal for diabetes (EU MDR intended purpose).
- [ ] Settings switch "Show nutrition notes" (on by default) that hides the amber notes.
- [ ] Line under the sugar bar: "Includes natural sugar from fruit and milk."


## Spend protection (launch sprint WP6, 2026-10-22): owner tasks
Migration 0008 is applied and `analyze-meal` v5 is deployed (2026-10-22). Remaining, in order:

1. [x] **Apply migration `0008_cleanup_and_ssv.sql`** (done 2026-10-22) (SSV claims table and RPCs, `pg_cron` job `fitcal-cleanup-anonymous-users`). Check afterwards: `select jobname, schedule from cron.job;` shows the job, daily at 03:17 UTC.
2. [x] **Deploy `analyze-meal`** (done 2026-10-22, version 5) (it now includes `ssv.ts`). Leave `REWARD_REQUIRE_SSV` unset (= false): `/v1/reward/ad-earned` keeps granting as today, and SSV callbacks are only verified and recorded.
3. [ ] **AdMob SSV callback URL** (AdMob → Apps → FitCal → Ad units → the rewarded unit → *Server-side verification*):
   - Callback URL: `https://uedtovphilwstzdpzjrk.supabase.co/functions/v1/analyze-meal/v1/reward/ssv`
   - Press *Verify URL*. A valid signed test callback answers 200 with `{"status":"ignored","reason":"no_user"}` (the test has no FitCal user). 403 means a signature problem; 503 means Google's keys could not be fetched.
   - Save. It can take up to 24 h to apply.
4. [ ] **Check real callbacks** after a rewarded view on a build that has this release: function logs show `[reward-ssv] http=200 outcome=recorded`, and `select * from fitcal.reward_ssv_claims order by created_at desc limit 5;` has the row with your user ID.
5. [ ] **Turn SSV on:** `supabase secrets set REWARD_REQUIRE_SSV=true`. From then on only AdMob's callback grants the +2 (same daily cap of 10), once per `transaction_id`; `/v1/reward/ad-earned` just confirms it to the app (425 if the callback is late; the app keeps its optimistic count until the next quota sync). Roll back by setting it to `false`.
6. [ ] **Budget alerts** (provider consoles):
   - OpenRouter: Settings → Credits/Keys → set a credit limit on the key `analyze-meal` uses.
   - Google Cloud (Gemini): Billing → Budgets & alerts → a monthly budget on the Gemini project with email alerts at 50/90/100 %. Also cap the Gemini API quota (requests per day) under APIs & Services → Quotas.
   - Groq: Settings → Limits → set a monthly spend/usage limit (only if `GROQ_API_KEY` is ever added; see the note below on Groq and the privacy policy).

## Launch review (2026-10-12)
Code-side fixes done this session:
- **Premium deferred to v1.1** (owner decision). Removed Premium/subscription/RevenueCat claims from the in-app policy, `site/privacy.html`, the delete-account dialog and the store description. Deleted the `SubscriptionManager.purchasePlan(productId)` overload that granted Premium locally without payment.
- Groq removed from the policy, consent screen and Data safety notes, because it isn't configured. **If you add `GROQ_API_KEY`, add Groq back to all three first.**
- **R8 on:** `isMinifyEnabled` + `isShrinkResources`; Ktor/Sentry keep rules added. Release AAB builds, and the release APK launches on the emulator without crashing. A full click-through of the release build is still needed (see the device test below).

Still blocking launch (only you can do these):
1. [x] **Upload keystore** (done 2026-10-12: `C:/Users/ahmed/keys/fitcal-upload.jks`, alias `fitcal`, upload-key SHA-1 `AF:EF:50:74:40:53:97:C4:A5:BD:DE:4A:3E:82:E4:A1:6C:9A:14:7A`). Original note: create it, add `RELEASE_KEYSTORE_PATH/PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` to `local.properties`, and back it up off this PC. Without it the release build is signed with the **debug key**.
2. [ ] **DNS `CNAME fitcal → cname.vercel-dns.com`.** `site/` is already deployed to Vercel project `fitcal-site` (2026-10-12) with `fitcal.tecaa.xyz` attached; it goes live once the CNAME resolves. The `*.vercel.app` URLs stay behind Vercel login on purpose, and the custom domain is public. Redeploy whenever `site/` changes.
3. [ ] **MX conflict:** `tecaa.xyz` has both Zoho and Name.com MX records at priority 10. Keep one provider.
4. [ ] **Play Console:** closed test (12 testers × 14 days if the account is personal and new), Data safety, Health declaration, 18+, store assets.
5. [ ] **Google sign-in:** OAuth clients (Web + Android with upload *and* Play App Signing SHA-1), Supabase Google provider, `GOOGLE_WEB_CLIENT_ID` in `local.properties`.
6. [ ] `SENTRY_DSN` in `local.properties`; upload `mapping.txt` per release now that R8 is on.
7. [ ] Real-device test of the **signed release** build: consent, age gate, scan, quota wall, interstitial, rewarded, account create/reset, delete.
8. [ ] Supabase Pro on launch day (backups, no auto-pause). Budget caps on Gemini and OpenRouter.
9. [x] Old worktree `.claude/worktrees/zealous-bhabha-fb5c78` removed and its edits discarded (owner decision, 2026-10-12).

Scope decision: **Android-only for v1.** iOS code stays in the repo but isn't a release target.

## Done
**2026-10-06**
- Live `analyze-meal` edge function is at version 7:
  - medical-safety prompt;
  - Groq fallback switched to `qwen/qwen3.8-27b`;
  - client can't pick the AI model;
  - plate size validated;
  - server-side allowance with +1 scan/day for accounts.
- Migration `0007` applied (`consume_scan` cap raised to 6).
- `worker/` removed. Only your local `worker/.dev.vars` remains; delete it once the keys are confirmed in Supabase function secrets.

**2026-10-07**
- Removed AppLovin MAX and App Open ads. Ads are AdMob only:
  - banner, interstitial at quota, and rewarded all verified on the emulator;
  - AdMob paid-event revenue now feeds `AdTelemetryManager`.
- Removed the "Provider/Diagnostics" card from Ads & Perks. It showed "Official Test Units" to real users.
- Privacy policy (in-app and `privacy.html`) now names Supabase (EU) as the backend and drops Cloudflare and AppLovin; dated October 2026.
- Docs updated: `AGENTS.md`, `STORE-SUBMISSION-CHECKLIST.md`, `ADS-OPERATIONAL-LINKS.md` (rewritten for AdMob), `SECURITY-REMEDIATION.md` (update note), `FITCAL-BACKEND-TASK.md` (marked superseded), plus code comments.
- Tests: 126 passing (74 app + 52 shared).

**2026-10-07 (later), live: edge function v8, migrations 0008 + 0009:**
- The live gateway was missing two routes the app calls; the old Worker had them:
  - `/v1/account/delete`: Play requires working in-app deletion, so this one is critical;
  - `/v1/reward/ad-earned`: rewarded ads never actually granted scans server-side.

  Both are added to `supabase/functions/analyze-meal/index.ts`, backed by migration `0008_account_delete_and_reward_claim.sql`.
- Deletion is safe for this shared Supabase project:
  - FitCal rows are deleted in one transaction;
  - the login itself is deleted only if no other app (`yinyang`, `crm`, storage) uses it.
- Reward claims are capped at 10 bonus scans/day, atomically in the database.
- Settings shows "Deleting…" and an error if deletion fails. Before, it did nothing. Consent is withdrawn only after the server confirms.
- `site/` (`index.html`, `privacy.html`, `app-ads.txt`) is ready to host; `privacy.html` moved there.
- Policy now has a `#delete` section (the web deletion link Play asks for). Dropped a stale "cached for 30 days" claim, since the gateway no longer caches.

- Tested end to end in production with throwaway guests (since deleted):
  - no token gives 401;
  - rewards grant 2, 4, 6, 8, 10, then 429, even with 10 parallel claims;
  - delete removes meals, water, quota and the login, and the old token then gets 401;
  - your CRM admin and yinyang user were untouched.
- `0009` fixes a bug the test found: the `crm.handle_new_user` trigger gives every login a CRM `member` row, which made every FitCal login look "used elsewhere" so it was never deleted. Untouched default rows are now ignored.

**2026-10-10 (later), live: edge function v10, migrations 0011 + 0012:**
- **Rename finished:** schema `fitter` was renamed to `fitcal`. Before, the app talked to `fitcal`, which the API never exposed, so meal, profile and water sync had **never worked** in production. Now tested end to end: upserts, soft delete, quota RPCs, isolation between users.
- Created the missing `fitcal.entitlements` table. The RevenueCat webhook now writes it and returns 503 on failure, so RevenueCat retries instead of losing the purchase.
- Account deletion's "login used by another app?" check now skips `fitcal` (it would otherwise never delete logins after the rename).
- Dropped the leftover `public.consume_scan` / `get_scan_quota` / `grant_bonus_scan` functions.
- **User feedback:** thumbs up/down with reasons under every AI result, plus Settings → "Send feedback or report a problem". Stored in `fitcal.feedback` via `POST /v1/feedback`; read it with `select * from fitcal_analytics.feedback_inbox;` and `fitcal_analytics.scan_ratings`.
- [ ] **Your step:** Dashboard → Data API → Exposed schemas: remove `fitter` (it's now an empty placeholder), then let Claude run `drop schema fitter;`.

**2026-10-10, live: edge function v9, migration 0010 — analytics (see `ANALYTICS.md`):**
- The old telemetry had never stored a single event (wrong column names). Events now go app → gateway `POST /v1/events` → `fitter.log_events`. They are queued on the device, survive restarts, and upload every 60 s, on background and at boot.
- Full funnel: consent, app open/sessions, screen views, scan started/succeeded/failed, review corrections, meal logged, quota wall, rewarded ads, auth, settings. The gateway adds its own AI-provider latency/fallback events.
- Dashboards: `fitcal_analytics.*` views (SQL editor only).
- Settings → Privacy → "Share usage analytics" switch (opt-out). Privacy policy (in-app and `site/`) updated.
- Sentry crash reporting (`sentry-android-core`) is wired but **off until `SENTRY_DSN` is set in `local.properties`**.
- Tested end to end in production with a throwaway guest (since deleted).

## ⚠ Other apps on this Supabase project (not FitCal code, your call)
- Every CRM table (`companies`, `contacts`, `deals`, `tasks`, `activities`, `profiles`, `pipeline_stages`) has RLS policies allowing **all operations for any `authenticated` user**. Anonymous FitCal guests count as `authenticated`.
- It isn't reachable today: `crm` is not in Exposed schemas, so the API answers 406. It becomes a full read/write leak the moment `crm` is exposed. Fix: scope those policies to real CRM users (for example a `crm.profiles.role` check, or excluding `auth.jwt()->>'is_anonymous' = 'true'`).
- `on_auth_user_created` creates a CRM `member` profile for every FitCal guest, which pollutes the CRM. Consider skipping anonymous users in `crm.handle_new_user()` (`if NEW.is_anonymous then return NEW; end if;`).
- Longer term, give FitCal its own Supabase project so apps can't affect each other's logins.

## ✅ FitCal moved to its own Supabase project (2026-10-11/12)
**FitCal** `uedtovphilwstzdpzjrk` (eu-west-1, Free plan, $0).
- Baseline schema in `supabase/migrations/0001_fitcal_baseline.sql`; the old shared-project history is in `supabase/legacy-shared-project/`.
- Gateway deployed; secrets `GEMINI_API_KEY`, `OPENROUTER_API_KEY`. **Groq skipped by choice**: the failover runs Gemini then OpenRouter. Add `GROQ_API_KEY` later for a third fallback.
- Dashboard: `fitcal` exposed, anonymous sign-ins, manual linking, confirm email, SMTP via Resend, code templates with the logo.
- Tested end to end on 2026-10-12, then cleaned up:
  - app sync and quota RPCs;
  - user isolation; direct writes refused;
  - events, feedback, rewards;
  - **real Gemini scan (3.1 s)**;
  - account deletion.
- Old shared project cleaned up (migration `legacy-shared-project/migrations/0013`):
  - removed FitCal's schemas and 5 test guests;
  - kept the 11 CRM/yinyang users and their data;
  - removed `fitcal`/`fitter` from Exposed schemas.
- [x] Old project fully cleared (2026-10-12): `analyze-meal` function deleted; its 6 FitCal secrets deleted; custom SMTP off, which also reset its templates. Leftover: delete the old project's Resend key in Resend.
- [x] AI provider keys removed from `local.properties`.
- [x] SMTP works (real code emails delivered on 2026-10-12).
- [x] Auth settings checked and fixed on 2026-10-12: Email OTP length **6** (it was 8, which the app can't accept); password min 8 with lowercase, uppercase and digits (matches `AuthValidation.kt`); Site URL `https://fitcal.tecaa.xyz`; code-free subjects. Leaked-password protection needs the Pro plan, so it's skipped. Email rate limit 30/h.
- [ ] Real email test: Claude sends a code email to an address you choose.

## Production setup (only you can do these)
Domain: **`tecaa.xyz`** (Name.com). FitCal lives at **`fitcal.tecaa.xyz`**. Contact: **`fitcal@tecaa.xyz`**. Auth emails are sent from **`no-reply@tecaa.xyz`** via Resend. DNS records go in Name.com → Manage DNS Records; the Host field is relative (`fitcal`, not `fitcal.tecaa.xyz`).

1. [ ] **Website.** Add DNS `CNAME fitcal → cname.vercel-dns.com`, then deploy `site/` to Vercel with `fitcal.tecaa.xyz` as its domain (Claude can do the Vercel part if you say so). Check that `https://fitcal.tecaa.xyz/app-ads.txt` loads.
2. [ ] **Receive mail at `fitcal@tecaa.xyz`**, the one FitCal contact address: support, privacy and deletion requests, the Play Console developer email, and DMARC reports. Set it up in Name.com → tecaa.xyz → Email Forwarding → `fitcal` → your inbox. Name.com adds the MX records itself. Reviewers do email it.
3. [x] **Resend domain verified** (2026-10-10): root domain `tecaa.xyz` (not `fitcal.tecaa.xyz`), region eu-west-1. DKIM `resend._domainkey` and SPF/MX on `send.tecaa.xyz` are live.
   - [ ] **Add DMARC** in Name.com: Host `_dmarc`, type TXT, value `v=DMARC1; p=none; rua=mailto:fitcal@tecaa.xyz`. It matters more on `.xyz`. After ~2 clean weeks, change `p=none` to `p=quarantine`.
   - [ ] **Supabase → Authentication → Emails → SMTP Settings** (you enter the key; Claude never handles it): enable custom SMTP, host `smtp.resend.com`, port `465`, username `resend`, password = a Resend API key with **sending access only** for `tecaa.xyz`, sender email `no-reply@tecaa.xyz`, sender name `FitCal`.
4. [ ] **Auth settings** (dashboard only; there's no API access from here). The repo copy is `supabase/config.toml`.
   - Authentication → Sign In / Providers → Email: **Confirm email ON**, **Secure email change ON**, Email OTP length **6**, expiry **3600**.
   - Same page → Passwords: minimum length **8**, requirements **lowercase, uppercase letters and digits**. Also turn on **leaked password protection** (the security advisor flags it).
   - Authentication → Sign In / Providers: **Allow manual linking ON** (needed for Google linking to a guest).
   - Authentication → URL Configuration: Site URL `https://fitcal.tecaa.xyz`.
   - Authentication → Emails → Templates: paste `supabase/templates/email_change.html` into **Change email address** — superseded: see the new-project section above. The default templates send links, but the app asks for a 6-digit code, so **account creation and password reset don't work until this is done**.
   - Authentication → Rate limits: raise "emails per hour" from the default to about 100 once SMTP is on.
   - [x] ~~add `fitcal` to Exposed schemas~~ (done). [ ] Remove `fitter` from Exposed schemas, then Claude drops the empty placeholder.
   - Then let Claude run a real end-to-end test: guest → create account with a code → sign out → reset password.
5. [ ] **Play Console:**
   - privacy policy `https://fitcal.tecaa.xyz/privacy.html`;
   - deletion URL `…/privacy.html#delete`;
   - developer website `https://fitcal.tecaa.xyz/`;
   - Data safety;
   - Health apps declaration (Nutrition and weight management);
   - target audience 18+.
6. [ ] **AdMob:** set the app's developer website once it's linked to the Play listing, then wait up to 24 h for app-ads.txt verification.
7. [ ] Google OAuth clients: Web, plus Android with debug and release SHA-1. Put `GOOGLE_WEB_CLIENT_ID` in `local.properties`.
8. [ ] Do one real scan on a device to confirm the edge function works end to end.
9. [ ] Decide on the Gemini model. `gemini-2.5-flash` is restricted to existing users; Google recommends `gemini-3.8-flash`. Changing it affects cost and output.
10. [ ] **WP7 guest ads (2026-10-21):**
  - AdMob console → Apps → FitCal → Ad units → the interstitial unit (`ca-app-pub-6111686385218385/4814322830`) → allow **video** and **interactive (playable)** ads. No code change is needed.
  - Deploy `supabase/functions/analyze-meal` (with the new `allowance.ts`) so guests get 3 scans/day from day one and accounts 5 + 1 in week one, 3 + 1 after. Until it is deployed, the server still gives guests 5 in week one, which is more than the app shows, not less.
  - Optional check: `deno test supabase/functions/analyze-meal/allowance_test.ts`.

## Add (backlog, in priority order)
1. **Premium purchase:**
   - add the RevenueCat SDK and a paywall screen;
   - ~~create the `entitlements` table the gateway reads~~ (done in migration 0011, `fitcal.entitlements`);
   - set RevenueCat's app user ID to the Supabase user ID (the webhook ignores anonymous RevenueCat IDs);
   - set `REVENUECAT_WEBHOOK_SECRET`.

   Premium is unbuyable today.
2. **Crash reporting.** Wired up (Sentry); only needs `SENTRY_DSN` in `local.properties`. See `ANALYTICS.md`.
3. **Abuse and spend protection:**
   - provider budget caps (OpenRouter credit limit, Gemini budget alert);
   - AdMob server-side verification (SSV) for rewarded ads, so `/v1/reward/ad-earned` can only be claimed after a real ad. Today the 10/day cap limits the damage;
   - a rate limit on `/v1/recalculate`;
   - a scheduled cleanup of anonymous users with no data older than 30 days;
   - later, Play Integrity checks.
4. **Barcode scanning.** Exact data for packaged foods, cheap to run, top competitor feature.
5. **Home-screen widgets** (Android first, Jetpack Glance):
   - *Today* widget: calorie ring + protein/carbs/fat remaining, read from the local meal store (works offline, no network);
   - *Quick scan* widget/shortcut: one tap opens the camera straight from the home screen;
   - *Water* widget: +1 glass button that writes through the same repository as the dashboard;
   - refresh on meal logged / water changed / midnight, and respect the privacy rules (no data on the lock screen unless the user opts in);
   - add `widget_added`, `widget_removed` and `widget_tap` analytics events (see `ANALYTICS.md`);
   - later: iOS WidgetKit once iOS is a release target.
6. **Feedback follow-ups:** a simple way to read and answer `fitcal_analytics.feedback_inbox` (email reply or a small admin page), and the Play in-app review prompt after a few positive scan ratings.
