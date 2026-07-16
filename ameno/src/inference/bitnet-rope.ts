/**
 * @etzhayyim/ameno/inference/bitnet-rope — Rotary Position Embedding
 * precompute + apply (ADR-2605263800 R1b commit 6).
 *
 * BitNet 2B uses the LLaMA-3 RoPE convention: split-half (NOT
 * interleaved). For head_dim `d`, vector `x` of length `d` is split
 * into two halves `[x_0, ..., x_{d/2-1}, x_{d/2}, ..., x_{d-1}]`; the
 * rotation at position `p` is:
 *
 *     for i in 0..d/2:
 *       θ_i = p × base^(-2i / d)
 *       a = x[i]
 *       b = x[i + d/2]
 *       x'[i]       = a × cos(θ_i) - b × sin(θ_i)
 *       x'[i + d/2] = a × sin(θ_i) + b × cos(θ_i)
 *
 * `base` = `rope_theta` from the model config (BitNet 2B = 500_000).
 *
 * ## Precompute
 *
 * `precomputeRope(config)` builds two Float32Array tables of shape
 * `[max_position_embeddings, head_dim/2]` filled with `cos(θ)` and
 * `sin(θ)` for every (position, dimension-pair) combination. For
 * BitNet 2B at max_pos=4096 and head_dim=64 (so half=32):
 *
 *   cos table:  4096 × 32 × 4 bytes = 512 KB
 *   sin table:  4096 × 32 × 4 bytes = 512 KB
 *   total:                            1 MB
 *
 * Tiny relative to ADR-2605241900 §G1 budget. Precompute happens
 * once at model load; per-token apply costs are O(head_dim) per
 * Q/K vector per head.
 *
 * ## Apply
 *
 * `applyRopeHfStyle(vec, position, cache)` rotates `vec` in-place.
 * Caller dispatches once per Q vector + once per K vector at each
 * decode step. The same `vec` reference is mutated; no allocation
 * inside the hot path.
 *
 * ## Why split-half not interleaved
 *
 * HuggingFace transformers switched to split-half during the
 * LLaMA-1 → LLaMA-2 era (commit 4.31.0; cf. PR #21861) for SIMD
 * friendliness — the first-half and second-half are contiguous
 * memory regions, so vector load + multiply-add is straightforward.
 * BitNet's HF export inherits this convention. Implementing the
 * interleaved variant would produce incorrect attention.
 */

import type { BitNetConfig } from "./bitnet-config.ts";

/** Precomputed cos/sin tables for a single model configuration. */
export interface RopeCache {
  /** `cos(p × base^(-2i/d))` indexed as `[p × halfHead + i]`. */
  readonly cos: Float32Array;
  /** `sin(p × base^(-2i/d))` indexed as `[p × halfHead + i]`. */
  readonly sin: Float32Array;
  /** Maximum position (`config.max_position_embeddings`). */
  readonly maxPos: number;
  /** `config.head_dim / 2`. */
  readonly halfHead: number;
  /** `config.rope_theta` (echoed for diagnostics). */
  readonly base: number;
}

/**
 * Precompute the RoPE cos/sin tables for a given BitNet config.
 *
 * One-shot at model load. The returned cache is frozen — never mutate
 * the tables; allocate a new cache if the config changes (it won't —
 * config is itself frozen via `parseBitNetConfig`).
 *
 * Complexity: `max_pos × head_dim / 2` multiplications + 2 sin/cos
 * calls each. For BitNet 2B that's ~131k operations — ~1 ms on M1.
 */
export function precomputeRope(config: BitNetConfig): RopeCache {
  const maxPos = config.max_position_embeddings;
  const headDim = config.head_dim;
  const halfHead = headDim / 2;
  if (!Number.isInteger(halfHead)) {
    throw new Error(
      `precomputeRope: head_dim (${String(headDim)}) is not even — cannot split-half rotate`,
    );
  }
  const base = config.rope_theta;

  const cos = new Float32Array(maxPos * halfHead);
  const sin = new Float32Array(maxPos * halfHead);

  // Precompute the inverse-frequencies once:
  //   inv_freq[i] = base^(-2i / head_dim) = 1 / base^(2i / head_dim)
  const invFreq = new Float32Array(halfHead);
  for (let i = 0; i < halfHead; i++) {
    invFreq[i] = 1.0 / Math.pow(base, (2 * i) / headDim);
  }

  for (let p = 0; p < maxPos; p++) {
    const off = p * halfHead;
    for (let i = 0; i < halfHead; i++) {
      const angle = p * invFreq[i]!;
      cos[off + i] = Math.cos(angle);
      sin[off + i] = Math.sin(angle);
    }
  }

  return Object.freeze({
    cos,
    sin,
    maxPos,
    halfHead,
    base,
  });
}

/**
 * Apply RoPE to a single vector IN PLACE (HF split-half convention).
 *
 * `vec.length` MUST equal `cache.halfHead × 2` — the full head_dim.
 * `position` MUST be in `[0, cache.maxPos)`.
 *
 * The vector is mutated; the same reference is returned for chaining.
 *
 * For multi-head dispatch: call once per (head, vector). Vectors are
 * stored row-major as `[num_heads × head_dim]`, so the caller passes
 * `vec.subarray(headIdx * headDim, (headIdx + 1) * headDim)`.
 */
export function applyRopeHfStyle(
  vec: Float32Array,
  position: number,
  cache: RopeCache,
): Float32Array {
  const half = cache.halfHead;
  if (vec.length !== half * 2) {
    throw new Error(
      `applyRopeHfStyle: vec.length=${String(vec.length)} != head_dim=${String(half * 2)}`,
    );
  }
  if (position < 0 || position >= cache.maxPos) {
    throw new Error(
      `applyRopeHfStyle: position=${String(position)} out of [0, ${String(cache.maxPos)})`,
    );
  }
  const off = position * half;
  for (let i = 0; i < half; i++) {
    const c = cache.cos[off + i]!;
    const s = cache.sin[off + i]!;
    const a = vec[i]!;
    const b = vec[i + half]!;
    vec[i] = a * c - b * s;
    vec[i + half] = a * s + b * c;
  }
  return vec;
}

/**
 * Apply RoPE to a sequence of `num_heads` packed head-vectors
 * (row-major `[num_heads × head_dim]`). Convenience wrapper for the
 * attention dispatch — calls `applyRopeHfStyle` `num_heads` times.
 *
 * `multiHeadVec` is mutated in place.
 */
export function applyRopeMultiHead(
  multiHeadVec: Float32Array,
  numHeads: number,
  position: number,
  cache: RopeCache,
): Float32Array {
  const headDim = cache.halfHead * 2;
  if (multiHeadVec.length !== numHeads * headDim) {
    throw new Error(
      `applyRopeMultiHead: vec.length=${String(multiHeadVec.length)} != num_heads × head_dim=${String(numHeads * headDim)}`,
    );
  }
  for (let h = 0; h < numHeads; h++) {
    const view = multiHeadVec.subarray(h * headDim, (h + 1) * headDim);
    applyRopeHfStyle(view, position, cache);
  }
  return multiHeadVec;
}
