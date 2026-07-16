/**
 * @etzhayyim/ameno/inference/bitnet-attention — multi-head attention
 * forward step with GQA (grouped-query attention) and BitLinear QKVO
 * projections (ADR-2605263800 R1b commit 11).
 *
 * Per-token, per-layer attention forward:
 *
 *   1. Q = BitLinear(hidden, W_q)   — dim hidden_size  (= num_heads × head_dim)
 *      K = BitLinear(hidden, W_k)   — dim kv_dim       (= num_kv_heads × head_dim)
 *      V = BitLinear(hidden, W_v)   — dim kv_dim
 *
 *   2. RoPE applied to Q and K at the current position (split-half
 *      LLaMA-3 convention via `bitnet-rope`).
 *
 *   3. K and V written to KV cache at (layerIdx, position) and
 *      `setCurrentLength(position + 1)` advances the cache — idempotent
 *      across layers at the same position.
 *
 *   4. For each Q head `h`:
 *        kvHead = h / gqa_group_size
 *        For each cached position p in [0, currentLength):
 *          score[p] = (Q_h · K_cache[layer, p, kvHead]) × 1/√head_dim
 *        softmaxInPlace(score, currentLength)
 *        attn_out_h = Σ_p score[p] × V_cache[layer, p, kvHead]
 *
 *   5. Output projection: out = BitLinear(attn_out, W_o)
 *
 * The residual add (`x = x + out`) is the CALLER's responsibility,
 * done in `bitnet-transformer.ts` (R1b commit 13).
 *
 * ## Layer flow at position p
 *
 *   for each layer L in 0..numLayers:
 *     applyAttention(hidden, p, L, weights[L], ..., scratch, attn_out)
 *     // After EACH layer's attention, KV cache currentLength == p + 1.
 *     // All layers at the same position write to slot p; advancing
 *     // currentLength is idempotent.
 *
 * The caller does NOT need to setCurrentLength itself between layers.
 *
 * ## fp32 fallback (R1b)
 *
 * The BitLinear projections route through `applyBitLinearFp32Fallback`
 * — pure-TS dequantization + multiply. The wgpu/wasm dispatch path
 * stays for R1c (when the kernel-side pointer marshalling lands).
 */

import type { BitNetConfig } from "./bitnet-config.ts";
import type { RopeCache } from "./bitnet-rope.ts";
import type { KvCache } from "./bitnet-kv-cache.ts";
import type { BitLinearWeightPack } from "./bitnet-weight-pack.ts";

import { applyBitLinearFp32Fallback } from "./bitnet-bitlinear-dispatch.ts";
import { applyRopeMultiHead } from "./bitnet-rope.ts";
import {
  softmaxInPlace,
  dotProductF32,
  scaleInPlaceF32,
} from "./bitnet-math.ts";

/** Per-layer attention weights (four BitLinear projections). */
export interface AttentionLayerWeights {
  readonly q_proj: BitLinearWeightPack;
  readonly k_proj: BitLinearWeightPack;
  readonly v_proj: BitLinearWeightPack;
  readonly o_proj: BitLinearWeightPack;
}

/**
 * Caller-allocated scratch buffers — reused across all calls to avoid
 * heap traffic in the hot path. Pre-allocate once at model load with
 * `allocateAttentionScratch(config, maxPos)`.
 */
export interface AttentionScratch {
  /** Q workspace [hidden_size]. */
  q: Float32Array;
  /** K workspace [kvDim]. */
  k: Float32Array;
  /** V workspace [kvDim]. */
  v: Float32Array;
  /** Scratch for K cache slice [maxPos × kvDim]. */
  kCacheSlice: Float32Array;
  /** Scratch for V cache slice [maxPos × kvDim]. */
  vCacheSlice: Float32Array;
  /** Attention scores workspace [maxPos]. */
  scores: Float32Array;
  /** Per-head attention output workspace [hidden_size]. */
  attnOut: Float32Array;
}

/** Allocate a fresh scratch struct sized for the model + max ctx. */
export function allocateAttentionScratch(
  config: BitNetConfig,
  maxPos = config.max_position_embeddings,
): AttentionScratch {
  const kvDim = config.num_key_value_heads * config.head_dim;
  return {
    q: new Float32Array(config.hidden_size),
    k: new Float32Array(kvDim),
    v: new Float32Array(kvDim),
    kCacheSlice: new Float32Array(maxPos * kvDim),
    vCacheSlice: new Float32Array(maxPos * kvDim),
    scores: new Float32Array(maxPos),
    attnOut: new Float32Array(config.hidden_size),
  };
}

/**
 * Single attention forward step at one position, one layer.
 *
 * Mutates: `kvCache` (writes layer's K/V at the position) and writes
 * `out` (the attention output, NOT residual-added).
 *
 * `out.length === config.hidden_size` is required.
 */
export function applyAttention(
  hidden: Float32Array,
  position: number,
  layerIdx: number,
  weights: AttentionLayerWeights,
  ropeCache: RopeCache,
  kvCache: KvCache,
  config: BitNetConfig,
  scratch: AttentionScratch,
  out: Float32Array,
): void {
  const numHeads = config.num_attention_heads;
  const numKvHeads = config.num_key_value_heads;
  const headDim = config.head_dim;
  const hiddenSize = config.hidden_size;
  const kvDim = numKvHeads * headDim;
  const gqaGroup = numHeads / numKvHeads;
  const attnScale = 1.0 / Math.sqrt(headDim);

  if (hidden.length !== hiddenSize) {
    throw new Error(
      `applyAttention: hidden.length=${String(hidden.length)} != hidden_size=${String(hiddenSize)}`,
    );
  }
  if (out.length !== hiddenSize) {
    throw new Error(
      `applyAttention: out.length=${String(out.length)} != hidden_size=${String(hiddenSize)}`,
    );
  }

  // ── 1. QKV projections via BitLinear (fp32 fallback for R1b) ──
  applyBitLinearFp32Fallback(hidden, weights.q_proj, scratch.q);
  applyBitLinearFp32Fallback(hidden, weights.k_proj, scratch.k);
  applyBitLinearFp32Fallback(hidden, weights.v_proj, scratch.v);

  // ── 2. RoPE on Q and K (per-head split-half rotation) ──
  applyRopeMultiHead(scratch.q, numHeads, position, ropeCache);
  applyRopeMultiHead(scratch.k, numKvHeads, position, ropeCache);

  // ── 3. Write K/V to cache and advance length ──
  kvCache.setKVAt(layerIdx, position, scratch.k, scratch.v);
  // Advancing to position + 1 is idempotent across same-position layers.
  if (kvCache.currentLength() < position + 1) {
    kvCache.setCurrentLength(position + 1);
  }
  const seqLen = kvCache.currentLength();

  // ── 4. Cache slices for this layer (read into pre-allocated scratch) ──
  kvCache.sliceInto(layerIdx, "k", scratch.kCacheSlice);
  kvCache.sliceInto(layerIdx, "v", scratch.vCacheSlice);

  // ── 5. For each query head: compute scores, softmax, weighted V sum ──
  for (let h = 0; h < numHeads; h++) {
    const kvHead = (h / gqaGroup) | 0;
    const qHeadBase = h * headDim;
    const qSlice = scratch.q.subarray(qHeadBase, qHeadBase + headDim);

    // Compute scores against every cached position.
    for (let p = 0; p < seqLen; p++) {
      const kSliceBase = p * kvDim + kvHead * headDim;
      const kSlice = scratch.kCacheSlice.subarray(
        kSliceBase,
        kSliceBase + headDim,
      );
      scratch.scores[p] = dotProductF32(qSlice, kSlice);
    }
    // Scale by 1/√head_dim (only the first seqLen entries are meaningful).
    {
      const scoresPart = scratch.scores.subarray(0, seqLen);
      scaleInPlaceF32(scoresPart, attnScale);
      softmaxInPlace(scoresPart);
    }

    // Weighted sum of V slices into the head's slot of attnOut.
    for (let d = 0; d < headDim; d++) {
      let acc = 0;
      for (let p = 0; p < seqLen; p++) {
        const vSliceBase = p * kvDim + kvHead * headDim;
        acc += scratch.scores[p]! * scratch.vCacheSlice[vSliceBase + d]!;
      }
      scratch.attnOut[qHeadBase + d] = acc;
    }
  }

  // ── 6. Output projection via BitLinear ──
  applyBitLinearFp32Fallback(scratch.attnOut, weights.o_proj, out);
}
