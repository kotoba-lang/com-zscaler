/**
 * @etzhayyim/ameno/inference/bitnet-bridge — WASM module loader +
 * typed wrapper for the `baien-wasm-ternary` Rust crate
 * (ADR-2605263300 §1 L2b).
 *
 * The Rust crate at `40-engine/baien-wasm-ternary/` mirrors the
 * bitnet.cpp public API surface at the **kernel-task level** (NOT the
 * llama-wrapper level). The bridge exposes that surface to ameno
 * TypeScript callers as `class BitnetWasmModule`.
 *
 * R0 (this commit) ships:
 *   - `loadBitnetWasm(url)` — module load + capability probe (real).
 *   - `class BitnetWasmModule` — typed handle.
 *   - `BitnetTensorType` enum (mirrors `GGML_TYPE_I2_S` etc.).
 *   - dispatch methods (`ggmlBitnetMulMatTaskCompute`, etc.) all
 *     throw R1c markers.
 *
 * R1c lands the actual dispatch by wiring the wasm-bindgen exports.
 *
 * Gate references:
 *   - G2 (API mirror = kernel-task level) — method names and
 *     signatures match `40-engine/baien-wasm-ternary/src/api.rs`.
 *   - G7 (clean-room, not vendored) — this file calls a Rust crate
 *     under `40-engine/`, not a vendored upstream blob.
 *   - G9 (no silent fp16 fallback) — all dispatch methods throw on
 *     R0; the caller decides whether to retry with fp16 (legacy
 *     transformers.js path).
 *   - G11 (single-instance per tab) — `loadBitnetWasm` memoises by
 *     URL; the second call returns the cached module.
 */

/**
 * Tensor type tags. Values match `ggml-bitnet.h` byte-for-byte
 * (gate G2). Only the i2_s + q8_0 + f16 + f32 entries are populated
 * — these are the only types the BitLinear kernel touches.
 */
export const BitnetTensorType = {
  /** 32-bit float. Tag matches ggml. */
  F32: 0,
  /** 16-bit float. Tag matches ggml. */
  F16: 1,
  /** 8-bit signed quantized (block size 32). Tag matches ggml. */
  Q8_0: 8,
  /**
   * 2-bit signed ternary (BitNet 1.58). Tag matches
   * `microsoft/BitNet:src/ggml-bitnet.h:GGML_TYPE_I2_S = 40`.
   */
  I2_S: 40,
} as const;

export type BitnetTensorTypeValue =
  (typeof BitnetTensorType)[keyof typeof BitnetTensorType];

/**
 * Result of probing the loaded WASM module's capabilities. Returned
 * by `BitnetWasmModule.capability()`.
 */
export interface BitnetWasmCapability {
  /**
   * Module-reported semantic version (matches `Cargo.toml`).
   * Useful for ADR-version gating in callers.
   */
  readonly version: string;
  /**
   * Whether the module was built with SIMD intrinsics. R0 always
   * returns `false` (scalar reference only); R1c switches this on.
   */
  readonly hasSimd: boolean;
  /** Whether the module was built with the experimental LUT path. */
  readonly hasLut: boolean;
}

/**
 * wasm-bindgen exports shape we expect. Kept as a structural type so
 * the bridge does not depend on the generated `.d.ts` of the crate
 * (which exists only after `wasm-pack build` runs). R1c will replace
 * this with the real generated type.
 */
interface BitnetWasmExports {
  /** ggml_bitnet_init — returns 0 on success. */
  ggml_bitnet_init(): number;
  /** ggml_bitnet_can_mul_mat — returns 1 if (i2_s × q8_0 → f16). */
  ggml_bitnet_can_mul_mat(
    src0_ty: number,
    src1_ty: number,
    dst_ty: number,
  ): number;
  /** Module version string accessor. */
  version(): string;
  /** Reports whether the SIMD inner loop is compiled in. */
  has_simd(): number;
  /** Reports whether the LUT-expanded path is compiled in. */
  has_lut(): number;
  /** Memory accessor exported by wasm-bindgen. */
  readonly memory: WebAssembly.Memory;
}

/**
 * Loaded WASM module + typed bitnet.cpp API surface.
 *
 * Construction:
 *   const mod = await loadBitnetWasm("/static/baien-wasm-ternary.wasm");
 *
 * Disposal: WebAssembly.Memory is GC'd with the module; `dispose()`
 * is a no-op today but reserved so callers can write disposal logic
 * that survives R1c's stateful LUT cache.
 */
export class BitnetWasmModule {
  private readonly exports: BitnetWasmExports;

  /** @internal use `loadBitnetWasm()` */
  constructor(exports: BitnetWasmExports) {
    this.exports = exports;
  }

  /** Returns the module's capability report (version + feature bits). */
  capability(): BitnetWasmCapability {
    return {
      version: this.exports.version(),
      hasSimd: this.exports.has_simd() !== 0,
      hasLut: this.exports.has_lut() !== 0,
    };
  }

  /**
   * `ggml_bitnet_init` — populates the LUT precompute table. Idempotent.
   * Returns 0 on success.
   *
   * R0: the underlying Rust impl is a stub that sets a flag and
   * returns 0 (the real LUT comes in R1c). Calling this is safe but
   * has no functional effect yet.
   */
  ggmlBitnetInit(): number {
    return this.exports.ggml_bitnet_init();
  }

  /**
   * `ggml_bitnet_can_mul_mat` — returns true iff the (src0_ty, src1_ty,
   * dst_ty) triple is supported by this build. For BitNet 1.58 the
   * canonical triple is (I2_S, Q8_0, F16).
   *
   * R0 supports the canonical triple. Unknown triples return false.
   */
  ggmlBitnetCanMulMat(
    src0_ty: BitnetTensorTypeValue,
    src1_ty: BitnetTensorTypeValue,
    dst_ty: BitnetTensorTypeValue,
  ): boolean {
    return (
      this.exports.ggml_bitnet_can_mul_mat(src0_ty, src1_ty, dst_ty) !== 0
    );
  }

  /**
   * `ggml_bitnet_mul_mat_task_compute` — the kernel-level matmul.
   *
   * R0: **throws R1c**. The Rust scalar reference matmul exists, but
   * the wasm-bindgen export wiring (pointer marshalling for
   * `*const u8`, `*const f16`, etc.) is non-trivial and lands in R1c
   * alongside the SIMD upgrade.
   *
   * R1c will accept typed-array views into `this.exports.memory` and
   * call the underlying Rust function directly.
   */
  ggmlBitnetMulMatTaskCompute(
    ..._args: unknown[]
  ): never {
    throw new Error(
      "ggmlBitnetMulMatTaskCompute: R1c — requires wasm-bindgen pointer marshalling for i2_s weight blocks + q8 activation LUT. Rust scalar reference is wired (see 40-engine/baien-wasm-ternary/src/matmul.rs); only the JS↔WASM boundary glue is missing.",
    );
  }

  /** No-op disposal (R0 reserved hook for R1c LUT-cache cleanup). */
  dispose(): void {
    /* no-op (R0) */
  }
}

/**
 * Memoised module load. Subsequent calls with the same `url` return
 * the same `BitnetWasmModule` instance (gate G11: single-instance
 * per tab).
 *
 * Errors:
 *   - Throws if `fetch(url)` fails.
 *   - Throws if `WebAssembly.instantiateStreaming` throws.
 *   - Throws if the resulting instance does NOT advertise the
 *     expected exports (sanity check that we loaded the right .wasm).
 *
 * Note: this loader does NOT silently fall back to fp16 — the caller
 * (e.g. `probeBitnetBackend`) handles fallback policy.
 */
const moduleCache = new Map<string, Promise<BitnetWasmModule>>();

export function loadBitnetWasm(url: string): Promise<BitnetWasmModule> {
  const cached = moduleCache.get(url);
  if (cached !== undefined) return cached;

  const loaded = (async (): Promise<BitnetWasmModule> => {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(
        `loadBitnetWasm: fetch failed ${String(response.status)} ${response.statusText} for ${url}`,
      );
    }
    // We do NOT use `instantiateStreaming` directly because some
    // CDNs serve .wasm with the wrong MIME type and the streaming
    // path errors out. Fall back to ArrayBuffer.
    const bytes = await response.arrayBuffer();
    const result = await WebAssembly.instantiate(bytes, {});

    const exp = result.instance.exports as unknown as Partial<BitnetWasmExports>;
    const missing: string[] = [];
    for (const key of [
      "ggml_bitnet_init",
      "ggml_bitnet_can_mul_mat",
      "version",
      "has_simd",
      "has_lut",
      "memory",
    ] as const) {
      if (!(key in exp)) missing.push(key);
    }
    if (missing.length > 0) {
      throw new Error(
        `loadBitnetWasm: module at ${url} is missing exports: ${missing.join(", ")}. Likely the wrong .wasm artifact.`,
      );
    }

    return new BitnetWasmModule(exp as BitnetWasmExports);
  })();

  moduleCache.set(url, loaded);
  // Evict from cache on failure so a retry can re-attempt.
  loaded.catch(() => {
    moduleCache.delete(url);
  });
  return loaded;
}
