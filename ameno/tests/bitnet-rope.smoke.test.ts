/**
 * Smoke tests for `bitnet-rope` (ADR-2605263800 R1b commit 6).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-rope.smoke.test.ts
 *
 * Test strategy:
 *   - precomputeRope produces correctly-shaped tables.
 *   - At position 0: cos=1, sin=0 → applyRope is identity.
 *   - At any position: rotation preserves the L2 norm of each pair.
 *   - Multi-head dispatch agrees with single-head loop.
 *   - Out-of-range position throws.
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  precomputeRope,
  applyRopeHfStyle,
  applyRopeMultiHead,
} from "../src/inference/bitnet-rope.ts";
import { parseBitNetConfig, type BitNetConfig } from "../src/inference/bitnet-config.ts";

/** Small test config — head_dim=8 (halfHead=4) to keep tables small. */
const TINY_CONFIG_JSON = {
  hidden_size: 64,
  num_hidden_layers: 2,
  num_attention_heads: 8,
  num_key_value_heads: 2,
  intermediate_size: 128,
  vocab_size: 1024,
  max_position_embeddings: 16,
  rope_theta: 10_000,
  rms_norm_eps: 1e-5,
  tie_word_embeddings: false,
};

const TINY_CONFIG: BitNetConfig = parseBitNetConfig(TINY_CONFIG_JSON);

test("precomputeRope — table shapes", () => {
  const cache = precomputeRope(TINY_CONFIG);
  // head_dim = 64/8 = 8 → halfHead = 4
  assert.equal(cache.halfHead, 4);
  assert.equal(cache.maxPos, 16);
  assert.equal(cache.base, 10_000);
  assert.equal(cache.cos.length, 16 * 4);
  assert.equal(cache.sin.length, 16 * 4);
});

test("precomputeRope — position 0 has cos=1, sin=0", () => {
  const cache = precomputeRope(TINY_CONFIG);
  for (let i = 0; i < cache.halfHead; i++) {
    assert.equal(cache.cos[i], 1, `cos[0, ${i}] = 1`);
    assert.equal(cache.sin[i], 0, `sin[0, ${i}] = 0`);
  }
});

test("precomputeRope — position 1, dim 0 is (cos(1), sin(1))", () => {
  const cache = precomputeRope(TINY_CONFIG);
  // At position 1, dim 0: angle = 1 × base^(0/head_dim) = 1 × 1 = 1
  const off = 1 * cache.halfHead;
  assert.ok(
    Math.abs(cache.cos[off + 0]! - Math.cos(1)) < 1e-6,
    `cos[1, 0] ≈ cos(1)`,
  );
  assert.ok(
    Math.abs(cache.sin[off + 0]! - Math.sin(1)) < 1e-6,
    `sin[1, 0] ≈ sin(1)`,
  );
});

test("applyRopeHfStyle — position 0 is identity", () => {
  const cache = precomputeRope(TINY_CONFIG);
  const vec = new Float32Array([1, 2, 3, 4, 5, 6, 7, 8]);
  const original = Array.from(vec);
  applyRopeHfStyle(vec, 0, cache);
  for (let i = 0; i < 8; i++) {
    assert.ok(
      Math.abs(vec[i]! - original[i]!) < 1e-6,
      `i=${i}: ${vec[i]} ≈ ${original[i]}`,
    );
  }
});

test("applyRopeHfStyle — preserves L2 norm of each split-half pair", () => {
  const cache = precomputeRope(TINY_CONFIG);
  const vec = new Float32Array([1, 2, 3, 4, 5, 6, 7, 8]);
  // Pairs (split-half): (vec[0], vec[4]), (vec[1], vec[5]), (vec[2], vec[6]), (vec[3], vec[7])
  const origNorms: number[] = [];
  for (let i = 0; i < cache.halfHead; i++) {
    const a = vec[i]!;
    const b = vec[i + cache.halfHead]!;
    origNorms.push(Math.sqrt(a * a + b * b));
  }
  applyRopeHfStyle(vec, 7, cache); // arbitrary position
  for (let i = 0; i < cache.halfHead; i++) {
    const a = vec[i]!;
    const b = vec[i + cache.halfHead]!;
    const newNorm = Math.sqrt(a * a + b * b);
    assert.ok(
      Math.abs(newNorm - origNorms[i]!) < 1e-5,
      `pair ${i}: norm preserved ${origNorms[i]} → ${newNorm}`,
    );
  }
});

test("applyRopeHfStyle — full rotation (specific angle) matches manual computation", () => {
  const cache = precomputeRope(TINY_CONFIG);
  // At position 1, dim 0: angle = 1.
  // Pair: (vec[0], vec[4]) → (vec[0]*cos(1) - vec[4]*sin(1), vec[0]*sin(1) + vec[4]*cos(1))
  const vec = new Float32Array([2, 0, 0, 0, 3, 0, 0, 0]);
  applyRopeHfStyle(vec, 1, cache);
  const c = Math.cos(1);
  const s = Math.sin(1);
  const expected0 = 2 * c - 3 * s;
  const expected4 = 2 * s + 3 * c;
  assert.ok(Math.abs(vec[0]! - expected0) < 1e-6, `vec[0] = 2 cos(1) - 3 sin(1)`);
  assert.ok(Math.abs(vec[4]! - expected4) < 1e-6, `vec[4] = 2 sin(1) + 3 cos(1)`);
});

test("applyRopeHfStyle — out-of-range position throws", () => {
  const cache = precomputeRope(TINY_CONFIG);
  const vec = new Float32Array(8);
  assert.throws(() => applyRopeHfStyle(vec, 16, cache), /out of \[0, 16\)/);
  assert.throws(() => applyRopeHfStyle(vec, -1, cache), /out of \[0, 16\)/);
});

test("applyRopeHfStyle — wrong vec length throws", () => {
  const cache = precomputeRope(TINY_CONFIG);
  const vec = new Float32Array(7); // head_dim=8 expected
  assert.throws(() => applyRopeHfStyle(vec, 0, cache), /vec\.length=7/);
});

test("applyRopeMultiHead — matches per-head dispatch", () => {
  const cache = precomputeRope(TINY_CONFIG);
  const numHeads = 3;
  const headDim = 8;
  const data = new Float32Array(numHeads * headDim);
  for (let i = 0; i < data.length; i++) {
    data[i] = Math.sin(i * 0.3) * 1.7;
  }
  const dataCopy = new Float32Array(data);

  // Apply via the multi-head helper.
  applyRopeMultiHead(data, numHeads, 5, cache);

  // Apply manually one head at a time.
  for (let h = 0; h < numHeads; h++) {
    const view = dataCopy.subarray(h * headDim, (h + 1) * headDim);
    applyRopeHfStyle(view, 5, cache);
  }

  // Should agree byte-for-byte.
  for (let i = 0; i < data.length; i++) {
    assert.ok(
      Math.abs(data[i]! - dataCopy[i]!) < 1e-6,
      `i=${i}: ${data[i]} != ${dataCopy[i]}`,
    );
  }
});

test("applyRopeMultiHead — wrong total length throws", () => {
  const cache = precomputeRope(TINY_CONFIG);
  const data = new Float32Array(23); // 3 × 8 = 24 expected
  assert.throws(
    () => applyRopeMultiHead(data, 3, 0, cache),
    /vec\.length=23/,
  );
});

test("precomputeRope — large BitNet 2B-like config builds in <100ms", () => {
  const bigConfig = parseBitNetConfig({
    ...TINY_CONFIG_JSON,
    hidden_size: 2048,
    num_attention_heads: 32, // head_dim=64 → halfHead=32
    max_position_embeddings: 4096,
    rope_theta: 500_000,
  });
  const start = Date.now();
  const cache = precomputeRope(bigConfig);
  const elapsed = Date.now() - start;
  assert.equal(cache.cos.length, 4096 * 32);
  assert.ok(elapsed < 100, `precompute took ${elapsed}ms (expected <100)`);
});
