/**
 * @etzhayyim/ameno/inference/kernels/bitlinear-forward — WGSL BitLinear
 * forward kernel (ADR-2605263300 §5).
 *
 * BitLinear forward computes
 *
 *     Y[m, n] = (Σ_k unpack_i2s(W_packed[m, k]) · unpack_i8(X_q8[k, n]))
 *               · W_scale[m] · X_scale[n / block]
 *
 * where `W_packed` is the i2_s ternary weight tensor (4 weights per u32
 * — see `i2s` layout constants in `40-engine/baien-wasm-ternary/src/i2s.rs`,
 * mirrored bit-for-bit here per gate G3) and `X_q8` is the q8-quantized
 * activation tensor (4 i8 values per u32).
 *
 * R0 (this commit) ships:
 *   - `WGSL_BITLINEAR_FORWARD`  — authoritative WGSL string
 *   - `BitLinearForwardParams`  — Params layout (immutable contract)
 *   - `dispatchBitLinearForward` — throws R1a marker
 *
 * R1a wires the dispatch (bind groups, pipeline, queue submit).
 * R1b wires the transformers.js layer-replacement bridge.
 * R1c replaces the scalar reference matmul in the Rust WASM crate
 * with v128 SIMD; this WGSL kernel is unaffected by R1c.
 *
 * Gate references:
 *   - G3 (i2_s layout drift = revert) — see `I2S_*` constants below;
 *     must match `i2s.rs` exactly.
 *   - G4 (scalar reference is numerical contract) — this shader must
 *     agree with the Rust scalar matmul to ±1 ULP fp16 on the fixed
 *     test vector before R1a passes.
 *   - G9 (no silent fallback) — `dispatchBitLinearForward` throws,
 *     does not silently call transformers.js.
 *   - G14 (zero behaviour change in R0) — this module is unused by
 *     default; the existing `inference.ts` dispatch is untouched.
 */

/**
 * i2_s packed-weight layout constants. **MUST** match
 * `40-engine/baien-wasm-ternary/src/i2s.rs` byte-for-byte (gate G3).
 *
 * Each byte packs 4 weights, little-endian within the byte:
 *
 *     bit  7 6 5 4 3 2 1 0
 *          w3  w2  w1  w0
 *
 * Each 2-bit slot encodes:
 *
 *     2-bit | weight
 *     ------+--------
 *     00    |   0
 *     01    |  +1
 *     10    |  -1
 *     11    |  -1   (reserved/equivalent — upstream maps both 10 and 11 to -1)
 *
 * This matches `microsoft/BitNet:src/ggml-bitnet.h` and the HF
 * `onnx-community/bitnet-b1.58-2B-4T-bf16-ONNX` packed format.
 */
export const I2S_WEIGHTS_PER_BYTE = 4 as const;
export const I2S_BITS_PER_WEIGHT = 2 as const;
export const I2S_WEIGHTS_PER_U32 = 16 as const;

/**
 * Params struct passed via uniform binding 6. Encoded as 6 × u32
 * (24 bytes; WebGPU uniform requires 16-byte alignment so the struct
 * is padded to 32 bytes — the shader's `struct Params { ... }` makes
 * the padding explicit).
 *
 * `kBlocks` is `ceil(K / Q8_BLOCK_SIZE)`. `Q8_BLOCK_SIZE` matches
 * llama.cpp / bitnet.cpp's q8_0 block size of 32 elements — bound at
 * shader compile time via `const Q8_BLOCK_SIZE: u32 = 32u;` in the
 * WGSL source.
 */
export interface BitLinearForwardParams {
  /** Output rows. */
  readonly M: number;
  /** Output cols. */
  readonly N: number;
  /** Inner dim. */
  readonly K: number;
  /** `ceil(K / 32)` — number of q8_0 blocks per output column. */
  readonly kBlocks: number;
  /** Reserved for R1b layer-replacement metadata (e.g. layer index). */
  readonly reserved0: number;
  /** Reserved for R1b. */
  readonly reserved1: number;
}

/**
 * BitLinear forward shader.
 *
 * Bindings:
 *   @group(0) @binding(0)  W_packed  : array<u32>    — i2_s weights (16 / u32)  [M × ceil(K/16)]
 *   @group(0) @binding(1)  X_q8      : array<i32>    — q8 activations (4 i8 / u32) [ceil(K/4) × N]
 *   @group(0) @binding(2)  W_scale   : array<f16>    — per-row weight scale [M]
 *   @group(0) @binding(3)  X_scale   : array<f16>    — per-block activation scale [kBlocks × N]
 *   @group(0) @binding(4)  Y         : array<f16>    — output [M × N]
 *   @group(0) @binding(5)  P         : Params
 *
 * Workgroup: 16 × 16 × 1. One output tile per workgroup.
 *
 * Requires:
 *   - `shader-f16` feature on the GPUAdapter (probed by
 *     `probeBitnetBackend()` — caller MUST NOT submit this shader to
 *     an adapter that does not advertise it).
 *
 * Source-of-truth (ADR-2605263400 §1, gate R1a-G1):
 *   `40-engine/baien-wasm-ternary/shaders/bitlinear_forward.wgsl`.
 *
 *   The Rust crate is the implementation-of-record for the BitLinear
 *   kernel (it owns the scalar reference matmul that defines the
 *   numerical contract per G4). We import the WGSL from there via
 *   Vite's `?raw` query — the ambient declaration at
 *   `src/types/wgsl-raw.d.ts` makes `tsc --noEmit` happy.
 */
import wgslBitLinearForward from "../../../../../40-engine/baien-wasm-ternary/shaders/bitlinear_forward.wgsl?raw";

export const WGSL_BITLINEAR_FORWARD: string = wgslBitLinearForward;

/**
 * Dispatch the BitLinear forward shader.
 *
 * R0 (this commit) throws with a clear pointer to R1a (numeric test
 * harness — isolated dispatch via Dawn/node, ±1 ULP vs scalar
 * reference). R1b wires this into the transformers.js layer-replacement
 * bridge so the trunk's `MatMul` nodes route through here.
 *
 * Signature is permissive (`_args: unknown[]`) until R1a fixes it —
 * matches the pattern from `train/kernels.ts:dispatchLoraForward`.
 */
export async function dispatchBitLinearForward(
  ..._args: unknown[]
): Promise<void> {
  throw new Error(
    "dispatchBitLinearForward: R1a — requires isolated WGSL kernel dispatch via Dawn/node against scalar reference matmul. WGSL_BITLINEAR_FORWARD is ready; bind-group + pipeline setup is missing.",
  );
}
