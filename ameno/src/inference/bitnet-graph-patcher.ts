/**
 * @etzhayyim/ameno/inference/bitnet-graph-patcher — ONNX graph
 * rewriter (ADR-2605263700 R1b commit 2).
 *
 * Walks a BitNet ONNX `ModelProto`, finds `MatMul` nodes whose weight
 * input matches the trunk-projection pattern, and replaces them with
 * `etzhayyim.ai::BitLinear` custom-op nodes.
 *
 * **In-scope this commit**:
 *   - Node matcher + opType/domain mutation.
 *   - Opset import registration for the `etzhayyim.ai` domain.
 *   - PatchResult shape (counts + replaced tensor names).
 *   - R1b-G1 gate: replaced-node count must match expectation.
 *
 * **Out-of-scope (later commits in the R1b chain)**:
 *   - **bf16 → i2_s weight transformation.** The HF
 *     `onnx-community/bitnet-b1.58-2B-4T-bf16-ONNX` artifact ships
 *     weights as bf16 dense tensors (the BitNet 1.58 packing happens
 *     offline by Microsoft and the ONNX export reverses it). To run
 *     our BitLinear kernel we need (W_packed: i2_s, W_scale: f16).
 *     The weight transform pass lands in R1b commit ≥3 (calls
 *     `ggml_bitnet_transform_tensor` from
 *     `40-engine/baien-wasm-ternary/src/api.rs`).
 *   - **Adding the activation-scale input.** BitLinear needs a
 *     per-block X_scale tensor; this is computed at runtime by the
 *     custom-op handler in `bitnet-custom-op.ts` (R1b commit ≥3),
 *     not by the graph rewriter.
 *   - **Initializer cleanup.** The original bf16 weight initializer
 *     is left in place; a future pass strips it once R1b commit ≥3
 *     verifies the patched graph runs end-to-end.
 *
 * This commit is therefore "**the node-replacement pass only**" —
 * the result is a graph that ORT-Web will reject (no BitLinear handler
 * registered yet) but whose structure can be inspected, unit-tested,
 * and reasoned about offline.
 */

import {
  ModelProtoView,
  type NodeProtoView,
} from "./onnx-proto-min.ts";

/**
 * Pattern for BitNet trunk projection weight tensors.
 *
 * Matches the HuggingFace transformers export convention:
 *
 *   model.layers.<N>.self_attn.{q,k,v,o}_proj.weight
 *   model.layers.<N>.mlp.{gate,up,down}_proj.weight
 *
 * Critically does NOT match runtime attention matmuls like
 * `model.layers.<N>.self_attn.attn_weights` (the QK^T product) —
 * those are computed at inference time, not stored as initializers,
 * and replacing them would corrupt the attention output.
 *
 * The trailing `.weight` is what distinguishes a stored initializer
 * from a runtime tensor.
 */
export const BITNET_TRUNK_PROJ_PATTERN: RegExp =
  /\.(q_proj|k_proj|v_proj|o_proj|gate_proj|up_proj|down_proj)\.weight$/;

/** The custom-op domain we register for BitLinear. */
export const ETZHAYYIM_OPSET_DOMAIN = "etzhayyim.ai" as const;

/** The custom-op name. Matches the `compute` handler registration in
 *  `bitnet-custom-op.ts` (R1b commit ≥3). */
export const BITLINEAR_OP_TYPE = "BitLinear" as const;

/** The opset version. Bumped when the BitLinear op signature changes. */
export const ETZHAYYIM_OPSET_VERSION = 1 as const;

/** Default `MatMul` op type (in the empty/ai.onnx default opset). */
const MATMUL_OP_TYPE = "MatMul";

/**
 * Result of a graph-patch operation.
 *
 * Carries enough information for callers to:
 *   - Hand the patched bytes to ORT-Web.
 *   - Verify the replacement count against expectation (gate R1b-G1).
 *   - Emit telemetry on which trunk layers were rewritten.
 */
export interface PatchResult {
  /** Patched ONNX bytes ready to hand to `ort.InferenceSession.create`. */
  readonly bytes: Uint8Array;
  /** Number of `MatMul` nodes that were rewritten to `BitLinear`. */
  readonly replacedCount: number;
  /**
   * Names of the weight tensors whose enclosing MatMul node was
   * replaced. Sorted in graph-walk order so consecutive entries
   * correspond to consecutive transformer layers.
   */
  readonly replacedTensors: readonly string[];
  /**
   * Names of MatMul nodes that were SKIPPED because they didn't match
   * the trunk-projection pattern. Useful for verifying we didn't
   * accidentally rewrite attention QK^T or other runtime matmuls.
   */
  readonly skippedMatMulNames: readonly string[];
}

/**
 * Options governing the patch behaviour. R1b commit 2 keeps this minimal;
 * later commits add a `weightTransformer` hook for the bf16 → i2_s pass.
 */
export interface PatchOptions {
  /**
   * Expected replacement count. If set and the actual count differs,
   * `patchBitNetGraph` throws (gate R1b-G1 from ADR-2605263700 §7).
   *
   * For `onnx-community/bitnet-b1.58-2B-4T-bf16-ONNX` the expected
   * count is `30 layers × 7 projections = 210` (verified empirically
   * once we read the real model header in R1b commit ≥3). Until then
   * callers should leave this `undefined` to disable the check.
   */
  readonly expectedReplacements?: number;
}

/**
 * Determine whether a given MatMul node should be replaced.
 *
 * A node matches when its **second input** (`node.input[1]`, the
 * weight tensor by ONNX convention) is named according to
 * `BITNET_TRUNK_PROJ_PATTERN`.
 *
 * Returns the matching weight-tensor name on hit, or `null` on miss.
 *
 * Exported for unit-test access.
 */
export function matchTrunkProjection(node: NodeProtoView): string | null {
  if (node.opType !== MATMUL_OP_TYPE) return null;
  const inputs = node.input;
  // MatMul takes (A, B) where B is the weight tensor in the
  // transformers convention. Reject nodes that don't have both inputs
  // — we don't know what to do with degenerate matmuls.
  if (inputs.length < 2) return null;
  const weightName = inputs[1];
  if (weightName === undefined) return null;
  if (!BITNET_TRUNK_PROJ_PATTERN.test(weightName)) return null;
  return weightName;
}

/**
 * Patch a BitNet ONNX `ModelProto` by replacing trunk-projection
 * MatMul nodes with `etzhayyim.ai::BitLinear` custom ops.
 *
 * The input bytes are NOT modified — a fresh `Uint8Array` is returned
 * via `PatchResult.bytes`. Safe to call concurrently on the same
 * input (idempotent in the sense that re-patching an already-patched
 * graph is a no-op because all `BitLinear` nodes will have `opType !=
 * "MatMul"` and skip the matcher).
 *
 * @throws if `options.expectedReplacements` is set and the actual
 *         count differs (gate R1b-G1).
 */
export function patchBitNetGraph(
  originalBytes: Uint8Array,
  options: PatchOptions = {},
): PatchResult {
  const model = ModelProtoView.fromBytes(originalBytes);
  const replacedTensors: string[] = [];
  const skippedMatMulNames: string[] = [];

  for (const node of model.graph.nodes) {
    if (node.opType !== MATMUL_OP_TYPE) {
      // Not a MatMul → nothing to do for this commit.
      continue;
    }
    const weightName = matchTrunkProjection(node);
    if (weightName === null) {
      // It is a MatMul but doesn't match the trunk-projection pattern.
      // Record the skip so callers can audit (gate R1b-G1 sibling).
      skippedMatMulNames.push(node.name);
      continue;
    }

    // ── REWRITE ──
    node.opType = BITLINEAR_OP_TYPE;
    node.domain = ETZHAYYIM_OPSET_DOMAIN;
    replacedTensors.push(weightName);
  }

  // Register our custom-op opset so ORT-Web doesn't reject the graph
  // on "unknown domain".
  if (replacedTensors.length > 0) {
    model.upsertOpsetImport({
      domain: ETZHAYYIM_OPSET_DOMAIN,
      version: ETZHAYYIM_OPSET_VERSION,
    });
  }

  if (
    options.expectedReplacements !== undefined &&
    options.expectedReplacements !== replacedTensors.length
  ) {
    throw new Error(
      `bitnet-graph-patcher: replaced ${String(replacedTensors.length)} MatMul nodes, ` +
        `expected ${String(options.expectedReplacements)} (gate R1b-G1, ADR-2605263700 §7). ` +
        `Skipped MatMul names: [${skippedMatMulNames.slice(0, 5).join(", ")}${skippedMatMulNames.length > 5 ? ", ..." : ""}]. ` +
        `Replaced tensors: [${replacedTensors.slice(0, 5).join(", ")}${replacedTensors.length > 5 ? ", ..." : ""}].`,
    );
  }

  const patchedBytes = model.toBytes();
  return {
    bytes: patchedBytes,
    replacedCount: replacedTensors.length,
    replacedTensors,
    skippedMatMulNames,
  };
}
