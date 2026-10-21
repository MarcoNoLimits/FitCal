# Launch sprint: features for v1 (free launch, no Premium)

Owner decisions, 2026-10-21:
- Launch without Premium or any Google Play subscription. Premium stays deferred to v1.1 (AGENTS.md Task 10). Don't mention Premium in UI or store text.
- Build these for launch: WP1 to WP7 below.
- Commits must not carry AI attribution: no `Co-Authored-By` trailer and no session links.

Read `AGENTS.md` first. Its rules win over this file: no ads on the camera or review screens, no monetization changes outside §5, and keep `./gradlew :FitCal-UI:testDebugUnitTest` green.

## How to run this

Run it on the owner's computer, where the Android SDK and the phone are. The cloud environment can't reach `dl.google.com`.

1. Start from up-to-date `main`.
2. Run one agent per work package, each in its own git worktree and branch (`launch/wp1-errors`, `launch/wp2-barcode`, and so on).
3. The packages below are split so they mostly touch different files. Where two touch the same file, the overlap is listed.
4. Each agent must, before it hands back:
   - write unit tests for its pure logic, next to the existing tests in `FitCal-UI/src/commonTest` and `shared/src/commonTest`;
   - run `./gradlew :FitCal-UI:testDebugUnitTest` and get it green;
   - run `./gradlew :FitCal-UI:assembleDebug` and get it compiling.
5. Merge into `main` in this order, rebuilding and re-running the tests after each merge: WP1, WP4, WP5, WP3, WP2, WP6, WP7. WP3, WP6 and WP7 all edit `supabase/functions/analyze-meal/index.ts`; WP7 also edits the scan gate that WP3 reuses.
6. After the last merge:
   - run `./gradlew :FitCal-UI:installDebug` with the phone connected;
   - smoke-test the checklist at the end of this file.
7. Don't deploy edge functions or apply migrations to the live Supabase project without the owner's go-ahead. Write the files; the owner deploys.

## WP1 · Friendly scan errors, quieter Sentry (S)

Files: `ui/screens/camera/CameraScreen.kt` (error state), `androidMain/.../CrashReporting.android.kt`.

The work:
- On "Could not analyze image", release builds show a short plain message and the existing Retake / Resend buttons:
  - "We couldn't read this photo. Check your connection and try again."
  - with a separate wording for "no connection".
- The "API DIAGNOSTICS LOG" box shows only when `isDebugBuild` is true.
- Sentry `beforeSend`: drop events whose root cause is a network-unavailable error (`UnknownHostException`, `ConnectException`, `SocketTimeoutException`, "Unable to resolve host"). They're expected offline, not bugs. Example: FITCAL-2, a token refresh made while the phone was offline.

Done when:
- a release build never shows raw API text;
- a unit test covers the error-message mapping, and another covers the Sentry filter predicate, extracted as a pure function.

## WP2 · Barcode scanner (M)

Files:
- `CameraScreen.kt`: the capture UI, a different region from WP1;
- new `ui/screens/camera/barcode/*`;
- new `shared/.../api/OpenFoodFactsClient.kt`;
- `App.kt` and `NavRoutes.kt`, for one route.

The work:
- Add a mode strip on the camera screen: **Scan food · Barcode**. If WP3 has merged first, add **Describe** too.
- Scan with ML Kit `barcode-scanning` (bundled model, on-device, free).
- Look the product up with Open Food Facts:
  - `GET https://world.openfoodfacts.org/api/v2/product/{code}.json`;
  - send a `User-Agent: FitCal/1.0 (contact email)` header, as their API terms require;
  - read `product_name`, `nutriments.*_100g` and `serving_quantity`.
- Turn the product into a one-item `NutritionResponse`:
  - weight = the serving size, or 100 g;
  - macros scaled from the per-100g values.
- Open the existing review screen with it, so the weight can be edited and recalculated locally.
- Not found: say so, then offer "Take a photo instead" or "Add it manually".
- Barcode lookups **do not** use an AI scan, don't touch the quota, and show no interstitial.
- Cache successful lookups locally by barcode.

Done when:
- the per-100g to serving scaling and the OFF JSON parsing (found, not found, missing nutriments) are unit-tested;
- the camera asks for permission once and works for both modes.

## WP3 · Type a meal, type a fix (M)

Files:
- `supabase/functions/analyze-meal/index.ts`: new route;
- `ReviewScreen.kt`: the text-fix field;
- a new describe screen;
- `App.kt` and `NavRoutes.kt`;
- `GatewayNutritionClient.kt`.

The work:
- **Gateway:** add `POST /v1/analyze-text`, which takes `{ description }` and returns the same `NutritionResponse` as a photo scan.
  - It uses the existing system prompt with no image, and the existing failover.
  - It consumes **one scan** through `consume_scan`, with the same server-side quota as photos.
  - It caps the description length at 500 characters.
- **Client:** add a "Describe your meal" screen with a text field and an example placeholder ("2 eggs, toast with butter, black coffee").
  - It goes through the same scan gate as a photo: quota available means process; quota exhausted means `showScanProcessingAd { process }` (AGENTS.md §5.2).
  - Then it opens the review screen.
- **Review screen:** add a field "Tell FitCal what's off" (for example "it was fried in butter"). It sends the items plus the hint to `/v1/recalculate`, which is already capped per day, and replaces the items with the result.

Done when:
- the client request building is tested;
- the gateway rejects empty or too-long text;
- quota and interstitial behaviour match photo scans exactly.

## WP4 · Log again, favourites, streak (M)

Files:
- `DashboardScreen.kt`: the meal rows and one streak chip near the date strip;
- `shared/.../model/UserData.kt`: `LoggedMeal`;
- the meal repositories;
- new migration `supabase/migrations/0006_meal_favorites.sql`.

The work:
- `LoggedMeal.favorite: Boolean = false`. Add the column to `fitcal.meals` with default false, and keep RLS unchanged.
- **Log again:** on a meal row, add an action (overflow or long-press) that copies the meal, items included, to today at the current time. It uses no scan.
- **Favourites:** star or unstar a meal. Above the history list, show a horizontal row of favourites with one-tap log, and recent distinct meals when there are no favourites.
- **Streak:** the number of consecutive days, ending today or yesterday, with at least one logged meal. Show it as a chip (for example "🔥 5-day streak") only when it's 2 or more. Write it as a pure function over meal dates.

Done when:
- the streak function is tested: gaps, today not logged yet, timezone day boundaries;
- copy-meal is tested and gets a new id and today's date;
- old meals without the field still load.

## WP5 · Weight history and Progress screen (M)

Files:
- new `ui/screens/progress/*`;
- `plan/PersonalPlanMath.kt`, so `checkIn` also records an entry;
- the user repositories;
- new migration `supabase/migrations/0007_weight_entries.sql`;
- `App.kt` and `NavRoutes.kt`;
- the dashboard, for one entry point only.

The work:
- Add a `WeightEntry(date, weightKg)` store, local plus Supabase `fitcal.weight_entries` with RLS limited to the user's own rows. Keep one entry per day; the latest wins.
- Record an entry on every plan check-in and whenever the weight changes in Settings.
- **Progress screen:**
  - a weight line chart (Compose Canvas) over 30 or 90 days, with the plan's target as a dashed line;
  - 7-day bars of calories and protein against target.
- Entry point: a "Progress" link on the calorie card or a header icon. Ask before adding a fourth header icon.
- A banner is allowed on this screen; it's passive history, AGENTS.md §5.5.

Done when:
- the chart's scale math is tested, and so is de-duplication per day;
- check-in still updates the profile exactly as before.

## WP6 · Spend protection (S to M)

Files:
- `supabase/functions/analyze-meal/index.ts`: a new route plus a change to `/v1/reward/ad-earned`;
- new migration `supabase/migrations/0008_cleanup_and_ssv.sql`;
- `AndroidAdManager.kt`: pass the user ID to the rewarded ad.

The work:
- **AdMob SSV:** add `GET /v1/reward/ssv`, which AdMob calls after a real rewarded view.
  - Verify Google's ECDSA signature against `https://www.gstatic.com/admob/reward/verifier-keys.json` (cache the keys).
  - Use `custom_data` / `user_id` to find the user.
  - Grant the bonus there, deduplicated by `transaction_id`.
  - Set `ServerSideVerificationOptions(userId = supabaseUserId)` on the rewarded ad.
  - Keep `/v1/reward/ad-earned` working until SSV is live, behind a flag `REWARD_REQUIRE_SSV`, false by default.
  - The +2 bonus and the daily cap stay exactly as they are (AGENTS.md §5).
- **Cleanup:** add a `pg_cron` job that deletes anonymous auth users older than 30 days with no meals and no events.
- **Budget alerts:** these are owner tasks. List them in `tasks/NEXT.md`: an OpenRouter credit limit, a Gemini/Google Cloud budget alert, and Groq usage limits.

Done when:
- the SSV signature verification is unit-tested with Google's published test vector;
- the grant is idempotent per `transaction_id`;
- the owner has a short checklist for setting the SSV callback URL in the AdMob console.

## WP7 · Guest ad cadence and sign-in card (S to M)

This is a monetization change. The spec is AGENTS.md §5.1, §5.2 (item 6) and §5.2a, updated by the owner on 2026-10-21. Follow it exactly.

Files:
- `ads/AdManager.kt`: `ScanQuotaManager`, `AdConfig`;
- `ScanGate.kt` / `App.kt` (the scan gate);
- `supabase/functions/analyze-meal/index.ts` (allowance);
- a new first-open card in `ui/screens/auth/` or `ui/components/`.

The work:
- **Allowance:** guests get no week-one bonus.
  - Client `getDailyFreeLimit()`: guests 3; permanent accounts 5+1 in week one, 3+1 after.
  - Gateway: apply `WEEK_ONE_DAILY_ALLOWANCE` only when the verified user is a permanent account. The server stays authoritative.
- **Guest interstitial cadence:** for guests only, from the second scan of the day, show the interstitial before processing when at least 90 s have passed since the last interstitial.
  - Store the last-shown time when an interstitial actually shows (`onAdShowedFullScreenContent`), not when one is requested.
  - Put it in a pure function, `shouldShowGuestInterstitial(isGuest, scansToday, lastInterstitialMs, nowMs)`.
  - If no ad is loaded, the scan proceeds. Never block a scan.
- **First-open card:** show it once, to guests only, after the age gate and consent:
  - title "Create a free account";
  - body: "No ads for your first week, plus 1 extra scan every day. Everything you log comes with you.";
  - buttons: Create account (opens `AuthDestination`) and Not now.
  - Track `signin_card_shown`, `signin_card_accept` and `signin_card_dismiss` (see `ANALYTICS.md`).
- **Owner, in the AdMob console:** on the interstitial ad unit, allow video and interactive (playable) ads. No code is needed for this. Add the step to `tasks/NEXT.md`.

Done when:
- `ScanQuotaManagerTest` is updated and new tests cover:
  - guest limit 3 in week one;
  - account limit 6 in week one;
  - first guest scan of the day has no ad;
  - second scan within 90 s has no ad;
  - second scan after 90 s gets the ad;
  - signed-in users in week one never see an ad within their free scans;
- the gateway allowance matches the client for guests and accounts;
- the card shows once and never to signed-in users.

## Smoke test on the phone, after all merges

- [ ] Photo scan works. Past the quota, an interstitial plays before analysis. No ads on the camera or review screens.
- [ ] The scan button docks into the dashboard card and returns to the bottom when scrolling.
- [ ] A barcode scan of a packaged food opens the review screen, and the scan count doesn't change.
- [ ] "Describe your meal" logs a meal and uses one scan. "Tell FitCal what's off" updates the items.
- [ ] Favourite a meal, log it again from the favourites row, and the streak chip shows from day 2.
- [ ] A plan check-in adds a point to the Progress weight chart.
- [ ] As a guest: first scan has no ad, the second scan shows an interstitial, a third scan within 90 s has none, and the sign-in card appeared once on first open.
- [ ] Signed in during week one: no ads for the first 6 scans of the day.
- [ ] Airplane mode while scanning shows the friendly offline message, and no new Sentry issue appears.
