/**
 * AdMob rewarded-ad server-side verification (SSV).
 *
 * After a real rewarded view, AdMob calls GET /v1/reward/ssv?<params>&signature=<sig>&key_id=<id>.
 * The signature is an ECDSA P-256 / SHA-256 signature, DER-encoded and web-safe base64, over the
 * URL-decoded query string that precedes "&signature=". The public keys are published at
 * ADMOB_VERIFIER_KEYS_URL and rotate, so they are cached and re-fetched on an unknown key id.
 *
 * This module is pure (WebCrypto + fetch only) so it runs under Deno, the edge runtime and the
 * unit tests in ssv_test.ts. The matching algorithm is Google's Tink RewardedAdsVerifier.
 * Docs: https://developers.google.com/admob/android/ssv
 */

export const ADMOB_VERIFIER_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";

const SIGNATURE_PARAM = "signature=";
const KEY_ID_PARAM = "key_id=";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Parameters AdMob sends on an SSV callback. Empty strings when absent. */
export interface SsvParams {
  adNetwork: string;
  adUnit: string;
  customData: string;
  rewardAmount: string;
  rewardItem: string;
  timestamp: string;
  transactionId: string;
  userId: string;
  keyId: string;
}

export interface SignedQuery {
  /** The exact bytes Google signed: the decoded query string before "&signature=". */
  message: string;
  /** DER-encoded ECDSA signature. */
  signature: Uint8Array;
  keyId: string;
}

/** Decodes standard or web-safe base64, with or without padding. */
export function base64Decode(input: string): Uint8Array {
  let b64 = input.trim().replace(/-/g, "+").replace(/_/g, "/");
  while (b64.length % 4 !== 0) b64 += "=";
  const binary = atob(b64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

/** Percent-decoding as java.net.URI.getQuery does it: "+" stays "+". */
function percentDecode(s: string): string {
  try {
    return decodeURIComponent(s);
  } catch {
    return s;
  }
}

/**
 * Splits a raw (still percent-encoded) query string into the signed message, the signature and
 * the key id. signature and key_id must be the last two parameters, in that order.
 * Returns null when the query is not in that shape.
 */
export function splitSignedQuery(rawQuery: string): SignedQuery | null {
  const query = rawQuery.startsWith("?") ? rawQuery.slice(1) : rawQuery;
  const sigAt = query.lastIndexOf("&" + SIGNATURE_PARAM);
  if (sigAt <= 0) return null;
  const message = percentDecode(query.slice(0, sigAt));
  const tail = query.slice(sigAt + 1); // "signature=...&key_id=..."
  const keyAt = tail.indexOf("&" + KEY_ID_PARAM);
  if (keyAt === -1) return null;
  const sigText = percentDecode(tail.slice(SIGNATURE_PARAM.length, keyAt));
  const keyId = percentDecode(tail.slice(keyAt + 1 + KEY_ID_PARAM.length));
  if (!sigText || !/^\d+$/.test(keyId)) return null;
  let signature: Uint8Array;
  try {
    signature = base64Decode(sigText);
  } catch {
    return null;
  }
  return { message, signature, keyId };
}

/** Reads the AdMob callback parameters (URLSearchParams decoding is fine for values). */
export function parseSsvParams(rawQuery: string): SsvParams {
  const p = new URLSearchParams(rawQuery.startsWith("?") ? rawQuery.slice(1) : rawQuery);
  const get = (k: string) => p.get(k) ?? "";
  return {
    adNetwork: get("ad_network"),
    adUnit: get("ad_unit"),
    customData: get("custom_data"),
    rewardAmount: get("reward_amount"),
    rewardItem: get("reward_item"),
    timestamp: get("timestamp"),
    transactionId: get("transaction_id"),
    userId: get("user_id"),
    keyId: get("key_id"),
  };
}

/** The Supabase user the reward is for: user_id first, then custom_data. Must be a UUID. */
export function resolveSsvUserId(params: Pick<SsvParams, "userId" | "customData">): string | null {
  for (const candidate of [params.userId, params.customData]) {
    const v = candidate.trim();
    if (UUID_RE.test(v)) return v.toLowerCase();
  }
  return null;
}

/**
 * Converts a DER ECDSA signature (SEQUENCE { INTEGER r, INTEGER s }) into the fixed-width
 * r || s form WebCrypto expects. Throws on malformed input.
 */
export function derToP1363(der: Uint8Array, size = 32): Uint8Array {
  let pos = 0;
  const fail = (why: string): never => {
    throw new Error(`Invalid DER signature: ${why}`);
  };
  const readLength = (): number => {
    if (pos >= der.length) fail("truncated length");
    let len = der[pos++];
    if (len & 0x80) {
      const n = len & 0x7f;
      if (n === 0 || n > 2) fail("bad length");
      len = 0;
      for (let i = 0; i < n; i++) {
        if (pos >= der.length) fail("truncated length");
        len = (len << 8) | der[pos++];
      }
    }
    return len;
  };
  const readInteger = (): Uint8Array => {
    if (der[pos++] !== 0x02) fail("expected INTEGER");
    const len = readLength();
    if (len === 0 || pos + len > der.length) fail("bad INTEGER length");
    let bytes = der.subarray(pos, pos + len);
    pos += len;
    while (bytes.length > 1 && bytes[0] === 0) bytes = bytes.subarray(1);
    if (bytes.length > size) fail("INTEGER too large");
    const out = new Uint8Array(size);
    out.set(bytes, size - bytes.length);
    return out;
  };

  if (der[pos++] !== 0x30) fail("expected SEQUENCE");
  const seqLen = readLength();
  if (pos + seqLen !== der.length) fail("SEQUENCE length mismatch");
  const r = readInteger();
  const s = readInteger();
  if (pos !== der.length) fail("trailing bytes");
  const out = new Uint8Array(size * 2);
  out.set(r, 0);
  out.set(s, size);
  return out;
}

/** Parses Google's key JSON into keyId → base64 SPKI. Key ids are kept as strings. */
export function parseVerifierKeys(json: unknown): Map<string, string> {
  const keys = new Map<string, string>();
  // deno-lint-ignore no-explicit-any
  const list = (json as any)?.keys;
  if (!Array.isArray(list)) return keys;
  for (const k of list) {
    const id = k?.keyId;
    const b64 = typeof k?.base64 === "string"
      ? k.base64
      : typeof k?.pem === "string"
      ? k.pem.replace(/-----(BEGIN|END) PUBLIC KEY-----/g, "").replace(/\s+/g, "")
      : null;
    if ((typeof id === "number" || typeof id === "string") && b64) keys.set(String(id), b64);
  }
  return keys;
}

async function importSpki(base64Spki: string): Promise<CryptoKey> {
  return await crypto.subtle.importKey(
    "spki",
    base64Decode(base64Spki),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );
}

/** Verifies one signed message against a base64 SPKI P-256 public key. Never throws. */
export async function verifyEcdsaDer(message: string, derSignature: Uint8Array, base64Spki: string): Promise<boolean> {
  try {
    const key = await importSpki(base64Spki);
    return await crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" },
      key,
      derToP1363(derSignature),
      new TextEncoder().encode(message),
    );
  } catch {
    return false;
  }
}

export type SsvVerifyResult =
  | { ok: true; params: SsvParams }
  | { ok: false; reason: "malformed" | "unknown_key" | "bad_signature" };

/** Source of verifier keys; lets tests inject Google's published test key. */
export interface VerifierKeySource {
  /** Returns the key, re-fetching once when the id is unknown (keys rotate). */
  get(keyId: string): Promise<string | null>;
}

/** Verifies a full SSV callback query string. */
export async function verifySsvQuery(rawQuery: string, keys: VerifierKeySource): Promise<SsvVerifyResult> {
  const signed = splitSignedQuery(rawQuery);
  if (!signed) return { ok: false, reason: "malformed" };
  const spki = await keys.get(signed.keyId);
  if (!spki) return { ok: false, reason: "unknown_key" };
  const valid = await verifyEcdsaDer(signed.message, signed.signature, spki);
  if (!valid) return { ok: false, reason: "bad_signature" };
  return { ok: true, params: parseSsvParams(rawQuery) };
}

/** Static key set, for tests. */
export class StaticKeySource implements VerifierKeySource {
  constructor(private readonly keys: Map<string, string>) {}
  get(keyId: string): Promise<string | null> {
    return Promise.resolve(this.keys.get(keyId) ?? null);
  }
}

/**
 * Fetches Google's verifier keys and caches them. Refreshes after [ttlMs], and on a cache miss
 * at most once per [missRefetchMs] so a flood of bogus key ids cannot hammer gstatic.
 */
export class CachedVerifierKeys implements VerifierKeySource {
  private keys = new Map<string, string>();
  private fetchedAt = 0;
  private lastMissFetch = 0;
  private inflight: Promise<void> | null = null;

  constructor(
    private readonly fetchJson: () => Promise<unknown> = async () => {
      const resp = await fetch(ADMOB_VERIFIER_KEYS_URL);
      if (!resp.ok) throw new Error(`verifier keys fetch failed (${resp.status})`);
      return await resp.json();
    },
    private readonly ttlMs = 12 * 60 * 60 * 1000,
    private readonly missRefetchMs = 60 * 1000,
    private readonly now: () => number = () => Date.now(),
  ) {}

  private refresh(): Promise<void> {
    if (!this.inflight) {
      this.inflight = this.fetchJson()
        .then((json) => {
          const parsed = parseVerifierKeys(json);
          if (parsed.size === 0) throw new Error("verifier keys response had no keys");
          this.keys = parsed;
          this.fetchedAt = this.now();
        })
        .finally(() => {
          this.inflight = null;
        });
    }
    return this.inflight;
  }

  async get(keyId: string): Promise<string | null> {
    let refreshed = false;
    if (this.fetchedAt === 0 || this.now() - this.fetchedAt > this.ttlMs) {
      try {
        await this.refresh();
        refreshed = true;
      } catch (e) {
        // A stale key set beats none; with no keys at all the caller answers 5xx and AdMob retries.
        if (this.keys.size === 0) throw e;
      }
    }
    const hit = this.keys.get(keyId);
    if (hit) return hit;
    if (refreshed || this.now() - this.lastMissFetch < this.missRefetchMs) return null;
    this.lastMissFetch = this.now();
    await this.refresh();
    return this.keys.get(keyId) ?? null;
  }
}

// ─── Route logic ─────────────────────────────────────────────────────────────

export type SsvClaimStatus = "granted" | "capped" | "recorded" | "duplicate" | "unknown_user";

export interface SsvClaimResult {
  status: SsvClaimStatus;
  granted: number;
  bonus: number | null;
}

export interface SsvClaimRequest {
  transactionId: string;
  userId: string;
  /** Whether to grant now (REWARD_REQUIRE_SSV on) or only record the verified view. */
  grant: boolean;
  adUnit: string;
  rewardItem: string;
  rewardAmount: number | null;
}

export interface SsvRouteDeps {
  keys: VerifierKeySource;
  grant: boolean;
  /** fitcal.claim_ssv_reward: idempotent per transactionId. */
  claim(req: SsvClaimRequest): Promise<SsvClaimResult>;
}

export interface SsvRouteResult {
  status: number;
  body: Record<string, unknown>;
}

/**
 * Handles GET /v1/reward/ssv. AdMob retries non-2xx answers, so a verified callback that
 * cannot or need not grant (duplicate, cap reached, unknown or missing user, the console's
 * "Verify URL" test) still answers 200. Bad signatures answer 403; a key outage 503.
 */
export async function handleSsvCallback(rawQuery: string, deps: SsvRouteDeps): Promise<SsvRouteResult> {
  let verified: SsvVerifyResult;
  try {
    verified = await verifySsvQuery(rawQuery, deps.keys);
  } catch (e) {
    return { status: 503, body: { error: "verifier_keys_unavailable", message: String((e as Error)?.message ?? e) } };
  }
  if (!verified.ok) {
    if (verified.reason === "malformed") return { status: 400, body: { error: "malformed" } };
    // An unknown key id may be a rotation we have not fetched yet: let AdMob retry.
    if (verified.reason === "unknown_key") return { status: 503, body: { error: "unknown_key" } };
    return { status: 403, body: { error: "bad_signature" } };
  }

  const p = verified.params;
  const transactionId = p.transactionId.trim();
  if (!transactionId || transactionId.length > 128) {
    return { status: 400, body: { error: "missing_transaction_id" } };
  }
  const userId = resolveSsvUserId(p);
  if (!userId) {
    // Signed but not ours to grant (e.g. the AdMob console test callback).
    return { status: 200, body: { status: "ignored", reason: "no_user" } };
  }

  const amount = Number.parseInt(p.rewardAmount, 10);
  const result = await deps.claim({
    transactionId,
    userId,
    grant: deps.grant,
    adUnit: p.adUnit,
    rewardItem: p.rewardItem,
    rewardAmount: Number.isFinite(amount) ? amount : null,
  });
  return { status: 200, body: { status: result.status, granted: result.granted } };
}
