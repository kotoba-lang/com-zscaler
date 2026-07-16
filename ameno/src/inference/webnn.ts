/**
 * @etzhayyim/ameno/inference/webnn — WebNN feature-detection + backend
 * routing layer (ADR-2605252100).
 *
 * WebNN is the W3C Candidate Recommendation Draft (snapshot 2026-05-21)
 * neural-network INFERENCE API. The browser maps WebNN graph calls to
 * the OS-native NPU stack:
 *
 *   - Windows  → DirectML on NPU device (Copilot+ PC) or dGPU
 *   - macOS/iOS → CoreML on Apple Neural Engine
 *   - Android  → NNAPI → QNN HTP delegate (Snapdragon) or TFLite/GPU
 *   - ChromeOS/Linux → TFLite/XNNPACK (CPU) or Edge TPU NNAPI
 *
 * ameno only sees the abstraction: it requests a `deviceType` of
 * `npu` / `gpu` / `cpu`, and the browser picks the native stack. The
 * mapping is opaque to ameno (W4 in the ADR).
 *
 * Hard rules from ADR-2605252100:
 *   W1 — INFERENCE ONLY. The WebNN spec has no backward / gradient /
 *        optimizer ops. Training stays on WebGPU (ADR-2605242630).
 *        `dispatchWebnnInference` is the only dispatch wrapper this
 *        module ever exposes; there is no `dispatchWebnnBackward`.
 *   W2 — Every call site MUST feature-detect via
 *        `detectWebnnSupport()` before dispatching.
 *   W3 — WebGPU is the universal fallback. Safari/WebKit has no WebNN
 *        signal as of 2026-05-25; iOS stays on WebGPU until Apple
 *        ships (R2 ADR).
 *   W4 — The native stack is browser-selected, not app-selected. We
 *        pass `deviceType`; we do NOT pick CoreML vs DirectML vs
 *        NNAPI explicitly.
 *
 * R0 (this commit) ships everything EXCEPT the actual ORT WebNN EP
 * wiring. `dispatchWebnnInference` throws with an R1 marker, matching
 * the pattern of `train/kernels.ts` `dispatchLoraForward`.
 */

/**
 * Inference backend identifier returned by `selectInferenceBackend`.
 * Consumed by `loadModel` / `generate` in `../inference.ts`.
 */
export type InferenceBackend =
  | "webnn-npu"
  | "webnn-gpu"
  | "webgpu"
  | "wasm";

/**
 * WebNN `deviceType` enum as defined in the W3C spec. The spec also
 * lists `'default'` but ameno never requests it — we always pick
 * explicitly so the selection ladder is observable.
 */
export type WebnnDeviceType = "npu" | "gpu" | "cpu";

/**
 * Result of probing `navigator.ml` and attempting to acquire an
 * `MLContext` for each deviceType. The probe is non-throwing — if
 * WebNN is absent or a context cannot be acquired, the corresponding
 * `has*Context` field is `false` and `acquireError` carries the
 * reason for telemetry.
 *
 * `deviceClass` is the same UA classification used by
 * `train/device.ts` — kept here so a single `WebnnSupport` value
 * carries everything a caller needs to pick a backend without
 * re-probing the UA.
 */
export interface WebnnSupport {
  readonly hasNavigatorML: boolean;
  readonly hasNpuContext: boolean;
  readonly hasGpuContext: boolean;
  readonly hasCpuContext: boolean;
  readonly deviceClass: "ios" | "android" | "wasm-desktop";
  /**
   * The most recent error from a failed `createContext` attempt, if
   * any. `null` when all three probes succeeded or when WebNN is
   * simply absent. For telemetry / run-log only — selection logic
   * does not consult this field.
   */
  readonly acquireError: string | null;
}

/**
 * Minimal `navigator.ml` shape. The full WebNN spec exposes
 * `MLGraphBuilder`, `MLOperand`, etc., but for the R0 probe we only
 * need `createContext`. We declare the shape locally rather than
 * pulling in `@webgpu/types`-style declarations (the WebNN
 * type-definition package is still in flux, 2026-05).
 */
interface NavigatorML {
  createContext(options?: {
    deviceType?: WebnnDeviceType;
  }): Promise<unknown>;
}

interface MLNavigator {
  ml?: NavigatorML;
}

/**
 * Probe `navigator.ml` and attempt to acquire an `MLContext` for each
 * deviceType (`'npu'`, `'gpu'`, `'cpu'`). Safe to call on any
 * environment — returns an all-`false` `WebnnSupport` when
 * `navigator.ml` is undefined (Safari, older Chromium, Firefox).
 *
 * NOTE: this function is async because `createContext` is async per
 * the spec. The UA classification is sync and reuses the same
 * heuristic as `train/device.ts` (kept inline to avoid a circular
 * import between the inference and train modules).
 */
export async function detectWebnnSupport(): Promise<WebnnSupport> {
  const deviceClass = classifyDeviceClass(
    typeof navigator !== "undefined" ? navigator.userAgent : "",
  );

  if (typeof navigator === "undefined") {
    return {
      hasNavigatorML: false,
      hasNpuContext: false,
      hasGpuContext: false,
      hasCpuContext: false,
      deviceClass,
      acquireError: null,
    };
  }

  const ml = (navigator as Navigator & MLNavigator).ml;
  if (!ml || typeof ml.createContext !== "function") {
    return {
      hasNavigatorML: false,
      hasNpuContext: false,
      hasGpuContext: false,
      hasCpuContext: false,
      deviceClass,
      acquireError: null,
    };
  }

  let lastError: string | null = null;
  const tryDevice = async (dt: WebnnDeviceType): Promise<boolean> => {
    try {
      const ctx = await ml.createContext({ deviceType: dt });
      return ctx !== null && ctx !== undefined;
    } catch (e) {
      lastError = e instanceof Error ? e.message : String(e);
      return false;
    }
  };

  // Probe in the order we'll prefer to use them. Each probe is
  // independent; we run them sequentially so the browser doesn't
  // contend on context acquisition for a device type it has only
  // one of (NPU).
  const hasNpuContext = await tryDevice("npu");
  const hasGpuContext = await tryDevice("gpu");
  const hasCpuContext = await tryDevice("cpu");

  return {
    hasNavigatorML: true,
    hasNpuContext,
    hasGpuContext,
    hasCpuContext,
    deviceClass,
    acquireError: lastError,
  };
}

/**
 * Pick an inference backend from a probed `WebnnSupport`, honouring
 * the selection ladder in ADR-2605252100 §2 L2.
 *
 * Default `preferred = 'npu'`. The ladder is:
 *
 *   1. `preferred='npu'` && `hasNpuContext`  → `'webnn-npu'`
 *   2. `preferred ∈ {npu, gpu}` && `hasGpuContext` → `'webnn-gpu'`
 *   3. existing WebGPU adapter (probed elsewhere) → `'webgpu'`
 *   4. `'wasm'`
 *
 * The caller passes the probed `support` (cheap to cache for the
 * session) plus an optional preferred deviceType. The function does
 * NOT probe WebGPU itself — that's `inference.ts`'s
 * `checkWebGPU()`. We return `'webgpu'` when WebNN is unavailable
 * and the caller is responsible for confirming WebGPU is actually
 * present (otherwise the loader falls through to `'wasm'`).
 */
export function selectInferenceBackend(
  support: WebnnSupport,
  preferred: WebnnDeviceType = "npu",
): InferenceBackend {
  if (preferred === "npu" && support.hasNpuContext) {
    return "webnn-npu";
  }
  if (
    (preferred === "npu" || preferred === "gpu") &&
    support.hasGpuContext
  ) {
    return "webnn-gpu";
  }
  if (preferred === "cpu" && support.hasCpuContext) {
    // We don't expose a 'webnn-cpu' backend variant — WebNN CPU is
    // identical-or-worse vs WASM SIMD on every measured device. Fall
    // through to 'wasm'. (R1 may revisit if a device proves
    // otherwise.)
    return "wasm";
  }
  // WebNN not viable for the preferred deviceType. Fall through to
  // WebGPU (the caller validates adapter presence) or WASM.
  return "webgpu";
}

/**
 * Dispatch an inference call through the WebNN execution provider.
 *
 * R1 — wires `onnxruntime-web/webnn` + `MLContext` acquisition +
 * session options. Until R1 lands, this throws with a clear marker
 * pointing to the EP wire-up work. The same pattern is used by
 * `train/kernels.ts` `dispatchLoraForward` for the WebGPU autograd
 * dispatch.
 *
 * The signature is intentionally untyped (`...unknown[]`) at R0 —
 * the exact ORT session option shape is fixed in R1 once we pin the
 * `onnxruntime-web` version that ships WebNN EP stable.
 */
export async function dispatchWebnnInference(
  ..._args: unknown[]
): Promise<never> {
  throw new Error(
    "dispatchWebnnInference: R1 — requires onnxruntime-web/webnn EP wire-up + MLContext acquisition + session-option binding. detectWebnnSupport() and selectInferenceBackend() are ready; only the ORT EP plumbing is missing. See ADR-2605252100 §3.",
  );
}

/**
 * Convenience: probe + select in one call. Cheap enough for boot-time
 * use; cache the result for the session.
 */
export async function probeAndSelect(
  preferred: WebnnDeviceType = "npu",
): Promise<{ support: WebnnSupport; backend: InferenceBackend }> {
  const support = await detectWebnnSupport();
  const backend = selectInferenceBackend(support, preferred);
  return { support, backend };
}

// ── Helpers ───────────────────────────────────────────────────────

/**
 * UA-based device classification, matching `train/device.ts`. Kept
 * inline (not imported) to avoid a circular dependency between
 * inference and train modules. Update both copies when iPadOS or
 * Android UA fingerprints drift.
 */
function classifyDeviceClass(
  userAgent: string,
): "ios" | "android" | "wasm-desktop" {
  const ua = userAgent;
  if (/\bAndroid\b/i.test(ua)) return "android";
  if (/\b(iPhone|iPad|iPod)\b/i.test(ua)) return "ios";
  if (
    /\bMacintosh\b/i.test(ua) &&
    typeof navigator !== "undefined" &&
    (navigator as Navigator & { maxTouchPoints?: number }).maxTouchPoints !==
      undefined &&
    ((navigator as Navigator & { maxTouchPoints?: number }).maxTouchPoints ??
      0) > 1
  ) {
    // iPadOS desktop-class Safari fingerprint.
    return "ios";
  }
  return "wasm-desktop";
}
