/**
 * Smoke tests for `bitnet-runtime` (ADR-2605263800 R1b commit 14).
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  BitNetRuntime,
  type BitNetModelWeights,
} from "../src/inference/bitnet-runtime.ts";
import { parseBitNetConfig } from "../src/inference/bitnet-config.ts";
import { transformBf16ToI2sAndScale } from "../src/inference/bitnet-weight-transformer.ts";
import {
  DataType,
  TensorProtoView,
  decodeMessage,
  encodeTensorProto,
} from "../src/inference/onnx-proto-min.ts";
import type { TransformerLayerWeights } from "../src/inference/bitnet-transformer.ts";

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

function buildLayerWeights(seedBase: number): TransformerLayerWeights {
  const H = TINY_CONFIG.hidden_size;
  const KV = TINY_CONFIG.num_key_value_heads * TINY_CONFIG.head_dim;
  const F = TINY_CONFIG.intermediate_size;
  const norm_w = new Float32Array(H).fill(1.0);
  return {
    attn: {
      q_proj: buildPack(H, H, seedBase + 1, `q_proj_${seedBase}`),
      k_proj: buildPack(KV, H, seedBase + 2, `k_proj_${seedBase}`),
      v_proj: buildPack(KV, H, seedBase + 3, `v_proj_${seedBase}`),
      o_proj: buildPack(H, H, seedBase + 4, `o_proj_${seedBase}`),
    },
    ffn: {
      gate_proj: buildPack(F, H, seedBase + 10, `gate_proj_${seedBase}`),
      up_proj: buildPack(F, H, seedBase + 20, `up_proj_${seedBase}`),
      down_proj: buildPack(H, F, seedBase + 30, `down_proj_${seedBase}`),
    },
    input_layernorm_w: new Float32Array(norm_w),
    post_attention_layernorm_w: new Float32Array(norm_w),
  };
}

function buildModelWeights(): BitNetModelWeights {
  const V = TINY_CONFIG.vocab_size;
  const H = TINY_CONFIG.hidden_size;
  const embedTokens = new Float32Array(V * H);
  for (let i = 0; i < embedTokens.length; i++) {
    embedTokens[i] = Math.sin(i * 0.057) * 0.6;
  }
  return {
    embedTokens,
    layers: [buildLayerWeights(100), buildLayerWeights(200)],
    finalNorm: new Float32Array(H).fill(1.0),
    lmHead: buildPack(V, H, 999, "lm_head"),
  };
}

// ──────────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────────

test("BitNetRuntime — construction shape validation passes", () => {
  const weights = buildModelWeights();
  const runtime = new BitNetRuntime(TINY_CONFIG, weights);
  assert.equal(runtime.getPosition(), 0);
  assert.equal(runtime.getKvCacheShape().numLayers, 2);
});

test("BitNetRuntime — decode returns a valid token id + advances position", () => {
  const weights = buildModelWeights();
  const runtime = new BitNetRuntime(TINY_CONFIG, weights);
  const next = runtime.decode(7);
  assert.ok(next >= 0 && next < TINY_CONFIG.vocab_size, `next=${next} in vocab`);
  assert.equal(runtime.getPosition(), 1);
});

test("BitNetRuntime — multi-step decode advances position correctly", () => {
  const weights = buildModelWeights();
  const runtime = new BitNetRuntime(TINY_CONFIG, weights);
  const outputs = runtime.decodeMany([1, 2, 3, 4]);
  assert.equal(outputs.length, 4);
  for (const t of outputs) {
    assert.ok(t >= 0 && t < TINY_CONFIG.vocab_size);
  }
  assert.equal(runtime.getPosition(), 4);
});

test("BitNetRuntime — determinism: same input → same output", () => {
  const weights = buildModelWeights();
  const r1 = new BitNetRuntime(TINY_CONFIG, weights);
  const r2 = new BitNetRuntime(TINY_CONFIG, weights);
  const out1 = r1.decodeMany([5, 10, 15]);
  const out2 = r2.decodeMany([5, 10, 15]);
  assert.deepEqual(out1, out2);
});

test("BitNetRuntime — reset clears position + KV cache", () => {
  const weights = buildModelWeights();
  const runtime = new BitNetRuntime(TINY_CONFIG, weights);
  runtime.decodeMany([1, 2, 3]);
  assert.equal(runtime.getPosition(), 3);
  runtime.reset();
  assert.equal(runtime.getPosition(), 0);
  // After reset, the same first-token call should give the same answer
  // as starting fresh.
  const fresh = new BitNetRuntime(TINY_CONFIG, weights);
  assert.equal(runtime.decode(7), fresh.decode(7));
});

test("BitNetRuntime — decode throws on invalid tokenId", () => {
  const weights = buildModelWeights();
  const runtime = new BitNetRuntime(TINY_CONFIG, weights);
  assert.throws(() => runtime.decode(-1), /tokenId=-1 out of \[0, 64\)/);
  assert.throws(() => runtime.decode(64), /tokenId=64 out of \[0, 64\)/);
});

test("BitNetRuntime — decode throws when past max_position", () => {
  const weights = buildModelWeights();
  const runtime = new BitNetRuntime(TINY_CONFIG, weights);
  // Fill to max (8 positions).
  for (let i = 0; i < 8; i++) runtime.decode(0);
  assert.throws(() => runtime.decode(0), /sequence too long/);
});

test("BitNetRuntime — construction throws on mis-shaped weights", () => {
  const goodWeights = buildModelWeights();
  // Wrong-size embedding.
  const badEmbed: BitNetModelWeights = {
    ...goodWeights,
    embedTokens: new Float32Array(100), // wrong (expected 64×16=1024)
  };
  assert.throws(() => new BitNetRuntime(TINY_CONFIG, badEmbed), /embedTokens\.length=100/);

  // Wrong layer count.
  const badLayers: BitNetModelWeights = {
    ...goodWeights,
    layers: [goodWeights.layers[0]!], // only 1, expected 2
  };
  assert.throws(() => new BitNetRuntime(TINY_CONFIG, badLayers), /layers\.length=1/);
});

test("BitNetRuntime — tied lm_head smoke", () => {
  const tiedConfig = parseBitNetConfig({
    hidden_size: 16,
    num_hidden_layers: 2,
    num_attention_heads: 4,
    num_key_value_heads: 2,
    intermediate_size: 32,
    vocab_size: 64,
    max_position_embeddings: 8,
    rope_theta: 10_000,
    rms_norm_eps: 1e-5,
    tie_word_embeddings: true,
  });
  const baseWeights = buildModelWeights();
  const tiedWeights: BitNetModelWeights = {
    embedTokens: baseWeights.embedTokens,
    layers: baseWeights.layers,
    finalNorm: baseWeights.finalNorm,
    lmHead: "tied",
  };
  const runtime = new BitNetRuntime(tiedConfig, tiedWeights);
  const next = runtime.decode(3);
  assert.ok(next >= 0 && next < 64);
  assert.equal(runtime.getPosition(), 1);
});

test("BitNetRuntime — full 4-token decode round-trip is well-formed", () => {
  const weights = buildModelWeights();
  const runtime = new BitNetRuntime(TINY_CONFIG, weights);
  const outputs = runtime.decodeMany([10, 20, 30, 40]);
  // Outputs all finite + in range.
  for (const t of outputs) {
    assert.ok(Number.isInteger(t), `output ${t} is integer`);
    assert.ok(t >= 0 && t < 64, `output ${t} in [0,64)`);
  }
  // KV cache shape sanity.
  const shape = runtime.getKvCacheShape();
  assert.equal(shape.numLayers, 2);
  assert.equal(shape.maxPos, 8);
});
