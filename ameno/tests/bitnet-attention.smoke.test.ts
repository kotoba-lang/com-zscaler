/**
 * Smoke tests for `bitnet-attention` (ADR-2605263800 R1b commit 11).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-attention.smoke.test.ts
 *
 * Strategy: we don't have a reference implementation to bit-compare
 * against (that's R1b commit 14 microbench's job, against the
 * transformers.js fp16 path). For now we verify:
 *   - shape preservation (output is hidden_size)
 *   - KV cache writes happen at (layerIdx, position) and length advances
 *   - determinism (same input → same output across calls)
 *   - position 0 self-attention is finite (no NaN, no Inf)
 *   - zero hidden input gives zero output (no spurious noise)
 *   - throws on wrong-shaped hidden / out
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  applyAttention,
  allocateAttentionScratch,
  type AttentionLayerWeights,
} from "../src/inference/bitnet-attention.ts";
import { parseBitNetConfig } from "../src/inference/bitnet-config.ts";
import { precomputeRope } from "../src/inference/bitnet-rope.ts";
import { KvCache } from "../src/inference/bitnet-kv-cache.ts";
import { transformBf16ToI2sAndScale } from "../src/inference/bitnet-weight-transformer.ts";
import {
  DataType,
  TensorProtoView,
  decodeMessage,
  encodeTensorProto,
} from "../src/inference/onnx-proto-min.ts";

// ──────────────────────────────────────────────────────────────
// Fixture helpers
// ──────────────────────────────────────────────────────────────

const TINY_CONFIG = parseBitNetConfig({
  hidden_size: 16, // → 4 heads × 4 head_dim
  num_hidden_layers: 2,
  num_attention_heads: 4,
  num_key_value_heads: 2, // GQA group_size = 4/2 = 2
  intermediate_size: 32,
  vocab_size: 64,
  max_position_embeddings: 8,
  rope_theta: 10_000,
  rms_norm_eps: 1e-5,
  tie_word_embeddings: false,
});

function encodeBf16(f32: Float32Array): Uint8Array {
  const out = new Uint8Array(f32.length * 2);
  const dv = new DataView(out.buffer);
  const tmp = new ArrayBuffer(4);
  const tmpF = new Float32Array(tmp);
  const tmpU = new Uint32Array(tmp);
  for (let i = 0; i < f32.length; i++) {
    tmpF[0] = f32[i]!;
    const bf16Bits = (tmpU[0]! >>> 16) & 0xffff;
    dv.setUint16(i * 2, bf16Bits, true);
  }
  return out;
}

function buildPack(rows: number, cols: number, seed: number, name = "W") {
  const f32 = new Float32Array(rows * cols);
  // Deterministic pseudo-random weights in roughly [-1, +1].
  for (let i = 0; i < f32.length; i++) {
    f32[i] = Math.sin((i + seed) * 0.137) * 0.8;
  }
  const bf16Bytes = encodeBf16(f32);
  const tensorBytes = encodeTensorProto({
    name,
    dataType: DataType.BFLOAT16,
    dims: [rows, cols],
    rawData: bf16Bytes,
  });
  const view = new TensorProtoView(decodeMessage(tensorBytes));
  const { packed, scale } = transformBf16ToI2sAndScale(name, view);
  return {
    origName: name,
    packed: packed.rawData,
    scale: scale.rawData,
    dims: [rows, cols] as readonly [number, number],
  };
}

function buildLayerWeights(): AttentionLayerWeights {
  const H = TINY_CONFIG.hidden_size; // 16
  const KV = TINY_CONFIG.num_key_value_heads * TINY_CONFIG.head_dim; // 2 × 4 = 8
  return {
    q_proj: buildPack(H, H, 1, "q_proj"), // [16, 16]
    k_proj: buildPack(KV, H, 2, "k_proj"), // [8, 16]
    v_proj: buildPack(KV, H, 3, "v_proj"), // [8, 16]
    o_proj: buildPack(H, H, 4, "o_proj"), // [16, 16]
  };
}

// ──────────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────────

test("applyAttention — shape preservation at position 0", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateAttentionScratch(TINY_CONFIG);

  const hidden = new Float32Array(16);
  for (let i = 0; i < 16; i++) hidden[i] = Math.sin(i * 0.3) * 0.5;
  const out = new Float32Array(16);

  applyAttention(hidden, 0, 0, weights, ropeCache, kvCache, TINY_CONFIG, scratch, out);

  // Output length unchanged.
  assert.equal(out.length, 16);
  // Output finite.
  for (let i = 0; i < 16; i++) {
    assert.ok(Number.isFinite(out[i]!), `out[${i}] = ${out[i]} (finite)`);
  }
});

test("applyAttention — KV cache state after one step", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateAttentionScratch(TINY_CONFIG);

  assert.equal(kvCache.currentLength(), 0);

  const hidden = new Float32Array(16).fill(0.1);
  const out = new Float32Array(16);
  applyAttention(hidden, 0, 0, weights, ropeCache, kvCache, TINY_CONFIG, scratch, out);

  // currentLength advanced to 1.
  assert.equal(kvCache.currentLength(), 1);
  // K cache at (layer 0, position 0) has SOMETHING (not all zeros).
  const k0 = kvCache.slice(0, "k");
  assert.equal(k0.length, 1 * 8); // 1 position × kvDim=8
  let nonZero = 0;
  for (const v of k0) if (Math.abs(v) > 1e-6) nonZero++;
  assert.ok(nonZero > 0, "K cache slot 0 wrote non-zero values");
});

test("applyAttention — second layer at same position re-uses length", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights0 = buildLayerWeights();
  const weights1 = buildLayerWeights(); // same shape, different seed would help but ok
  const scratch = allocateAttentionScratch(TINY_CONFIG);

  const hidden = new Float32Array(16).fill(0.1);
  const out = new Float32Array(16);
  applyAttention(hidden, 0, 0, weights0, ropeCache, kvCache, TINY_CONFIG, scratch, out);
  applyAttention(hidden, 0, 1, weights1, ropeCache, kvCache, TINY_CONFIG, scratch, out);
  // After two layers at position 0, length is still 1.
  assert.equal(kvCache.currentLength(), 1);
  // Both layers have K/V written at position 0.
  const k0 = kvCache.slice(0, "k");
  const k1 = kvCache.slice(1, "k");
  assert.equal(k0.length, 8);
  assert.equal(k1.length, 8);
});

test("applyAttention — multi-position decode advances length", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights0 = buildLayerWeights();
  const scratch = allocateAttentionScratch(TINY_CONFIG);

  const out = new Float32Array(16);
  for (let p = 0; p < 5; p++) {
    const hidden = new Float32Array(16);
    for (let i = 0; i < 16; i++) hidden[i] = Math.cos(i + p) * 0.3;
    applyAttention(hidden, p, 0, weights0, ropeCache, kvCache, TINY_CONFIG, scratch, out);
    assert.equal(kvCache.currentLength(), p + 1);
  }
});

test("applyAttention — determinism: same inputs → same output", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const weights = buildLayerWeights();

  const hidden = new Float32Array(16);
  for (let i = 0; i < 16; i++) hidden[i] = Math.sin(i * 0.7) * 0.4;

  // Run twice with fresh caches; outputs should agree byte-for-byte.
  const out1 = new Float32Array(16);
  const kvCache1 = new KvCache(TINY_CONFIG);
  const scratch1 = allocateAttentionScratch(TINY_CONFIG);
  applyAttention(hidden, 0, 0, weights, ropeCache, kvCache1, TINY_CONFIG, scratch1, out1);

  const out2 = new Float32Array(16);
  const kvCache2 = new KvCache(TINY_CONFIG);
  const scratch2 = allocateAttentionScratch(TINY_CONFIG);
  applyAttention(hidden, 0, 0, weights, ropeCache, kvCache2, TINY_CONFIG, scratch2, out2);

  for (let i = 0; i < 16; i++) {
    assert.equal(out1[i], out2[i], `i=${i}: ${out1[i]} != ${out2[i]}`);
  }
});

test("applyAttention — zero hidden input gives zero output (no spurious noise)", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateAttentionScratch(TINY_CONFIG);

  const hidden = new Float32Array(16); // all zeros
  const out = new Float32Array(16);
  applyAttention(hidden, 0, 0, weights, ropeCache, kvCache, TINY_CONFIG, scratch, out);

  // Q = K = V = 0 (BitLinear of zero is zero). After RoPE: still zero
  // (rotation of zero is zero). After scores: all zero → softmax of
  // uniform zeros = uniform 1/seqLen. Weighted sum of zero V = 0.
  // After o_proj: BitLinear of zero = zero.
  for (let i = 0; i < 16; i++) {
    assert.equal(out[i], 0, `out[${i}] = ${out[i]} (expected 0)`);
  }
});

test("applyAttention — throws on hidden shape mismatch", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateAttentionScratch(TINY_CONFIG);

  const hidden = new Float32Array(15); // wrong
  const out = new Float32Array(16);
  assert.throws(
    () => applyAttention(hidden, 0, 0, weights, ropeCache, kvCache, TINY_CONFIG, scratch, out),
    /hidden\.length=15 != hidden_size=16/,
  );
});

test("applyAttention — throws on out shape mismatch", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateAttentionScratch(TINY_CONFIG);

  const hidden = new Float32Array(16);
  const out = new Float32Array(15); // wrong
  assert.throws(
    () => applyAttention(hidden, 0, 0, weights, ropeCache, kvCache, TINY_CONFIG, scratch, out),
    /out\.length=15 != hidden_size=16/,
  );
});

test("allocateAttentionScratch — buffer sizes match config", () => {
  const scratch = allocateAttentionScratch(TINY_CONFIG);
  assert.equal(scratch.q.length, 16); // hidden_size
  assert.equal(scratch.k.length, 8); // kvDim = 2 × 4
  assert.equal(scratch.v.length, 8);
  assert.equal(scratch.kCacheSlice.length, 8 * 8); // maxPos × kvDim
  assert.equal(scratch.vCacheSlice.length, 8 * 8);
  assert.equal(scratch.scores.length, 8); // maxPos
  assert.equal(scratch.attnOut.length, 16);
});
