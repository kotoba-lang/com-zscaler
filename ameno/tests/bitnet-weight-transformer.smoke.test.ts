/**
 * Smoke tests for `bitnet-weight-transformer` (ADR-2605263700 R1b
 * commit 3).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-weight-transformer.smoke.test.ts
 *
 * What this verifies:
 *   - bf16 ↔ fp32 conversion correctness on a few known values.
 *   - absmean threshold quantization matches the Rust algorithm.
 *   - i2_s byte packing matches `40-engine/baien-wasm-ternary/src/i2s.rs`.
 *   - End-to-end: build a model with a BitLinear node + bf16
 *     initializer, run the transform, assert the new (W_packed,
 *     W_scale) initializers exist and the node's input list is
 *     rewired.
 *   - Idempotent re-run of the transform skips already-transformed
 *     nodes.
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  ModelProtoView,
  TensorProtoView,
  WireType,
  DataType,
  decodeMessage,
  encodeMessage,
  encodeTensorProto,
  type FieldMap,
  type FieldEntry,
} from "../src/inference/onnx-proto-min.ts";
import {
  BITLINEAR_OP_TYPE,
  ETZHAYYIM_OPSET_DOMAIN,
} from "../src/inference/bitnet-graph-patcher.ts";
import {
  decodeBf16TensorToF32,
  packI2sRow,
  quantizeRowAbsmean,
  transformBf16ToI2sAndScale,
  transformBitNetWeights,
  I2S_PACKED_SUFFIX,
  W_SCALE_SUFFIX,
} from "../src/inference/bitnet-weight-transformer.ts";

// ──────────────────────────────────────────────────────────────
// Fixture helpers
// ──────────────────────────────────────────────────────────────

const textEncoder = new TextEncoder();

function encodeVarint(value: number): Uint8Array {
  const out: number[] = [];
  let v = value >>> 0;
  while (v >= 0x80) {
    out.push((v & 0x7f) | 0x80);
    v = v >>> 7;
  }
  out.push(v & 0x7f);
  return new Uint8Array(out);
}

function stringEntry(s: string): FieldEntry {
  return { wireType: WireType.LEN, bytes: textEncoder.encode(s) };
}

/** Encode an f32 array as bf16 bytes (little-endian, 2 bytes per element). */
function encodeBf16(f32: Float32Array): Uint8Array {
  const out = new Uint8Array(f32.length * 2);
  const dv = new DataView(out.buffer);
  const tmp = new ArrayBuffer(4);
  const tmpF = new Float32Array(tmp);
  const tmpU = new Uint32Array(tmp);
  for (let i = 0; i < f32.length; i++) {
    tmpF[0] = f32[i]!;
    const bf16Bits = (tmpU[0]! >>> 16) & 0xffff;
    dv.setUint16(i * 2, bf16Bits, /* le */ true);
  }
  return out;
}

/**
 * Build a fixture model with:
 *   - 1 BitLinear node consuming a 2×8 bf16 weight initializer
 *   - input[0] = "x", input[1] = "W_bf16"
 *   - the initializer "W_bf16" has shape 2×8 with known values
 */
function buildBitLinearFixture(weightF32: Float32Array, rows = 2, cols = 8): Uint8Array {
  // BitLinear node
  const node: FieldMap = new Map();
  node.set(1, [stringEntry("x"), stringEntry("W_bf16")]); // input
  node.set(2, [stringEntry("y")]); // output
  node.set(3, [stringEntry("bitlinear_node")]); // name
  node.set(4, [stringEntry(BITLINEAR_OP_TYPE)]); // op_type
  node.set(7, [stringEntry(ETZHAYYIM_OPSET_DOMAIN)]); // domain

  // bf16 initializer
  const bf16Bytes = encodeBf16(weightF32);
  const initBytes = encodeTensorProto({
    name: "W_bf16",
    dataType: DataType.BFLOAT16,
    dims: [rows, cols],
    rawData: bf16Bytes,
  });

  // GraphProto
  const graph: FieldMap = new Map();
  graph.set(1, [{ wireType: WireType.LEN, bytes: encodeMessage(node) }]); // node
  graph.set(2, [stringEntry("test_bitlinear_graph")]); // name
  graph.set(5, [{ wireType: WireType.LEN, bytes: initBytes }]); // initializer

  // Default opset
  const opset: FieldMap = new Map();
  opset.set(2, [{ wireType: WireType.VARINT, bytes: encodeVarint(18) }]);
  // etzhayyim opset
  const etzOpset: FieldMap = new Map();
  etzOpset.set(1, [stringEntry(ETZHAYYIM_OPSET_DOMAIN)]);
  etzOpset.set(2, [{ wireType: WireType.VARINT, bytes: encodeVarint(1) }]);

  const model: FieldMap = new Map();
  model.set(1, [{ wireType: WireType.VARINT, bytes: encodeVarint(9) }]);
  model.set(8, [
    { wireType: WireType.LEN, bytes: encodeMessage(opset) },
    { wireType: WireType.LEN, bytes: encodeMessage(etzOpset) },
  ]);
  model.set(7, [{ wireType: WireType.LEN, bytes: encodeMessage(graph) }]);
  return encodeMessage(model);
}

// ──────────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────────

test("quantizeRowAbsmean — alternating ±1", () => {
  const row = new Float32Array([1, -1, 1, -1, 1, -1, 1, -1]);
  const { scale, ternary } = quantizeRowAbsmean(row);
  // absmean = 1.0; threshold = 0.5; everything > 0.5 → +1, < -0.5 → -1
  assert.equal(scale, 1.0);
  assert.deepEqual(Array.from(ternary), [1, -1, 1, -1, 1, -1, 1, -1]);
});

test("quantizeRowAbsmean — small values near zero stay zero", () => {
  const row = new Float32Array([0.01, 0.01, 0.01, 0.01, 4, -4, 0.01, 0.01]);
  const { scale, ternary } = quantizeRowAbsmean(row);
  // absmean = (0.01*6 + 4 + 4) / 8 = 8.06 / 8 = 1.0075
  // threshold = 0.50375 → only ±4 cross it
  assert.ok(Math.abs(scale - 1.0075) < 1e-5, `scale=${scale}`);
  assert.deepEqual(Array.from(ternary), [0, 0, 0, 0, 1, -1, 0, 0]);
});

test("packI2sRow — matches the i2s.rs byte encoding (known case)", () => {
  // [0, +1, -1, +1] → bits w3 w2 w1 w0 = 01 10 01 00 = 0b01100100 = 0x64
  const ternary = new Int8Array([0, 1, -1, 1]);
  const packed = packI2sRow(ternary);
  assert.equal(packed.length, 1);
  assert.equal(packed[0], 0b01_10_01_00, `got 0x${packed[0]!.toString(16)}`);
});

test("packI2sRow — pads final partial byte with zeros", () => {
  const ternary = new Int8Array([1, -1, 1]); // 3 elements
  const packed = packI2sRow(ternary);
  // 1 byte: slot0=01, slot1=10, slot2=01, slot3=00 → 0b00 01 10 01 = 0x19
  assert.equal(packed.length, 1);
  assert.equal(packed[0], 0b00_01_10_01);
});

test("decodeBf16TensorToF32 — round-trips with encodeBf16", () => {
  const original = new Float32Array([0.0, 1.0, -1.0, 0.5, 2.0, -2.0, 100.0, 0.125]);
  const bf16Bytes = encodeBf16(original);
  const tensorBytes = encodeTensorProto({
    name: "t",
    dataType: DataType.BFLOAT16,
    dims: [1, 8],
    rawData: bf16Bytes,
  });
  const fmap = decodeMessage(tensorBytes);
  const view = new TensorProtoView(fmap);
  const { rows, cols, f32 } = decodeBf16TensorToF32(view);
  assert.equal(rows, 1);
  assert.equal(cols, 8);
  // bf16 has 7 bits of mantissa → up to ~1% relative error for non-zero values.
  for (let i = 0; i < 8; i++) {
    if (original[i] === 0) {
      assert.equal(f32[i], 0);
    } else {
      const rel = Math.abs(f32[i]! - original[i]!) / Math.abs(original[i]!);
      assert.ok(rel < 0.01, `i=${i}: ${original[i]} vs ${f32[i]} (rel=${rel})`);
    }
  }
});

test("transformBf16ToI2sAndScale — produces correct dims + dtype", () => {
  // 2×8 bf16 input. Row 0 = alternating ±1; Row 1 = all near-zero except [0,4].
  const f32 = new Float32Array([
    1, -1, 1, -1, 1, -1, 1, -1,
    0.01, 0.01, 0.01, 0.01, 4, -4, 0.01, 0.01,
  ]);
  const bf16Bytes = encodeBf16(f32);
  const tensorBytes = encodeTensorProto({
    name: "W",
    dataType: DataType.BFLOAT16,
    dims: [2, 8],
    rawData: bf16Bytes,
  });
  const view = new TensorProtoView(decodeMessage(tensorBytes));
  const { packed, scale } = transformBf16ToI2sAndScale("W", view);

  // packed: 2 rows × ceil(8/4) = 2 bytes per row → 4 bytes total.
  assert.equal(packed.name, "W" + I2S_PACKED_SUFFIX);
  assert.equal(packed.dataType, DataType.INT8);
  assert.deepEqual(packed.dims, [2, 2]);
  assert.equal(packed.rawData.length, 4);

  // scale: 2 f16 values → 4 bytes.
  assert.equal(scale.name, "W" + W_SCALE_SUFFIX);
  assert.equal(scale.dataType, DataType.FLOAT16);
  assert.deepEqual(scale.dims, [2]);
  assert.equal(scale.rawData.length, 4);
});

test("transformBitNetWeights — end-to-end on fixture", () => {
  // 2×8 bf16 weight, all alternating ±1.
  const f32 = new Float32Array([
    1, -1, 1, -1, 1, -1, 1, -1,
    -1, 1, -1, 1, -1, 1, -1, 1,
  ]);
  const fixtureBytes = buildBitLinearFixture(f32, 2, 8);
  const model = ModelProtoView.fromBytes(fixtureBytes);

  // Before transform: 1 initializer (W_bf16), node input = [x, W_bf16].
  assert.equal(model.graph.initializers.length, 1);
  assert.equal(model.graph.initializers[0]!.name, "W_bf16");
  assert.deepEqual(model.graph.nodes[0]!.input, ["x", "W_bf16"]);

  const result = transformBitNetWeights(model);

  assert.equal(result.transformedCount, 1);
  assert.deepEqual(result.originalWeightNames, ["W_bf16"]);
  assert.deepEqual(result.newTensorNames, [
    "W_bf16" + I2S_PACKED_SUFFIX,
    "W_bf16" + W_SCALE_SUFFIX,
  ]);
  assert.equal(result.skippedNodeNames.length, 0);

  // After transform: 3 initializers (original bf16 left in place + 2 new),
  // node input = [x, W_bf16.i2s_packed, W_bf16.scale].
  const initNames = model.graph.initializers.map((t) => t.name);
  assert.equal(initNames.length, 3);
  assert.ok(initNames.includes("W_bf16"));
  assert.ok(initNames.includes("W_bf16.i2s_packed"));
  assert.ok(initNames.includes("W_bf16.scale"));

  assert.deepEqual(model.graph.nodes[0]!.input, [
    "x",
    "W_bf16.i2s_packed",
    "W_bf16.scale",
  ]);
});

test("transformBitNetWeights — idempotent (re-running is a no-op)", () => {
  const f32 = new Float32Array([1, -1, 1, -1, 1, -1, 1, -1, -1, 1, -1, 1, -1, 1, -1, 1]);
  const fixtureBytes = buildBitLinearFixture(f32, 2, 8);
  const model = ModelProtoView.fromBytes(fixtureBytes);

  const first = transformBitNetWeights(model);
  assert.equal(first.transformedCount, 1);

  // Round-trip and re-load to verify it's a property of the bytes, not
  // just the in-memory model.
  const patchedBytes = model.toBytes();
  const model2 = ModelProtoView.fromBytes(patchedBytes);
  const second = transformBitNetWeights(model2);
  assert.equal(second.transformedCount, 0, "second pass transforms nothing");
  assert.equal(second.skippedNodeNames.length, 1, "node was skipped (already transformed)");
});

test("transformBitNetWeights — round-trip survives encode → decode", () => {
  const f32 = new Float32Array([1, -1, 1, -1, 1, -1, 1, -1, -1, 1, -1, 1, -1, 1, -1, 1]);
  const fixtureBytes = buildBitLinearFixture(f32, 2, 8);
  const model = ModelProtoView.fromBytes(fixtureBytes);
  transformBitNetWeights(model);
  const patchedBytes = model.toBytes();
  const model2 = ModelProtoView.fromBytes(patchedBytes);
  assert.equal(model2.graph.initializers.length, 3);
  assert.deepEqual(model2.graph.nodes[0]!.input, [
    "x",
    "W_bf16.i2s_packed",
    "W_bf16.scale",
  ]);
});
