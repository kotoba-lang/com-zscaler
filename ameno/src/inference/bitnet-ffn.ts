/**
 * @etzhayyim/ameno/inference/bitnet-ffn — SwiGLU feed-forward block
 * with BitLinear gate/up/down projections (ADR-2605263800 R1b commit 12).
 *
 * Per-token, per-layer FFN forward:
 *
 *     gate = BitLinear(hidden, W_gate)        — [hidden_size → ffn_inner]
 *     up   = BitLinear(hidden, W_up)          — [hidden_size → ffn_inner]
 *     mid  = silu(gate) ⊙ up                  — [ffn_inner]    via applySwiGluCombine
 *     out  = BitLinear(mid, W_down)           — [ffn_inner → hidden_size]
 *
 * Three BitLinear projections + one SwiGLU combine. The
 * intermediate `mid` reuses the `gate` buffer (the combine is
 * in-place safe per `applySwiGluCombine`).
 *
 * Residual add (`x = x + out`) is the caller's responsibility,
 * done in `bitnet-transformer.ts` (R1b commit 13).
 *
 * Like the attention module, this currently routes through the
 * fp32-fallback BitLinear dispatch. The R1c bridge swaps in the
 * wgpu/wasm kernels without changing this module's signature.
 */

import type { BitNetConfig } from "./bitnet-config.ts";
import type { BitLinearWeightPack } from "./bitnet-weight-pack.ts";

import { applyBitLinearFp32Fallback } from "./bitnet-bitlinear-dispatch.ts";
import { applySwiGluCombine } from "./bitnet-silu.ts";

/** Per-layer FFN weights (three BitLinear projections). */
export interface FfnLayerWeights {
  readonly gate_proj: BitLinearWeightPack;
  readonly up_proj: BitLinearWeightPack;
  readonly down_proj: BitLinearWeightPack;
}

/** Caller-allocated FFN scratch. Reused across all calls. */
export interface FfnScratch {
  /** Gate workspace [ffn_inner]. Reused as `mid` after SwiGLU combine. */
  gate: Float32Array;
  /** Up workspace [ffn_inner]. */
  up: Float32Array;
}

/** Allocate FFN scratch sized for a given config. */
export function allocateFfnScratch(config: BitNetConfig): FfnScratch {
  return {
    gate: new Float32Array(config.intermediate_size),
    up: new Float32Array(config.intermediate_size),
  };
}

/**
 * Apply one SwiGLU FFN block forward.
 *
 * - `hidden`: input activations, length `config.hidden_size`.
 * - `weights`: gate/up/down BitLinear packs for this layer.
 * - `out`: output buffer, length `config.hidden_size`. Caller-allocated.
 *
 * Throws on hidden/out shape mismatch.
 */
export function applyFfn(
  hidden: Float32Array,
  weights: FfnLayerWeights,
  config: BitNetConfig,
  scratch: FfnScratch,
  out: Float32Array,
): void {
  const hiddenSize = config.hidden_size;
  const ffnInner = config.intermediate_size;

  if (hidden.length !== hiddenSize) {
    throw new Error(
      `applyFfn: hidden.length=${String(hidden.length)} != hidden_size=${String(hiddenSize)}`,
    );
  }
  if (out.length !== hiddenSize) {
    throw new Error(
      `applyFfn: out.length=${String(out.length)} != hidden_size=${String(hiddenSize)}`,
    );
  }
  if (scratch.gate.length !== ffnInner || scratch.up.length !== ffnInner) {
    throw new Error(
      `applyFfn: scratch.gate.length=${String(scratch.gate.length)} or scratch.up.length=${String(scratch.up.length)} != ffn_inner=${String(ffnInner)}`,
    );
  }

  // ── 1. gate = BitLinear(hidden, W_gate) ──
  applyBitLinearFp32Fallback(hidden, weights.gate_proj, scratch.gate);
  // ── 2. up = BitLinear(hidden, W_up) ──
  applyBitLinearFp32Fallback(hidden, weights.up_proj, scratch.up);
  // ── 3. silu(gate) ⊙ up → reuse gate buffer ──
  applySwiGluCombine(scratch.gate, scratch.up, scratch.gate);
  // ── 4. out = BitLinear(mid, W_down) ──
  applyBitLinearFp32Fallback(scratch.gate, weights.down_proj, out);
}
