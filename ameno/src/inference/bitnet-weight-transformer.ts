/**
 * @etzhayyim/ameno/inference/bitnet-weight-transformer — bf16 → i2_s
 * sign-quantization of BitNet trunk weights (ADR-2605263700 R1b
 * commit 3).
 *
 * The HF `onnx-community/bitnet-b1.58-2B-4T-bf16-ONNX` artifact ships
 * BitNet trunk weights as **bf16 dense tensors**. The R0 BitLinear
 * kernel reads i2_s packed weights + a per-row f16 scale. This
 * module bridges the two by performing absmean sign-quantization in
 * pure TypeScript — mirroring `ggml_bitnet_transform_tensor` from
 * `40-engine/baien-wasm-ternary/src/api.rs` byte-for-byte.
 *
 * ## Algorithm (matches the Rust crate)
 *
 * For each row:
 *
 *   absmean = Σ|w_i| / n_cols
 *   threshold = absmean × 0.5
 *   w_quant_i = +1   if w_i > threshold
 *               -1   if w_i < -threshold
 *                0   otherwise
 *
 * Output:
 *   - W_packed:  i2_s, 4 weights / byte, INT8 dtype container
 *   - W_scale:   per-row absmean, FLOAT16
 *
 * ## Wire integration with the patched ONNX graph
 *
 * `transformBitNetWeights(modelView)` walks every `BitLinear` node
 * (already rewritten by `bitnet-graph-patcher`), reads the bf16
 * weight initializer named by `node.input[1]`, runs the transform,
 * appends two new initializers (`<orig>.i2s_packed` + `<orig>.scale`)
 * to the GraphProto's initializer list, and updates the node's input
 * list to point at the new tensors.
 *
 * **Idempotent**: if the node's input[1] already points at an
 * `.i2s_packed`-suffixed name, the node is skipped.
 *
 * The original bf16 initializer is **left in place**. Stripping it
 * is a separate pass (later R1b commit) — keeping it allows
 * fallback to the legacy fp16 path during development.
 *
 * ## Gate compliance
 *
 * - **G2 (kernel-task-level API mirror)**: the in-TS transform here
 *   replicates `ggml_bitnet_transform_tensor` field-for-field
 *   (input shape, absmean computation, threshold sign-quant). A
 *   later commit can swap this for a direct WASM call into
 *   `40-engine/baien-wasm-ternary/`; both implementations MUST
 *   produce bit-identical output on the same input.
 * - **G3 (i2_s layout invariant)**: the byte-packing uses the same
 *   little-endian 4-weights-per-byte encoding (`00 → 0`, `01 → +1`,
 *   `10/11 → -1`) defined in `bitlinear-forward.ts` and `i2s.rs`.
 */

import {
  ModelProtoView,
  DataType,
  encodeTensorProto,
  type DataTypeValue,
  type TensorProtoView,
  type NodeProtoView,
} from "./onnx-proto-min.ts";
import { BITLINEAR_OP_TYPE } from "./bitnet-graph-patcher.ts";

/** Suffix appended to the original bf16 weight name for W_packed. */
export const I2S_PACKED_SUFFIX = ".i2s_packed" as const;

/** Suffix appended to the original bf16 weight name for W_scale. */
export const W_SCALE_SUFFIX = ".scale" as const;

/** Result of a transform pass. */
export interface WeightTransformResult {
  /** Number of BitLinear nodes whose weight was transformed. */
  readonly transformedCount: number;
  /** Names of the original bf16 tensors we read. */
  readonly originalWeightNames: readonly string[];
  /** Names of the new (W_packed, W_scale) tensor pairs added. */
  readonly newTensorNames: readonly string[];
  /** Names of BitLinear nodes that were skipped (already transformed). */
  readonly skippedNodeNames: readonly string[];
}

// ──────────────────────────────────────────────────────────────
// Pure quantization algorithm — mirrors Rust ggml_bitnet_transform_tensor
// ──────────────────────────────────────────────────────────────

/**
 * Convert one bf16 value (held in a 16-bit unsigned int) to fp32.
 *
 * The bf16 format is the high 16 bits of an IEEE-754 fp32 — sign +
 * 8-bit exponent + 7-bit mantissa. To convert to fp32 we simply
 * left-shift 16 bits into a fresh fp32 (zero-fill the low half).
 */
function bf16ToF32(bf16Bits: number): number {
  const buf = new ArrayBuffer(4);
  const u32 = new Uint32Array(buf);
  u32[0] = (bf16Bits & 0xffff) << 16;
  return new Float32Array(buf)[0]!;
}

/**
 * Convert an fp32 value to fp16 (IEEE-754 binary16) bits. Returns the
 * 16-bit unsigned representation.
 *
 * Handles: ±0, normal, subnormal, ±Infinity, NaN. Rounds to nearest
 * (ties to even).
 *
 * This is the standard `f32 → f16` algorithm; same one used by the
 * `half` crate in Rust. We need it because the per-row absmean
 * scale ships as f16 in the W_scale initializer.
 */
function f32ToF16Bits(f: number): number {
  const buf = new ArrayBuffer(4);
  new Float32Array(buf)[0] = f;
  const bits = new Uint32Array(buf)[0]!;

  const sign = (bits >>> 16) & 0x8000;
  let exp = (bits >>> 23) & 0xff;
  let mant = bits & 0x7fffff;

  if (exp === 0xff) {
    // Inf / NaN
    return sign | 0x7c00 | (mant !== 0 ? 1 : 0);
  }
  if (exp === 0) {
    // Subnormal fp32 → fp16 0 (underflow).
    return sign;
  }
  const newExp = exp - 127 + 15;
  if (newExp >= 0x1f) {
    // Overflow → Inf.
    return sign | 0x7c00;
  }
  if (newExp <= 0) {
    // Subnormal fp16.
    if (newExp < -10) return sign; // underflow
    mant = (mant | 0x800000) >>> (1 - newExp);
    // Round to nearest even.
    if ((mant & 0x1000) !== 0) {
      mant += 0x2000;
    }
    return sign | (mant >>> 13);
  }
  // Normal fp16.
  if ((mant & 0x1000) !== 0) {
    mant += 0x2000;
    if ((mant & 0x800000) !== 0) {
      mant = 0;
      exp = newExp + 1;
    } else {
      exp = newExp;
    }
  } else {
    exp = newExp;
  }
  return sign | (exp << 10) | (mant >>> 13);
}

/**
 * Pack 4 i8 ternary values into one byte. Mirrors
 * `pack_i2s_byte` in `40-engine/baien-wasm-ternary/src/i2s.rs`.
 *
 *   2-bit encoding: 00 → 0 ; 01 → +1 ; 10 → -1 (11 reserved/-1 too)
 *
 * `weights[i] === 0` → bits 00, `> 0` → bits 01, `< 0` → bits 10.
 * Byte layout little-endian within the byte: w3 w2 w1 w0.
 */
function packI2sByte(weights: readonly [number, number, number, number]): number {
  let byte = 0;
  for (let i = 0; i < 4; i++) {
    const w = weights[i]!;
    let bits = 0;
    if (w === 0) bits = 0b00;
    else if (w > 0) bits = 0b01;
    else bits = 0b10;
    byte |= bits << (i * 2);
  }
  return byte;
}

/**
 * Sign-quantize a row of fp32 weights with the absmean threshold.
 * Returns the per-row scale (absmean) + the i8 ternary array.
 *
 * Bit-equivalent to the Rust implementation per gate G2.
 */
export function quantizeRowAbsmean(
  row: Float32Array,
): { scale: number; ternary: Int8Array } {
  let sumAbs = 0.0;
  for (let i = 0; i < row.length; i++) {
    sumAbs += Math.abs(row[i]!);
  }
  const absmean = sumAbs / row.length;
  const threshold = absmean * 0.5;
  const ternary = new Int8Array(row.length);
  for (let i = 0; i < row.length; i++) {
    const w = row[i]!;
    if (w > threshold) ternary[i] = 1;
    else if (w < -threshold) ternary[i] = -1;
    // else: 0 (default).
  }
  return { scale: absmean, ternary };
}

/**
 * Pack an i8 ternary array into i2_s bytes (4 weights / byte).
 * Pads the final byte with zeros if the row length is not a multiple
 * of 4.
 */
export function packI2sRow(ternary: Int8Array): Uint8Array {
  const nBytes = Math.ceil(ternary.length / 4);
  const out = new Uint8Array(nBytes);
  for (let b = 0; b < nBytes; b++) {
    const tile: [number, number, number, number] = [0, 0, 0, 0];
    for (let s = 0; s < 4; s++) {
      const idx = b * 4 + s;
      if (idx < ternary.length) {
        tile[s] = ternary[idx]!;
      }
    }
    out[b] = packI2sByte(tile);
  }
  return out;
}

// ──────────────────────────────────────────────────────────────
// bf16 weight decode + transform + initializer emission
// ──────────────────────────────────────────────────────────────

/**
 * Decode a bf16 TensorProto into a Float32Array, row-major.
 *
 * The TensorProto's `dims` are the 2D shape (out_rows, in_cols). The
 * `raw_data` is little-endian uint16 (one per bf16 weight).
 */
export function decodeBf16TensorToF32(t: TensorProtoView): {
  rows: number;
  cols: number;
  f32: Float32Array;
} {
  if (t.dataType !== DataType.BFLOAT16) {
    throw new Error(
      `decodeBf16TensorToF32: expected BFLOAT16 (data_type=${String(DataType.BFLOAT16)}), got ${String(t.dataType)}`,
    );
  }
  const dims = t.dims;
  if (dims.length !== 2) {
    throw new Error(
      `decodeBf16TensorToF32: expected 2D tensor, got ${String(dims.length)}D (${dims.join(",")})`,
    );
  }
  const rows = dims[0]!;
  const cols = dims[1]!;
  const expectedBytes = rows * cols * 2; // 2 bytes per bf16
  const raw = t.rawData;
  if (raw.length !== expectedBytes) {
    throw new Error(
      `decodeBf16TensorToF32: raw_data length ${String(raw.length)} != expected ${String(expectedBytes)} (${String(rows)}×${String(cols)})`,
    );
  }
  const dv = new DataView(raw.buffer, raw.byteOffset, raw.byteLength);
  const f32 = new Float32Array(rows * cols);
  for (let i = 0; i < rows * cols; i++) {
    const bf16Bits = dv.getUint16(i * 2, /* littleEndian */ true);
    f32[i] = bf16ToF32(bf16Bits);
  }
  return { rows, cols, f32 };
}

/**
 * Run the bf16 → i2_s + f16 transform on one tensor. Returns the two
 * new TensorProto definitions ready to encode + append to the graph.
 *
 * `packed_dims` = `[rows, ceil(cols/4)]`.
 * `scale_dims` = `[rows]`.
 */
export function transformBf16ToI2sAndScale(
  origName: string,
  t: TensorProtoView,
): {
  packed: import("./onnx-proto-min.ts").NewTensorProto;
  scale: import("./onnx-proto-min.ts").NewTensorProto;
} {
  const { rows, cols, f32 } = decodeBf16TensorToF32(t);

  // Allocate the output buffers.
  const packedColsBytes = Math.ceil(cols / 4);
  const packedBytes = new Uint8Array(rows * packedColsBytes);
  const scaleBytes = new Uint8Array(rows * 2); // f16 = 2 bytes/element
  const scaleDv = new DataView(scaleBytes.buffer);

  for (let r = 0; r < rows; r++) {
    const rowSlice = f32.subarray(r * cols, (r + 1) * cols);
    const { scale, ternary } = quantizeRowAbsmean(rowSlice);
    const rowPacked = packI2sRow(ternary);
    packedBytes.set(rowPacked, r * packedColsBytes);
    scaleDv.setUint16(r * 2, f32ToF16Bits(scale), /* littleEndian */ true);
  }

  return {
    packed: {
      name: origName + I2S_PACKED_SUFFIX,
      dataType: DataType.INT8,
      dims: [rows, packedColsBytes],
      rawData: packedBytes,
    },
    scale: {
      name: origName + W_SCALE_SUFFIX,
      dataType: DataType.FLOAT16,
      dims: [rows],
      rawData: scaleBytes,
    },
  };
}

// ──────────────────────────────────────────────────────────────
// Graph-level orchestrator
// ──────────────────────────────────────────────────────────────

/**
 * Walk every `BitLinear` node in the model graph, transform its
 * bf16 weight initializer into (i2_s packed, f16 scale), and rewire
 * the node's input list.
 *
 * Idempotent: nodes whose input[1] already ends in
 * `.i2s_packed` are skipped (already-transformed).
 *
 * **MUTATES** the passed-in `ModelProtoView` in place. Caller must
 * call `model.toBytes()` afterwards to get the patched bytes.
 */
export function transformBitNetWeights(
  model: ModelProtoView,
): WeightTransformResult {
  const originalWeightNames: string[] = [];
  const newTensorNames: string[] = [];
  const skippedNodeNames: string[] = [];

  const graph = model.graph;
  for (const node of graph.nodes) {
    if (!isBitLinearNode(node)) continue;

    const inputs = node.input;
    if (inputs.length < 2 || inputs[1] === undefined) continue;
    const weightName = inputs[1];

    // Idempotent: skip nodes that have already been transformed.
    if (weightName.endsWith(I2S_PACKED_SUFFIX)) {
      skippedNodeNames.push(node.name);
      continue;
    }

    const orig = graph.findInitializer(weightName);
    if (orig === null) {
      // Initializer missing — caller will see this in
      // newTensorNames being shorter than originalWeightNames; we
      // log via the skipped list for telemetry.
      skippedNodeNames.push(node.name + " (initializer missing)");
      continue;
    }

    const { packed, scale } = transformBf16ToI2sAndScale(weightName, orig);

    // Encode + append the two new initializers.
    graph.addInitializerBytes(encodeTensorProto(packed));
    graph.addInitializerBytes(encodeTensorProto(scale));
    originalWeightNames.push(weightName);
    newTensorNames.push(packed.name, scale.name);

    // Rewire node.input: replace input[1] with W_packed.name; append
    // W_scale.name as input[2].
    rewireNodeInputs(node, packed.name, scale.name);
  }

  return {
    transformedCount: originalWeightNames.length,
    originalWeightNames,
    newTensorNames,
    skippedNodeNames,
  };
}

function isBitLinearNode(node: NodeProtoView): boolean {
  return node.opType === BITLINEAR_OP_TYPE;
}

/**
 * Rewrite the node's input list:
 *   [X, W_bf16, ...existing] → [X, W_packed, W_scale, ...existing]
 *
 * Pure mutation of the underlying FieldMap.
 */
function rewireNodeInputs(
  node: NodeProtoView,
  packedName: string,
  scaleName: string,
): void {
  const textEncoder = new TextEncoder();
  const existing = node.input;
  const newInputs: string[] = [existing[0]!, packedName, scaleName, ...existing.slice(2)];

  // Re-encode the input list as field 1 in the NodeProto's FieldMap.
  const NODE_INPUT_FIELD = 1;
  node.fieldMap.set(
    NODE_INPUT_FIELD,
    newInputs.map((s) => ({
      wireType: 2, // WireType.LEN
      bytes: textEncoder.encode(s),
    })),
  );
}
