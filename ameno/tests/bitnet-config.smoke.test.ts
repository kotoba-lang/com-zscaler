/**
 * Smoke tests for `bitnet-config` (ADR-2605263800 R1b commit 5).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-config.smoke.test.ts
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  parseBitNetConfig,
  assertCanonicalBitNet2b,
  computeBufferSizes,
  BITNET_2B_4T_CANONICAL,
  type BitNetConfig,
} from "../src/inference/bitnet-config.ts";

/**
 * Fixture config matching the canonical BitNet 2B 4T values.
 *
 * Field names + values match what
 * `microsoft/bitnet-b1.58-2B-4T-bf16-ONNX/config.json` ships with on
 * HuggingFace as of 2026-05-26.
 */
const FIXTURE_BITNET_2B_4T = {
  architectures: ["BitNetForCausalLM"],
  bos_token_id: 1,
  eos_token_id: 2,
  hidden_act: "silu",
  hidden_size: 2048,
  initializer_range: 0.02,
  intermediate_size: 5632,
  max_position_embeddings: 4096,
  model_type: "bitnet",
  num_attention_heads: 32,
  num_hidden_layers: 30,
  num_key_value_heads: 8,
  pretraining_tp: 1,
  rms_norm_eps: 1e-5,
  rope_scaling: null,
  rope_theta: 500_000,
  tie_word_embeddings: false,
  torch_dtype: "bfloat16",
  transformers_version: "4.40.0",
  use_cache: true,
  vocab_size: 128_256,
};

test("parseBitNetConfig — happy path: BitNet 2B 4T fixture", () => {
  const config = parseBitNetConfig(JSON.stringify(FIXTURE_BITNET_2B_4T));
  assert.equal(config.hidden_size, 2048);
  assert.equal(config.num_hidden_layers, 30);
  assert.equal(config.num_attention_heads, 32);
  assert.equal(config.num_key_value_heads, 8);
  assert.equal(config.head_dim, 64, "derived head_dim = 2048/32");
  assert.equal(config.intermediate_size, 5632);
  assert.equal(config.vocab_size, 128_256);
  assert.equal(config.max_position_embeddings, 4096);
  assert.equal(config.rope_theta, 500_000);
  assert.equal(config.rms_norm_eps, 1e-5);
  assert.equal(config.tie_word_embeddings, false);
});

test("parseBitNetConfig — accepts pre-parsed object input", () => {
  const config = parseBitNetConfig(FIXTURE_BITNET_2B_4T);
  assert.equal(config.head_dim, 64);
});

test("parseBitNetConfig — returns frozen object", () => {
  const config = parseBitNetConfig(FIXTURE_BITNET_2B_4T);
  assert.ok(Object.isFrozen(config), "returned config is frozen");
});

test("parseBitNetConfig — throws on missing field", () => {
  const incomplete = { ...FIXTURE_BITNET_2B_4T } as Record<string, unknown>;
  delete incomplete["intermediate_size"];
  assert.throws(
    () => parseBitNetConfig(incomplete),
    /missing or non-numeric field 'intermediate_size'/,
  );
});

test("parseBitNetConfig — throws on non-divisible hidden_size / num_attention_heads", () => {
  const broken = { ...FIXTURE_BITNET_2B_4T, hidden_size: 2049 }; // not divisible by 32
  assert.throws(
    () => parseBitNetConfig(broken),
    /not divisible by num_attention_heads/,
  );
});

test("parseBitNetConfig — throws on wrong field type", () => {
  const broken = { ...FIXTURE_BITNET_2B_4T, hidden_size: "two thousand forty-eight" };
  assert.throws(
    () => parseBitNetConfig(broken),
    /missing or non-numeric field 'hidden_size'/,
  );
});

test("parseBitNetConfig — throws on missing boolean field", () => {
  const broken = { ...FIXTURE_BITNET_2B_4T } as Record<string, unknown>;
  delete broken["tie_word_embeddings"];
  assert.throws(
    () => parseBitNetConfig(broken),
    /missing or non-boolean field 'tie_word_embeddings'/,
  );
});

test("assertCanonicalBitNet2b — passes on canonical fixture", () => {
  const config = parseBitNetConfig(FIXTURE_BITNET_2B_4T);
  // Should NOT throw.
  assertCanonicalBitNet2b(config);
});

test("assertCanonicalBitNet2b — throws on hidden_size mismatch", () => {
  const variant = { ...FIXTURE_BITNET_2B_4T, hidden_size: 4096, num_attention_heads: 32 };
  // head_dim becomes 128, but other canonical values don't match.
  const config = parseBitNetConfig(variant);
  assert.throws(
    () => assertCanonicalBitNet2b(config),
    /hidden_size: expected 2048, got 4096/,
  );
});

test("assertCanonicalBitNet2b — tolerance on rms_norm_eps", () => {
  // Slight variant in the eps shouldn't trip if tolerance is set.
  const variant = { ...FIXTURE_BITNET_2B_4T, rms_norm_eps: 1.1e-5 };
  const config = parseBitNetConfig(variant);
  assert.throws(() => assertCanonicalBitNet2b(config), /rms_norm_eps/);
  // With tolerance it passes.
  assertCanonicalBitNet2b(config, { rmsNormEpsTolerance: 1e-6 });
});

test("computeBufferSizes — canonical BitNet 2B 4T values", () => {
  const config = parseBitNetConfig(FIXTURE_BITNET_2B_4T);
  const sizes = computeBufferSizes(config);
  assert.equal(sizes.embeddingDim, 2048);
  assert.equal(sizes.qProjOut, 2048, "32 heads × 64 head_dim = 2048");
  assert.equal(sizes.kvProjOut, 512, "8 KV heads × 64 head_dim = 512 (GQA)");
  assert.equal(sizes.ffnInner, 5632);
  assert.equal(sizes.lmHeadOut, 128_256);
  assert.equal(sizes.gqaGroupSize, 4, "32 Q heads / 8 KV heads = 4");
  assert.ok(Object.isFrozen(sizes), "sizes are frozen");
});

test("BITNET_2B_4T_CANONICAL — exposed constants match fixture", () => {
  assert.equal(BITNET_2B_4T_CANONICAL.hidden_size, FIXTURE_BITNET_2B_4T.hidden_size);
  assert.equal(BITNET_2B_4T_CANONICAL.num_hidden_layers, FIXTURE_BITNET_2B_4T.num_hidden_layers);
  assert.equal(BITNET_2B_4T_CANONICAL.num_attention_heads, FIXTURE_BITNET_2B_4T.num_attention_heads);
  assert.equal(BITNET_2B_4T_CANONICAL.vocab_size, FIXTURE_BITNET_2B_4T.vocab_size);
  // head_dim isn't in the canonical constants — it's derived per
  // `computeBufferSizes(config)`. The previous test covers it.
});
