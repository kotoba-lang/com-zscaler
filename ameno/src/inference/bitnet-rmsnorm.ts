/**
 * @etzhayyim/ameno/inference/bitnet-rmsnorm — Root-Mean-Square layer
 * normalization (ADR-2605263800 R1b commit 8).
 *
 * BitNet uses **RMSNorm** (Zhang & Sennrich 2019, "Root Mean Square
 * Layer Normalization", arXiv:1910.07467), not LayerNorm. RMSNorm
 * normalizes by RMS (root mean square) of activations and applies a
 * per-dimension learned scale:
 *
 *     rms = sqrt(mean(x_i^2) + eps)
 *     y_i = x_i × w_i / rms
 *
 * `eps` from `config.rms_norm_eps` (BitNet 2B = 1e-5). `w` is the
 * learned per-dim scale tensor; in the HF BitNet ONNX it shows up as
 * `model.layers.<N>.{input_layernorm,post_attention_layernorm}.weight`
 * and `model.norm.weight` (the final norm before lm_head).
 *
 * Unlike LayerNorm, RMSNorm has no bias term and no mean-centering.
 * It's strictly cheaper per token: O(d) instead of 2×O(d).
 *
 * ## Variants
 *
 *   - `applyRmsNorm(x, w, eps, out)` — generic, fp32 throughout.
 *   - `applyRmsNormInPlace(x, w, eps)` — write back to `x`.
 *
 * Both are in-place-friendly: the temporary `mean(x²)` reduction
 * doesn't allocate.
 *
 * ## Numerical stability
 *
 * For BitNet 2B's hidden_size = 2048, the sum `Σx²` is summing 2048
 * terms. With activations in roughly [-3, 3] (post-attention/FFN
 * residual + RMSNorm cycle), the sum stays well under fp32 envelope.
 * The `+ eps` inside the sqrt guarantees we never divide by zero
 * even with all-zero input.
 */

/**
 * Apply RMSNorm to `x` (length `d`) using per-dim weight `w` (length `d`),
 * write the result to `out` (length `d`). Caller-allocated buffers.
 *
 * Allows `out === x` for in-place use (the reduction completes before
 * the per-element write begins).
 */
export function applyRmsNorm(
  x: Float32Array,
  w: Float32Array,
  eps: number,
  out: Float32Array,
): void {
  const d = x.length;
  if (w.length !== d) {
    throw new Error(
      `applyRmsNorm: x.length=${String(d)} != w.length=${String(w.length)}`,
    );
  }
  if (out.length !== d) {
    throw new Error(
      `applyRmsNorm: x.length=${String(d)} != out.length=${String(out.length)}`,
    );
  }
  let sumSq = 0;
  for (let i = 0; i < d; i++) {
    const v = x[i]!;
    sumSq += v * v;
  }
  const rms = Math.sqrt(sumSq / d + eps);
  const invRms = 1.0 / rms;
  for (let i = 0; i < d; i++) {
    out[i] = x[i]! * w[i]! * invRms;
  }
}

/** Sugar: rewrite `x` in place. */
export function applyRmsNormInPlace(
  x: Float32Array,
  w: Float32Array,
  eps: number,
): void {
  applyRmsNorm(x, w, eps, x);
}
