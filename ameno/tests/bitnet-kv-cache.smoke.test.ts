/**
 * Smoke tests for `bitnet-kv-cache` (ADR-2605263800 R1b commit 7).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-kv-cache.smoke.test.ts
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import { KvCache } from "../src/inference/bitnet-kv-cache.ts";
import { parseBitNetConfig, type BitNetConfig } from "../src/inference/bitnet-config.ts";

/** Tiny config — manageable cache size for testing. */
const TINY_CONFIG_JSON = {
  hidden_size: 64,
  num_hidden_layers: 4,
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
// head_dim = 64/8 = 8; kv_dim = 2 × 8 = 16

test("KvCache — shape derived from config", () => {
  const cache = new KvCache(TINY_CONFIG);
  assert.equal(cache.shape.numLayers, 4);
  assert.equal(cache.shape.maxPos, 16);
  assert.equal(cache.shape.numKvHeads, 2);
  assert.equal(cache.shape.headDim, 8);
  assert.equal(cache.shape.kvDim, 16);
  // Total = 4 × 2 × 16 × 16 × 2 bytes = 4096 bytes
  assert.equal(cache.shape.totalBytes, 4 * 2 * 16 * 16 * 2);
});

test("KvCache — currentLength starts at 0", () => {
  const cache = new KvCache(TINY_CONFIG);
  assert.equal(cache.currentLength(), 0);
});

test("KvCache — appendPosition increments length", () => {
  const cache = new KvCache(TINY_CONFIG);
  // K and V are each numLayers × kvDim = 4 × 16 = 64 elements.
  const k = new Float32Array(4 * 16);
  const v = new Float32Array(4 * 16);
  cache.appendPosition(k, v);
  assert.equal(cache.currentLength(), 1);
  cache.appendPosition(k, v);
  assert.equal(cache.currentLength(), 2);
});

test("KvCache — appendPosition throws on wrong-sized buffer", () => {
  const cache = new KvCache(TINY_CONFIG);
  const k = new Float32Array(63); // wrong (should be 64)
  const v = new Float32Array(64);
  assert.throws(
    () => cache.appendPosition(k, v),
    /expected k\.length=v\.length=64/,
  );
});

test("KvCache — appendPosition throws when full", () => {
  const cache = new KvCache(TINY_CONFIG);
  const k = new Float32Array(64);
  const v = new Float32Array(64);
  for (let i = 0; i < 16; i++) {
    cache.appendPosition(k, v);
  }
  assert.equal(cache.currentLength(), 16);
  assert.throws(() => cache.appendPosition(k, v), /cache full/);
});

test("KvCache — slice returns appended K values (round-trip fp16)", () => {
  const cache = new KvCache(TINY_CONFIG);
  // Write a recognizable pattern. K[layer, kvDim] = layer * 100 + dim.
  const k = new Float32Array(4 * 16);
  const v = new Float32Array(4 * 16);
  for (let layer = 0; layer < 4; layer++) {
    for (let dim = 0; dim < 16; dim++) {
      k[layer * 16 + dim] = layer * 100 + dim;
      v[layer * 16 + dim] = -(layer * 100 + dim); // negative for V
    }
  }
  cache.appendPosition(k, v);
  cache.appendPosition(k, v);

  // Slice layer 2 K → 2 positions × 16 = 32 fp32 values.
  const sliceK = cache.slice(2, "k");
  assert.equal(sliceK.length, 32);
  for (let pos = 0; pos < 2; pos++) {
    for (let dim = 0; dim < 16; dim++) {
      const expected = 2 * 100 + dim; // layer 2
      const actual = sliceK[pos * 16 + dim]!;
      // fp16 round-trip → relative error <~ 1e-3 for values up to ~300.
      const rel = expected === 0 ? Math.abs(actual) : Math.abs((actual - expected) / expected);
      assert.ok(rel < 1e-3, `pos=${pos} dim=${dim}: expected ${expected}, got ${actual}`);
    }
  }

  // Slice layer 2 V → negative pattern.
  const sliceV = cache.slice(2, "v");
  for (let pos = 0; pos < 2; pos++) {
    for (let dim = 0; dim < 16; dim++) {
      const expected = -(2 * 100 + dim);
      const actual = sliceV[pos * 16 + dim]!;
      const rel = expected === 0 ? Math.abs(actual) : Math.abs((actual - expected) / expected);
      assert.ok(rel < 1e-3, `V pos=${pos} dim=${dim}: expected ${expected}, got ${actual}`);
    }
  }
});

test("KvCache — slice for unwritten layer returns zeros", () => {
  const cache = new KvCache(TINY_CONFIG);
  const k = new Float32Array(4 * 16);
  k[0] = 42;
  const v = new Float32Array(4 * 16);
  cache.appendPosition(k, v);
  // Layer 3 was not touched by k[0]; but our appendPosition writes
  // to ALL layers. So layer 3's K starts at k[3*16] = 0.
  const sliceLayer3 = cache.slice(3, "k");
  assert.equal(sliceLayer3.length, 16);
  for (let i = 0; i < 16; i++) {
    assert.equal(sliceLayer3[i], 0, `layer 3 K[${i}] = 0`);
  }
});

test("KvCache — clear resets length + buffer", () => {
  const cache = new KvCache(TINY_CONFIG);
  const k = new Float32Array(4 * 16);
  const v = new Float32Array(4 * 16);
  for (let i = 0; i < 64; i++) {
    k[i] = i + 1;
    v[i] = -(i + 1);
  }
  cache.appendPosition(k, v);
  cache.appendPosition(k, v);
  assert.equal(cache.currentLength(), 2);

  cache.clear();
  assert.equal(cache.currentLength(), 0);

  // After clear + 1 append, the slice should contain only fresh data.
  const newK = new Float32Array(4 * 16);
  newK[0] = 7;
  cache.appendPosition(newK, new Float32Array(4 * 16));
  const slice0 = cache.slice(0, "k");
  assert.equal(slice0.length, 16);
  assert.ok(Math.abs(slice0[0]! - 7) < 1e-3);
  for (let i = 1; i < 16; i++) {
    assert.equal(slice0[i], 0, `slice0[${i}] = 0 after clear`);
  }
});

test("KvCache — sliceInto reuses caller-allocated buffer", () => {
  const cache = new KvCache(TINY_CONFIG);
  const k = new Float32Array(4 * 16);
  const v = new Float32Array(4 * 16);
  for (let i = 0; i < 64; i++) {
    k[i] = i;
  }
  cache.appendPosition(k, v);
  cache.appendPosition(k, v);

  const buf = new Float32Array(100); // larger than needed
  cache.sliceInto(1, "k", buf);
  // Layer 1 K = k[16..32] = [16, 17, ..., 31]
  for (let pos = 0; pos < 2; pos++) {
    for (let dim = 0; dim < 16; dim++) {
      const expected = 16 + dim;
      const actual = buf[pos * 16 + dim]!;
      const rel = Math.abs((actual - expected) / expected);
      assert.ok(rel < 1e-3, `pos=${pos} dim=${dim}: ${expected} vs ${actual}`);
    }
  }
});

test("KvCache — sliceInto throws on short buffer", () => {
  const cache = new KvCache(TINY_CONFIG);
  const k = new Float32Array(4 * 16);
  const v = new Float32Array(4 * 16);
  cache.appendPosition(k, v);
  cache.appendPosition(k, v);
  // needs 2 × 16 = 32 elements; give 31.
  const short = new Float32Array(31);
  assert.throws(() => cache.sliceInto(0, "k", short), /out\.length=31 < needed=32/);
});

test("KvCache — readHeadPosition returns single position single head", () => {
  const cache = new KvCache(TINY_CONFIG);
  const k = new Float32Array(4 * 16);
  // Layer 2, kv head 1, dim 3 has value 99.
  // Index = layer 2 base (32) + head 1 offset (8) + dim 3 = 32 + 8 + 3 = 43.
  k[2 * 16 + 1 * 8 + 3] = 99;
  cache.appendPosition(k, new Float32Array(4 * 16));

  const head = cache.readHeadPosition(2, "k", 1, 0);
  assert.equal(head.length, 8); // head_dim
  for (let i = 0; i < 8; i++) {
    if (i === 3) {
      assert.ok(Math.abs(head[i]! - 99) < 0.5, `dim=3: ${head[i]} ≈ 99`);
    } else {
      assert.equal(head[i], 0, `dim=${i}: 0`);
    }
  }
});

test("KvCache — readHeadPosition throws on out-of-range indices", () => {
  const cache = new KvCache(TINY_CONFIG);
  const k = new Float32Array(4 * 16);
  cache.appendPosition(k, new Float32Array(4 * 16));
  assert.throws(() => cache.readHeadPosition(4, "k", 0, 0), /layer out of range/);
  assert.throws(() => cache.readHeadPosition(0, "k", 2, 0), /kvHead out of range/);
  assert.throws(() => cache.readHeadPosition(0, "k", 0, 1), /position=1 out of \[0, currentLength=1\)/);
});

test("KvCache — BitNet 2B-sized allocation succeeds and reports correct totalBytes", () => {
  const bigConfig = parseBitNetConfig({
    ...TINY_CONFIG_JSON,
    hidden_size: 2048,
    num_hidden_layers: 30,
    num_attention_heads: 32,
    num_key_value_heads: 8,
    max_position_embeddings: 4096,
  });
  const cache = new KvCache(bigConfig);
  // 30 × 2 × 4096 × (8 × 64) × 2 bytes = 30 × 2 × 4096 × 512 × 2 = 251_658_240 bytes (240 MiB)
  assert.equal(cache.shape.totalBytes, 30 * 2 * 4096 * 512 * 2);
  assert.equal(cache.shape.totalBytes, 251_658_240);
});
