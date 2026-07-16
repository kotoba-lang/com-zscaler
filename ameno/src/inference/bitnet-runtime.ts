/**
 * @etzhayyim/ameno/inference/bitnet-runtime — BitNet decode loop +
 * model state (ADR-2605263800 R1b commit 14).
 *
 * Wraps the per-layer assembly (RMSNorm + attention + FFN + residuals)
 * into a runtime object that owns:
 *
 *   - The decoded model weights (embedding table, per-layer
 *     attention + FFN BitLinear packs + RMSNorm scales, final
 *     norm scale, lm_head pack or tied-embedding flag).
 *   - The KV cache (one per-position decode-step's worth of K/V).
 *   - The RoPE precompute.
 *   - All the scratch buffers (allocated once at construction).
 *   - The current decode position.
 *
 * The public surface is small:
 *
 *   - `decode(tokenId)` — embed + run all layers + final norm + lm_head
 *     + greedy argmax → returns the next predicted token id.
 *   - `decodeMany(tokenIds)` — prefill convenience (loops `decode`).
 *   - `reset()` — clear KV cache + reset position to 0 (start a new
 *     sequence).
 *
 * The decode loop is the **end-to-end forward path** for baien edge
 * inference. R1b commit 15 wires this into `inference.ts` + microbench.
 *
 * ## Tied embeddings
 *
 * If `config.tie_word_embeddings === true`, the lm_head projection
 * shares its weight with the input embedding table: `logits = hidden
 * × embedTokens^T`. BitNet 2B sets this to `false`, so a separate
 * `lm_head` BitLinear pack is the common case. We support both.
 *
 * ## fp16 vs fp32
 *
 * Internally we accumulate everything in fp32. The R0 numerical
 * contract (G4) is ±1 ULP fp16 against the scalar Rust reference,
 * which means fp32 accumulators that round to fp16 at the final
 * store are the floor.
 *
 * The final `logits` and the embedding table are stored as fp32 in
 * RAM (1 GB for BitNet 2B at 128 256 × 2048). Loaded once at model
 * construction. Tied-embedding callers save the 1 GB; we pay it
 * because BitNet 2B is `tie_word_embeddings: false`.
 */

import type { BitNetConfig } from "./bitnet-config.ts";
import type { BitLinearWeightPack } from "./bitnet-weight-pack.ts";

import { precomputeRope, type RopeCache } from "./bitnet-rope.ts";
import { KvCache } from "./bitnet-kv-cache.ts";
import {
  applyTransformerBlock,
  allocateTransformerBlockScratch,
  type TransformerLayerWeights,
  type TransformerBlockScratch,
} from "./bitnet-transformer.ts";
import { applyRmsNormInPlace } from "./bitnet-rmsnorm.ts";
import { applyBitLinearFp32Fallback } from "./bitnet-bitlinear-dispatch.ts";
import { argmaxF32, dotProductF32 } from "./bitnet-math.ts";

/** Full per-model weights ready to consume. */
export interface BitNetModelWeights {
  /** Embedding table, row-major `[vocab_size × hidden_size]`. */
  readonly embedTokens: Float32Array;
  /** One block of per-layer weights per transformer layer. */
  readonly layers: readonly TransformerLayerWeights[];
  /** Final RMSNorm scale before the lm_head, `[hidden_size]`. */
  readonly finalNorm: Float32Array;
  /**
   * lm_head projection. Either a separate BitLinear pack (the BitNet
   * 2B case) OR the literal string `"tied"` to indicate the lm_head
   * shares weight with `embedTokens` (the LLaMA-1 + small-model case;
   * cosine-similarity decode against the embedding rows).
   */
  readonly lmHead: BitLinearWeightPack | "tied";
}

/**
 * BitNet decode-loop runtime. Stateful: owns the KV cache + position
 * counter. Single-sequence-at-a-time (no batching at R1b).
 */
export class BitNetRuntime {
  public readonly config: BitNetConfig;
  public readonly weights: BitNetModelWeights;
  private readonly ropeCache: RopeCache;
  private readonly kvCache: KvCache;
  private readonly blockScratch: TransformerBlockScratch;
  private readonly hidden: Float32Array;
  private readonly logits: Float32Array;
  private position: number;

  constructor(config: BitNetConfig, weights: BitNetModelWeights) {
    this.config = config;
    this.weights = weights;
    this.ropeCache = precomputeRope(config);
    this.kvCache = new KvCache(config);
    this.blockScratch = allocateTransformerBlockScratch(config);
    this.hidden = new Float32Array(config.hidden_size);
    this.logits = new Float32Array(config.vocab_size);
    this.position = 0;

    // Shape sanity-check the weights at construction so we catch
    // mis-wired packs before the first decode step.
    if (weights.embedTokens.length !== config.vocab_size * config.hidden_size) {
      throw new Error(
        `BitNetRuntime: embedTokens.length=${String(weights.embedTokens.length)} != vocab × hidden = ${String(config.vocab_size * config.hidden_size)}`,
      );
    }
    if (weights.layers.length !== config.num_hidden_layers) {
      throw new Error(
        `BitNetRuntime: layers.length=${String(weights.layers.length)} != num_hidden_layers=${String(config.num_hidden_layers)}`,
      );
    }
    if (weights.finalNorm.length !== config.hidden_size) {
      throw new Error(
        `BitNetRuntime: finalNorm.length=${String(weights.finalNorm.length)} != hidden_size=${String(config.hidden_size)}`,
      );
    }
    if (weights.lmHead !== "tied") {
      const [lmRows, lmCols] = weights.lmHead.dims;
      if (lmRows !== config.vocab_size || lmCols !== config.hidden_size) {
        throw new Error(
          `BitNetRuntime: lm_head dims [${String(lmRows)},${String(lmCols)}] != [vocab=${String(config.vocab_size)}, hidden=${String(config.hidden_size)}]`,
        );
      }
    }
  }

  /** Current decode position. 0 before any decode() call. */
  getPosition(): number {
    return this.position;
  }

  /** Reset KV cache + position to start a fresh sequence. */
  reset(): void {
    this.kvCache.clear();
    this.position = 0;
  }

  /**
   * Greedy decode one token: embed input `tokenId`, run all layers,
   * apply final RMSNorm, project to vocab via lm_head, argmax. Advance
   * the position counter. Return the predicted next token id.
   */
  decode(tokenId: number): number {
    if (tokenId < 0 || tokenId >= this.config.vocab_size) {
      throw new Error(
        `BitNetRuntime.decode: tokenId=${String(tokenId)} out of [0, ${String(this.config.vocab_size)})`,
      );
    }
    if (this.position >= this.config.max_position_embeddings) {
      throw new Error(
        `BitNetRuntime.decode: position=${String(this.position)} >= max_position_embeddings=${String(this.config.max_position_embeddings)} (sequence too long)`,
      );
    }

    // ── 1. Embedding lookup ──
    const H = this.config.hidden_size;
    const embedBase = tokenId * H;
    for (let i = 0; i < H; i++) {
      this.hidden[i] = this.weights.embedTokens[embedBase + i]!;
    }

    // ── 2. Per-layer transformer blocks ──
    for (let layerIdx = 0; layerIdx < this.config.num_hidden_layers; layerIdx++) {
      applyTransformerBlock(
        this.hidden,
        this.position,
        layerIdx,
        this.weights.layers[layerIdx]!,
        this.ropeCache,
        this.kvCache,
        this.config,
        this.blockScratch,
      );
    }

    // ── 3. Final RMSNorm ──
    applyRmsNormInPlace(this.hidden, this.weights.finalNorm, this.config.rms_norm_eps);

    // ── 4. lm_head: logits = hidden · W_lm^T ──
    if (this.weights.lmHead === "tied") {
      // Tied: logits[v] = hidden · embedTokens[v, :].
      const V = this.config.vocab_size;
      for (let v = 0; v < V; v++) {
        const row = this.weights.embedTokens.subarray(v * H, (v + 1) * H);
        this.logits[v] = dotProductF32(this.hidden, row);
      }
    } else {
      applyBitLinearFp32Fallback(this.hidden, this.weights.lmHead, this.logits);
    }

    // ── 5. Argmax greedy decode ──
    const nextToken = argmaxF32(this.logits);

    // ── 6. Advance position ──
    this.position++;

    return nextToken;
  }

  /**
   * Prefill convenience: decode each token in `tokenIds` sequentially.
   * Returns the array of next-token predictions for each input
   * position (length === tokenIds.length).
   */
  decodeMany(tokenIds: readonly number[]): number[] {
    const out: number[] = [];
    for (const id of tokenIds) {
      out.push(this.decode(id));
    }
    return out;
  }

  /** Read-only access to the KV cache shape (for telemetry / G1 close). */
  getKvCacheShape() {
    return this.kvCache.shape;
  }
}
