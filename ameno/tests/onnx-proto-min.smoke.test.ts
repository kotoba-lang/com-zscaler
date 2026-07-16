/**
 * Smoke tests for `onnx-proto-min` (ADR-2605263700 R1b commit 1).
 *
 * Run via Node 22.6+ native type-stripping:
 *
 *   node --experimental-strip-types --test tests/onnx-proto-min.smoke.test.ts
 *
 * Or once we add vitest to the package, via `pnpm test`.
 *
 * The tests are intentionally tiny and self-contained — no .onnx
 * fixture file on disk, no protobufjs cross-check (which would
 * require an external library at this stage of R1b). The fixture
 * is constructed in-memory by `buildFixtureModelProto()` so the
 * round-trip provability is purely against our own encoder/decoder.
 *
 * What this verifies:
 *
 *   1. Encode → decode round-trips the bytes exactly (byte-for-byte).
 *   2. Field-number access returns the values we wrote.
 *   3. Node mutation (op_type + domain) survives a re-encode.
 *   4. Opset upsert adds a new entry without clobbering existing ones.
 *
 * What this does NOT verify (covered by cycle-4+ work):
 *
 *   - Round-trip against real ONNX models (HF BitNet 2B); that test
 *     lives in `bitnet-graph-patcher.test.ts` once the patcher lands.
 *   - Unknown-field passthrough on messages we haven't touched in this
 *     fixture (e.g. AttributeProto, TensorProto).
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  ModelProtoView,
  buildFixtureModelProto,
  decodeMessage,
  encodeMessage,
} from "../src/inference/onnx-proto-min.ts";

test("fixture round-trips byte-for-byte", () => {
  const bytes = buildFixtureModelProto();
  const decoded = decodeMessage(bytes);
  const reencoded = encodeMessage(decoded);
  assert.deepEqual(
    Array.from(reencoded),
    Array.from(bytes),
    "encode(decode(bytes)) must equal bytes",
  );
});

test("ModelProtoView exposes the 2 fixture nodes", () => {
  const bytes = buildFixtureModelProto();
  const model = ModelProtoView.fromBytes(bytes);
  const nodes = model.graph.nodes;
  assert.equal(nodes.length, 2, "fixture has 2 nodes");
  assert.equal(nodes[0]?.opType, "MatMul");
  assert.equal(nodes[1]?.opType, "Identity");
  assert.equal(nodes[0]?.name, "test_matmul");
  assert.equal(nodes[1]?.name, "test_identity");
  assert.deepEqual(nodes[0]?.input, ["in_a", "in_b"]);
  assert.deepEqual(nodes[0]?.output, ["matmul_out"]);
  assert.deepEqual(nodes[1]?.input, ["matmul_out"]);
  assert.deepEqual(nodes[1]?.output, ["out"]);
});

test("opType + domain mutation survives encode → decode", () => {
  const bytes = buildFixtureModelProto();
  const model = ModelProtoView.fromBytes(bytes);
  assert.equal(model.graph.nodes[0]?.opType, "MatMul");
  assert.equal(model.graph.nodes[0]?.domain, "");

  // Mutate
  const node0 = model.graph.nodes[0];
  if (!node0) throw new Error("node 0 missing");
  node0.opType = "BitLinear";
  node0.domain = "etzhayyim.ai";

  const patched = model.toBytes();

  // Re-decode the patched bytes
  const model2 = ModelProtoView.fromBytes(patched);
  assert.equal(model2.graph.nodes[0]?.opType, "BitLinear");
  assert.equal(model2.graph.nodes[0]?.domain, "etzhayyim.ai");
  // Identity node should be untouched.
  assert.equal(model2.graph.nodes[1]?.opType, "Identity");
  assert.equal(model2.graph.nodes[1]?.domain, "");
});

test("graph + model metadata round-trips after mutation", () => {
  const bytes = buildFixtureModelProto();
  const model = ModelProtoView.fromBytes(bytes);
  const node0 = model.graph.nodes[0];
  if (!node0) throw new Error("node 0 missing");
  node0.opType = "BitLinear";
  const patched = model.toBytes();
  const model2 = ModelProtoView.fromBytes(patched);
  assert.equal(model2.graph.name, "test_graph", "graph.name preserved");
  // ir_version + producer_name are stored in unknown-field passthrough;
  // verify they survive by checking the underlying field map directly.
  const irVersionEntries = model2.fieldMap.get(1); // MODEL_F.IR_VERSION
  assert.ok(irVersionEntries && irVersionEntries.length === 1, "ir_version present");
  const producerEntries = model2.fieldMap.get(2); // MODEL_F.PRODUCER_NAME
  assert.ok(producerEntries && producerEntries.length === 1, "producer_name present");
});

test("upsertOpsetImport adds a new entry", () => {
  const bytes = buildFixtureModelProto();
  const model = ModelProtoView.fromBytes(bytes);

  // Fixture has the default opset (domain="" version=18).
  const before = model.opsetImports;
  assert.equal(before.length, 1);
  assert.equal(before[0]?.domain, "");
  assert.equal(before[0]?.version, 18);

  // Add the etzhayyim custom-op opset.
  model.upsertOpsetImport({ domain: "etzhayyim.ai", version: 1 });

  const after = model.opsetImports;
  assert.equal(after.length, 2, "opset count increased by 1");
  assert.equal(after[0]?.domain, "");
  assert.equal(after[0]?.version, 18, "default opset preserved");
  const etzhayyim = after.find((o) => o.domain === "etzhayyim.ai");
  assert.ok(etzhayyim !== undefined, "etzhayyim.ai opset added");
  assert.equal(etzhayyim?.version, 1);

  // Round-trip through encode → decode.
  const patched = model.toBytes();
  const model2 = ModelProtoView.fromBytes(patched);
  const after2 = model2.opsetImports;
  assert.equal(after2.length, 2, "opset count survives re-encode");
  assert.ok(
    after2.find((o) => o.domain === "etzhayyim.ai" && o.version === 1),
    "etzhayyim.ai opset survives re-encode",
  );
});

test("upsertOpsetImport updates an existing domain", () => {
  const bytes = buildFixtureModelProto();
  const model = ModelProtoView.fromBytes(bytes);
  // Bump the default opset version 18 → 21.
  model.upsertOpsetImport({ domain: "", version: 21 });
  const after = model.opsetImports;
  assert.equal(after.length, 1, "no new opset added when updating existing");
  assert.equal(after[0]?.version, 21);
});
