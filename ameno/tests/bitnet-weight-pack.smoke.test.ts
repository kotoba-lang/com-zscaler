/**
 * Smoke tests for `bitnet-weight-pack` (ADR-2605263800 pivot).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-weight-pack.smoke.test.ts
 *
 * What this verifies:
 *   - Extraction picks ONLY initializers matching the trunk-projection
 *     pattern (q/k/v/o/gate/up/down _proj.weight), skips others.
 *   - Pack contents (packed bytes + scale bytes + dims) are identical
 *     to what `transformBf16ToI2sAndScale` would produce.
 *   - countByLayer correctly buckets multi-layer fixtures.
 *   - typeMismatches reports non-bf16 initializers under the pattern.
 *   - verifyBitNet2bPackStructure flags missing layers / wrong counts.
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
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
  extractBitLinearWeightPack,
  verifyBitNet2bPackStructure,
} from "../src/inference/bitnet-weight-pack.ts";
import { transformBf16ToI2sAndScale } from "../src/inference/bitnet-weight-transformer.ts";

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

/** Encode an f32 array as bf16 bytes (little-endian uint16). */
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

interface FixtureInit {
  readonly name: string;
  readonly dataType: number;
  readonly dims: readonly [number, number];
  readonly f32?: Float32Array; // for bf16 case
  readonly rawData?: Uint8Array; // for non-bf16 mismatch case
}

function buildModelWithInitializers(inits: readonly FixtureInit[]): Uint8Array {
  const initBytes = inits.map((i) => {
    const raw = i.rawData ?? (i.f32 ? encodeBf16(i.f32) : new Uint8Array(0));
    return {
      wireType: WireType.LEN,
      bytes: encodeTensorProto({
        name: i.name,
        dataType: i.dataType as 1 | 3 | 7 | 10 | 16,
        dims: i.dims,
        rawData: raw,
      }),
    };
  });
  const graph: FieldMap = new Map();
  graph.set(2, [stringEntry("test_pack_graph")]); // name
  graph.set(5, initBytes); // initializer

  const opset: FieldMap = new Map();
  opset.set(2, [{ wireType: WireType.VARINT, bytes: encodeVarint(18) }]);

  const model: FieldMap = new Map();
  model.set(1, [{ wireType: WireType.VARINT, bytes: encodeVarint(9) }]);
  model.set(8, [{ wireType: WireType.LEN, bytes: encodeMessage(opset) }]);
  model.set(7, [{ wireType: WireType.LEN, bytes: encodeMessage(graph) }]);
  return encodeMessage(model);
}

const ROW_PATTERN = new Float32Array([1, -1, 1, -1, 1, -1, 1, -1]);

// ──────────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────────

test("extractBitLinearWeightPack — picks trunk-projection weights only", () => {
  const f32_2x8 = new Float32Array(16);
  f32_2x8.set(ROW_PATTERN, 0);
  f32_2x8.set(ROW_PATTERN, 8);

  const bytes = buildModelWithInitializers([
    {
      name: "model.layers.0.self_attn.q_proj.weight",
      dataType: DataType.BFLOAT16,
      dims: [2, 8],
      f32: f32_2x8,
    },
    {
      name: "model.layers.0.self_attn.k_proj.weight",
      dataType: DataType.BFLOAT16,
      dims: [2, 8],
      f32: f32_2x8,
    },
    {
      name: "model.layers.0.input_layernorm.weight", // NOT trunk-proj
      dataType: DataType.BFLOAT16,
      dims: [2, 8],
      f32: f32_2x8,
    },
    {
      name: "model.embed_tokens.weight", // NOT trunk-proj
      dataType: DataType.BFLOAT16,
      dims: [2, 8],
      f32: f32_2x8,
    },
  ]);

  const result = extractBitLinearWeightPack(bytes);
  assert.equal(result.packs.size, 2);
  assert.ok(result.packs.has("model.layers.0.self_attn.q_proj.weight"));
  assert.ok(result.packs.has("model.layers.0.self_attn.k_proj.weight"));
  assert.equal(result.typeMismatches.length, 0);
});

test("extractBitLinearWeightPack — pack contents match the transform", () => {
  const f32 = new Float32Array(16);
  f32.set(ROW_PATTERN, 0);
  f32.set(ROW_PATTERN, 8);
  const bytes = buildModelWithInitializers([
    {
      name: "model.layers.0.self_attn.q_proj.weight",
      dataType: DataType.BFLOAT16,
      dims: [2, 8],
      f32,
    },
  ]);
  const result = extractBitLinearWeightPack(bytes);
  const pack = result.packs.get("model.layers.0.self_attn.q_proj.weight");
  assert.ok(pack !== undefined);

  // Construct the SAME initializer view + run the transform directly;
  // pack bytes MUST agree byte-for-byte (gate G2 — same algorithm).
  const bf16Bytes = encodeBf16(f32);
  const initBytes = encodeTensorProto({
    name: "model.layers.0.self_attn.q_proj.weight",
    dataType: DataType.BFLOAT16,
    dims: [2, 8],
    rawData: bf16Bytes,
  });
  const view = new TensorProtoView(decodeMessage(initBytes));
  const direct = transformBf16ToI2sAndScale(
    "model.layers.0.self_attn.q_proj.weight",
    view,
  );

  assert.deepEqual(Array.from(pack.packed), Array.from(direct.packed.rawData));
  assert.deepEqual(Array.from(pack.scale), Array.from(direct.scale.rawData));
  assert.deepEqual(pack.dims, [2, 8]);
});

test("extractBitLinearWeightPack — countByLayer buckets multi-layer fixtures", () => {
  const f32 = new Float32Array(8);
  f32.set(ROW_PATTERN);
  const layerNames = [
    "model.layers.0.self_attn.q_proj.weight",
    "model.layers.0.self_attn.k_proj.weight",
    "model.layers.0.mlp.gate_proj.weight",
    "model.layers.1.self_attn.q_proj.weight",
    "model.layers.5.mlp.down_proj.weight",
    "model.layers.5.mlp.up_proj.weight",
  ];
  const bytes = buildModelWithInitializers(
    layerNames.map((name) => ({
      name,
      dataType: DataType.BFLOAT16,
      dims: [1, 8] as const,
      f32,
    })),
  );
  const result = extractBitLinearWeightPack(bytes);
  assert.equal(result.packs.size, 6);
  assert.equal(result.countByLayer.get(0), 3);
  assert.equal(result.countByLayer.get(1), 1);
  assert.equal(result.countByLayer.get(5), 2);
});

test("extractBitLinearWeightPack — typeMismatches reports non-bf16 initializers", () => {
  const bytes = buildModelWithInitializers([
    {
      name: "model.layers.0.self_attn.q_proj.weight",
      dataType: DataType.FLOAT, // WRONG — fp32 not bf16
      dims: [1, 8] as const,
      rawData: new Uint8Array(32), // 8 × f32 = 32 bytes; content irrelevant
    },
  ]);
  const result = extractBitLinearWeightPack(bytes);
  assert.equal(result.packs.size, 0, "no packs extracted");
  assert.equal(result.typeMismatches.length, 1);
  assert.equal(result.typeMismatches[0]!.name, "model.layers.0.self_attn.q_proj.weight");
  assert.equal(result.typeMismatches[0]!.dataType, DataType.FLOAT);
});

test("verifyBitNet2bPackStructure — flags pack count mismatch", () => {
  const f32 = new Float32Array(8);
  f32.set(ROW_PATTERN);
  const bytes = buildModelWithInitializers([
    {
      name: "model.layers.0.self_attn.q_proj.weight",
      dataType: DataType.BFLOAT16,
      dims: [1, 8] as const,
      f32,
    },
  ]);
  const result = extractBitLinearWeightPack(bytes);
  // Expected 30 × 7 = 210, actual 1.
  const issues = verifyBitNet2bPackStructure(result);
  assert.ok(issues.length > 0, "issues reported");
  assert.ok(
    issues[0]!.includes("pack count = 1"),
    "first issue mentions pack count",
  );
});

test("verifyBitNet2bPackStructure — clean when expectation matches", () => {
  const f32 = new Float32Array(8);
  f32.set(ROW_PATTERN);
  // Build 1 layer × 2 projections → check against `(1, 2)`.
  const bytes = buildModelWithInitializers([
    {
      name: "model.layers.0.self_attn.q_proj.weight",
      dataType: DataType.BFLOAT16,
      dims: [1, 8] as const,
      f32,
    },
    {
      name: "model.layers.0.self_attn.k_proj.weight",
      dataType: DataType.BFLOAT16,
      dims: [1, 8] as const,
      f32,
    },
  ]);
  const result = extractBitLinearWeightPack(bytes);
  const issues = verifyBitNet2bPackStructure(result, 1, 2);
  assert.deepEqual(issues, [], "no issues with matching expectation");
});
