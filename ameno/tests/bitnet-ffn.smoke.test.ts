/**
 * Smoke tests for `bitnet-ffn` (ADR-2605263800 R1b commit 12).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-ffn.smoke.test.ts
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  applyFfn,
  allocateFfnScratch,
  type FfnLayerWeights,
} from "../src/inference/bitnet-ffn.ts";
import { parseBitNetConfig } from "../src/inference/bitnet-config.ts";
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
  hidden_size: 16,
  num_hidden_layers: 2,
  num_attention_heads: 4,
  num_key_value_heads: 2,
  intermediate_size: 32, // FFN inner
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

function buildFfnWeights(): FfnLayerWeights {
  const H = TINY_CONFIG.hidden_size; // 16
  const F = TINY_CONFIG.intermediate_size; // 32
  return {
    gate_proj: buildPack(F, H, 10, "gate_proj"), // [32, 16]
    up_proj: buildPack(F, H, 20, "up_proj"), // [32, 16]
    down_proj: buildPack(H, F, 30, "down_proj"), // [16, 32]
  };
}

// ──────────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────────

test("applyFfn — shape preservation", () => {
  const weights = buildFfnWeights();
  const scratch = allocateFfnScratch(TINY_CONFIG);
  const hidden = new Float32Array(16);
  for (let i = 0; i < 16; i++) hidden[i] = Math.cos(i * 0.5) * 0.3;
  const out = new Float32Array(16);
  applyFfn(hidden, weights, TINY_CONFIG, scratch, out);
  assert.equal(out.length, 16);
  for (let i = 0; i < 16; i++) {
    assert.ok(Number.isFinite(out[i]!), `out[${i}] = ${out[i]} (finite)`);
  }
});

test("applyFfn — zero hidden → zero output", () => {
  // BitLinear(0) = 0; silu(0) × 0 = 0; BitLinear(0) = 0.
  const weights = buildFfnWeights();
  const scratch = allocateFfnScratch(TINY_CONFIG);
  const hidden = new Float32Array(16); // all zero
  const out = new Float32Array(16);
  applyFfn(hidden, weights, TINY_CONFIG, scratch, out);
  for (let i = 0; i < 16; i++) {
    assert.equal(out[i], 0);
  }
});

test("applyFfn — determinism: same inputs → same output", () => {
  const weights = buildFfnWeights();
  const hidden = new Float32Array(16);
  for (let i = 0; i < 16; i++) hidden[i] = Math.sin(i * 0.91) * 0.5;

  const scratch1 = allocateFfnScratch(TINY_CONFIG);
  const out1 = new Float32Array(16);
  applyFfn(hidden, weights, TINY_CONFIG, scratch1, out1);

  const scratch2 = allocateFfnScratch(TINY_CONFIG);
  const out2 = new Float32Array(16);
  applyFfn(hidden, weights, TINY_CONFIG, scratch2, out2);

  for (let i = 0; i < 16; i++) {
    assert.equal(out1[i], out2[i], `i=${i}: ${out1[i]} != ${out2[i]}`);
  }
});

test("applyFfn — different inputs → different outputs", () => {
  const weights = buildFfnWeights();
  const scratch = allocateFfnScratch(TINY_CONFIG);

  const h1 = new Float32Array(16);
  for (let i = 0; i < 16; i++) h1[i] = Math.sin(i * 0.3) * 0.5;
  const out1 = new Float32Array(16);
  applyFfn(h1, weights, TINY_CONFIG, scratch, out1);

  const h2 = new Float32Array(16);
  for (let i = 0; i < 16; i++) h2[i] = Math.cos(i * 0.7) * 0.4;
  const out2 = new Float32Array(16);
  applyFfn(h2, weights, TINY_CONFIG, scratch, out2);

  // At least one element differs significantly.
  let diffCount = 0;
  for (let i = 0; i < 16; i++) {
    if (Math.abs(out1[i]! - out2[i]!) > 0.01) diffCount++;
  }
  assert.ok(diffCount > 4, `expected several outputs to differ; only ${diffCount} did`);
});

test("applyFfn — throws on hidden shape mismatch", () => {
  const weights = buildFfnWeights();
  const scratch = allocateFfnScratch(TINY_CONFIG);
  const hidden = new Float32Array(15); // wrong
  const out = new Float32Array(16);
  assert.throws(
    () => applyFfn(hidden, weights, TINY_CONFIG, scratch, out),
    /hidden\.length=15 != hidden_size=16/,
  );
});

test("applyFfn — throws on out shape mismatch", () => {
  const weights = buildFfnWeights();
  const scratch = allocateFfnScratch(TINY_CONFIG);
  const hidden = new Float32Array(16);
  const out = new Float32Array(15); // wrong
  assert.throws(
    () => applyFfn(hidden, weights, TINY_CONFIG, scratch, out),
    /out\.length=15 != hidden_size=16/,
  );
});

test("applyFfn — throws on scratch shape mismatch", () => {
  const weights = buildFfnWeights();
  const scratch = {
    gate: new Float32Array(33), // wrong; expected 32
    up: new Float32Array(32),
  };
  const hidden = new Float32Array(16);
  const out = new Float32Array(16);
  assert.throws(
    () => applyFfn(hidden, weights, TINY_CONFIG, scratch, out),
    /scratch\.gate\.length=33|scratch\.up\.length=32/,
  );
});

test("allocateFfnScratch — buffer sizes", () => {
  const scratch = allocateFfnScratch(TINY_CONFIG);
  assert.equal(scratch.gate.length, 32);
  assert.equal(scratch.up.length, 32);
});

test("applyFfn — small numeric: silu(gate=0) × up = 0", () => {
  // We can't directly control gate/up since they go through BitLinear,
  // but we can verify the SwiGLU semantics: when gate happens to be
  // zero (e.g. weights cancel), the mid is zero regardless of up,
  // and out = BitLinear(zero) = 0.
  //
  // Easiest way: zero hidden. (Already tested above as "zero → zero".)
  // This test is here to document the silu(0) = 0 invariant.
  const weights = buildFfnWeights();
  const scratch = allocateFfnScratch(TINY_CONFIG);
  const hidden = new Float32Array(16);
  const out = new Float32Array(16);
  applyFfn(hidden, weights, TINY_CONFIG, scratch, out);
  // After FFN with zero hidden:
  //   gate = BitLinear(0) = 0
  //   up = BitLinear(0) = 0
  //   mid = silu(0) × 0 = 0
  //   out = BitLinear(0) = 0
  let nonZeroCount = 0;
  for (let i = 0; i < 16; i++) if (out[i] !== 0) nonZeroCount++;
  assert.equal(nonZeroCount, 0, "all output zero");
});
