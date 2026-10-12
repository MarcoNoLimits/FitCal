# FitCal — Release Info (keep this current)

Everything needed to ship and re-ship FitCal from one place. **No passwords or API keys go in this file.**
Passwords live in your password manager and in `local.properties` (git-ignored).

---

## 1. Upload key (created 2026-10-12)

| Item | Value |
|---|---|
| Keystore file | `C:/Users/ahmed/keys/fitcal-upload.jks` (outside the repo; `*.jks` is git-ignored) |
| Alias | `fitcal` |
| Owner | `CN=FitCal, OU=FitCal, O=FitCal` |
| Valid | 2026-10-12 → 2054-02-27 |
| **SHA-1** | `AF:EF:50:74:40:53:97:C4:A5:BD:DE:4A:3E:82:E4:A1:6C:9A:14:7A` |
| **SHA-256** | `F4:49:9F:52:4B:02:0E:06:75:5B:76:97:EF:8B:A2:D8:0E:AE:B1:75:29:84:6C:F5:13:3A:E9:ED:1F:59:F7:1E` |
| Password | In your password manager (not here) |

- [ ] **Backup:** copy `fitcal-upload.jks` and its password somewhere other than this PC.

`local.properties` needs these four lines for release builds (if they're missing, Gradle silently signs with the **debug** key):
```
RELEASE_KEYSTORE_PATH=C:/Users/ahmed/keys/fitcal-upload.jks
RELEASE_KEYSTORE_PASSWORD=...
RELEASE_KEY_ALIAS=fitcal
RELEASE_KEY_PASSWORD=...
```

Check which key signed a bundle (no password needed; the Owner line must say `CN=FitCal`):
```bash
"C:/Program Files/Android/Android Studio1/jbr/bin/keytool.exe" -printcert -jarfile FitCal-UI/build/outputs/bundle/release/FitCal-UI-release.aab
```

## 2. App signing key (Google holds it)

With Play App Signing, Google re-signs the app that users install. Its fingerprint only exists after the first upload.

| Item | Value |
|---|---|
| App signing SHA-1 | _TODO: Play Console → Test and release → App integrity → App signing_ |
| App signing SHA-256 | _TODO_ |

## 3. Where each fingerprint goes

| Where | Which SHA-1 | Why |
|---|---|---|
| Google Cloud → Credentials → OAuth client **Android** `com.fitcal.app` #1 | Upload key (§1) | Google sign-in on builds you install yourself |
| Google Cloud → OAuth client **Android** `com.fitcal.app` #2 | App signing key (§2) | Google sign-in for **real users** installing from Play |
| Google Cloud → OAuth client **Web** | none | Its client ID goes in `local.properties` as `GOOGLE_WEB_CLIENT_ID`, and its ID + secret go in Supabase → Auth → Providers → Google |

## 3b. Google OAuth clients (Google Cloud project "FitCal", created 2026-10-12)

| Client | Client ID | Notes |
|---|---|---|
| Web `FitCal Supabase` | `846576167463-1482ovcavtvnh87fb0gmk5eopds61lvk.apps.googleusercontent.com` | `GOOGLE_WEB_CLIENT_ID` in `local.properties`; ID + secret in Supabase → Auth → Google. Redirect URI `https://uedtovphilwstzdpzjrk.supabase.co/auth/v1/callback`. The secret is stored only in Supabase. |
| Android (upload key) | `846576167463-knle3ffi0gpeqqcbjltcsb6ollkilc22.apps.googleusercontent.com` (assumed; check the client names in Cloud Console) | SHA-1 `AF:EF:50:…:14:7A` |
| Android (debug key) | `846576167463-dcabg4jeusrht2nq34lfqtqlci3atp7m.apps.googleusercontent.com` (assumed) | SHA-1 `D4:72:F3:85:6B:50:1F:13:3A:20:7D:CB:9C:41:36:1B:6F:D5:78:D4` (this PC's `~/.android/debug.keystore`) |
| Android (Play app signing) | _TODO after the first upload_ | SHA-1 from Play Console → App integrity |

**Testing (2026-10-12):** on the emulator the Google button opens Credential Manager and reaches Google's account step. The full flow is **not tested yet**: picking an account, the SHA-1/client-ID match, and Supabase accepting the token. Test it on a real phone: guest with a logged meal → Continue with Google → the meal is still there. If it fails with "developer error" or code 10, the SHA-1 or package doesn't match an Android client.

Branding was validated by Google on 2026-10-12. The Web client secret was rotated on 2026-10-12 (the old one, which appeared in a chat screenshot, is deleted); the current secret is stored only in Supabase. Scopes: email, profile and openid only, with no logo (a logo would trigger brand verification).

## 4. Identifiers and URLs

| Item | Value |
|---|---|
| Package name | `com.fitcal.app` |
| Version | `versionCode 1`, `versionName 1.0` (raise `versionCode` on every upload) |
| AdMob App ID | `ca-app-pub-8255091497626517~5945613703` |
| Supabase project | `uedtovphilwstzdpzjrk` (EU, Free plan) |
| Website (Vercel `fitcal-site`, auto-deploys `site/` from `main`) | https://fitcal.tecaa.xyz/ |
| Privacy policy | https://fitcal.tecaa.xyz/privacy.html |
| Account deletion URL | https://fitcal.tecaa.xyz/privacy.html#delete |
| app-ads.txt | https://fitcal.tecaa.xyz/app-ads.txt |
| Contact / developer email | `fitcal@tecaa.xyz` |
| Repo | https://github.com/MarcoNoLimits/MacroVision-KMP |

## 5. Play Console answers

All form answers (Data safety, content rating, Health declaration, target audience 18+, store description, reviewer note) are in [`STORE-SUBMISSION-CHECKLIST.md`](STORE-SUBMISSION-CHECKLIST.md). Copy from there instead of answering from memory, so the answers stay consistent with the privacy policy.

## 6. Status (2026-10-12)

- [ ] **Play developer account: $25 one-time fee, not paid yet.** Everything below that touches Play waits on this.
- [ ] Closed test: 12 testers for 14 days, if the account is personal and new. Collect testers' Gmail addresses now: _list them here_.
- [ ] First AAB upload → fill in §2 → second Android OAuth client.
- [x] Email (2026-10-12): Name.com forwarding only (receive-only; replies go out from Gmail). Zoho records removed, root SPF is `v=spf1 ~all`, DMARC is `p=none` with reports to `fitcal@tecaa.xyz`. Auth emails send via Resend (`resend._domainkey`, `send.tecaa.xyz`). Around 2026-10-26, if reports are clean, change DMARC to `p=quarantine`.
- [ ] Confirm forwarding: send a test from another account to `fitcal@tecaa.xyz`.
- [x] `SENTRY_DSN` set (2026-10-12, Sentry org `tecaa`, US region); a test event was accepted.
- [x] Readable crash stack traces: the Sentry Gradle plugin uploads `mapping.txt` on every release build (org `tecaa`, project `fitcal`, token `SENTRY_AUTH_TOKEN` in `local.properties`). First upload worked on 2026-10-12. Without the token the build skips the upload and still succeeds.
- [x] Spend caps (2026-10-12): an OpenRouter key credit limit and a Google Cloud budget alert. Gemini is billed to the owner's **Google Cloud credits** (paid tier, so prompts aren't used for training). When the credits run out, usage is charged to the linked billing account; the budget alert is the warning. Emergency stop: Supabase secret `KILL_SWITCH=true`.
- [x] R8/Kotlin version warning fixed: R8 pinned to 9.4.28 in the root `build.gradle.kts`. Remove the pin when AGP is upgraded to 9.x.
- [x] Abuse limits (2026-10-12, migration 0002 + gateway v3, tested live): clients can only read their own quota rows; recalculate is capped at 50/day per user with 30 items and 80-char names. Per user per day: up to 16 photo scans (6 free + 10 rewarded).
- [x] Editable logged meals (2026-10-12, migration 0004 applied to production): `fitcal.meals.items` (jsonb, default `[]`, max 50 items / 32 KB) stores each meal's ingredients so it can be reopened from the dashboard. App builds from before this don't send the column; builds from after need it, so it has to stay applied.
- [ ] Supabase → Authentication → Rate Limits: lower anonymous sign-ins from 30 to 10 per hour per IP.
- [ ] Later: AdMob server-side verification for rewarded ads, so bonus scans can't be claimed without watching an ad.
- [ ] Real-device test of the signed release build.
- [ ] Supabase Pro once there's real traffic or revenue (Free = no backups).

The full backlog is in [`tasks/NEXT.md`](tasks/NEXT.md).
