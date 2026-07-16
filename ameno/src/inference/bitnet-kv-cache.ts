/**
 * @etzhayyim/ameno/inference/bitnet-kv-cache — fp16 KV cache for the
 * BitNet forward-override runtime (ADR-2605263800 R1b commit 7).
 *
 * Per-layer, per-(K/V), per-position, per-(kv_head × head_dim) storage
 * for the attention key + value tensors accumulated across decode
 * steps. q8-quantized KV cache (smaller envelope) is R4; R1b ships
 * fp16 to keep the decode path numerically simple.
 *
 * ## Memory layout
 *
 * The buffer is a single `Uint16Array` (uint16 holds the fp16 bit
 * pattern). Indexing:
 *
 *   buf[layer × (2 × max_pos × kv_dim)
 *     + kOrV × (max_pos × kv_dim)
 *     + position × kv_dim
 *     + kvHead × head_dim
 *     + dim]
 *
 *   where kv_dim = num_kv_heads × head_dim.
 *
 * Per-position writes are O(kv_dim) sequential = cache-friendly.
 * Per-layer slices over positions [0, currentLength) are
 * O(positions × kv_dim) sequential = also cache-friendly.
 *
 * ## Budget (BitNet 2B at 4k context)
 *
 *   30 layers × 2 (K+V) × 4096 positions × (8 × 64) kv_dim × 2 bytes
 *   = 30 × 2 × 4096 × 512 × 2
 *   = 240 MB
 *
 * Fits under ADR-2605241900 §G1 envelope (≤2 GB @ 4k ctx). At 16 k
 * context the cache grows to ~960 MB — still under the §G1 16 k
 * ceiling (2.5 GB) but at that point the q8 KV-cache R4 work
 * becomes desirable.
 *
 * ## fp16 conversions
 *
 * The cache stores the fp16 BIT PATTERN as `uint16`. Conversion
 * between f32 and the fp16 bits is handled by `f32ToF16Bits` /
 * `f16BitsToF32` — mirrors the same algorithm used in
 * `bitnet-weight-transformer`.
 */

import type { BitNetConfig } from "./bitnet-config.ts";

/** Tag — distinguish K from V at the storage layer. */
export type KvKind = "k" | "v";

const K_INDEX = 0;
const V_INDEX = 1;

function kvKindIndex(kind: KvKind): 0 | 1 {
  return kind === "k" ? K_INDEX : V_INDEX;
}

/**
 * Per-layer per-kind shape descriptors. Returned by `KvCache.shape`
 * for verification + telemetry.
 */
export interface KvCacheShape {
  readonly numLayers: number;
  readonly maxPos: number;
  readonly numKvHeads: number;
  readonly headDim: number;
  /** num_kv_heads × head_dim. */
  readonly kvDim: number;
  /** Total bytes allocated. */
  readonly totalBytes: number;
}

/**
 * fp16 KV cache. Single contiguous Uint16Array; positions appended
 * one at a time as the decode loop advances.
 */
export class KvCache {
  public readonly shape: KvCacheShape;
  private readonly buf: Uint16Array;
  private length: number;

  constructor(config: BitNetConfig) {
    const numLayers = config.num_hidden_layers;
    const maxPos = config.max_position_embeddings;
    const numKvHeads = config.num_key_value_heads;
    const headDim = config.head_dim;
    const kvDim = numKvHeads * headDim;
    const elements = numLayers * 2 * maxPos * kvDim;
    const totalBytes = elements * 2; // uint16
    this.shape = Object.freeze({
      numLayers,
      maxPos,
      numKvHeads,
      headDim,
      kvDim,
      totalBytes,
    });
    this.buf = new Uint16Array(elements);
    this.length = 0;
  }

  /** Current sequence length (number of positions written so far). */
  currentLength(): number {
    return this.length;
  }

  /** Reset to empty without releasing memory. */
  clear(): void {
    this.buf.fill(0);
    this.length = 0;
  }

  /**
   * Write the K + V vectors for the current position at every layer.
   *
   * `k` and `v` MUST each be `numLayers × kvDim` fp32 values (row-major
   * layer-then-kv_dim). They are converted to fp16 bits on write.
   *
   * Increments `currentLength()` by 1. Throws if the cache is full.
   *
   * For the prefill case (writing N positions at once), the caller
   * loops `appendPosition` N times.
   */
  appendPosition(k: Float32Array, v: Float32Array): void {
    if (this.length >= this.shape.maxPos) {
      throw new Error(
        `KvCache.appendPosition: cache full (currentLength=${String(this.length)} = max_pos=${String(this.shape.maxPos)})`,
      );
    }
    const expectedLen = this.shape.numLayers * this.shape.kvDim;
    if (k.length !== expectedLen || v.length !== expectedLen) {
      throw new Error(
        `KvCache.appendPosition: expected k.length=v.length=${String(expectedLen)} (numLayers × kvDim), got k=${String(k.length)} v=${String(v.length)}`,
      );
    }

    const pos = this.length;
    const { numLayers, kvDim, maxPos } = this.shape;
    const perLayer = 2 * maxPos * kvDim;

    for (let layer = 0; layer < numLayers; layer++) {
      const layerBase = layer * perLayer;
      const kBase = layerBase + K_INDEX * maxPos * kvDim + pos * kvDim;
      const vBase = layerBase + V_INDEX * maxPos * kvDim + pos * kvDim;
      const kSrc = layer * kvDim;
      for (let i = 0; i < kvDim; i++) {
        this.buf[kBase + i] = f32ToF16Bits(k[kSrc + i]!);
        this.buf[vBase + i] = f32ToF16Bits(v[kSrc + i]!);
      }
    }
    this.length++;
  }

  /**
   * Write one layer's K + V at a specific (layer, position) WITHOUT
   * touching `currentLength`. Used by the layer-by-layer forward path,
   * which computes K/V one layer at a time. Caller is responsible for
   * calling `setCurrentLength(p+1)` once all 30 layers have written
   * to position `p`.
   *
   * `k` and `v` MUST each be exactly `kvDim` fp32 values (one layer
   * worth). Throws on size or index mismatch.
   */
  setKVAt(layer: number, position: number, k: Float32Array, v: Float32Array): void {
    const { numLayers, kvDim, maxPos } = this.shape;
    if (layer < 0 || layer >= numLayers) {
      throw new Error(
        `KvCache.setKVAt: layer=${String(layer)} out of [0, ${String(numLayers)})`,
      );
    }
    if (position < 0 || position >= maxPos) {
      throw new Error(
        `KvCache.setKVAt: position=${String(position)} out of [0, ${String(maxPos)})`,
      );
    }
    if (k.length !== kvDim || v.length !== kvDim) {
      throw new Error(
        `KvCache.setKVAt: expected k.length=v.length=${String(kvDim)}, got k=${String(k.length)} v=${String(v.length)}`,
      );
    }
    const perLayer = 2 * maxPos * kvDim;
    const kBase = layer * perLayer + K_INDEX * maxPos * kvDim + position * kvDim;
    const vBase = layer * perLayer + V_INDEX * maxPos * kvDim + position * kvDim;
    for (let i = 0; i < kvDim; i++) {
      this.buf[kBase + i] = f32ToF16Bits(k[i]!);
      this.buf[vBase + i] = f32ToF16Bits(v[i]!);
    }
  }

  /**
   * Set the sequence length explicitly. Used by the layer-by-layer
   * forward path after all `setKVAt(layer, p, ...)` calls for the
   * new position `p` have completed. `n` MUST be in `[0, maxPos]`.
   */
  setCurrentLength(n: number): void {
    if (n < 0 || n > this.shape.maxPos) {
      throw new Error(
        `KvCache.setCurrentLength: n=${String(n)} out of [0, ${String(this.shape.maxPos)}]`,
      );
    }
    this.length = n;
  }

  /**
   * Return a fp32 view of `[layer][kind][0..currentLength × kvDim]`.
   *
   * Allocates a fresh Float32Array on each call. For the hot attention
   * path, callers should reuse a pre-allocated scratch buffer via
   * `sliceInto(...)` instead.
   */
  slice(layer: number, kind: KvKind): Float32Array {
    const out = new Float32Array(this.length * this.shape.kvDim);
    this.sliceInto(layer, kind, out);
    return out;
  }

  /**
   * Copy `[layer][kind][0..currentLength × kvDim]` into the provided
   * fp32 buffer, converting each uint16 → fp32. The buffer must be
   * at least `currentLength × kvDim` long.
   */
  sliceInto(layer: number, kind: KvKind, out: Float32Array): void {
    const { numLayers, kvDim, maxPos } = this.shape;
    if (layer < 0 || layer >= numLayers) {
      throw new Error(
        `KvCache.sliceInto: layer=${String(layer)} out of [0, ${String(numLayers)})`,
      );
    }
    const needed = this.length * kvDim;
    if (out.length < needed) {
      throw new Error(
        `KvCache.sliceInto: out.length=${String(out.length)} < needed=${String(needed)}`,
      );
    }
    const perLayer = 2 * maxPos * kvDim;
    const base = layer * perLayer + kvKindIndex(kind) * maxPos * kvDim;
    for (let i = 0; i < needed; i++) {
      out[i] = f16BitsToF32(this.buf[base + i]!);
    }
  }

  /**
   * Read one position's K (or V) for one layer + one KV head as fp32.
   *
   * Used by the attention dispatch for the most recent step. Allocates;
   * not for hot paths.
   */
  readHeadPosition(
    layer: number,
    kind: KvKind,
    kvHead: number,
    position: number,
  ): Float32Array {
    const { numLayers, kvDim, headDim, numKvHeads, maxPos } = this.shape;
    if (layer < 0 || layer >= numLayers) {
      throw new Error(`KvCache.readHeadPosition: layer out of range`);
    }
    if (kvHead < 0 || kvHead >= numKvHeads) {
      throw new Error(`KvCache.readHeadPosition: kvHead out of range`);
    }
    if (position < 0 || position >= this.length) {
      throw new Error(
        `KvCache.readHeadPosition: position=${String(position)} out of [0, currentLength=${String(this.length)})`,
      );
    }
    const perLayer = 2 * maxPos * kvDim;
    const base =
      layer * perLayer +
      kvKindIndex(kind) * maxPos * kvDim +
      position * kvDim +
      kvHead * headDim;
    const out = new Float32Array(headDim);
    for (let i = 0; i < headDim; i++) {
      out[i] = f16BitsToF32(this.buf[base + i]!);
    }
    return out;
  }
}

// ──────────────────────────────────────────────────────────────
// f32 ↔ f16 bit conversions
// ──────────────────────────────────────────────────────────────

/** Mirrors `bitnet-weight-transformer:f32ToF16Bits`. */
function f32ToF16Bits(f: number): number {
  const buf = new ArrayBuffer(4);
  new Float32Array(buf)[0] = f;
  const bits = new Uint32Array(buf)[0]!;

  const sign = (bits >>> 16) & 0x8000;
  let exp = (bits >>> 23) & 0xff;
  let mant = bits & 0x7fffff;

  if (exp === 0xff) {
    return sign | 0x7c00 | (mant !== 0 ? 1 : 0);
  }
  if (exp === 0) {
    return sign;
  }
  const newExp = exp - 127 + 15;
  if (newExp >= 0x1f) {
    return sign | 0x7c00;
  }
  if (newExp <= 0) {
    if (newExp < -10) return sign;
    mant = (mant | 0x800000) >>> (1 - newExp);
    if ((mant & 0x1000) !== 0) {
      mant += 0x2000;
    }
    return sign | (mant >>> 13);
  }
  if ((mant & 0x1000) !== 0) {
    mant += 0x2000;
    if ((mant & 0x800000) !== 0) {
      mant = 0;
      exp = newExp + 1;
    } else {
      exp = newExp;
    }
  } else {
    exp = newExp;
  }
  return sign | (exp << 10) | (mant >>> 13);
}

/** Convert fp16 bit pattern (uint16) back to fp32. */
function f16BitsToF32(bits: number): number {
  const sign = (bits & 0x8000) << 16;
  const expRaw = (bits >>> 10) & 0x1f;
  const mantRaw = bits & 0x3ff;
  let bits32: number;
  if (expRaw === 0) {
    if (mantRaw === 0) {
      bits32 = sign;
    } else {
      // Subnormal fp16 → normalized fp32.
      let exp = -14;
      let mant = mantRaw;
      while ((mant & 0x400) === 0) {
        mant <<= 1;
        exp--;
      }
      mant &= 0x3ff;
      bits32 = sign | ((exp + 127) << 23) | (mant << 13);
    }
  } else if (expRaw === 0x1f) {
    bits32 = sign | 0x7f800000 | (mantRaw << 13);
  } else {
    bits32 = sign | ((expRaw - 15 + 127) << 23) | (mantRaw << 13);
  }
  const buf = new ArrayBuffer(4);
  new Uint32Array(buf)[0] = bits32;
  return new Float32Array(buf)[0]!;
}
