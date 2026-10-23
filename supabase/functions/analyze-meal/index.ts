/**
 * FitCal AI Gateway — Supabase Edge Function. The app's only path to the AI providers;
 * paid provider keys live here as function secrets and never ship in the app.
 * Routes: POST /v1/analyze-meal · POST /v1/analyze-text · POST /v1/recalculate
 *         GET /v1/entitlements
 *         POST /v1/account/delete · POST /v1/reward/ad-earned · POST /v1/events
 *         POST /v1/feedback
 *         POST /v1/webhook/revenuecat · GET /v1/reward/ssv · GET /health
 *
 * Auth: deployed with verify_jwt = false so the RevenueCat webhook and AdMob's SSV
 *       callback (neither carries a Supabase JWT) can reach it. The SSV callback is
 *       authenticated by Google's ECDSA signature instead (ssv.ts). Every other /v1 route
 *       verifies the bearer token with Supabase Auth itself; anonymous sign-ins are real
 *       users and pass.
 *
 * Secrets (set via `supabase secrets set KEY=value`):
 *   GEMINI_API_KEY, OPENROUTER_API_KEY, GROQ_API_KEY
 *   SUPABASE_SERVICE_ROLE_KEY  (injected automatically by the runtime)
 *   SUPABASE_URL               (injected automatically by the runtime)
 *   REVENUECAT_WEBHOOK_SECRET
 *   FITCAL_ENV                 ("dev" enables mock responses; legacy name FITTER_ENV also read)
 *   VLM_PRIMARY_PROVIDER       ("openrouter" (default) | "gemini" | "groq" | "mock")
 *   VLM_OPENROUTER_MODEL       (default "google/gemini-3.1-flash-lite"; see vlm.ts)
 *   VLM_ALLOW_FALLBACK         ("true" | "false", default "true")
 *   KILL_SWITCH                ("true" to disable all non-health endpoints)
 *   REWARD_REQUIRE_SSV         ("true" = rewarded bonus is granted only by AdMob's SSV
 *                               callback; default "false" = POST /v1/reward/ad-earned grants
 *                               and SSV callbacks are only verified and recorded)
 *
 * Database: every table and RPC lives in schema `fitcal` (renamed from `fitter` in
 * migration 0011). Service-role requests must send the fitcal profile headers.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { appendHint, buildDescriptionPrompt, cleanHint, validateDescription } from "./text_input.ts";
import { CachedVerifierKeys, handleSsvCallback, type SsvClaimResult } from "./ssv.ts";
import { ACCOUNT_BONUS, BASE_DAILY_ALLOWANCE, dailyAllowance, isWeekOne } from "./allowance.ts";
import { buildPhotoPrompt, executeVlmFailover, type VlmOutcome } from "./vlm.ts";

// ─── Constants ────────────────────────────────────────────────────────────────

// Free scans per day: see allowance.ts. Computed here from server facts only; the client's value is ignored.
const REWARD_BONUS_PER_AD = 2;
const REWARD_BONUS_DAILY_CAP = 10;

// /v1/recalculate: text-only AI call when a user edits portions. Capped per user per day
// (fitcal.consume_recalc, migration 0002) and per request, so it can't be used as a free LLM.
const RECALC_DAILY_CAP = 50;
const RECALC_MAX_ITEMS = 30;
const RECALC_MAX_NAME_CHARS = 80;
const RECALC_MAX_GRAMS = 5000;

// No Access-Control-Allow-Origin: the native app sends no Origin, and browsers must
// not be able to read responses made with a user's token.
const CORS_HEADERS = {
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, x-device-id, apikey",
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

function err401(message = "Authentication required"): Response {
  return new Response(JSON.stringify({ error: "Unauthorized", message }), {
    status: 401,
    headers: { "Content-Type": "application/json", "WWW-Authenticate": "Bearer", ...CORS_HEADERS },
  });
}

function err402Quota(): Response {
  return jsonResponse(
    { error: "quota_exhausted", message: "Daily scan quota exceeded." },
    402
  );
}

// ─── Quota (server-side, service_role) ────────────────────────────────────────

// Permanent = a real account the user can sign back into: a confirmed email or a linked OAuth identity.
function isPermanentAccount(user: any): boolean {
  if (!user || user.is_anonymous) return false;
  if (user.email_confirmed_at) return true;
  return (user.identities ?? []).some((i: any) => i.provider && i.provider !== "email");
}

async function checkQuotaServerSide(
  userId: string,
  permanent: boolean
): Promise<{ allowed: boolean; effectiveAllowance: number }> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const url = `${supabaseUrl.replace(/\/$/, "")}/rest/v1/rpc/get_scan_quota`;
  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept-Profile": "fitcal",
      "Content-Profile": "fitcal",
      apikey: serviceRole,
      Authorization: `Bearer ${serviceRole}`,
    },
    body: JSON.stringify({ p_user_id: userId, p_allowance: BASE_DAILY_ALLOWANCE }),
  });
  if (!resp.ok) throw new Error(`get_scan_quota RPC failed (${resp.status}): ${await resp.text()}`);
  const data: any = await resp.json();

  // Guests get no week-one bonus (owner decision 2026-10-21); only permanent accounts do.
  const weekOne = isWeekOne(data?.first_install_date, Date.now());
  const effectiveAllowance = dailyAllowance(weekOne, permanent);

  const used = typeof data?.used === "number" ? data.used : 0;
  const bonus = typeof data?.bonus === "number" ? data.bonus : 0;
  return { allowed: effectiveAllowance + bonus - used > 0, effectiveAllowance };
}

async function consumeScanServerSide(userId: string, allowance: number): Promise<boolean> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const url = `${supabaseUrl.replace(/\/$/, "")}/rest/v1/rpc/consume_scan`;
  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept-Profile": "fitcal",
      "Content-Profile": "fitcal",
      apikey: serviceRole,
      Authorization: `Bearer ${serviceRole}`,
    },
    body: JSON.stringify({ p_user_id: userId, p_allowance: allowance }),
  });
  if (!resp.ok) throw new Error(`consume_scan RPC failed (${resp.status}): ${await resp.text()}`);
  const result = await resp.json();
  if (typeof result === "boolean") return result;
  if (Array.isArray(result) && result.length > 0) return Boolean(result[0]);
  return Boolean(result);
}

async function callFitcalRpc<T>(fn: string, args: Record<string, unknown>): Promise<T> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const resp = await fetch(`${supabaseUrl.replace(/\/$/, "")}/rest/v1/rpc/${fn}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept-Profile": "fitcal",
      "Content-Profile": "fitcal",
      apikey: serviceRole,
      Authorization: `Bearer ${serviceRole}`,
    },
    body: JSON.stringify(args),
  });
  if (!resp.ok) throw new Error(`${fn} RPC failed (${resp.status}): ${await resp.text()}`);
  return (await resp.json()) as T;
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Premium = an active fitcal_premium entitlement (fitter_premium kept for old purchases).
async function isPremiumUser(userId: string): Promise<boolean> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const resp = await fetch(
    `${supabaseUrl.replace(/\/$/, "")}/rest/v1/entitlements?user_id=eq.${userId}&entitlement_id=in.(fitcal_premium,fitter_premium)&select=status`,
    { headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}`, "Accept-Profile": "fitcal" } }
  );
  if (!resp.ok) {
    console.error(`entitlements read failed (${resp.status}): ${await resp.text()}`);
    return false;
  }
  const rows: any[] = await resp.json();
  return rows.some((e) => e.status === "active");
}

// ─── Analytics ───────────────────────────────────────────────────────────────
// Events land in fitcal.analytics_events via fitcal.log_events (migrations 0010, 0011).
// Payloads are behavioural only: never meal names or nutrition values.

const MAX_CLIENT_EVENTS_PER_REQUEST = 100;

interface AnalyticsEvent {
  event: string;
  props?: Record<string, unknown>;
  client_ts?: number;
  session_id?: string;
}

async function logEvents(
  userId: string | null,
  events: AnalyticsEvent[],
  source: "client" | "server",
  appVersion: string | null = null,
  platform: string | null = null,
): Promise<number> {
  if (events.length === 0) return 0;
  return await callFitcalRpc<number>("log_events", {
    p_user_id: userId,
    p_events: events,
    p_app_version: appVersion,
    p_platform: platform,
    p_source: source,
  });
}

// Server events never delay or fail the user's request. A null user records an
// anonymous count (used after account deletion, when the user's events are erased).
function trackServerEvent(userId: string | null, event: string, props: Record<string, unknown>): void {
  const task = logEvents(userId, [{ event, props, client_ts: Date.now() }], "server")
    .catch((e) => console.warn(`[analytics] ${event} not logged:`, e.message));
  // deno-lint-ignore no-explicit-any
  (globalThis as any).EdgeRuntime?.waitUntil?.(task);
}

// Short, stable error class for dashboards (no user content, no provider payloads).
function errorClass(err: any): string {
  const msg = String(err?.message ?? err ?? "unknown");
  if (msg.startsWith("All VLM providers failed")) return "all_providers_failed";
  if (msg.includes("Primary provider")) return "primary_failed_no_fallback";
  if (err instanceof SyntaxError) return "bad_json";
  return msg.slice(0, 80);
}

// ─── RevenueCat webhook ───────────────────────────────────────────────────────

function base64ToUint8Array(b64: string): Uint8Array {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function verifyRevenueCatSignature(request: Request, body: string, secret: string): Promise<boolean> {
  const sig = request.headers.get("X-RevenueCat-Signature") || "";
  if (!sig) return false;
  try {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
    return crypto.subtle.verify("HMAC", key, base64ToUint8Array(sig), encoder.encode(body));
  } catch { return false; }
}

// ─── AdMob SSV ────────────────────────────────────────────────────────────────

// Google's verifier keys, cached across requests on this isolate (ssv.ts).
const admobVerifierKeys = new CachedVerifierKeys();

function rewardRequiresSsv(): boolean {
  return (Deno.env.get("REWARD_REQUIRE_SSV") ?? "false").toLowerCase().trim() === "true";
}

// How long POST /v1/reward/ad-earned waits for AdMob's SSV callback when SSV is required.
// AdMob usually calls within a few seconds of the reward.
const SSV_CONFIRM_ATTEMPTS = 5;
const SSV_CONFIRM_INTERVAL_MS = 1200;
const SSV_CONFIRM_WINDOW_S = 120;

// ─── Main handler ─────────────────────────────────────────────────────────────

const supabaseAdmin = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false, autoRefreshToken: false } }
);

Deno.serve(async (req: Request) => {
  const url = new URL(req.url);

  // CORS preflight
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS_HEADERS });

  // Kill switch
  if (Deno.env.get("KILL_SWITCH") === "true") {
    return jsonResponse({ error: "Service temporarily unavailable", message: "Kill switch is active." }, 503);
  }

  // Health (unauthenticated)
  if (url.pathname.endsWith("/health") || url.pathname === "/") {
    return jsonResponse({ status: "healthy" }, 200);
  }

  // ── RevenueCat webhook (uses webhook secret, not user JWT) ────────────────
  if (req.method === "POST" && url.pathname.endsWith("/v1/webhook/revenuecat")) {
    try {
      const body = await req.text();
      const secret = Deno.env.get("REVENUECAT_WEBHOOK_SECRET");
      const isDev = (Deno.env.get("FITCAL_ENV") || Deno.env.get("FITTER_ENV")) === "dev";
      if (!secret) {
        if (!isDev) {
          console.error("REVENUECAT_WEBHOOK_SECRET not set — rejecting webhook");
          return jsonResponse({ error: "Service Unavailable", message: "Webhook not configured" }, 503);
        }
      } else if (!(await verifyRevenueCatSignature(req, body, secret))) {
        return jsonResponse({ error: "Unauthorized", message: "Invalid webhook signature" }, 401);
      }
      const event = (JSON.parse(body) as any).event || {};
      const appUserId = event.app_user_id || event.original_app_user_id;
      const entitlementId = event.entitlement_id || "fitcal_premium";
      const type: string = event.type || "";

      // RevenueCat's app_user_id must be the Supabase user ID; anonymous RevenueCat IDs
      // ($RCAnonymousID:…) cannot be matched to a user and are acknowledged but skipped.
      const isUserId = typeof appUserId === "string" && UUID_RE.test(appUserId);
      if (appUserId && !isUserId) {
        console.warn(`[revenuecat] skipping non-Supabase app_user_id for ${type}`);
      }
      if (isUserId) {
        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const isEligible = ["INITIAL_PURCHASE", "RENEWAL", "NON_RENEWING_PURCHASE", "UNCANCELLATION", "PRODUCT_CHANGE"].includes(type);
        const upsert = await fetch(`${supabaseUrl.replace(/\/$/, "")}/rest/v1/entitlements?on_conflict=user_id,entitlement_id`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Prefer": "resolution=merge-duplicates,return=minimal",
            "Content-Profile": "fitcal",
            apikey: serviceRole,
            Authorization: `Bearer ${serviceRole}`,
          },
          body: JSON.stringify({
            user_id: appUserId,
            entitlement_id: entitlementId,
            status: isEligible ? "active" : "expired",
            updated_at: new Date().toISOString(),
          }),
        });
        // A failed write must not be acknowledged, so RevenueCat retries it.
        if (!upsert.ok) {
          console.error(`[revenuecat] entitlement upsert failed (${upsert.status}): ${await upsert.text()}`);
          return jsonResponse({ error: "Service Unavailable", message: "Could not record entitlement" }, 503);
        }
        trackServerEvent(appUserId, "subscription_event", { type, entitlement: entitlementId, active: isEligible });
      }

      return jsonResponse({ status: "received", processed: isUserId }, 200);
    } catch (e: any) {
      console.error("RevenueCat webhook error:", e.message);
      return jsonResponse({ error: "Bad Request" }, 400);
    }
  }

  // ── GET /v1/reward/ssv ─────────────────────────────────────────────────────
  // AdMob rewarded-ad server-side verification callback. No JWT: Google's signature over
  // the query string is the authentication. Grants the same +2 under the same daily cap
  // as /v1/reward/ad-earned, once per transaction_id, and only when REWARD_REQUIRE_SSV is
  // on (before that, verified callbacks are recorded so the setup can be checked first).
  if (req.method === "GET" && url.pathname.endsWith("/v1/reward/ssv")) {
    try {
      const grant = rewardRequiresSsv();
      const result = await handleSsvCallback(url.search, {
        keys: admobVerifierKeys,
        grant,
        claim: (c) =>
          callFitcalRpc<SsvClaimResult>("claim_ssv_reward", {
            p_transaction_id: c.transactionId,
            p_user_id: c.userId,
            p_amount: REWARD_BONUS_PER_AD,
            p_daily_cap: REWARD_BONUS_DAILY_CAP,
            p_grant: c.grant,
            p_ad_unit: c.adUnit || null,
            p_reward_item: c.rewardItem || null,
            p_reward_amount: c.rewardAmount,
          }),
      });
      const outcome = String(result.body.status ?? result.body.error ?? "unknown");
      console.log(`[reward-ssv] http=${result.status} outcome=${outcome} grant=${grant}`);
      if (result.status === 200 && (outcome === "granted" || outcome === "capped" || outcome === "recorded")) {
        trackServerEvent(null, "reward_ssv", { outcome, grant });
      }
      return jsonResponse(result.body, result.status);
    } catch (e: any) {
      // Database or network failure: a 5xx makes AdMob retry, and the retry is deduplicated.
      console.error("[reward-ssv] error:", e.message);
      return jsonResponse({ error: "Internal Server Error" }, 500);
    }
  }

  // ── All other /v1/... endpoints require a verified user session ───────────
  // getUser() checks the token with Supabase Auth. The public anon key has no user
  // and is rejected; anonymous sign-ins are real users and are accepted.
  const authHeader = req.headers.get("Authorization") || "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
  if (!token) return err401();

  const { data: authData, error: authError } = await supabaseAdmin.auth.getUser(token);
  if (authError || !authData?.user) return err401("Invalid or expired session");
  const userId = authData.user.id;
  const permanentAccount = isPermanentAccount(authData.user);
  const accountBonus = permanentAccount ? ACCOUNT_BONUS : 0;

  try {
    // ── GET /v1/entitlements ────────────────────────────────────────────────
    if (req.method === "GET" && url.pathname.endsWith("/v1/entitlements")) {
      const premium = await isPremiumUser(userId);
      return jsonResponse({ premium, user_id: userId }, 200);
    }

    // ── POST /v1/analyze-meal ───────────────────────────────────────────────
    if (req.method === "POST" && url.pathname.endsWith("/v1/analyze-meal")) {
      const body: any = await req.json();
      const imageBase64 = body.image_base64 || body.imageBase64;
      const plateSize = Number(body.plate_size_inches ?? body.plateSizeInches);
      const plateSizeInches = Number.isFinite(plateSize) && plateSize >= 4 && plateSize <= 20 ? plateSize : null;

      if (!imageBase64 || typeof imageBase64 !== "string") {
        return jsonResponse({ error: "Bad Request", message: "image_base64 is required" }, 400);
      }

      // Premium check — skip quota for premium users
      const isPremium = await isPremiumUser(userId);

      // Quota pre-check BEFORE VLM spend (does not consume quota if VLM fails)
      let effectiveAllowance = BASE_DAILY_ALLOWANCE + accountBonus;
      if (!isPremium) {
        try {
          const quotaCheck = await checkQuotaServerSide(userId, permanentAccount);
          effectiveAllowance = quotaCheck.effectiveAllowance;
          if (!quotaCheck.allowed) {
            trackServerEvent(userId, "quota_denied", { allowance: effectiveAllowance });
            return err402Quota();
          }
        } catch (quotaErr: any) {
          console.error("get_scan_quota RPC error:", quotaErr.message);
          return jsonResponse({ error: "Service temporarily unavailable", message: "Could not verify quota. Try again." }, 503);
        }
      }

      const userPrompt = buildPhotoPrompt(plateSizeInches);

      console.log(`[analyze-meal] user=${userId} allowance=${effectiveAllowance}`);

      const startedAt = Date.now();
      const imageKb = Math.round((imageBase64.length * 3) / 4 / 1024);
      let outcome: VlmOutcome;
      try {
        outcome = await executeVlmFailover(userPrompt, imageBase64);
      } catch (vlmErr: any) {
        trackServerEvent(userId, "vlm_analyze", {
          ok: false,
          latency_ms: Date.now() - startedAt,
          error: errorClass(vlmErr),
          premium: isPremium,
          image_kb: imageKb,
        });
        throw vlmErr;
      }
      const { result, provider } = outcome;
      trackServerEvent(userId, "vlm_analyze", {
        ok: true,
        provider,
        fallback: outcome.failed.length > 0,
        failed_providers: outcome.failed,
        latency_ms: Date.now() - startedAt,
        items: result.items.length,
        low_confidence_items: result.items.filter((i) => i.confidence === "low").length,
        premium: isPremium,
        plate_size_set: plateSizeInches !== null,
        image_kb: imageKb,
      });

      // Consume 1 scan quota ONLY after VLM inference succeeded
      if (!isPremium) {
        try {
          await consumeScanServerSide(userId, effectiveAllowance);
        } catch (consumeErr: any) {
          console.error("Post-scan consume_scan RPC error:", consumeErr.message);
        }
      }

      return new Response(JSON.stringify(result), {
        status: 200,
        headers: { "Content-Type": "application/json", "X-Provider": provider, ...CORS_HEADERS },
      });
    }

    // ── POST /v1/analyze-text ───────────────────────────────────────────────
    // "Describe your meal": { description } → the same NutritionResponse as a photo scan.
    // Counts as one scan: the quota flow below mirrors /v1/analyze-meal step for step
    // (premium skip, pre-check before AI spend, 402 when exhausted, consume_scan only
    // after a successful answer). Keep the two routes in step when either changes.
    if (req.method === "POST" && url.pathname.endsWith("/v1/analyze-text")) {
      const body: any = await req.json().catch(() => null);
      const check = validateDescription(body?.description);
      if (!check.ok) {
        return jsonResponse({ error: "Bad Request", message: check.message }, 400);
      }

      const isPremium = await isPremiumUser(userId);

      let effectiveAllowance = BASE_DAILY_ALLOWANCE + accountBonus;
      if (!isPremium) {
        try {
          const quotaCheck = await checkQuotaServerSide(userId, permanentAccount);
          effectiveAllowance = quotaCheck.effectiveAllowance;
          if (!quotaCheck.allowed) {
            trackServerEvent(userId, "quota_denied", { allowance: effectiveAllowance, input: "text" });
            return err402Quota();
          }
        } catch (quotaErr: any) {
          console.error("get_scan_quota RPC error:", quotaErr.message);
          return jsonResponse({ error: "Service temporarily unavailable", message: "Could not verify quota. Try again." }, 503);
        }
      }

      console.log(`[analyze-text] user=${userId} allowance=${effectiveAllowance}`);

      // Behavioural analytics only: the length of the text, never the text itself.
      const startedAt = Date.now();
      let outcome: VlmOutcome;
      try {
        outcome = await executeVlmFailover(buildDescriptionPrompt(check.description));
      } catch (vlmErr: any) {
        trackServerEvent(userId, "vlm_analyze", {
          ok: false,
          input: "text",
          latency_ms: Date.now() - startedAt,
          error: errorClass(vlmErr),
          premium: isPremium,
          chars: check.description.length,
        });
        throw vlmErr;
      }
      const { result, provider } = outcome;
      trackServerEvent(userId, "vlm_analyze", {
        ok: true,
        input: "text",
        provider,
        fallback: outcome.failed.length > 0,
        failed_providers: outcome.failed,
        latency_ms: Date.now() - startedAt,
        items: result.items.length,
        low_confidence_items: result.items.filter((i) => i.confidence === "low").length,
        premium: isPremium,
        chars: check.description.length,
      });

      if (!isPremium) {
        try {
          await consumeScanServerSide(userId, effectiveAllowance);
        } catch (consumeErr: any) {
          console.error("Post-scan consume_scan RPC error:", consumeErr.message);
        }
      }

      return new Response(JSON.stringify(result), {
        status: 200,
        headers: { "Content-Type": "application/json", "X-Provider": provider, ...CORS_HEADERS },
      });
    }

    // ── POST /v1/account/delete ─────────────────────────────────────────────
    // Play account-deletion requirement and GDPR Art. 17. The user comes from the
    // verified token only. Reports success only once the data is actually gone.
    if (req.method === "POST" && url.pathname.endsWith("/v1/account/delete")) {
      const canDeleteLogin = await callFitcalRpc<boolean>("delete_account_data", { p_user_id: userId });
      if (canDeleteLogin === true) {
        const { error } = await supabaseAdmin.auth.admin.deleteUser(userId);
        if (error && error.status !== 404) throw new Error(`auth user deletion failed: ${error.message}`);
      } else {
        // The same login is used by another app on this project: FitCal data is gone,
        // the login stays so the other app's data is not cascade-deleted.
        console.log(`[account-delete] user=${userId} FitCal data deleted, login kept (used elsewhere)`);
      }
      console.log(`[account-delete] user=${userId} completed`);
      trackServerEvent(null, "account_deleted", { login_deleted: canDeleteLogin === true });
      return jsonResponse({ deleted: true }, 200);
    }

    // ── POST /v1/reward/ad-earned ───────────────────────────────────────────
    // Grants rewarded-ad bonus scans, capped per day in the database.
    // With REWARD_REQUIRE_SSV on, this route grants nothing: it waits briefly for AdMob's
    // SSV callback (GET /v1/reward/ssv) and reports that grant. If the callback has not
    // arrived yet it answers 425, the app keeps its optimistic count, and the next quota
    // sync shows the server value.
    if (req.method === "POST" && url.pathname.endsWith("/v1/reward/ad-earned") && rewardRequiresSsv()) {
      await req.body?.cancel().catch(() => {});
      for (let attempt = 0; attempt < SSV_CONFIRM_ATTEMPTS; attempt++) {
        const recent = await callFitcalRpc<{ found: boolean; granted: number; bonus: number }>("recent_ssv_reward", {
          p_user_id: userId,
          p_within_seconds: SSV_CONFIRM_WINDOW_S,
        });
        if (recent.found) {
          const capped = recent.granted === 0;
          trackServerEvent(userId, "reward_claimed", { via: "ssv", granted: recent.granted, capped });
          if (capped) {
            return jsonResponse(
              { error: "Reward limit reached", message: `Maximum of ${REWARD_BONUS_DAILY_CAP} rewarded scans per day reached.` },
              429
            );
          }
          return jsonResponse({ bonus: recent.bonus, granted: recent.granted }, 200);
        }
        if (attempt < SSV_CONFIRM_ATTEMPTS - 1) await new Promise((r) => setTimeout(r, SSV_CONFIRM_INTERVAL_MS));
      }
      trackServerEvent(userId, "reward_claimed", { via: "ssv", pending: true });
      return jsonResponse({ error: "Reward pending", message: "Waiting for the ad network to confirm the reward." }, 425);
    }
    if (req.method === "POST" && url.pathname.endsWith("/v1/reward/ad-earned")) {
      const body: any = await req.json().catch(() => ({}));
      const requested = Math.min(Math.max(Math.trunc(Number(body?.amount ?? REWARD_BONUS_PER_AD)) || 0, 1), REWARD_BONUS_PER_AD);
      const bonus = await callFitcalRpc<number | null>("claim_reward_bonus", {
        p_user_id: userId,
        p_amount: requested,
        p_daily_cap: REWARD_BONUS_DAILY_CAP,
      });
      trackServerEvent(userId, "reward_claimed", { requested, granted: bonus === null ? 0 : requested, capped: bonus === null });
      if (bonus === null) {
        return jsonResponse(
          { error: "Reward limit reached", message: `Maximum of ${REWARD_BONUS_DAILY_CAP} rewarded scans per day reached.` },
          429
        );
      }
      console.log(`[reward] user=${userId} bonus=${bonus}`);
      return jsonResponse({ bonus, granted: requested }, 200);
    }

    // ── POST /v1/recalculate ────────────────────────────────────────────────
    if (req.method === "POST" && url.pathname.endsWith("/v1/recalculate")) {
      const body: any = await req.json().catch(() => null);
      const rawItems = body?.items;
      if (!Array.isArray(rawItems) || rawItems.length === 0) {
        return jsonResponse({ error: "Bad Request", message: "items array is required with {name, grams}" }, 400);
      }
      if (rawItems.length > RECALC_MAX_ITEMS) {
        return jsonResponse({ error: "Bad Request", message: `At most ${RECALC_MAX_ITEMS} items per request` }, 400);
      }
      // Names are user-editable text that goes into the prompt: strip control characters and cap the length.
      // Weights are clamped rather than rejected: the review screen can legitimately send 0 g.
      const items = rawItems.map((it: any) => ({
        name: String(it?.name ?? it?.item ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, RECALC_MAX_NAME_CHARS) || "Food item",
        grams: Number(it?.grams ?? it?.weight_est_g),
      }));
      if (items.some((it) => !Number.isFinite(it.grams))) {
        return jsonResponse({ error: "Bad Request", message: "Each item needs numeric grams" }, 400);
      }
      for (const it of items) it.grams = Math.min(Math.max(it.grams, 0), RECALC_MAX_GRAMS);

      // Counted before the AI call, so failed calls count too; fails closed if the counter is unavailable.
      let withinCap: boolean;
      try {
        withinCap = await callFitcalRpc<boolean>("consume_recalc", { p_user_id: userId, p_daily_cap: RECALC_DAILY_CAP });
      } catch (capErr: any) {
        console.error("consume_recalc RPC error:", capErr.message);
        return jsonResponse({ error: "Service temporarily unavailable", message: "Could not verify limit. Try again." }, 503);
      }
      if (!withinCap) {
        trackServerEvent(userId, "recalc_denied", { cap: RECALC_DAILY_CAP });
        return jsonResponse(
          { error: "recalc_limit", message: `Maximum of ${RECALC_DAILY_CAP} recalculations per day reached.` },
          429
        );
      }

      const itemsPrompt = items
        .map((it) => `- ${it.name}: ${Math.round(it.grams)}g`)
        .join("\n");
      // Optional "Tell FitCal what's off" note from the review screen (cleaned and capped).
      const hint = cleanHint(body?.hint);
      const prompt = appendHint(
        `Analyze these food items and estimate their nutritional contents based on the given weights.\nItems:\n${itemsPrompt}`,
        hint,
      );

      console.log(`[recalculate] user=${userId}`);
      const startedAt = Date.now();
      let outcome: VlmOutcome;
      try {
        outcome = await executeVlmFailover(prompt);
      } catch (vlmErr: any) {
        trackServerEvent(userId, "vlm_recalculate", {
          ok: false,
          latency_ms: Date.now() - startedAt,
          error: errorClass(vlmErr),
          items: items.length,
          hint: hint !== null,
        });
        throw vlmErr;
      }
      const { result, provider } = outcome;
      trackServerEvent(userId, "vlm_recalculate", {
        ok: true,
        provider,
        fallback: outcome.failed.length > 0,
        failed_providers: outcome.failed,
        latency_ms: Date.now() - startedAt,
        items: items.length,
        hint: hint !== null,
      });
      return new Response(JSON.stringify(result), {
        status: 200,
        headers: { "Content-Type": "application/json", "X-Provider": provider, ...CORS_HEADERS },
      });
    }

    // ── POST /v1/events ─────────────────────────────────────────────────────
    // Client analytics batch: { app_version, platform, events: [{event, props, client_ts, session_id}] }.
    // The user comes from the verified token; log_events validates names and sizes.
    if (req.method === "POST" && url.pathname.endsWith("/v1/events")) {
      const body: any = await req.json().catch(() => null);
      const events = Array.isArray(body?.events) ? body.events.slice(0, MAX_CLIENT_EVENTS_PER_REQUEST) : null;
      if (!events) return jsonResponse({ error: "Bad Request", message: "events array is required" }, 400);
      const accepted = await logEvents(
        userId,
        events,
        "client",
        typeof body.app_version === "string" ? body.app_version : null,
        typeof body.platform === "string" ? body.platform : null,
      );
      return jsonResponse({ accepted }, 200);
    }

    // ── POST /v1/feedback ───────────────────────────────────────────────────
    // { kind: "scan_accuracy"|"bug"|"idea"|"other", rating?, message?, contact_email?,
    //   context?, app_version?, platform? }. Limited to 30 per user per day.
    if (req.method === "POST" && url.pathname.endsWith("/v1/feedback")) {
      const body: any = await req.json().catch(() => null);
      const kind = body?.kind;
      if (!["scan_accuracy", "bug", "idea", "other"].includes(kind)) {
        return jsonResponse({ error: "Bad Request", message: "kind must be scan_accuracy, bug, idea or other" }, 400);
      }
      const rating = Number.isInteger(body.rating) && body.rating >= 1 && body.rating <= 5 ? body.rating : null;
      const message = typeof body.message === "string" ? body.message.slice(0, 2000) : null;
      if (kind === "scan_accuracy" ? rating === null : !message?.trim()) {
        return jsonResponse({ error: "Bad Request", message: "scan_accuracy needs a rating; other kinds need a message" }, 400);
      }
      const email = typeof body.contact_email === "string" && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.contact_email.trim())
        ? body.contact_email.trim()
        : null;
      const id = await callFitcalRpc<number | null>("submit_feedback", {
        p_user_id: userId,
        p_kind: kind,
        p_rating: rating,
        p_message: message,
        p_contact_email: email,
        p_context: body.context && typeof body.context === "object" && !Array.isArray(body.context) ? body.context : {},
        p_app_version: typeof body.app_version === "string" ? body.app_version : null,
        p_platform: typeof body.platform === "string" ? body.platform : null,
      });
      if (id === null) {
        return jsonResponse({ error: "Too Many Requests", message: "Feedback limit reached for today." }, 429);
      }
      return jsonResponse({ id }, 200);
    }

    return jsonResponse({ error: "Not Found" }, 404);
  } catch (error: any) {
    console.error("Edge Function error:", error);
    return jsonResponse({ error: "Internal Server Error", message: "Analysis failed. Please try again." }, 500);
  }
});
