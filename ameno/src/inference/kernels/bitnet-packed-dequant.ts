/**
 * @etzhayyim/ameno/inference/kernels/bitnet-packed-dequant — WGSL
 * i2_s → f16 dequant shader (ADR-2605263300 §5).
 *
 * Standalone shader that takes a tile of i2_s packed weights + the
 * per-row scale and writes a contiguous f16 weight tile. Used as:
 *
 *   1. A debug-only fallback for transformers.js layer-replacement
 *      (R1b) when the WebGPU adapter advertises insufficient
 *      `maxComputeWorkgroupStorageSize` for the fused BitLinear
 *      kernel — we dequant once, then submit a generic fp16 GEMM.
 *
 *   2. A correctness oracle for the SIMD path: dequant a tile in
 *      shader, compare against the scalar Rust dequant in
 *      `40-engine/baien-wasm-ternary/src/i2s.rs`, expect bit-identical
 *      output (gate G4).
 *
 * The i2_s layout constants below MUST match
 * `40-engine/baien-wasm-ternary/src/i2s.rs` and
 * `./bitlinear-forward.ts` (gate G3).
 */

/**
 * Params struct for the dequant shader. Encoded as 4 × u32 (uniform
 * block — 16 bytes, naturally aligned).
 */
export interface BitnetPackedDequantParams {
  /** Output rows. */
  readonly M: number;
  /** Output cols (= K — the inner dim of the BitLinear matmul). */
  readonly K: number;
  /** Reserved for R1b. */
  readonly reserved0: number;
  /** Reserved for R1b. */
  readonly reserved1: number;
}

/**
 * i2_s packed-weight → f16 dequant shader.
 *
 * Bindings:
 *   @group(0) @binding(0)  W_packed : array<u32>   — i2_s (16 weights / u32)
 *   @group(0) @binding(1)  W_scale  : array<f16>   — per-row scale [M]
 *   @group(0) @binding(2)  W_out    : array<f16>   — dense fp16 [M × K]
 *   @group(0) @binding(3)  P        : Params
 *
 * Workgroup: 16 × 16 × 1. One f16 weight per thread.
 *
 * Source-of-truth (ADR-2605263400 §1, gate R1a-G1):
 *   `40-engine/baien-wasm-ternary/shaders/bitnet_packed_dequant.wgsl`.
 */
import wgslPackedDequant from "../../../../../40-engine/baien-wasm-ternary/shaders/bitnet_packed_dequant.wgsl?raw";

export const WGSL_BITNET_PACKED_DEQUANT: string = wgslPackedDequant;

/**
 * Dispatch the i2_s → f16 dequant shader.
 *
 * R0 throws R1a marker. R1a wires the dispatch + numeric test.
 */
export async function dispatchPackedDequant(
  ..._args: unknown[]
): Promise<void> {
  throw new Error(
    "dispatchPackedDequant: R1a — requires isolated WGSL kernel dispatch + bit-identical comparison against `baien-wasm-ternary/src/i2s.rs` scalar dequant. WGSL_BITNET_PACKED_DEQUANT is ready.",
  );
}
