/**
 * @etzhayyim/ameno/lora-runtime — LoRA adapter loader + WebGPU compute-shader merge.
 *
 * Scaffold v0.1.0. The full safetensors parser + WebGPU merge kernel land in
 * a follow-up; today the module exposes the public type surface and stubs
 * that throw on use so the svelte appview compiles and the LoRA UI panel
 * renders without runtime errors as long as the user does not toggle an
 * adapter on.
 */

export interface LoraAdapterMeta {
  adapterId: string;
  actorDid: string;
  baseModel: string;
  domain: string;
  /** B2 / R2 URI of the adapter.safetensors blob. */
  weightUri: string;
  weightByteSize: number;
  weightSha256: string;
  adapterRank: number;
  /** α scaling × 1000 (lexicon integer constraint). */
  adapterAlpha: number;
  adapterFormat: string;
}

export interface LoraLayerWeights {
  /** Down-projection (in_features × rank). */
  A: Float32Array;
  /** Up-projection (rank × out_features). */
  B: Float32Array;
  rank: number;
  inFeatures: number;
  outFeatures: number;
}

export interface LoadedLoraAdapter {
  meta: LoraAdapterMeta;
  layers: Map<string, LoraLayerWeights>;
}

export interface AdapterMergeSpec {
  /** Loaded adapter to merge. */
  adapter: LoadedLoraAdapter;
  /** Optional override of the meta-declared α (× 1000). */
  alphaOverride?: number;
}

export async function parseSafetensors(_buf: ArrayBuffer): Promise<Map<string, LoraLayerWeights>> {
  throw new Error("parseSafetensors: not yet implemented in 0.1.0 scaffold");
}

export async function fetchLoraAdapter(_meta: LoraAdapterMeta): Promise<LoadedLoraAdapter> {
  throw new Error("fetchLoraAdapter: not yet implemented in 0.1.0 scaffold");
}

export async function applyLoraAdapters(_specs: AdapterMergeSpec[]): Promise<void> {
  if (_specs.length === 0) return;
  throw new Error("applyLoraAdapters: not yet implemented in 0.1.0 scaffold");
}
