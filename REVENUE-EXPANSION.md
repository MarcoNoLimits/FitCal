# FitCal — Revenue Expansion Ideas (post-launch)

Status: **parked until after launch.** These sit on top of the current model (AdMob ads + scan quota, Premium planned for v1.1). They don't replace it. Before anything here changes ad or quota behavior, follow Section 5 of `AGENTS.md`.

Captured 2026-10-19 for the later marketing and growth phase.

## Why these work: free users are cheap to serve

At ~$0.00017 per Gemini Flash scan (AGENTS.md §5.9):

| Daily active users | Scans/day (×3) | AI cost/month | + Supabase | Total |
|---|---|---|---|---|
| 1,000 | 3,000 | ~$15 | $25 | ~$40 |
| 10,000 | 30,000 | ~$150 | ~$25–75 | ~$200 |
| 100,000 | 300,000 | ~$1,500 | ~$300+ | ~$2,000 |

Small revenue from people who aren't the end user covers a large free user base.

---

## 1. Coach / professional dashboard (B2B2C) — top priority

**Idea:** dietitians, personal trainers, gyms and coaching programs pay for a web dashboard where they see their clients' scanned meals, macros and plan adherence. Clients keep using FitCal as normal.

- **Price:** ~$15–40/month per professional, tiered by number of clients.
- **Math:** one trainer at $20/month covers ~100,000 scans. About 100 paying pros covers a 100k DAU user base.
- **Proven by:** Trainerize, That Clean Life, Nutrium (they charge the pro, not the client).
- **What we already have:** Supabase auth, synced meals and meal items, personal plans, RLS.
- **What's missing:**
  - "Share with my coach" invite link or code in the app, with explicit consent the client can revoke
  - New RLS policies that let a linked coach read a client's meals
  - A web dashboard: client list, daily macros against plan, meal photos, notes
  - Billing for coaches (Stripe on the web, so Play billing isn't involved)
- **Privacy:** food logs are close to health data under GDPR. Sharing must be opt-in per client, revocable, and covered in the privacy policy.
- **Marketing angle:** coaches bring their clients with them. Every coach we sign up is a user acquisition channel.

## 2. Scan API for developers

**Idea:** sell the meal-recognition pipeline as an API to other apps (fitness, recipes, health, grocery).

- **Price:** ~$0.005–0.02 per scan, or monthly tiers. Our cost is ~$0.0002, so margins are high.
- **Competitors:** Passio, LogMeal, Calorie Mama.
- **What we already have:** the `analyze-meal` gateway with Gemini → OpenRouter → Groq failover, food database grounding, server-side quotas, kill switch.
- **What's missing:**
  - API keys per customer, separate from user JWTs
  - Per-key usage metering and rate limits
  - Public docs and a sandbox
  - Accuracy benchmarks we can publish (needed before anyone will pay)
- **Bonus:** the consumer app works as the live demo and keeps improving the product.

## 3. Employers, insurers and wellness programs

**Idea:** corporate wellness and health programs pay per employee per month to offer FitCal (ad-free for their members) as a benefit.

- **Price:** typically ~$1–5 per member per month.
- **Pros:** large contracts, predictable revenue.
- **Cons:** slow sales cycles, procurement and security reviews (DPA, GDPR, possibly ISO/SOC expectations). Needs traction and case studies first.
- **When:** after #1 proves engagement and retention numbers we can show.

## Bonus: Donations / tip jar

**Idea:** a small "Support FitCal" option for users who love the app.

- Options: one-time Play in-app product ("Buy us a coffee"), or an external page (Ko-fi, Buy Me a Coffee, GitHub Sponsors).
- **Play policy:** inside the Android app, digital tips must go through Google Play Billing. External donation links in the app are risky, so keep those to the website and socials.
- **Expectations:** goodwill and a little income, not a business model. Could unlock a cosmetic thank-you (badge, app icon) but never extra scans, so it doesn't compete with Premium.

---

## Explicitly rejected

- **Selling or sharing user data.** Health-adjacent data under GDPR, against Play health-app policy, and it would destroy trust.
- **Unlabeled affiliate or product placement in scan results.** It breaks the "no ads on Camera/Review" rule (AGENTS.md §2.3).

## Suggested order

1. Launch and get real retention data (ads + quota, as specified now).
2. Premium (v1.1, RevenueCat).
3. Coach dashboard (#1). Donations can go in alongside this, since they're cheap to add.
4. Scan API (#2), once accuracy is benchmarked.
5. Wellness and insurers (#3), once there are case studies.
