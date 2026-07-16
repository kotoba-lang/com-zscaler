/**
 * @etzhayyim/ameno/train — Browser-side LoRA-only training for baien.
 *
 * Scaffold v0.2.0 — R1a framework. This module is the orchestration
 * barrel for the L2 layer of ADR-2605242600 + ADR-2605242630. The R1a
 * commit lands every framework piece except the actual WebGPU autograd
 * dispatch (which is R1b — see kernels.ts dispatch wrappers).
 *
 * runFederatedRound() now performs:
 *   1. Shard load + CID verify (G10 round-replay parity).
 *   2. Charter Rider scan (G6 — abort if >5% rows rejected).
 *   3. Pre-eval `lossBefore` via the injected `deps.evalLoss` closure.
 *   4. Numerics-path warm-up + selection via the injected
 *      `deps.runWarmupStep` closure (sweeps A → B → C, skips A on mobile).
 *   5. Throws inside the actual training loop with a marker pointing to
 *      R1b — until WebGPU LoRA forward/backward + Adam step are wired
 *      through transformers.js layer-replacement OR a tfjs-webgpu autograd
 *      bridge, no real Δ can be produced.
 *   6. Post-eval `lossAfter` will be reachable once R1b returns.
 *
 * Loss + grad-norm fields in the returned `TrainRoundResult` are
 * integer-scaled by 1_000_000 to match the lexicon (the AT Lexicon v1
 * schema forbids floats — see
 * 00-contracts/lexicons/com/etzhayyim/baien/distributedTrainDelta.json).
 *
 * `signDeltaManifest` and `publishDeltaRecord` STAY throws — those belong
 * to R2 (ES256 passkey signing + firehose publish).
 *
 * Constitutional invariants (enforced at runtime once R1b lands the
 * autograd dispatch):
 *   - Trunk + modality encoders frozen (ADR-2605241900 G1).
 *   - Only LoRA A (in×r) / B (r×out) over q/k/v/o_proj are trainable
 *     (ADR-2605242600 G2).
 *   - charter_rider.scan() runs on the shard BEFORE the first step
 *     (G6); rounds with >5% drop are aborted device-side.
 *   - DP clip + Gaussian noise applied on-device, NOT at the aggregator
 *     (ADR-2605242600 §2 L2 step 6).
 *   - Delta is signed with the member's passkey-derived ES256 key
 *     (ADR-2605231525); no server-issued token is ever accepted.
 */

import {
  detectDeviceClass as detectDeviceClassImpl,
  selectNumericsPath as selectNumericsPathImpl,
  type DeviceProfile,
  type NumericsPath,
  type WarmupShard,
} from "./train/device";
import {
  loadShard,
  type LoadedShard,
  type ShardExample,
} from "./train/shard";
import { scanShard, type ScanResult } from "./train/charter-rider";

export type TrainDeviceClass = "ios" | "android" | "wasm-desktop";

/** LoRA training hyperparameters. Defaults mirror ADR-2605231300 §LoRA. */
export interface LoraTrainConfig {
  rank: number;
  alpha: number;
  dropout: number;
  /** Adam learning rate. */
  learningRate: number;
  /** Number of micro-batch=1 steps to run this round. */
  stepCount: number;
  /** L2 clip threshold τ for differential privacy. */
  dpClipTau: number;
  /** Gaussian noise σ added after clipping. */
  dpNoiseSigma: number;
  /** Target modules — fixed to q/k/v/o_proj per G2; included for visibility. */
  targetModules: readonly string[];
}

export const TRAIN_DEFAULTS: LoraTrainConfig = {
  rank: 16,
  alpha: 32,
  dropout: 0.05,
  learningRate: 2e-4,
  stepCount: 50,
  dpClipTau: 1.0,
  dpNoiseSigma: 0.01,
  targetModules: ["q_proj", "k_proj", "v_proj", "o_proj"],
} as const;

/** Per-device step-count budget (ADR-2605242600 §2 L2 step 4). */
export const DEVICE_STEP_BUDGET: Record<TrainDeviceClass, number> = {
  ios: 50,
  android: 30,
  "wasm-desktop": 500,
} as const;

export interface TrainShardRef {
  /** IPFS CID of the dataset shard (resolved via com.etzhayyim.substrate.datasetPin). */
  datasetShardCid: string;
  /** Size in bytes; informational. */
  sizeBytes: number;
}

export interface RoundContext {
  /** IPFS CID of the frozen trunk + encoders for this round (round-frozen per G10). */
  baseModelCid: string;
  /** IPFS CID of the adapter the device starts from. Empty-adapter CID for iter=0. */
  prevAdapterCid: string;
  /** Monotonic round counter per (actorDid, baseModelCid). */
  iter: number;
  /** Caller DID (Adherent SBT holder per G7); used only for receipt/manifest. */
  actorDid: string;
  /** Detected device class for the step-count budget + receipt deviceClass field. */
  deviceClass: TrainDeviceClass;
}

export interface CharterRiderScanResult {
  /** Total rows in the shard. */
  totalRows: number;
  /** Rows the scanner rejected (per ADR-2605192200 §2(a)..(h)). */
  rejectedRows: number;
  /** True iff (rejectedRows / totalRows) <= 0.05. */
  passed: boolean;
  /** Sample of rejected row evidence (truncated to keep the manifest small). */
  evidenceSample: ReadonlyArray<{ category: string; evidence: string }>;
}

export interface TrainRoundResult {
  /** Mean eval-microbench loss BEFORE the training step. Scaled by 1_000_000 to match the lexicon (integer-only) on the wire. */
  lossBefore: number;
  /** Mean eval-microbench loss AFTER the training step. Same scaling as lossBefore. */
  lossAfter: number;
  /** L2 norm of the on-device DP-clipped delta. Scaled by 1_000_000 to match the lexicon. */
  gradNormL2: number;
  /** IPFS CID of the safetensors blob containing the delta. */
  deltaCid: string;
  /** Steps actually performed (may be < config.stepCount if thermal-throttled). */
  stepsCompleted: number;
  /** Scanner result for the shard. */
  scanner: CharterRiderScanResult;
  /** Numerics path selected at warm-up (A / B / C). Informational. */
  numericsPath: NumericsPath;
}

export interface SignedDeltaManifest {
  v: 1;
  actorDid: string;
  baseModelCid: string;
  datasetShardCid: string;
  prevAdapterCid: string;
  deltaCid: string;
  iter: number;
  stepCount: number;
  deviceClass: TrainDeviceClass;
  /** Scaled by 1_000_000 ('micro-loss' units); the lexicon forbids floats. */
  lossBefore: number;
  /** Scaled by 1_000_000 ('micro-loss' units); same units as lossBefore. */
  lossAfter: number;
  /** Scaled by 1_000_000 ('micro-norm' units); the lexicon forbids floats. */
  gradNormL2: number;
  scannerPass: boolean;
  trainedAt: string;
  /** ES256 signature over canonical JSON of the preceding fields. */
  sig: string;
}

/**
 * Caller-supplied dependencies for a federated round. The framework
 * injects WebGPU forward / backward / Adam closures here so the
 * orchestration barrel stays free of the autograd dispatch (which is
 * R1b's scope).
 */
export interface RunRoundDeps {
  /**
   * Run `count` forward passes through the frozen trunk + LoRA on
   * `examples` and return the mean loss. Used for pre-eval and post-eval
   * passes around the training loop. Loss is in natural (float) units;
   * the orchestration layer scales by 1_000_000 before populating the
   * `TrainRoundResult` fields.
   */
  evalLoss(examples: readonly ShardExample[]): Promise<number>;
  /**
   * Run ONE forward+backward+Adam-update on a fixed 3-example warm-up
   * sub-shard under the supplied numerics path. Returns the L2 norm of
   * the resulting LoRA Δ (A-stack ‖ B-stack concatenated). The framework
   * uses this to sweep A → B → C and picks the first path whose Δ-norm
   * ratio against the fp64 reference is in [0.99, 1.01].
   */
  runWarmupStep(
    examples: readonly ShardExample[],
    path: NumericsPath,
  ): Promise<{ deltaNorm: number; fp64Reference: number }>;
  /** Raw shard bytes (JSONL UTF-8). Caller fetches from IPFS. */
  fetchShardBytes(cid: string): Promise<Uint8Array>;
}

/**
 * Run a single federated training round for baien on this device.
 *
 * Order of operations (mirrors ADR-2605242600 §2 L2 + ADR-2605242630):
 *   1. Fetch shard bytes via `deps.fetchShardBytes` and CID-verify them
 *      (G10 round-replay parity).
 *   2. `charter_rider.scan()` — abort the round if >5% rows are rejected.
 *   3. Pre-eval pass → `lossBefore`.
 *   4. Numerics-path warm-up via `deps.runWarmupStep`; pick A / B / C.
 *   5. WebGPU LoRA-only autograd, `stepCount` micro-batch=1 steps.
 *      **R1b**: not yet wired; this step throws with a clear marker.
 *   6. Post-eval pass → `lossAfter`.
 *   7. DP clip + Gaussian noise on the in-memory delta.
 *   8. Serialise delta to safetensors; pin to IPFS; record `deltaCid`.
 */
export async function runFederatedRound(
  ctx: RoundContext,
  shard: TrainShardRef,
  config: LoraTrainConfig = TRAIN_DEFAULTS,
  deps?: RunRoundDeps,
): Promise<TrainRoundResult> {
  if (!deps) {
    throw new Error(
      "runFederatedRound: RunRoundDeps not supplied; injection required (R1a framework)",
    );
  }
  // Step 1: shard load + CID verify.
  const bytes = await deps.fetchShardBytes(shard.datasetShardCid);
  const loaded: LoadedShard = await loadShard(bytes, {
    expectedCid: shard.datasetShardCid,
  });

  // Step 2: Charter Rider scan.
  const scan: ScanResult = scanShard(loaded.examples);
  if (!scan.passed) {
    throw new Error(
      `runFederatedRound: charter-rider scan rejected ${scan.rejectedRows}/${scan.totalRows} rows (>5% threshold); aborting round (G6, ADR-2605242630 §6)`,
    );
  }

  // Step 3: pre-eval lossBefore (float; scale to integer micro-loss at end).
  const lossBeforeFloat = await deps.evalLoss(loaded.examples);

  // Step 4: numerics-path warm-up + selection.
  const profile = detectDeviceClassImpl();
  const warmupShard: WarmupShard<ShardExample> = {
    examples: loaded.examples.slice(0, 3),
  };
  const numericsPath = await selectNumericsPathImpl<ShardExample>(
    profile,
    warmupShard,
    deps.runWarmupStep,
  );

  // Step 5: training loop — R1b lands here. The autograd dispatch
  //   (LoRA forward / backward / Adam step) hooks via transformers.js
  //   layer-replacement OR a tfjs-webgpu autograd bridge into kernels.ts.
  void ctx;
  void config;
  void lossBeforeFloat;
  void numericsPath;
  throw new Error(
    [
      "runFederatedRound: training loop not implemented in R1a framework.",
      "R1b: WebGPU autograd dispatch — requires transformers.js layer-replacement",
      "OR tfjs-webgpu autograd bridge. Hook point = kernels.ts",
      "(dispatchLoraForward / dispatchLoraBackward / dispatchAdamStep).",
      `[shard=${shard.datasetShardCid} scan=${scan.passed ? "pass" : "fail"} path=${numericsPath}]`,
    ].join(" "),
  );

  // (unreachable in R1a) — kept as scaffolding for R1b:
  //   const lossAfterFloat = await deps.evalLoss(loaded.examples);
  //   const gradNormL2Float = …;
  //   const deltaCid = …;
  //   return {
  //     lossBefore: Math.round(lossBeforeFloat * 1_000_000),
  //     lossAfter: Math.round(lossAfterFloat * 1_000_000),
  //     gradNormL2: Math.round(gradNormL2Float * 1_000_000),
  //     deltaCid,
  //     stepsCompleted: …,
  //     scanner: {
  //       totalRows: scan.totalRows,
  //       rejectedRows: scan.rejectedRows,
  //       passed: scan.passed,
  //       evidenceSample: scan.evidenceSample,
  //     },
  //     numericsPath,
  //   };
}

/**
 * Sign the round's manifest with the member's passkey-derived ES256
 * key (ADR-2605231525). The platform MUST hold the private key; this
 * function is only the canonicalisation + WebAuthn `sign` wrapper.
 *
 * NOTE: R2 — throws. Real implementation lands when the firehose
 * publish path is unblocked.
 */
export async function signDeltaManifest(
  _manifest: Omit<SignedDeltaManifest, "sig">,
): Promise<SignedDeltaManifest> {
  throw new Error(
    "signDeltaManifest: not yet implemented; activates in R2 (passkey-ES256 + firehose publish, ADR-2605242630 §7)",
  );
}

/**
 * Publish the signed delta record to the contributor's AT repo under
 * the lexicon `com.etzhayyim.baien.distributedTrainDelta`. The
 * aggregator subscribes to the firehose and picks it up from there.
 *
 * NOTE: R2 — throws. Real implementation lands with the aggregator.
 */
export async function publishDeltaRecord(
  _signed: SignedDeltaManifest,
): Promise<{ uri: string; cid: string }> {
  throw new Error(
    "publishDeltaRecord: not yet implemented; activates in R2 (aggregator + firehose, ADR-2605242630 §7)",
  );
}

/**
 * Probe the running environment and return the device class. Defaults
 * to `wasm-desktop` for non-mobile WebGPU contexts. The probe runs
 * before any training-step budget decision.
 *
 * Real implementation now lives in `train/device.ts`; this barrel
 * re-export keeps the 0.1.0 public API stable.
 */
export function detectDeviceClass(): TrainDeviceClass {
  return detectDeviceClassImpl().deviceClass;
}

// ── Re-exports for the train/* surface ──────────────────────────────
export {
  detectDeviceClass as probeDeviceProfile,
  selectNumericsPath,
  probeAdapterAsync,
} from "./train/device";
export { loadShard, gradeResponse } from "./train/shard";
export { scanShard } from "./train/charter-rider";
export {
  computeRoundId,
  openRoundDir,
} from "./train/opfs";
export type {
  DeviceProfile,
  NumericsPath,
  WarmupShard,
} from "./train/device";
export type { LoadedShard, ShardExample } from "./train/shard";
export type { ScanResult } from "./train/charter-rider";
export type { RoundMeta, RngState, RoundDirHandle } from "./train/opfs";
