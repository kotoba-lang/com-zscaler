/**
 * @etzhayyim/ameno/train/device — Device profile probe + numerics-path
 * selection (ADR-2605242630 §1 device matrix, §2 numerics fallback).
 *
 * `detectDeviceClass()` probes the running environment:
 *   - `navigator.gpu` presence (WebGPU runtime).
 *   - `requestAdapter()` + `adapter.info` (preferred, modern Chrome /
 *     Safari 17.5+) with a fallback through `adapter.requestAdapterInfo`
 *     (deprecated, cast via `unknown` to keep TS strict happy).
 *   - User-Agent string for iOS / Android classification (the adapter
 *     `vendor` field alone is insufficient — Apple GPU shows on both
 *     macOS and iOS; the UA disambiguates).
 *   - `adapter.limits` for the memory ceiling probe (matched against
 *     ADR-2605242630 §1 budgets).
 *
 * `selectNumericsPath()` sweeps the three paths in order:
 *
 * | Path | Forward | LoRA grad accum | Adam moments | Trigger             |
 * |------|---------|------------------|--------------|---------------------|
 * | A    | fp16    | fp16             | fp16         | Desktop only        |
 * | B    | fp16    | fp32             | fp32         | MOBILE DEFAULT      |
 * | C    | fp32    | fp32             | fp32         | path-B-overflow LR  |
 *
 * On mobile (iOS or Android device class), path A is SKIPPED — the
 * fallback table in ADR-2605242630 §2 specifies mobile defaults to
 * path B. For each candidate path the caller-supplied
 * `runOneShortRound` closure runs a forward+backward+Adam-update on a
 * fixed 3-example warm-up sub-shard and returns the resulting Δ-norm
 * plus an fp64 CPU-side reference. The first path whose
 * `deltaNorm / fp64Reference ∈ [0.99, 1.01]` wins.
 *
 * NOTE: this module never imports a WebGPU dispatch — that lands in
 * R1b. It only exposes the *selection policy*; the autograd closure
 * is injected by the caller.
 */

export type TrainDeviceClass = "ios" | "android" | "wasm-desktop";

/** Three numerics paths (ADR-2605242630 §2). */
export type NumericsPath = "A" | "B" | "C";

/**
 * A device profile is the union of:
 *   - the classification (`ios` / `android` / `wasm-desktop`),
 *   - the WebGPU adapter info (vendor, architecture, device, description),
 *   - whether path A (fp16 throughout) is eligible.
 *
 * The profile is immutable — callers MUST NOT mutate it. Re-running the
 * probe on the same device produces an equal profile.
 */
export interface DeviceProfile {
  readonly deviceClass: TrainDeviceClass;
  readonly hasWebGPU: boolean;
  readonly vendor: string;
  readonly architecture: string;
  readonly device: string;
  readonly description: string;
  readonly maxBufferBytes: number;
  readonly userAgent: string;
  /**
   * True iff path A (fp16 throughout) is allowed on this device. Mobile
   * devices (`ios`, `android`) always return `false` regardless of
   * adapter capability — ADR-2605242630 §2 fallback table.
   */
  readonly pathAEligible: boolean;
}

/**
 * Three-example warm-up sub-shard. The framework owns the slice. The
 * element type is `unknown` so this module stays free of a shard-module
 * import (avoids a circular dependency); callers narrow to ShardExample
 * at the dispatch boundary.
 */
export interface WarmupShard<T = unknown> {
  /** Three deterministic examples drawn from the head of the loaded shard. */
  readonly examples: readonly T[];
}

/**
 * One full forward+backward+Adam-update run on `examples` under the
 * supplied numerics path. Returns the L2 norm of the resulting LoRA Δ
 * (A-stack ‖ B-stack concatenated) plus an fp64 CPU-side reference for
 * the same computation. The framework checks
 * `deltaNorm / fp64Reference ∈ [0.99, 1.01]`.
 */
export type RunOneShortRound<T = unknown> = (
  examples: readonly T[],
  path: NumericsPath,
) => Promise<{ deltaNorm: number; fp64Reference: number }>;

/**
 * AdapterInfo shape across Chrome / Safari versions. The modern
 * accessor (`adapter.info`) returns these fields directly. The
 * deprecated accessor (`adapter.requestAdapterInfo()`) returns the same
 * shape async; we cast it through `unknown` to bypass the deprecation
 * flag without disabling TS strict mode.
 */
interface AdapterInfoShape {
  vendor?: string;
  architecture?: string;
  device?: string;
  description?: string;
}

/**
 * Probe the running environment and return a DeviceProfile.
 *
 * This function is SAFE to call without WebGPU available — when
 * `navigator.gpu` is missing, the profile reports `hasWebGPU=false` and
 * empty adapter fields, but still classifies via UA. Callers MUST
 * gate any GPU dispatch on `hasWebGPU === true`.
 */
export function detectDeviceClass(): DeviceProfile {
  const userAgent = typeof navigator !== "undefined" ? navigator.userAgent : "";
  const deviceClass = classifyDeviceClass(userAgent);

  // Default empty adapter shape — sync, no async required for the
  // baseline probe. The full async sweep lives in `probeAdapterAsync`
  // (callable by the caller for richer telemetry).
  const profile: DeviceProfile = {
    deviceClass,
    hasWebGPU:
      typeof navigator !== "undefined" &&
      typeof (navigator as Navigator & { gpu?: unknown }).gpu !== "undefined",
    vendor: "",
    architecture: "",
    device: "",
    description: "",
    maxBufferBytes: 0,
    userAgent,
    pathAEligible: deviceClass === "wasm-desktop",
  };
  return profile;
}

/**
 * Async deep-probe variant — returns a DeviceProfile populated with
 * adapter.info fields when WebGPU is available. Used by the cell
 * runtime + run-log writer; the sync `detectDeviceClass()` is enough
 * for the device-class classification + path-A eligibility flag.
 */
export async function probeAdapterAsync(): Promise<DeviceProfile> {
  const base = detectDeviceClass();
  if (!base.hasWebGPU) return base;

  const gpu = (navigator as Navigator & { gpu?: GPU }).gpu;
  if (!gpu) return base;

  let adapter: GPUAdapter | null = null;
  try {
    adapter = await gpu.requestAdapter();
  } catch {
    return base;
  }
  if (!adapter) return base;

  // Prefer modern `adapter.info`. Cast through unknown so the
  // deprecated `requestAdapterInfo` doesn't trigger TS deprecation
  // checks on toolchains that flag it.
  let info: AdapterInfoShape = {};
  const adapterUnknown = adapter as unknown as {
    info?: AdapterInfoShape;
    requestAdapterInfo?: () => Promise<AdapterInfoShape>;
  };
  if (adapterUnknown.info && typeof adapterUnknown.info === "object") {
    info = adapterUnknown.info;
  } else if (typeof adapterUnknown.requestAdapterInfo === "function") {
    try {
      info = await adapterUnknown.requestAdapterInfo();
    } catch {
      info = {};
    }
  }

  const limits = adapter.limits;
  // `maxBufferSize` is the most reliable single-buffer ceiling; fall
  // back to `maxStorageBufferBindingSize` if absent on older browsers.
  const maxBufferBytes =
    (limits as GPUSupportedLimits & { maxBufferSize?: number }).maxBufferSize ??
    limits.maxStorageBufferBindingSize ??
    0;

  return {
    ...base,
    vendor: info.vendor ?? "",
    architecture: info.architecture ?? "",
    device: info.device ?? "",
    description: info.description ?? "",
    maxBufferBytes,
  };
}

/**
 * Sweep numerics paths in order — A (desktop only) → B (default) → C
 * (last resort) — and return the first one whose Δ-norm ratio is in
 * [0.99, 1.01] against the fp64 CPU-side reference.
 *
 * Throws if all candidate paths fail. The caller (orchestration layer)
 * treats this as a round-abort condition.
 */
export async function selectNumericsPath<T = unknown>(
  profile: DeviceProfile,
  warmupShard: WarmupShard<T>,
  runOneShortRound: RunOneShortRound<T>,
): Promise<NumericsPath> {
  const candidates: NumericsPath[] = profile.pathAEligible
    ? ["A", "B", "C"]
    : ["B", "C"];

  let lastObserved: {
    path: NumericsPath;
    ratio: number;
  } | null = null;

  for (const path of candidates) {
    let result: { deltaNorm: number; fp64Reference: number };
    try {
      result = await runOneShortRound(warmupShard.examples, path);
    } catch {
      // Path threw (e.g. fp16 overflow on path A). Move on.
      continue;
    }
    if (
      !Number.isFinite(result.deltaNorm) ||
      !Number.isFinite(result.fp64Reference) ||
      result.fp64Reference === 0
    ) {
      // NaN / Inf / divide-by-zero → next path.
      continue;
    }
    const ratio = result.deltaNorm / result.fp64Reference;
    lastObserved = { path, ratio };
    if (ratio >= 0.99 && ratio <= 1.01) {
      return path;
    }
  }

  const ratioStr =
    lastObserved !== null
      ? `last ratio=${lastObserved.ratio.toFixed(4)} on path=${lastObserved.path}`
      : "no path returned a finite ratio";
  throw new Error(
    `selectNumericsPath: no candidate path landed in [0.99, 1.01] (${ratioStr}); aborting round (ADR-2605242630 §2)`,
  );
}

// ── Helpers ───────────────────────────────────────────────────────

function classifyDeviceClass(userAgent: string): TrainDeviceClass {
  // Order matters: iPad Safari may report "Macintosh" on iPadOS 13+,
  // so we check for iPhone / iPad markers first.
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
