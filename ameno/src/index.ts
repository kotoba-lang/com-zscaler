/**
 * @etzhayyim/ameno — Browser WebGPU inference engine.
 *
 * Provides client-side LLM inference via transformers.js + ONNX + WebGPU,
 * per-actor LoRA adapter merge via WebGPU compute shaders,
 * and RAG context injection via DuckDB-WASM.
 *
 * @packageDocumentation
 */

export {
  MODELS,
  checkWebGPU,
  getGPUInfo,
  loadModel,
  setLoraAdapters,
  getActiveAdapterIds,
  generate,
  type InferenceState,
  type InferenceDevice,
  type ChatMessage,
  type GenerationStats,
} from "./inference";

export {
  parseSafetensors,
  fetchLoraAdapter,
  applyLoraAdapters,
  type LoraAdapterMeta,
  type LoraLayerWeights,
  type LoadedLoraAdapter,
  type AdapterMergeSpec,
} from "./lora-runtime";

export {
  ragSearch,
  listActorAdapters,
  selectAdapters,
  ragLoraSelect,
  buildRagContextPrompt,
  type RagResult,
  type AdapterCandidate,
  type RagLoraContext,
} from "./rag-lora";

// WebNN inference fast path (ADR-2605252100). Detection + routing
// only at R0; `dispatchWebnnInference` throws until R1 wires the ORT
// WebNN EP.
export {
  detectWebnnSupport,
  selectInferenceBackend,
  probeAndSelect,
  dispatchWebnnInference,
  type WebnnSupport,
  type WebnnDeviceType,
  type InferenceBackend,
} from "./inference/webnn";

// WASM-actor loader (ADR-2606014500 + 2606014600) — resolve actor DID, fetch
// its content-addressed WASM via the apex trustless gateway, CID-verify, run
// browser-local. The "one Worker, many WASM actors" runtime in the browser.
export {
  didToDocUrl,
  resolveActorWasm,
  fetchVerifiedWasm,
  instantiateActor,
  runCompute,
  loadActor,
  isRawCidV1,
  cidV1Raw,
  type WasmActorRef,
  type WasmActorLoaderOpts,
} from "./inference/wasm-actor-loader";

// WASM-actor UI panel (ADR-2606015200) — framework-free widget the yoro appview
// mounts to run an actor browser-local and render its result.
export {
  formatActorResult,
  mountActorPanel,
  type ActorResult,
  type MountOpts,
} from "./inference/wasm-actor-panel";

export {
  TRAIN_DEFAULTS,
  DEVICE_STEP_BUDGET,
  detectDeviceClass,
  runFederatedRound,
  signDeltaManifest,
  publishDeltaRecord,
  // R1a framework re-exports
  probeDeviceProfile,
  selectNumericsPath,
  loadShard,
  gradeResponse,
  scanShard,
  computeRoundId,
  openRoundDir,
  type TrainDeviceClass,
  type LoraTrainConfig,
  type TrainShardRef,
  type RoundContext,
  type CharterRiderScanResult,
  type TrainRoundResult,
  type SignedDeltaManifest,
  type RunRoundDeps,
  type DeviceProfile,
  type NumericsPath,
  type WarmupShard,
  type LoadedShard,
  type ShardExample,
  type ScanResult,
  type RoundMeta,
  type RngState,
  type RoundDirHandle,
} from "./train";
