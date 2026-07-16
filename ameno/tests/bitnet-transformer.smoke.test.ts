/**
 * Smoke tests for `bitnet-transformer` (ADR-2605263800 R1b commit 13).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-transformer.smoke.test.ts
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  applyTransformerBlock,
  allocateTransformerBlockScratch,
  type TransformerLayerWeights,
} from "../src/inference/bitnet-transformer.ts";
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
// Fixtures
// ──────────────────────────────────────────────────────────────

const TINY_CONFIG = parseBitNetConfig({
  hidden_size: 16,
  num_hidden_layers: 2,
  num_attention_heads: 4,
  num_key_value_heads: 2,
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
  for (let i = 0; i < f32.length; i++) {
    f32[i] = Math.sin((i + seed) * 0.137) * 0.8;
  }
  const tensorBytes = encodeTensorProto({
    name,
    dataType: DataType.BFLOAT16,
    dims: [rows, cols],
    rawData: encodeBf16(f32),
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

function buildLayerWeights(): TransformerLayerWeights {
  const H = TINY_CONFIG.hidden_size; // 16
  const KV = TINY_CONFIG.num_key_value_heads * TINY_CONFIG.head_dim; // 8
  const F = TINY_CONFIG.intermediate_size; // 32
  // RMSNorm scale vectors initialised to all-ones (identity scale).
  const norm_w = new Float32Array(H);
  norm_w.fill(1.0);
  return {
    attn: {
      q_proj: buildPack(H, H, 1, "q_proj"),
      k_proj: buildPack(KV, H, 2, "k_proj"),
      v_proj: buildPack(KV, H, 3, "v_proj"),
      o_proj: buildPack(H, H, 4, "o_proj"),
    },
    ffn: {
      gate_proj: buildPack(F, H, 10, "gate_proj"),
      up_proj: buildPack(F, H, 20, "up_proj"),
      down_proj: buildPack(H, F, 30, "down_proj"),
    },
    input_layernorm_w: norm_w,
    post_attention_layernorm_w: new Float32Array(norm_w), // copy
  };
}

// ──────────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────────

test("applyTransformerBlock — shape preservation + all-finite", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateTransformerBlockScratch(TINY_CONFIG);

  const hidden = new Float32Array(16);
  for (let i = 0; i < 16; i++) hidden[i] = Math.sin(i * 0.4) * 0.3;
  const before = new Float32Array(hidden); // snapshot

  applyTransformerBlock(
    hidden,
    0,
    0,
    weights,
    ropeCache,
    kvCache,
    TINY_CONFIG,
    scratch,
  );

  assert.equal(hidden.length, 16);
  for (let i = 0; i < 16; i++) {
    assert.ok(Number.isFinite(hidden[i]!), `hidden[${i}] = ${hidden[i]} finite`);
  }
  // Block actually changed something (the residual + sublayer outputs
  // shouldn't all be zero for a non-zero input).
  let diffCount = 0;
  for (let i = 0; i < 16; i++) {
    if (Math.abs(hidden[i]! - before[i]!) > 1e-6) diffCount++;
  }
  assert.ok(diffCount > 0, "block mutated at least some elements");
});

test("applyTransformerBlock — zero input → zero output", () => {
  // hidden = 0 → RMSNorm(0) = 0 → attn(0) = 0 → residual + 0 = 0.
  // Then RMSNorm(0) = 0 → ffn(0) = 0 → residual + 0 = 0.
  // Final: still zero.
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateTransformerBlockScratch(TINY_CONFIG);
  const hidden = new Float32Array(16);
  applyTransformerBlock(
    hidden,
    0,
    0,
    weights,
    ropeCache,
    kvCache,
    TINY_CONFIG,
    scratch,
  );
  for (let i = 0; i < 16; i++) {
    assert.equal(hidden[i], 0, `hidden[${i}] = ${hidden[i]} expected 0`);
  }
});

test("applyTransformerBlock — KV cache advances by 1 per position", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateTransformerBlockScratch(TINY_CONFIG);

  assert.equal(kvCache.currentLength(), 0);

  for (let p = 0; p < 3; p++) {
    const hidden = new Float32Array(16);
    for (let i = 0; i < 16; i++) hidden[i] = Math.cos(i + p) * 0.3;
    applyTransformerBlock(
      hidden,
      p,
      0,
      weights,
      ropeCache,
      kvCache,
      TINY_CONFIG,
      scratch,
    );
    assert.equal(kvCache.currentLength(), p + 1);
  }
});

test("applyTransformerBlock — multi-layer at one position preserves length", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateTransformerBlockScratch(TINY_CONFIG);

  const hidden = new Float32Array(16);
  for (let i = 0; i < 16; i++) hidden[i] = 0.1;

  // Run BOTH layers at position 0; length should stay at 1.
  applyTransformerBlock(hidden, 0, 0, weights, ropeCache, kvCache, TINY_CONFIG, scratch);
  assert.equal(kvCache.currentLength(), 1);

  applyTransformerBlock(hidden, 0, 1, weights, ropeCache, kvCache, TINY_CONFIG, scratch);
  assert.equal(kvCache.currentLength(), 1);
});

test("applyTransformerBlock — determinism", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const weights = buildLayerWeights();

  const hidden = new Float32Array(16);
  for (let i = 0; i < 16; i++) hidden[i] = Math.sin(i * 0.91) * 0.4;

  const h1 = new Float32Array(hidden);
  const kvCache1 = new KvCache(TINY_CONFIG);
  const scratch1 = allocateTransformerBlockScratch(TINY_CONFIG);
  applyTransformerBlock(h1, 0, 0, weights, ropeCache, kvCache1, TINY_CONFIG, scratch1);

  const h2 = new Float32Array(hidden);
  const kvCache2 = new KvCache(TINY_CONFIG);
  const scratch2 = allocateTransformerBlockScratch(TINY_CONFIG);
  applyTransformerBlock(h2, 0, 0, weights, ropeCache, kvCache2, TINY_CONFIG, scratch2);

  for (let i = 0; i < 16; i++) {
    assert.equal(h1[i], h2[i], `i=${i}: ${h1[i]} != ${h2[i]}`);
  }
});

test("applyTransformerBlock — throws on hidden shape mismatch", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateTransformerBlockScratch(TINY_CONFIG);
  const hidden = new Float32Array(15); // wrong
  assert.throws(
    () => applyTransformerBlock(hidden, 0, 0, weights, ropeCache, kvCache, TINY_CONFIG, scratch),
    /hidden\.length=15 != hidden_size=16/,
  );
});

test("applyTransformerBlock — throws on layernorm weight shape mismatch", () => {
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  // Replace input_layernorm_w with wrong shape.
  const broken: TransformerLayerWeights = {
    ...weights,
    input_layernorm_w: new Float32Array(15),
  };
  const scratch = allocateTransformerBlockScratch(TINY_CONFIG);
  const hidden = new Float32Array(16);
  assert.throws(
    () => applyTransformerBlock(hidden, 0, 0, broken, ropeCache, kvCache, TINY_CONFIG, scratch),
    /input_layernorm_w\.length=15/,
  );
});

test("allocateTransformerBlockScratch — buffer sizes", () => {
  const scratch = allocateTransformerBlockScratch(TINY_CONFIG);
  assert.equal(scratch.residual.length, 16);
  assert.equal(scratch.subOut.length, 16);
  // attn + ffn scratches sized via their own allocators (tested upstream).
  assert.equal(scratch.attn.q.length, 16);
  assert.equal(scratch.ffn.gate.length, 32);
});

test("applyTransformerBlock — full forward through both layers at multiple positions", () => {
  // End-to-end integration test: 2 layers × 4 positions; KV cache
  // length should be 4 at the end; final hidden should be finite +
  // not equal to the initial hidden (block actually does something).
  const ropeCache = precomputeRope(TINY_CONFIG);
  const kvCache = new KvCache(TINY_CONFIG);
  const weights = buildLayerWeights();
  const scratch = allocateTransformerBlockScratch(TINY_CONFIG);

  const initial = new Float32Array(16);
  for (let i = 0; i < 16; i++) initial[i] = Math.sin(i * 0.5) * 0.4;

  let hidden = new Float32Array(initial);
  for (let p = 0; p < 4; p++) {
    // At each position, restart from the (same) initial pattern just
    // to exercise the block multiple times. (A real decode loop would
    // carry hidden through, but we're testing the block, not the loop.)
    hidden = new Float32Array(initial);
    for (let layer = 0; layer < 2; layer++) {
      applyTransformerBlock(
        hidden,
        p,
        layer,
        weights,
        ropeCache,
        kvCache,
        TINY_CONFIG,
        scratch,
      );
      for (let i = 0; i < 16; i++) {
        assert.ok(Number.isFinite(hidden[i]!), `p=${p} layer=${layer} i=${i}`);
      }
    }
  }
  assert.equal(kvCache.currentLength(), 4);
});
