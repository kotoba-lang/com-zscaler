/**
 * @etzhayyim/ameno/inference/kernels/dispatch — Backend selection
 * ladder for baien BitLinear inference (ADR-2605263300 §6).
 *
 *   1. WebGPU adapter present + `shader-f16` feature + workgroup-size
 *      ≥ 256 + shader compile succeeds → `webgpu-bitlinear`
 *   2. ELSE WebAssembly.SIMD supported + WASM module loaded →
 *      `wasm-ternary-simd`
 *   3. ELSE WebAssembly supported + WASM module loaded →
 *      `wasm-ternary-scalar`
 *   4. ELSE → `fp16-fallback`
 *
 * Gate G10: `probeBitnetBackend()` MUST NOT throw on any branch.
 * Capability detection is graceful; only actual dispatch can throw.
 */

import type { BitnetWasmModule } from "../bitnet-bridge";

/**
 * Active backend selected by `probeBitnetBackend()`. Returned to
 * callers as an opaque tag — the consumer (`inference.ts`) reads it
 * to pick a dispatch path. `"fp16-fallback"` is the legacy
 * transformers.js + ORT-Web path; the other three are this ADR's
 * additions.
 */
export type BitnetBackend =
  | "webgpu-bitlinear"
  | "wasm-ternary-simd"
  | "wasm-ternary-scalar"
  | "fp16-fallback";

/**
 * Result of probing the runtime environment. Carries everything the
 * caller needs to dispatch without re-probing.
 */
export interface BitnetBackendProbe {
  /** The selected backend (highest tier the runtime can satisfy). */
  readonly backend: BitnetBackend;
  /** GPUAdapter handle if `backend === "webgpu-bitlinear"`. */
  readonly gpuAdapter: GPUAdapter | null;
  /** WASM module if `backend` is one of the `wasm-ternary-*`. */
  readonly wasmModule: BitnetWasmModule | null;
  /**
   * Human-readable reason for the chosen tier — useful for telemetry
   * + the ameno status card. Examples:
   *   "webgpu-bitlinear: shader-f16 supported, workgroup-256 ok"
   *   "wasm-ternary-simd: WebAssembly.SIMD v128 detected"
   *   "fp16-fallback: WebGPU adapter null, WASM module load failed"
   */
  readonly reason: string;
}

/**
 * Detect whether the current runtime supports the WebAssembly SIMD
 * proposal (v128). The probe uses the canonical `WebAssembly.validate`
 * trick — assemble a minimal v128 module and check it validates.
 *
 * Returns `false` (NOT throw) on any error. This function is
 * synchronous; SIMD detection does not require an instantiation.
 *
 * Module bytes encode:
 *   (module (func (result v128) (v128.const i32x4 0 0 0 0)))
 *
 * which is the smallest module that uses a v128 opcode. If the
 * engine rejects v128, `validate()` returns `false`.
 */
export function hasWasmSimd(): boolean {
  if (typeof WebAssembly === "undefined") return false;
  try {
    // prettier-ignore
    const bytes = new Uint8Array([
      0x00, 0x61, 0x73, 0x6d, // magic
      0x01, 0x00, 0x00, 0x00, // version
      // type section: (func (result v128))
      0x01, 0x05, 0x01, 0x60, 0x00, 0x01, 0x7b,
      // function section
      0x03, 0x02, 0x01, 0x00,
      // code section: body = v128.const i32x4 0,0,0,0; end
      0x0a, 0x16, 0x01, 0x14, 0x00,
      0xfd, 0x0c, // v128.const
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x0b, // end
    ]);
    return WebAssembly.validate(bytes);
  } catch {
    return false;
  }
}

/**
 * Probe `navigator.gpu` and attempt to acquire an adapter with the
 * `shader-f16` feature. Returns `null` on any failure (graceful per
 * G10).
 *
 * The `shader-f16` requirement is non-negotiable for
 * `WGSL_BITLINEAR_FORWARD` — the shader enables it at the top of the
 * WGSL source. An adapter that does not advertise `shader-f16`
 * cannot run the kernel and is rejected here.
 */
async function probeWebGpuBitlinear(): Promise<{
  adapter: GPUAdapter | null;
  reason: string;
}> {
  const navAny = (typeof navigator !== "undefined" ? navigator : null) as
    | (Navigator & { gpu?: GPU })
    | null;
  if (!navAny?.gpu) {
    return { adapter: null, reason: "navigator.gpu absent" };
  }
  try {
    const adapter = await navAny.gpu.requestAdapter({
      powerPreference: "high-performance",
    });
    if (!adapter) {
      return { adapter: null, reason: "requestAdapter returned null" };
    }
    if (!adapter.features.has("shader-f16")) {
      return {
        adapter: null,
        reason: "adapter does not advertise 'shader-f16' feature",
      };
    }
    if (adapter.limits.maxComputeWorkgroupSizeX < 256) {
      return {
        adapter: null,
        reason: `maxComputeWorkgroupSizeX = ${String(adapter.limits.maxComputeWorkgroupSizeX)} < 256`,
      };
    }
    return {
      adapter,
      reason: "shader-f16 supported, workgroup-256 ok",
    };
  } catch (e) {
    return {
      adapter: null,
      reason: e instanceof Error ? `requestAdapter threw: ${e.message}` : "requestAdapter threw",
    };
  }
}

/**
 * Run the full backend-selection ladder.
 *
 * Caller pattern:
 *
 *   const probe = await probeBitnetBackend({ wasmModuleUrl: ... });
 *   if (probe.backend === "fp16-fallback") {
 *     // run legacy transformers.js path
 *   } else {
 *     // R1b: dispatch via probe.gpuAdapter or probe.wasmModule
 *   }
 *
 * R0: ALL four tiers can be returned. The `webgpu-bitlinear` and
 * `wasm-ternary-*` tiers do not yet dispatch (dispatchers in
 * `./bitlinear-forward.ts` and `../bitnet-bridge.ts` throw R1
 * markers). Callers MUST gate dispatch on R1 readiness — typically by
 * checking the ADR version returned by a future
 * `getKernelReadiness()` helper.
 */
export interface ProbeOptions {
  /**
   * URL of the `baien-wasm-ternary.wasm` module. R0 loaders accept a
   * `null` value (returns the probe without attempting WASM load) so
   * the function can be exercised before the static asset is published.
   */
  readonly wasmModuleUrl: string | null;
}

export async function probeBitnetBackend(
  opts: ProbeOptions,
): Promise<BitnetBackendProbe> {
  // 1. WebGPU + shader-f16
  const gpu = await probeWebGpuBitlinear();
  if (gpu.adapter) {
    return {
      backend: "webgpu-bitlinear",
      gpuAdapter: gpu.adapter,
      wasmModule: null,
      reason: `webgpu-bitlinear: ${gpu.reason}`,
    };
  }

  // 2 + 3. WASM
  if (opts.wasmModuleUrl !== null) {
    try {
      // Lazy-import to avoid pulling the bridge into the module graph
      // when the caller has no intention of using WASM (e.g. a unit
      // test that only wants the type).
      const { loadBitnetWasm } = await import("../bitnet-bridge");
      const wasmModule = await loadBitnetWasm(opts.wasmModuleUrl);
      if (hasWasmSimd()) {
        return {
          backend: "wasm-ternary-simd",
          gpuAdapter: null,
          wasmModule,
          reason: `wasm-ternary-simd: WebAssembly.SIMD v128 detected; webgpu fail = ${gpu.reason}`,
        };
      }
      return {
        backend: "wasm-ternary-scalar",
        gpuAdapter: null,
        wasmModule,
        reason: `wasm-ternary-scalar: WebAssembly.SIMD not available; webgpu fail = ${gpu.reason}`,
      };
    } catch (e) {
      return {
        backend: "fp16-fallback",
        gpuAdapter: null,
        wasmModule: null,
        reason: `fp16-fallback: webgpu fail = ${gpu.reason}; wasm load fail = ${e instanceof Error ? e.message : String(e)}`,
      };
    }
  }

  // 4. fp16 fallback (existing transformers.js path)
  return {
    backend: "fp16-fallback",
    gpuAdapter: null,
    wasmModule: null,
    reason: `fp16-fallback: webgpu fail = ${gpu.reason}; wasmModuleUrl=null`,
  };
}
