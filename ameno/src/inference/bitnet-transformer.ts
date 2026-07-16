/**
 * @etzhayyim/ameno/inference/bitnet-transformer — one full BitNet
 * transformer block (ADR-2605263800 R1b commit 13).
 *
 * Per-token, per-layer forward step assembling RMSNorm + attention +
 * RMSNorm + FFN + residuals. The canonical "pre-norm" residual block
 * shape used by LLaMA-family models and inherited by BitNet:
 *
 *     residual = hidden
 *     hidden   = RMSNorm(hidden, input_layernorm_w)
 *     hidden   = attention(hidden, ...) — KV cache mutated as side-effect
 *     hidden   = residual + hidden                                   (post-attn residual)
 *
 *     residual = hidden
 *     hidden   = RMSNorm(hidden, post_attention_layernorm_w)
 *     hidden   = FFN(hidden, ...)
 *     hidden   = residual + hidden                                   (post-FFN residual)
 *
 * In-place mutation of the caller's `hidden` buffer. The KV cache is
 * mutated (one layer's K/V appended at the position).
 *
 * ## Scratch sharing
 *
 * - `attnScratch` from `bitnet-attention.ts` (large: includes
 *   maxPos × kvDim K/V cache slices).
 * - `ffnScratch` from `bitnet-ffn.ts` (smaller: 2 × ffn_inner).
 * - `residual: Float32Array[hidden_size]` — held by the block scratch
 *   and reused between the two residual adds.
 * - `subOut: Float32Array[hidden_size]` — intermediate output of
 *   attention or FFN, before the residual add merges it back into
 *   hidden.
 *
 * Total per-block scratch is dominated by the attention slices
 * (maxPos × kvDim × 2 = 4096 × 512 × 4 × 2 = 16 MB at BitNet 2B max).
 * Shared across all 30 layers — allocate once.
 */

import type { BitNetConfig } from "./bitnet-config.ts";
import type { RopeCache } from "./bitnet-rope.ts";
import type { KvCache } from "./bitnet-kv-cache.ts";

import {
  applyAttention,
  allocateAttentionScratch,
  type AttentionLayerWeights,
  type AttentionScratch,
} from "./bitnet-attention.ts";
import {
  applyFfn,
  allocateFfnScratch,
  type FfnLayerWeights,
  type FfnScratch,
} from "./bitnet-ffn.ts";
import { applyRmsNormInPlace } from "./bitnet-rmsnorm.ts";
import { addInPlaceF32 } from "./bitnet-math.ts";

/**
 * Per-layer weights for a full transformer block. Pulls together the
 * attention four-pack + FFN three-pack + two RMSNorm scale vectors
 * stored as `Float32Array`s (HF transformers convention; these are
 * decoded once at model load).
 */
export interface TransformerLayerWeights {
  readonly attn: AttentionLayerWeights;
  readonly ffn: FfnLayerWeights;
  /** `model.layers.<N>.input_layernorm.weight` — fp32 [hidden_size]. */
  readonly input_layernorm_w: Float32Array;
  /** `model.layers.<N>.post_attention_layernorm.weight` — fp32 [hidden_size]. */
  readonly post_attention_layernorm_w: Float32Array;
}

/** Caller-allocated scratch for one transformer-block worth of work. */
export interface TransformerBlockScratch {
  readonly attn: AttentionScratch;
  readonly ffn: FfnScratch;
  /** Saved-residual workspace [hidden_size]. */
  residual: Float32Array;
  /** Sub-output workspace (attn or ffn out) [hidden_size]. */
  subOut: Float32Array;
}

/** Allocate a full transformer-block scratch sized for the config. */
export function allocateTransformerBlockScratch(
  config: BitNetConfig,
): TransformerBlockScratch {
  return {
    attn: allocateAttentionScratch(config),
    ffn: allocateFfnScratch(config),
    residual: new Float32Array(config.hidden_size),
    subOut: new Float32Array(config.hidden_size),
  };
}

/**
 * Run one transformer block forward at `position` for `layerIdx`,
 * mutating `hidden` in place.
 *
 * The block runs:
 *
 *     residual ← hidden
 *     hidden   ← RMSNorm(hidden, input_layernorm_w)
 *     hidden   ← attention(hidden, ...)
 *     hidden   ← residual + hidden
 *
 *     residual ← hidden
 *     hidden   ← RMSNorm(hidden, post_attention_layernorm_w)
 *     hidden   ← FFN(hidden)
 *     hidden   ← residual + hidden
 *
 * Throws on hidden shape mismatch.
 */
export function applyTransformerBlock(
  hidden: Float32Array,
  position: number,
  layerIdx: number,
  weights: TransformerLayerWeights,
  ropeCache: RopeCache,
  kvCache: KvCache,
  config: BitNetConfig,
  scratch: TransformerBlockScratch,
): void {
  const H = config.hidden_size;
  if (hidden.length !== H) {
    throw new Error(
      `applyTransformerBlock: hidden.length=${String(hidden.length)} != hidden_size=${String(H)}`,
    );
  }
  if (weights.input_layernorm_w.length !== H) {
    throw new Error(
      `applyTransformerBlock: input_layernorm_w.length=${String(weights.input_layernorm_w.length)} != hidden_size=${String(H)}`,
    );
  }
  if (weights.post_attention_layernorm_w.length !== H) {
    throw new Error(
      `applyTransformerBlock: post_attention_layernorm_w.length=${String(weights.post_attention_layernorm_w.length)} != hidden_size=${String(H)}`,
    );
  }

  // ── ATTENTION SUBLAYER ──

  // Save residual = hidden (snapshot).
  scratch.residual.set(hidden);

  // RMSNorm input in place.
  applyRmsNormInPlace(hidden, weights.input_layernorm_w, config.rms_norm_eps);

  // Run attention → subOut.
  applyAttention(
    hidden,
    position,
    layerIdx,
    weights.attn,
    ropeCache,
    kvCache,
    config,
    scratch.attn,
    scratch.subOut,
  );

  // hidden = residual + subOut.
  hidden.set(scratch.residual);
  addInPlaceF32(hidden, scratch.subOut);

  // ── FFN SUBLAYER ──

  scratch.residual.set(hidden);
  applyRmsNormInPlace(hidden, weights.post_attention_layernorm_w, config.rms_norm_eps);
  applyFfn(hidden, weights.ffn, config, scratch.ffn, scratch.subOut);
  hidden.set(scratch.residual);
  addInPlaceF32(hidden, scratch.subOut);
}
