/**
 * Unit tests for AdMob SSV verification.  Run: deno test supabase/functions/analyze-meal/ssv_test.ts
 *
 * The key pair is Google's published test vector from Tink's RewardedAdsVerifierTest
 * (tink-crypto/tink-java-apps, rewardedads): key id 1234, its public key JSON and the matching
 * PKCS#8 private key. SIGNED_CALLBACK was signed once with that private key (DER, web-safe
 * base64), so it is a fixed vector; other tests sign fresh messages the same way Tink does.
 * No assertion library import: the file has no network dependency.
 */

import {
  CachedVerifierKeys,
  derToP1363,
  handleSsvCallback,
  parseVerifierKeys,
  resolveSsvUserId,
  splitSignedQuery,
  type SsvClaimRequest,
  type SsvClaimResult,
  StaticKeySource,
  verifySsvQuery,
} from "./ssv.ts";

// ─── Google's test vector (Tink RewardedAdsVerifierTest) ─────────────────────

const GOOGLE_TEST_KEYS_JSON =
  '{"keys":[{"keyId":1234,"base64":"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPYnHwS8uegWAewQt' +
  'lxizmLFynwHcxRT1PK07cDA6/C4sXrVI1SzZCUx8U8S0LjMrT6ird/VW7be3Mz6t/srtRQ=="}]}';

const GOOGLE_TEST_PRIVATE_KEY_PKCS8 =
  "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgZj/Dldxz8fvKVF5O" +
  "TeAtK6tY3G1McmvhMppe6ayW6GahRANCAAQ9icfBLy56BYB7BC2XGLOYsXKfAdzF" +
  "FPU8rTtwMDr8LixetUjVLNkJTHxTxLQuMytPqKt39Vbtt7czPq3+yu1F";

const ALTERNATE_PUBLIC_KEY =
  "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEU8E6JppGKFG40r5dDU1idHRN52NuwsemFzXZh1oUqh3bGUPgPioH+RoW" +
  "nmVSUQz1WfM2426w9f0GADuXzpUkcw==";

const USER = "0b7f6c2e-3d4a-4f1b-9c8d-2a1e5f6b7c8d";

const CALLBACK_DATA =
  "ad_network=5450213213286189855&ad_unit=1234567890&custom_data=" + USER +
  "&reward_amount=1&reward_item=Reward%20scans&timestamp=1507770365237823" +
  "&transaction_id=123456789&user_id=" + USER;

/** CALLBACK_DATA signed with GOOGLE_TEST_PRIVATE_KEY_PKCS8 over its decoded form. */
const SIGNED_CALLBACK = CALLBACK_DATA +
  "&signature=MEQCIGoRoSw54BnnCXdc3bgyOG5aiQVXtIbB6j44wk1r1gPNAiBmH75jL_P9oK93JSJljnA8LaRWDYv0W8q1zQ62fpoutQ" +
  "&key_id=1234";

// ─── Helpers ─────────────────────────────────────────────────────────────────

function assert(cond: unknown, msg = "assertion failed"): asserts cond {
  if (!cond) throw new Error(msg);
}
function assertEquals<T>(actual: T, expected: T, msg = ""): void {
  const a = JSON.stringify(actual);
  const e = JSON.stringify(expected);
  if (a !== e) throw new Error(`${msg} expected ${e}, got ${a}`);
}

const testKeys = () => new StaticKeySource(parseVerifierKeys(JSON.parse(GOOGLE_TEST_KEYS_JSON)));

function b64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}
function bytesToB64Url(bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** r || s → DER, the encoding AdMob sends. */
function p1363ToDer(sig: Uint8Array): Uint8Array {
  const int = (x: Uint8Array) => {
    let i = 0;
    while (i < x.length - 1 && x[i] === 0) i++;
    let v = x.subarray(i);
    if (v[0] & 0x80) v = Uint8Array.of(0, ...v);
    return Uint8Array.of(0x02, v.length, ...v);
  };
  const r = int(sig.subarray(0, 32));
  const s = int(sig.subarray(32));
  return Uint8Array.of(0x30, r.length + s.length, ...r, ...s);
}

/** Signs like Google: ECDSA P-256 SHA-256 over the decoded query, DER, web-safe base64. */
async function signQuery(rawData: string, keyId = "1234"): Promise<string> {
  const key = await crypto.subtle.importKey(
    "pkcs8",
    b64ToBytes(GOOGLE_TEST_PRIVATE_KEY_PKCS8),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  const raw = new Uint8Array(
    await crypto.subtle.sign(
      { name: "ECDSA", hash: "SHA-256" },
      key,
      new TextEncoder().encode(decodeURIComponent(rawData)),
    ),
  );
  return `${rawData}&signature=${bytesToB64Url(p1363ToDer(raw))}&key_id=${keyId}`;
}

// ─── Tests ───────────────────────────────────────────────────────────────────

Deno.test("fixed Google test vector verifies and yields the callback params", async () => {
  const result = await verifySsvQuery(SIGNED_CALLBACK, testKeys());
  assert(result.ok, `expected ok, got ${JSON.stringify(result)}`);
  assertEquals(result.params.transactionId, "123456789");
  assertEquals(result.params.userId, USER);
  assertEquals(result.params.rewardItem, "Reward scans");
  assertEquals(result.params.keyId, "1234");
});

Deno.test("a freshly signed callback verifies (also with a leading '?')", async () => {
  const url = await signQuery("foo1=bar1&foo2=bar2");
  assert((await verifySsvQuery(url, testKeys())).ok);
  assert((await verifySsvQuery("?" + url, testKeys())).ok);
});

Deno.test("percent-encoded values are verified over their decoded form, like Tink", async () => {
  const url = await signQuery("foo=hello%20world&bar=user%40gmail.com");
  assert((await verifySsvQuery(url, testKeys())).ok);
});

Deno.test("any change to the signed part of the fixed vector is rejected", async () => {
  const keys = testKeys();
  const tampered = [
    SIGNED_CALLBACK.replace("transaction_id=123456789", "transaction_id=123456780"),
    SIGNED_CALLBACK.replace("reward_amount=1", "reward_amount=9"),
    SIGNED_CALLBACK.replace("user_id=" + USER, "user_id=" + USER.replace("0b7f", "1b7f")),
  ];
  for (const t of tampered) {
    assertEquals(await verifySsvQuery(t, keys), { ok: false, reason: "bad_signature" }, t);
  }
});

Deno.test("a changed signature byte is rejected", async () => {
  const signed = splitSignedQuery(SIGNED_CALLBACK)!;
  const sig = Uint8Array.from(signed.signature);
  sig[sig.length - 1] ^= 0x01;
  const url = CALLBACK_DATA + "&signature=" + bytesToB64Url(sig) + "&key_id=1234";
  assertEquals((await verifySsvQuery(url, testKeys())).ok, false);
});

Deno.test("a different public key for the same id is rejected", async () => {
  const keys = new StaticKeySource(new Map([["1234", ALTERNATE_PUBLIC_KEY]]));
  assertEquals(await verifySsvQuery(SIGNED_CALLBACK, keys), { ok: false, reason: "bad_signature" });
});

Deno.test("an unknown key id is rejected", async () => {
  const url = SIGNED_CALLBACK.replace("&key_id=1234", "&key_id=1235");
  assertEquals(await verifySsvQuery(url, testKeys()), { ok: false, reason: "unknown_key" });
});

Deno.test("signature and key_id must be the last two parameters", async () => {
  const keys = testKeys();
  for (
    const bad of [
      CALLBACK_DATA,
      CALLBACK_DATA + "&signature=abc",
      CALLBACK_DATA + "&signature=abc&key_id=notlong",
      CALLBACK_DATA + "&signature=abc&key_id=123&bar=baz",
      "signature=abc&key_id=1234",
    ]
  ) {
    assertEquals(await verifySsvQuery(bad, keys), { ok: false, reason: "malformed" }, bad);
  }
});

Deno.test("derToP1363 pads short integers and strips the sign byte", () => {
  // r = 0x00ff.. (33 bytes with sign pad), s = 0x01 (1 byte)
  const r = new Uint8Array(33);
  r[1] = 0xff;
  const der = Uint8Array.of(0x30, 2 + 33 + 2 + 1, 0x02, 33, ...r, 0x02, 1, 0x01);
  const out = derToP1363(der);
  assertEquals(out.length, 64);
  assertEquals(out[0], 0xff);
  assertEquals(out[63], 0x01);
  let threw = false;
  try {
    derToP1363(Uint8Array.of(0x31, 0));
  } catch {
    threw = true;
  }
  assert(threw, "non-SEQUENCE input must throw");
});

Deno.test("parseVerifierKeys reads Google's live JSON shape (base64 and pem)", () => {
  const live = {
    keys: [{
      keyId: 3335741209,
      pem: "-----BEGIN PUBLIC KEY-----\nMFkwEw\nYHKo==\n-----END PUBLIC KEY-----",
      base64: "MFkwEwYHKo==",
    }, { keyId: 7, pem: "-----BEGIN PUBLIC KEY-----\nAAAA\nBBBB\n-----END PUBLIC KEY-----" }],
  };
  const keys = parseVerifierKeys(live);
  assertEquals(keys.get("3335741209"), "MFkwEwYHKo==");
  assertEquals(keys.get("7"), "AAAABBBB");
  assertEquals(parseVerifierKeys({ nope: 1 }).size, 0);
});

Deno.test("resolveSsvUserId prefers user_id, falls back to custom_data, requires a UUID", () => {
  assertEquals(resolveSsvUserId({ userId: USER.toUpperCase(), customData: "" }), USER);
  assertEquals(resolveSsvUserId({ userId: "", customData: USER }), USER);
  assertEquals(resolveSsvUserId({ userId: "not-a-uuid", customData: "' or 1=1" }), null);
  assertEquals(resolveSsvUserId({ userId: "", customData: "" }), null);
});

Deno.test("CachedVerifierKeys caches, refetches on an unknown id at most once a minute", async () => {
  let fetches = 0;
  let now = 1_000_000;
  let body: unknown = JSON.parse(GOOGLE_TEST_KEYS_JSON);
  const cache = new CachedVerifierKeys(async () => {
    fetches++;
    return body;
  }, 60 * 60 * 1000, 60 * 1000, () => now);

  assert(await cache.get("1234"));
  assert(await cache.get("1234"));
  assertEquals(fetches, 1, "hits are served from cache");

  assertEquals(await cache.get("9999"), null);
  assertEquals(fetches, 2, "unknown id triggers one refetch");
  assertEquals(await cache.get("9999"), null);
  assertEquals(fetches, 2, "second miss inside a minute does not refetch");

  // Key rotation: the new key appears and is picked up after the miss window.
  body = { keys: [{ keyId: 9999, base64: ALTERNATE_PUBLIC_KEY }] };
  now += 61 * 1000;
  assertEquals(await cache.get("9999"), ALTERNATE_PUBLIC_KEY);
  assertEquals(fetches, 3);

  // Expired TTL with the network down keeps serving the stale keys.
  now += 2 * 60 * 60 * 1000;
  const failing = cache as unknown as { fetchJson: () => Promise<unknown> };
  failing.fetchJson = () => Promise.reject(new Error("offline"));
  assertEquals(await cache.get("9999"), ALTERNATE_PUBLIC_KEY);
});

// ─── Route: idempotent grant per transaction_id ──────────────────────────────

/**
 * In-memory stand-in for fitcal.claim_ssv_reward (migration 0008) with the same contract:
 * the first call per transaction_id records it and grants +amount up to the daily cap;
 * any later call with that id is a 'duplicate' that grants nothing.
 */
class FakeClaimStore {
  claims = new Map<string, SsvClaimResult>();
  bonus = new Map<string, number>();
  calls: SsvClaimRequest[] = [];
  constructor(private readonly amount = 2, private readonly cap = 10, private readonly users = new Set([USER])) {}

  claim = (req: SsvClaimRequest): Promise<SsvClaimResult> => {
    this.calls.push(req);
    if (!this.users.has(req.userId)) return Promise.resolve({ status: "unknown_user", granted: 0, bonus: null });
    const seen = this.claims.get(req.transactionId);
    if (seen) return Promise.resolve({ status: "duplicate", granted: 0, bonus: seen.bonus });
    const before = this.bonus.get(req.userId) ?? 0;
    let result: SsvClaimResult;
    if (!req.grant) result = { status: "recorded", granted: 0, bonus: before };
    else if (before >= this.cap) result = { status: "capped", granted: 0, bonus: before };
    else {
      const after = Math.min(before + this.amount, this.cap);
      this.bonus.set(req.userId, after);
      result = { status: "granted", granted: after - before, bonus: after };
    }
    this.claims.set(req.transactionId, result);
    return Promise.resolve(result);
  };
}

async function callbackFor(txId: string, user = USER): Promise<string> {
  return await signQuery(
    `ad_network=5450213213286189855&ad_unit=1234567890&reward_amount=1&reward_item=Reward` +
      `&timestamp=1507770365237823&transaction_id=${txId}&user_id=${user}`,
  );
}

Deno.test("the same transaction_id grants exactly once, also on AdMob retries", async () => {
  const store = new FakeClaimStore();
  const deps = { keys: testKeys(), grant: true, claim: store.claim };
  const url = await callbackFor("tx-1");

  const first = await handleSsvCallback(url, deps);
  assertEquals(first, { status: 200, body: { status: "granted", granted: 2 } });
  for (let i = 0; i < 3; i++) {
    const retry = await handleSsvCallback(url, deps);
    assertEquals(retry, { status: 200, body: { status: "duplicate", granted: 0 } }, "retry");
  }
  assertEquals(store.bonus.get(USER), 2, "bonus granted once");

  const second = await handleSsvCallback(await callbackFor("tx-2"), deps);
  assertEquals(second.body.status, "granted");
  assertEquals(store.bonus.get(USER), 4, "a new transaction grants again");
  assertEquals(store.calls[0].transactionId, "tx-1");
  assertEquals(store.calls[0].userId, USER);
});

Deno.test("the daily cap still applies to SSV grants", async () => {
  const store = new FakeClaimStore(2, 10);
  const deps = { keys: testKeys(), grant: true, claim: store.claim };
  for (let i = 0; i < 5; i++) await handleSsvCallback(await callbackFor(`cap-${i}`), deps);
  assertEquals(store.bonus.get(USER), 10);
  const over = await handleSsvCallback(await callbackFor("cap-5"), deps);
  assertEquals(over, { status: 200, body: { status: "capped", granted: 0 } });
});

Deno.test("with SSV not yet required the callback is only recorded, never granted", async () => {
  const store = new FakeClaimStore();
  const res = await handleSsvCallback(await callbackFor("rec-1"), { keys: testKeys(), grant: false, claim: store.claim });
  assertEquals(res, { status: 200, body: { status: "recorded", granted: 0 } });
  assertEquals(store.bonus.get(USER), undefined);
  assertEquals(store.calls[0].grant, false);
});

Deno.test("forged, malformed and user-less callbacks never reach the grant", async () => {
  const store = new FakeClaimStore();
  const deps = { keys: testKeys(), grant: true, claim: store.claim };
  const valid = await callbackFor("tx-9");

  const forged = valid.replace("transaction_id=tx-9", "transaction_id=tx-8");
  assertEquals((await handleSsvCallback(forged, deps)).status, 403);
  assertEquals((await handleSsvCallback("transaction_id=tx-9", deps)).status, 400);
  assertEquals((await handleSsvCallback(valid.replace("&key_id=1234", "&key_id=4321"), deps)).status, 503);

  const noUser = await signQuery("ad_network=1&ad_unit=2&reward_amount=1&reward_item=R&timestamp=1&transaction_id=123456789");
  assertEquals(await handleSsvCallback(noUser, deps), { status: 200, body: { status: "ignored", reason: "no_user" } });

  const noTx = await signQuery(`ad_unit=2&reward_amount=1&user_id=${USER}`);
  assertEquals((await handleSsvCallback(noTx, deps)).status, 400);

  assertEquals(store.calls.length, 0, "no claim attempted");
});

Deno.test("a key outage answers 503 so AdMob retries", async () => {
  const keys = new CachedVerifierKeys(() => Promise.reject(new Error("gstatic down")));
  const store = new FakeClaimStore();
  const res = await handleSsvCallback(SIGNED_CALLBACK, { keys, grant: true, claim: store.claim });
  assertEquals(res.status, 503);
  assertEquals(store.calls.length, 0);
});
