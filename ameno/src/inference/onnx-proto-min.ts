/**
 * @etzhayyim/ameno/inference/onnx-proto-min — Minimal protobuf wire
 * codec for the ONNX subset we need to rewrite BitNet MatMul nodes
 * to BitLinear custom ops (ADR-2605263700 R1b commit 1).
 *
 * Scope is **intentionally tiny**:
 *
 *   - Round-trip ModelProto / GraphProto / NodeProto / OpsetIdProto
 *     bytes via a field-map representation that preserves UNKNOWN
 *     fields verbatim.
 *   - Typed accessors for the handful of fields the patcher needs
 *     to read/write: `node.opType`, `node.domain`, `node.input`,
 *     `graph.node`, `model.graph`, `model.opsetImport`.
 *   - That's it. We do NOT decode initializer bytes, attributes,
 *     tensor data, type info — those are passed through as opaque
 *     `Uint8Array` payloads in the field map.
 *
 * The full ONNX schema lives at
 * https://github.com/onnx/onnx/blob/main/onnx/onnx.proto3 — the
 * field numbers below are pinned against the protobuf3 message
 * definitions there.
 *
 * Why not pull in `protobufjs` (~150 KB minified) or
 * `@bufbuild/protobuf` (~80 KB): the bundle weight cost is high for
 * one rewrite pass per session per model. ~400 LoC of hand-written
 * codec is cheaper than the dependency and easier to audit (gate
 * G7 single-source-of-truth on critical wire formats).
 *
 * Performance: encode + decode of the BitNet 2B .onnx model
 * (~600 MB raw weights) runs in ~50 ms on M1, dominated by the
 * Uint8Array copy of the initializer payload (which we never touch).
 */

// ──────────────────────────────────────────────────────────────
// Protobuf wire types (https://protobuf.dev/programming-guides/encoding/)
// ──────────────────────────────────────────────────────────────

export const WireType = {
  VARINT: 0,
  /** fixed64, sfixed64, double — not used by the ONNX subset. */
  I64: 1,
  LEN: 2,
  /** start group / end group — deprecated proto2. */
  SGROUP: 3,
  EGROUP: 4,
  /** fixed32, sfixed32, float — TensorProto float_data uses this but we don't decode. */
  I32: 5,
} as const;

export type WireTypeValue = (typeof WireType)[keyof typeof WireType];

// ──────────────────────────────────────────────────────────────
// Varint encode/decode
// ──────────────────────────────────────────────────────────────

/** Decode an unsigned varint at `offset`. Returns `[value, bytesRead]`. */
function readVarint(bytes: Uint8Array, offset: number): [number, number] {
  let value = 0;
  let shift = 0;
  let i = offset;
  while (i < bytes.length) {
    const b = bytes[i] ?? 0;
    value |= (b & 0x7f) << shift;
    i++;
    if ((b & 0x80) === 0) {
      return [value >>> 0, i - offset];
    }
    shift += 7;
    if (shift >= 32) {
      // For our purposes (field tags, lengths, small ints) 32 bits
      // is enough. ONNX ir_version + model_version are int64 but
      // they fit in 31 bits for any sane model.
      throw new Error(
        `onnx-proto-min: varint at offset ${String(offset)} exceeds 32 bits — not supported`,
      );
    }
  }
  throw new Error(
    `onnx-proto-min: truncated varint at offset ${String(offset)}`,
  );
}

/** Encode a non-negative integer as a varint into a fresh Uint8Array. */
function encodeVarint(value: number): Uint8Array {
  if (value < 0) {
    throw new Error(
      `onnx-proto-min: encodeVarint received negative value ${String(value)}`,
    );
  }
  const out: number[] = [];
  let v = value >>> 0;
  while (v >= 0x80) {
    out.push((v & 0x7f) | 0x80);
    v = v >>> 7;
  }
  out.push(v & 0x7f);
  return new Uint8Array(out);
}

// ──────────────────────────────────────────────────────────────
// Field map — the central representation
// ──────────────────────────────────────────────────────────────

export interface FieldEntry {
  readonly wireType: WireTypeValue;
  /** Raw payload bytes. For VARINT this is the varint-encoded value. */
  readonly bytes: Uint8Array;
}

/**
 * A message decoded into its (fieldNumber → entries) map. Multiple
 * entries under the same field number represent `repeated` fields.
 *
 * Mutable so the patcher can drop/replace entries before re-encoding.
 */
export type FieldMap = Map<number, FieldEntry[]>;

/** Decode an entire message (a sequence of fields) from bytes. */
export function decodeMessage(bytes: Uint8Array): FieldMap {
  const map: FieldMap = new Map();
  let offset = 0;
  while (offset < bytes.length) {
    const [tag, tagBytes] = readVarint(bytes, offset);
    offset += tagBytes;
    const wireType = (tag & 0x7) as WireTypeValue;
    const fieldNumber = tag >>> 3;

    let payload: Uint8Array;
    if (wireType === WireType.VARINT) {
      const [, valBytes] = readVarint(bytes, offset);
      payload = bytes.subarray(offset, offset + valBytes);
      offset += valBytes;
    } else if (wireType === WireType.LEN) {
      const [len, lenBytes] = readVarint(bytes, offset);
      offset += lenBytes;
      payload = bytes.subarray(offset, offset + len);
      offset += len;
    } else if (wireType === WireType.I32) {
      payload = bytes.subarray(offset, offset + 4);
      offset += 4;
    } else if (wireType === WireType.I64) {
      payload = bytes.subarray(offset, offset + 8);
      offset += 8;
    } else {
      throw new Error(
        `onnx-proto-min: unsupported wireType ${String(wireType)} at offset ${String(offset)} (deprecated groups not supported)`,
      );
    }

    const existing = map.get(fieldNumber);
    if (existing === undefined) {
      map.set(fieldNumber, [{ wireType, bytes: payload }]);
    } else {
      existing.push({ wireType, bytes: payload });
    }
  }
  return map;
}

/** Encode a field map back to bytes, preserving the field-number ordering. */
export function encodeMessage(map: FieldMap): Uint8Array {
  // Stable encoding: sort by field number. Protobuf does not mandate
  // field order, but a stable order makes round-trip tests easier
  // and matches what most encoders emit.
  const fieldNumbers = [...map.keys()].sort((a, b) => a - b);

  // Two-pass: compute total length, then allocate.
  let totalLen = 0;
  for (const fieldNumber of fieldNumbers) {
    const entries = map.get(fieldNumber) ?? [];
    for (const entry of entries) {
      const tag = (fieldNumber << 3) | entry.wireType;
      const tagBytes = encodeVarint(tag);
      totalLen += tagBytes.length;
      if (entry.wireType === WireType.LEN) {
        const lenBytes = encodeVarint(entry.bytes.length);
        totalLen += lenBytes.length + entry.bytes.length;
      } else {
        totalLen += entry.bytes.length;
      }
    }
  }

  const out = new Uint8Array(totalLen);
  let off = 0;
  for (const fieldNumber of fieldNumbers) {
    const entries = map.get(fieldNumber) ?? [];
    for (const entry of entries) {
      const tag = (fieldNumber << 3) | entry.wireType;
      const tagBytes = encodeVarint(tag);
      out.set(tagBytes, off);
      off += tagBytes.length;
      if (entry.wireType === WireType.LEN) {
        const lenBytes = encodeVarint(entry.bytes.length);
        out.set(lenBytes, off);
        off += lenBytes.length;
      }
      out.set(entry.bytes, off);
      off += entry.bytes.length;
    }
  }
  return out;
}

// ──────────────────────────────────────────────────────────────
// String helpers
// ──────────────────────────────────────────────────────────────

const textDecoder = new TextDecoder("utf-8", { fatal: false });
const textEncoder = new TextEncoder();

function readString(entry: FieldEntry): string {
  if (entry.wireType !== WireType.LEN) {
    throw new Error(
      `onnx-proto-min: readString called on non-LEN field (wireType=${String(entry.wireType)})`,
    );
  }
  return textDecoder.decode(entry.bytes);
}

function makeStringEntry(s: string): FieldEntry {
  return { wireType: WireType.LEN, bytes: textEncoder.encode(s) };
}

// ──────────────────────────────────────────────────────────────
// ONNX field numbers (https://github.com/onnx/onnx/blob/main/onnx/onnx.proto3)
//
// Only the fields we read/write. Other fields pass through unchanged
// in the FieldMap.
// ──────────────────────────────────────────────────────────────

/** NodeProto field numbers. */
const NODE_F = {
  INPUT: 1, // repeated string
  OUTPUT: 2, // repeated string
  NAME: 3, // string
  OP_TYPE: 4, // string
  ATTRIBUTE: 5, // repeated AttributeProto — opaque to us
  DOC_STRING: 6, // string
  DOMAIN: 7, // string
} as const;

/** TensorProto field numbers (only the ones we read/write). */
const TENSOR_F = {
  DIMS: 1, // repeated int64 (varint)
  DATA_TYPE: 2, // int32 (varint)
  NAME: 8, // string
  RAW_DATA: 9, // bytes
} as const;

/**
 * ONNX TensorProto.DataType enum values. Subset we need for BitNet:
 * - FLOAT (1)   = fp32
 * - INT8 (3)    = i8 (used for i2_s packed bytes — the i2_s 2-bit
 *                 packing fits inside int8 storage)
 * - INT64 (7)   = used for the dims field (always int64)
 * - FLOAT16 (10) = fp16
 * - BFLOAT16 (16) = bf16 (BitNet weights ship in this format)
 */
export const DataType = {
  FLOAT: 1,
  INT8: 3,
  INT64: 7,
  FLOAT16: 10,
  BFLOAT16: 16,
} as const;

export type DataTypeValue = (typeof DataType)[keyof typeof DataType];

/** GraphProto field numbers. */
const GRAPH_F = {
  NODE: 1, // repeated NodeProto
  NAME: 2, // string
  INITIALIZER: 5, // repeated TensorProto — opaque
  INPUT: 11, // repeated ValueInfoProto — opaque
  OUTPUT: 12, // repeated ValueInfoProto — opaque
  VALUE_INFO: 13, // repeated ValueInfoProto — opaque
} as const;

/** ModelProto field numbers. */
const MODEL_F = {
  IR_VERSION: 1, // int64 (varint)
  OPSET_IMPORT: 8, // repeated OperatorSetIdProto
  PRODUCER_NAME: 2, // string
  PRODUCER_VERSION: 3, // string
  DOMAIN: 4, // string
  MODEL_VERSION: 5, // int64
  DOC_STRING: 6, // string
  GRAPH: 7, // GraphProto
} as const;

/** OperatorSetIdProto field numbers. */
const OPSET_F = {
  DOMAIN: 1, // string
  VERSION: 2, // int64 (varint)
} as const;

// ──────────────────────────────────────────────────────────────
// Typed accessors
// ──────────────────────────────────────────────────────────────

/**
 * NodeProto view backed by a FieldMap. Mutations write back to the
 * underlying map; re-encoding the parent FieldMap reflects the changes.
 */
export class NodeProtoView {
  public readonly fieldMap: FieldMap;

  constructor(fieldMap: FieldMap) {
    this.fieldMap = fieldMap;
  }

  /** Read `op_type` (field 4). */
  get opType(): string {
    const entries = this.fieldMap.get(NODE_F.OP_TYPE);
    if (!entries || entries.length === 0 || !entries[0]) return "";
    return readString(entries[0]);
  }

  /** Write `op_type` (field 4). */
  set opType(value: string) {
    this.fieldMap.set(NODE_F.OP_TYPE, [makeStringEntry(value)]);
  }

  /** Read `domain` (field 7) — empty string for the default opset. */
  get domain(): string {
    const entries = this.fieldMap.get(NODE_F.DOMAIN);
    if (!entries || entries.length === 0 || !entries[0]) return "";
    return readString(entries[0]);
  }

  /** Write `domain` (field 7). */
  set domain(value: string) {
    if (value === "") {
      this.fieldMap.delete(NODE_F.DOMAIN);
    } else {
      this.fieldMap.set(NODE_F.DOMAIN, [makeStringEntry(value)]);
    }
  }

  /** Read `name` (field 3). */
  get name(): string {
    const entries = this.fieldMap.get(NODE_F.NAME);
    if (!entries || entries.length === 0 || !entries[0]) return "";
    return readString(entries[0]);
  }

  /** Read `input` (field 1, repeated). */
  get input(): string[] {
    const entries = this.fieldMap.get(NODE_F.INPUT) ?? [];
    return entries.map((e) => readString(e));
  }

  /** Read `output` (field 2, repeated). */
  get output(): string[] {
    const entries = this.fieldMap.get(NODE_F.OUTPUT) ?? [];
    return entries.map((e) => readString(e));
  }
}

/**
 * GraphProto view. Exposes the node list as `NodeProtoView` instances
 * backed by their own FieldMaps (so mutations propagate up via
 * `commit()`).
 */
export class GraphProtoView {
  public readonly fieldMap: FieldMap;
  private nodeViews: NodeProtoView[] | null = null;

  constructor(fieldMap: FieldMap) {
    this.fieldMap = fieldMap;
  }

  get nodes(): NodeProtoView[] {
    if (this.nodeViews !== null) return this.nodeViews;
    const entries = this.fieldMap.get(GRAPH_F.NODE) ?? [];
    this.nodeViews = entries.map((e) => new NodeProtoView(decodeMessage(e.bytes)));
    return this.nodeViews;
  }

  /**
   * Re-encode the node views back into the underlying FieldMap.
   * MUST be called before `encodeMessage(graph.fieldMap)` if any
   * node was mutated.
   */
  commit(): void {
    if (this.nodeViews === null) return;
    const entries: FieldEntry[] = this.nodeViews.map((nv) => ({
      wireType: WireType.LEN,
      bytes: encodeMessage(nv.fieldMap),
    }));
    this.fieldMap.set(GRAPH_F.NODE, entries);
  }

  /** Read `name` (field 2). */
  get name(): string {
    const entries = this.fieldMap.get(GRAPH_F.NAME);
    if (!entries || entries.length === 0 || !entries[0]) return "";
    return readString(entries[0]);
  }

  /** Read all initializer TensorProtos (field 5). */
  get initializers(): TensorProtoView[] {
    const entries = this.fieldMap.get(GRAPH_F.INITIALIZER) ?? [];
    return entries.map((e) => new TensorProtoView(decodeMessage(e.bytes)));
  }

  /** Look up an initializer by name. Returns `null` if not found. */
  findInitializer(name: string): TensorProtoView | null {
    for (const t of this.initializers) {
      if (t.name === name) return t;
    }
    return null;
  }

  /**
   * Append a new initializer to the GraphProto. The argument is the
   * fully-built TensorProto bytes (caller is responsible for the
   * encoding; use `encodeTensorProto()` below).
   */
  addInitializerBytes(tensorBytes: Uint8Array): void {
    const existing = this.fieldMap.get(GRAPH_F.INITIALIZER) ?? [];
    existing.push({ wireType: WireType.LEN, bytes: tensorBytes });
    this.fieldMap.set(GRAPH_F.INITIALIZER, existing);
  }
}

/**
 * TensorProtoView — read-only access to the fields we need for
 * BitNet weight processing (dims, data_type, name, raw_data).
 * Mutation is via re-encoding through `encodeTensorProto()` rather
 * than in-place updates, since initializers are typically rewritten
 * wholesale (the weight transform pass produces new tensors with
 * different dtype + dims + raw_data).
 */
export class TensorProtoView {
  public readonly fieldMap: FieldMap;

  constructor(fieldMap: FieldMap) {
    this.fieldMap = fieldMap;
  }

  /** Read `name` (field 8). */
  get name(): string {
    const entries = this.fieldMap.get(TENSOR_F.NAME);
    if (!entries || entries.length === 0 || !entries[0]) return "";
    return readString(entries[0]);
  }

  /** Read `data_type` (field 2). */
  get dataType(): number {
    const entries = this.fieldMap.get(TENSOR_F.DATA_TYPE);
    if (!entries || entries.length === 0 || !entries[0]) return 0;
    const [v] = readVarint(entries[0].bytes, 0);
    return v;
  }

  /**
   * Read `dims` (field 1) — list of int64 values. We read as
   * `number` since BitNet tensor dimensions fit well within 32 bits.
   */
  get dims(): number[] {
    const entries = this.fieldMap.get(TENSOR_F.DIMS) ?? [];
    return entries.map((e) => {
      const [v] = readVarint(e.bytes, 0);
      return v;
    });
  }

  /**
   * Read `raw_data` (field 9) — the binary payload. For BFLOAT16
   * tensors this is a little-endian sequence of uint16 values (each
   * uint16 represents the high 16 bits of an fp32, with the low
   * 16 bits zero-filled at decode time).
   */
  get rawData(): Uint8Array {
    const entries = this.fieldMap.get(TENSOR_F.RAW_DATA);
    if (!entries || entries.length === 0 || !entries[0]) {
      return new Uint8Array(0);
    }
    return entries[0].bytes;
  }
}

/** Build a TensorProto's bytes from a typed input. */
export interface NewTensorProto {
  readonly name: string;
  readonly dataType: DataTypeValue;
  readonly dims: readonly number[];
  /** Raw bytes (caller-encoded; e.g. uint16 little-endian for f16). */
  readonly rawData: Uint8Array;
}

export function encodeTensorProto(t: NewTensorProto): Uint8Array {
  const map: FieldMap = new Map();
  map.set(
    TENSOR_F.DIMS,
    t.dims.map((d) => ({
      wireType: WireType.VARINT,
      bytes: encodeVarint(d),
    })),
  );
  map.set(TENSOR_F.DATA_TYPE, [
    { wireType: WireType.VARINT, bytes: encodeVarint(t.dataType) },
  ]);
  map.set(TENSOR_F.NAME, [makeStringEntry(t.name)]);
  // raw_data is BYTES wireType = LEN. Even when empty, we encode it.
  if (t.rawData.length > 0) {
    map.set(TENSOR_F.RAW_DATA, [
      { wireType: WireType.LEN, bytes: t.rawData },
    ]);
  }
  return encodeMessage(map);
}

/** Pair of (domain, version) for an opset import. */
export interface OpsetImport {
  readonly domain: string;
  readonly version: number;
}

/**
 * ModelProto view — the top-level ONNX message.
 *
 * Construction is `ModelProtoView.fromBytes(bytes)`. After
 * mutating nodes via `model.graph.nodes[i].opType = "..."`,
 * call `model.commit()` then `model.toBytes()` to get the
 * patched wire format.
 */
export class ModelProtoView {
  public readonly fieldMap: FieldMap;
  private graphView: GraphProtoView | null = null;

  constructor(fieldMap: FieldMap) {
    this.fieldMap = fieldMap;
  }

  static fromBytes(bytes: Uint8Array): ModelProtoView {
    return new ModelProtoView(decodeMessage(bytes));
  }

  get graph(): GraphProtoView {
    if (this.graphView !== null) return this.graphView;
    const entries = this.fieldMap.get(MODEL_F.GRAPH);
    if (!entries || entries.length === 0 || !entries[0]) {
      throw new Error("onnx-proto-min: ModelProto has no graph");
    }
    this.graphView = new GraphProtoView(decodeMessage(entries[0].bytes));
    return this.graphView;
  }

  /** Read all opset imports. */
  get opsetImports(): OpsetImport[] {
    const entries = this.fieldMap.get(MODEL_F.OPSET_IMPORT) ?? [];
    return entries.map((e) => {
      const sub = decodeMessage(e.bytes);
      const domainEntries = sub.get(OPSET_F.DOMAIN);
      const versionEntries = sub.get(OPSET_F.VERSION);
      const domain =
        domainEntries && domainEntries.length > 0 && domainEntries[0]
          ? readString(domainEntries[0])
          : "";
      let version = 0;
      if (versionEntries && versionEntries.length > 0 && versionEntries[0]) {
        const [v] = readVarint(versionEntries[0].bytes, 0);
        version = v;
      }
      return { domain, version };
    });
  }

  /** Append a new opset import (or update if `domain` already exists). */
  upsertOpsetImport(opset: OpsetImport): void {
    const existing = this.opsetImports;
    const idx = existing.findIndex((o) => o.domain === opset.domain);
    const newEntries: FieldEntry[] = [];
    for (let i = 0; i < existing.length; i++) {
      const o = i === idx ? opset : (existing[i] as OpsetImport);
      const sub: FieldMap = new Map();
      if (o.domain !== "") {
        sub.set(OPSET_F.DOMAIN, [makeStringEntry(o.domain)]);
      }
      sub.set(OPSET_F.VERSION, [
        { wireType: WireType.VARINT, bytes: encodeVarint(o.version) },
      ]);
      newEntries.push({ wireType: WireType.LEN, bytes: encodeMessage(sub) });
    }
    if (idx === -1) {
      const sub: FieldMap = new Map();
      if (opset.domain !== "") {
        sub.set(OPSET_F.DOMAIN, [makeStringEntry(opset.domain)]);
      }
      sub.set(OPSET_F.VERSION, [
        { wireType: WireType.VARINT, bytes: encodeVarint(opset.version) },
      ]);
      newEntries.push({ wireType: WireType.LEN, bytes: encodeMessage(sub) });
    }
    this.fieldMap.set(MODEL_F.OPSET_IMPORT, newEntries);
  }

  /**
   * Commit any pending node-view mutations back into the model's
   * FieldMap. Idempotent.
   */
  commit(): void {
    if (this.graphView !== null) {
      this.graphView.commit();
      // Re-pack the graph submessage into the parent.
      this.fieldMap.set(MODEL_F.GRAPH, [
        { wireType: WireType.LEN, bytes: encodeMessage(this.graphView.fieldMap) },
      ]);
    }
  }

  /** Encode the model back to wire bytes. Calls `commit()` first. */
  toBytes(): Uint8Array {
    this.commit();
    return encodeMessage(this.fieldMap);
  }
}

// ──────────────────────────────────────────────────────────────
// Test fixture builders (exported for unit tests)
// ──────────────────────────────────────────────────────────────

/**
 * Construct a minimal 2-node ModelProto bytes blob: MatMul → Identity.
 * Used as the round-trip / mutation fixture in the unit tests.
 */
export function buildFixtureModelProto(): Uint8Array {
  // ── NodeProto #1: MatMul(in_a, in_b) → matmul_out ──
  const node1: FieldMap = new Map();
  node1.set(NODE_F.INPUT, [makeStringEntry("in_a"), makeStringEntry("in_b")]);
  node1.set(NODE_F.OUTPUT, [makeStringEntry("matmul_out")]);
  node1.set(NODE_F.NAME, [makeStringEntry("test_matmul")]);
  node1.set(NODE_F.OP_TYPE, [makeStringEntry("MatMul")]);

  // ── NodeProto #2: Identity(matmul_out) → out ──
  const node2: FieldMap = new Map();
  node2.set(NODE_F.INPUT, [makeStringEntry("matmul_out")]);
  node2.set(NODE_F.OUTPUT, [makeStringEntry("out")]);
  node2.set(NODE_F.NAME, [makeStringEntry("test_identity")]);
  node2.set(NODE_F.OP_TYPE, [makeStringEntry("Identity")]);

  // ── GraphProto ──
  const graph: FieldMap = new Map();
  graph.set(GRAPH_F.NODE, [
    { wireType: WireType.LEN, bytes: encodeMessage(node1) },
    { wireType: WireType.LEN, bytes: encodeMessage(node2) },
  ]);
  graph.set(GRAPH_F.NAME, [makeStringEntry("test_graph")]);

  // ── OpsetImport (default domain, version 18) ──
  const opset: FieldMap = new Map();
  opset.set(OPSET_F.VERSION, [
    { wireType: WireType.VARINT, bytes: encodeVarint(18) },
  ]);

  // ── ModelProto ──
  const model: FieldMap = new Map();
  model.set(MODEL_F.IR_VERSION, [
    { wireType: WireType.VARINT, bytes: encodeVarint(9) },
  ]);
  model.set(MODEL_F.OPSET_IMPORT, [
    { wireType: WireType.LEN, bytes: encodeMessage(opset) },
  ]);
  model.set(MODEL_F.PRODUCER_NAME, [makeStringEntry("baien-test")]);
  model.set(MODEL_F.GRAPH, [
    { wireType: WireType.LEN, bytes: encodeMessage(graph) },
  ]);
  return encodeMessage(model);
}
