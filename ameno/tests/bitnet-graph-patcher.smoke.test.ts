/**
 * Smoke tests for `bitnet-graph-patcher` (ADR-2605263700 R1b commit 2).
 *
 * Run via:
 *
 *   node --experimental-strip-types --test tests/bitnet-graph-patcher.smoke.test.ts
 *
 * Strategy: hand-construct a 4-MatMul fixture that covers the
 * matrix:
 *
 *   | # | weight input name                                      | should replace? |
 *   |---|--------------------------------------------------------|-----------------|
 *   | 1 | `model.layers.0.self_attn.q_proj.weight`               | YES (q_proj)    |
 *   | 2 | `model.layers.5.mlp.gate_proj.weight`                  | YES (gate_proj) |
 *   | 3 | `model.layers.0.self_attn.attn_scores`                 | NO (runtime QK^T) |
 *   | 4 | `some.other.layer.foo.weight`                          | NO (wrong pattern) |
 *
 * After patching:
 *   - 2 nodes replaced, op_type=BitLinear, domain=etzhayyim.ai.
 *   - 2 nodes skipped, op_type=MatMul, domain="".
 *   - etzhayyim.ai opset import added (version=1).
 *   - Round-trip survives encode → decode.
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  ModelProtoView,
  WireType,
  decodeMessage,
  encodeMessage,
  type FieldMap,
  type FieldEntry,
} from "../src/inference/onnx-proto-min.ts";

import {
  patchBitNetGraph,
  matchTrunkProjection,
  BITLINEAR_OP_TYPE,
  ETZHAYYIM_OPSET_DOMAIN,
  ETZHAYYIM_OPSET_VERSION,
} from "../src/inference/bitnet-graph-patcher.ts";

// ──────────────────────────────────────────────────────────────
// Fixture builder — 4-MatMul BitNet-shaped graph
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

function buildNode(opType: string, name: string, inputs: string[], outputs: string[]): FieldEntry {
  const node: FieldMap = new Map();
  node.set(1, inputs.map((s) => stringEntry(s))); // input
  node.set(2, outputs.map((s) => stringEntry(s))); // output
  node.set(3, [stringEntry(name)]); // name
  node.set(4, [stringEntry(opType)]); // op_type
  return { wireType: WireType.LEN, bytes: encodeMessage(node) };
}

function buildFourMatMulFixture(): Uint8Array {
  const graph: FieldMap = new Map();
  graph.set(1, [
    // #1: q_proj weight → SHOULD replace
    buildNode(
      "MatMul",
      "layer0_q_matmul",
      ["layer0_input", "model.layers.0.self_attn.q_proj.weight"],
      ["layer0_q_out"],
    ),
    // #2: gate_proj weight → SHOULD replace
    buildNode(
      "MatMul",
      "layer5_gate_matmul",
      ["layer5_input", "model.layers.5.mlp.gate_proj.weight"],
      ["layer5_gate_out"],
    ),
    // #3: attention QK^T runtime matmul → SHOULD NOT replace
    buildNode(
      "MatMul",
      "layer0_qk_matmul",
      ["layer0_q_out", "model.layers.0.self_attn.attn_scores"],
      ["layer0_qk_out"],
    ),
    // #4: wrong pattern (no _proj suffix) → SHOULD NOT replace
    buildNode(
      "MatMul",
      "misc_matmul",
      ["misc_a", "some.other.layer.foo.weight"],
      ["misc_out"],
    ),
  ]);
  graph.set(2, [stringEntry("test_4matmul_graph")]); // name

  // Opset (default)
  const opset: FieldMap = new Map();
  opset.set(2, [{ wireType: WireType.VARINT, bytes: encodeVarint(18) }]);

  const model: FieldMap = new Map();
  model.set(1, [{ wireType: WireType.VARINT, bytes: encodeVarint(9) }]); // ir_version
  model.set(8, [{ wireType: WireType.LEN, bytes: encodeMessage(opset) }]); // opset_import
  model.set(2, [stringEntry("baien-test-r1b")]); // producer_name
  model.set(7, [{ wireType: WireType.LEN, bytes: encodeMessage(graph) }]); // graph
  return encodeMessage(model);
}

// ──────────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────────

test("matchTrunkProjection — positive cases", () => {
  const fixture = buildFourMatMulFixture();
  const model = ModelProtoView.fromBytes(fixture);
  const nodes = model.graph.nodes;
  assert.equal(matchTrunkProjection(nodes[0]!), "model.layers.0.self_attn.q_proj.weight");
  assert.equal(matchTrunkProjection(nodes[1]!), "model.layers.5.mlp.gate_proj.weight");
});

test("matchTrunkProjection — negative cases", () => {
  const fixture = buildFourMatMulFixture();
  const model = ModelProtoView.fromBytes(fixture);
  const nodes = model.graph.nodes;
  assert.equal(matchTrunkProjection(nodes[2]!), null, "QK^T runtime matmul not matched");
  assert.equal(matchTrunkProjection(nodes[3]!), null, "wrong pattern not matched");
});

test("patchBitNetGraph — replaces 2 of 4 MatMul nodes", () => {
  const fixture = buildFourMatMulFixture();
  const result = patchBitNetGraph(fixture);
  assert.equal(result.replacedCount, 2);
  assert.deepEqual(result.replacedTensors, [
    "model.layers.0.self_attn.q_proj.weight",
    "model.layers.5.mlp.gate_proj.weight",
  ]);
  assert.deepEqual(result.skippedMatMulNames, ["layer0_qk_matmul", "misc_matmul"]);
});

test("patchBitNetGraph — patched bytes round-trip + decode shows mutations", () => {
  const fixture = buildFourMatMulFixture();
  const result = patchBitNetGraph(fixture);
  const patched = ModelProtoView.fromBytes(result.bytes);
  const nodes = patched.graph.nodes;
  // #1 + #2: rewritten
  assert.equal(nodes[0]!.opType, BITLINEAR_OP_TYPE);
  assert.equal(nodes[0]!.domain, ETZHAYYIM_OPSET_DOMAIN);
  assert.equal(nodes[1]!.opType, BITLINEAR_OP_TYPE);
  assert.equal(nodes[1]!.domain, ETZHAYYIM_OPSET_DOMAIN);
  // #3 + #4: untouched
  assert.equal(nodes[2]!.opType, "MatMul");
  assert.equal(nodes[2]!.domain, "");
  assert.equal(nodes[3]!.opType, "MatMul");
  assert.equal(nodes[3]!.domain, "");
});

test("patchBitNetGraph — registers etzhayyim.ai opset", () => {
  const fixture = buildFourMatMulFixture();
  const result = patchBitNetGraph(fixture);
  const patched = ModelProtoView.fromBytes(result.bytes);
  const etz = patched.opsetImports.find((o) => o.domain === ETZHAYYIM_OPSET_DOMAIN);
  assert.ok(etz !== undefined, "etzhayyim.ai opset added");
  assert.equal(etz!.version, ETZHAYYIM_OPSET_VERSION);
  // Default opset preserved.
  const def = patched.opsetImports.find((o) => o.domain === "");
  assert.ok(def !== undefined, "default opset preserved");
  assert.equal(def!.version, 18);
});

test("patchBitNetGraph — preserves node inputs/outputs", () => {
  const fixture = buildFourMatMulFixture();
  const result = patchBitNetGraph(fixture);
  const patched = ModelProtoView.fromBytes(result.bytes);
  // The rewritten node should still have the same inputs (the custom
  // op handler will consume them; we don't change the wiring here).
  assert.deepEqual(patched.graph.nodes[0]!.input, [
    "layer0_input",
    "model.layers.0.self_attn.q_proj.weight",
  ]);
  assert.deepEqual(patched.graph.nodes[0]!.output, ["layer0_q_out"]);
});

test("patchBitNetGraph — expectedReplacements gate (R1b-G1) — match", () => {
  const fixture = buildFourMatMulFixture();
  // 2 expected, 2 actual → ok.
  const result = patchBitNetGraph(fixture, { expectedReplacements: 2 });
  assert.equal(result.replacedCount, 2);
});

test("patchBitNetGraph — expectedReplacements gate (R1b-G1) — throws on mismatch", () => {
  const fixture = buildFourMatMulFixture();
  assert.throws(
    () => patchBitNetGraph(fixture, { expectedReplacements: 7 }),
    /gate R1b-G1/,
    "should throw when expected count mismatches",
  );
});

test("patchBitNetGraph — idempotent (re-patching a patched graph is a no-op)", () => {
  const fixture = buildFourMatMulFixture();
  const first = patchBitNetGraph(fixture);
  const second = patchBitNetGraph(first.bytes);
  // Already rewritten → nothing left to do on second pass.
  assert.equal(second.replacedCount, 0);
  // The skipped MatMul nodes are still skipped.
  assert.deepEqual(second.skippedMatMulNames, ["layer0_qk_matmul", "misc_matmul"]);
});

test("patchBitNetGraph — no MatMul nodes → no-op + no opset added", () => {
  // Construct a 1-node Identity-only graph.
  const node: FieldMap = new Map();
  node.set(1, [stringEntry("x")]);
  node.set(2, [stringEntry("y")]);
  node.set(3, [stringEntry("id_only")]);
  node.set(4, [stringEntry("Identity")]);
  const graph: FieldMap = new Map();
  graph.set(1, [{ wireType: WireType.LEN, bytes: encodeMessage(node) }]);
  const opset: FieldMap = new Map();
  opset.set(2, [{ wireType: WireType.VARINT, bytes: encodeVarint(18) }]);
  const model: FieldMap = new Map();
  model.set(8, [{ wireType: WireType.LEN, bytes: encodeMessage(opset) }]);
  model.set(7, [{ wireType: WireType.LEN, bytes: encodeMessage(graph) }]);
  const fixtureBytes = encodeMessage(model);

  const result = patchBitNetGraph(fixtureBytes);
  assert.equal(result.replacedCount, 0);
  const patched = ModelProtoView.fromBytes(result.bytes);
  const etz = patched.opsetImports.find((o) => o.domain === ETZHAYYIM_OPSET_DOMAIN);
  assert.equal(etz, undefined, "no etzhayyim opset added when nothing replaced");
});
