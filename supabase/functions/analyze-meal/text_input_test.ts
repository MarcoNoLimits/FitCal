// Run with: deno test supabase/functions/analyze-meal/text_input_test.ts
import {
  appendHint,
  buildDescriptionPrompt,
  cleanHint,
  MAX_DESCRIPTION_CHARS,
  MAX_HINT_CHARS,
  validateDescription,
} from "./text_input.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function assertEquals(actual: unknown, expected: unknown, message = "") {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message} expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

Deno.test("rejects a missing or non-string description", () => {
  assertEquals(validateDescription(undefined).ok, false);
  assertEquals(validateDescription(null).ok, false);
  assertEquals(validateDescription(42).ok, false);
  assertEquals(validateDescription({ text: "eggs" }).ok, false);
});

Deno.test("rejects an empty or whitespace-only description", () => {
  assertEquals(validateDescription("").ok, false);
  assertEquals(validateDescription("   \n\t ").ok, false);
  assertEquals(validateDescription("\u0000\u0007").ok, false);
});

Deno.test("rejects a description over the cap and accepts one exactly at it", () => {
  const atCap = "a".repeat(MAX_DESCRIPTION_CHARS);
  const check = validateDescription(atCap);
  assert(check.ok, "500 characters must pass");
  assertEquals(check.description.length, MAX_DESCRIPTION_CHARS);

  const over = validateDescription("a".repeat(MAX_DESCRIPTION_CHARS + 1));
  assertEquals(over.ok, false);
  assert(!over.ok && over.message.includes(String(MAX_DESCRIPTION_CHARS)), "message names the cap");
});

Deno.test("trims and flattens control characters and newlines", () => {
  const check = validateDescription("  2 eggs,\ntoast with butter,\r\n\tblack coffee  ");
  assert(check.ok, "valid text must pass");
  assertEquals(check.description, "2 eggs, toast with butter, black coffee");
});

Deno.test("measures the cap after cleaning, so padding does not count", () => {
  const padded = `   ${"b".repeat(MAX_DESCRIPTION_CHARS)}   \n`;
  assert(validateDescription(padded).ok, "surrounding whitespace is not counted");
});

Deno.test("description prompt contains the text between markers", () => {
  const prompt = buildDescriptionPrompt("2 eggs, toast with butter, black coffee");
  assert(prompt.includes("<<<MEAL\n2 eggs, toast with butter, black coffee\nMEAL>>>"), prompt);
  assert(prompt.includes("no photo"), "prompt says there is no image");
});

Deno.test("hint: blank or missing gives null, long hints are cut", () => {
  assertEquals(cleanHint(undefined), null);
  assertEquals(cleanHint(""), null);
  assertEquals(cleanHint("   "), null);
  assertEquals(cleanHint(7), null);
  assertEquals(cleanHint(" it was fried\nin butter "), "it was fried in butter");
  assertEquals(cleanHint("x".repeat(MAX_HINT_CHARS + 50))!.length, MAX_HINT_CHARS);
});

Deno.test("appendHint leaves the prompt alone without a hint", () => {
  assertEquals(appendHint("Items:\n- Egg: 100g", null), "Items:\n- Egg: 100g");
  const withHint = appendHint("Items:\n- Egg: 100g", "it was fried in butter");
  assert(withHint.startsWith("Items:\n- Egg: 100g\n"), withHint);
  assert(withHint.includes("<<<NOTE\nit was fried in butter\nNOTE>>>"), withHint);
});
