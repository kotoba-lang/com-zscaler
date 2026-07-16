/**
 * @etzhayyim/ameno/inference/bitnet-silu — SiLU activation (Swish)
 * for BitNet FFN gate (ADR-2605263800 R1b commit 8).
 *
 * BitNet uses the SwiGLU FFN pattern (Shazeer 2020, "GLU Variants
 * Improve Transformer"):
 *
 *     ffn(x) = down_proj( silu(gate_proj(x)) ⊙ up_proj(x) )
 *
 * where `⊙` is element-wise product and `silu(z) = z × σ(z)` with
 * `σ` the logistic sigmoid:
 *
 *     silu(z) = z × (1 / (1 + exp(-z)))
 *
 * SiLU is monotonic, smooth, and self-gating. It outputs values in
 * roughly `(-0.28, +∞)` for positive z (asymptotes to z) and
 * `(-0.28, 0)` for negative z. The minimum is at z ≈ -1.278
 * (silu ≈ -0.2785).
 *
 * ## Numerical notes
 *
 * For very large positive `z`, `exp(-z)` underflows to 0 → silu = z.
 * For very large negative `z`, `exp(-z)` overflows; we switch to the
 * stable form `silu(z) = z × exp(z) / (1 + exp(z))` for `z < 0`.
 * Combined: `silu(z) = z × σ(z)` with σ computed via the standard
 * branch:
 *
 *     σ(z) = 1 / (1 + exp(-z))         if z ≥ 0
 *     σ(z) = exp(z) / (1 + exp(z))     if z < 0
 *
 * This keeps the computation in `[0, 1]` for σ regardless of `|z|`.
 */

/**
 * Numerically stable logistic sigmoid.
 *
 * Returns σ(z) = 1 / (1 + exp(-z)) computed via the branch that
 * avoids overflow.
 */
export function sigmoid(z: number): number {
  if (z >= 0) {
    const ez = Math.exp(-z);
    return 1 / (1 + ez);
  }
  const ez = Math.exp(z);
  return ez / (1 + ez);
}

/**
 * SiLU (Swish) activation: silu(z) = z × σ(z).
 *
 * Scalar version. The vectorized path lives below.
 */
export function silu(z: number): number {
  return z * sigmoid(z);
}

/**
 * Apply SiLU element-wise to `x`, writing to `out`. Both same length.
 *
 * `out === x` is allowed (the write at index i only depends on
 * `x[i]`, no cross-element data flow).
 */
export function applySiluElementwise(
  x: Float32Array,
  out: Float32Array,
): void {
  if (x.length !== out.length) {
    throw new Error(
      `applySiluElementwise: x.length=${String(x.length)} != out.length=${String(out.length)}`,
    );
  }
  for (let i = 0; i < x.length; i++) {
    out[i] = silu(x[i]!);
  }
}

/**
 * SwiGLU combiner: `out[i] = silu(gate[i]) × up[i]`.
 *
 * This is the inner gate-and-multiply of the BitNet FFN: gate and
 * up are both BitLinear projections of the same input; their
 * element-wise product (with silu on the gate) goes into down_proj.
 *
 * Allows `out === gate` for in-place gate-mutation.
 */
export function applySwiGluCombine(
  gate: Float32Array,
  up: Float32Array,
  out: Float32Array,
): void {
  if (gate.length !== up.length || gate.length !== out.length) {
    throw new Error(
      `applySwiGluCombine: shapes disagree (gate=${String(gate.length)}, up=${String(up.length)}, out=${String(out.length)})`,
    );
  }
  for (let i = 0; i < gate.length; i++) {
    out[i] = silu(gate[i]!) * up[i]!;
  }
}
