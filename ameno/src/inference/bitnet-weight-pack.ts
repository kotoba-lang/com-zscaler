/**
 * @etzhayyim/ameno/inference/bitnet-weight-pack — extract BitNet
 * trunk weights from an ONNX blob into the side-channel format the
 * BitLinear kernel reads (ADR-2605263800).
 *
 * This is the **first commit after the R1b strategy pivot** away from
 * ORT-Web custom-op registration toward a full forward-override
 * runtime. The pack is the connective piece both strategies need:
 * given a `.onnx` blob, produce a `Map<origName, BitLinearWeightPack>`
 * containing (i2_s packed bytes, f16 scale bytes) for every
 * trunk-projection weight.
 *
 * The pack is consumed by:
 *   - The forward-override runtime (R1b commits 5-11, planned).
 *   - A future WebNN BitLinear EP (R3).
 *   - The R1c v128 SIMD inner loop microbench (which needs known-good
 *     weight inputs to compare against the scalar reference).
 *
 * The original `.onnx` bytes are NOT modified — extraction is read-only.
 * Once the pack is built, the caller can free the original `.onnx`
 * blob (~600 MB for BitNet 2B bf16). This is what closes the silent
 * G1 violation per ADR-2605241900 §G1.
 *
 * ## Layout
 *
 * ```
 * Pack key:  "model.layers.0.self_attn.q_proj.weight"      ← original bf16 name (verbatim)
 *
 *   {
 *     origName: "model.layers.0.self_attn.q_proj.weight",
 *     packed:   Uint8Array[2048 × ceil(2048/4) = 1,048,576 bytes],
 *     scale:    Uint8Array[2048 × 2 = 4,096 bytes (uint16 LE per-row scale)],
 *     dims:     [2048, 2048],
 *   }
 * ```
 *
 * The keys are the **original** bf16 initializer names (not the
 * `.i2s_packed` rewritten names). This lets the forward-override
 * runtime look up weights by the HF transformers layer naming
 * convention without translation.
 */

import {
  ModelProtoView,
  DataType,
  type TensorProtoView,
} from "./onnx-proto-min.ts";
import { BITNET_TRUNK_PROJ_PATTERN } from "./bitnet-graph-patcher.ts";
import { transformBf16ToI2sAndScale } from "./bitnet-weight-transformer.ts";

/**
 * One projection weight, transformed and ready for the BitLinear kernel.
 */
export interface BitLinearWeightPack {
  /** Original initializer name (e.g. `model.layers.0.self_attn.q_proj.weight`). */
  readonly origName: string;
  /** i2_s packed weight bytes. Length = rows × ceil(cols / 4). */
  readonly packed: Uint8Array;
  /** Per-row f16 scale (uint16 little-endian). Length = rows × 2 bytes. */
  readonly scale: Uint8Array;
  /** Shape of the ORIGINAL bf16 weight. `[rows, cols]`. */
  readonly dims: readonly [number, number];
}

/** Result of an extraction pass. */
export interface ExtractResult {
  /** Per-tensor extracted packs, keyed by `origName`. */
  readonly packs: ReadonlyMap<string, BitLinearWeightPack>;
  /**
   * Per-layer-index count of extracted packs. Layer index parsed
   * from `model.layers.<N>.<...>.weight`. Useful for verifying that
   * every layer got 7 projections (q/k/v/o/gate/up/down) extracted.
   */
  readonly countByLayer: ReadonlyMap<number, number>;
  /**
   * Names of initializers whose name DID match the pattern but
   * whose data_type was NOT bfloat16. Telemetry-only — for now this
   * is expected to be empty on the HF artifact (all trunk weights
   * are bf16). If the HF export schema changes, these warnings let
   * us notice without silent skips.
   */
  readonly typeMismatches: readonly { name: string; dataType: number }[];
}

const LAYER_INDEX_PATTERN = /\bmodel\.layers\.(\d+)\./;

/**
 * Walk the model's initializer list, find every bf16 trunk-projection
 * weight, run the bf16 → (i2_s packed, f16 scale) transform, and
 * collect the results into a pack map.
 *
 * Does NOT mutate the input model.
 */
export function extractBitLinearWeightPack(
  modelBytes: Uint8Array,
): ExtractResult {
  const model = ModelProtoView.fromBytes(modelBytes);
  const packs = new Map<string, BitLinearWeightPack>();
  const countByLayer = new Map<number, number>();
  const typeMismatches: { name: string; dataType: number }[] = [];

  for (const init of model.graph.initializers) {
    if (!BITNET_TRUNK_PROJ_PATTERN.test(init.name)) continue;
    if (init.dataType !== DataType.BFLOAT16) {
      typeMismatches.push({ name: init.name, dataType: init.dataType });
      continue;
    }
    const pack = extractOneInitializer(init);
    if (pack === null) continue;
    packs.set(pack.origName, pack);
    bumpLayerCount(countByLayer, pack.origName);
  }

  return { packs, countByLayer, typeMismatches };
}

function extractOneInitializer(
  init: TensorProtoView,
): BitLinearWeightPack | null {
  const dims = init.dims;
  if (dims.length !== 2) return null;
  const rows = dims[0]!;
  const cols = dims[1]!;
  // Run the (already-tested) transform.
  const { packed, scale } = transformBf16ToI2sAndScale(init.name, init);
  return {
    origName: init.name,
    packed: packed.rawData,
    scale: scale.rawData,
    dims: [rows, cols],
  };
}

function bumpLayerCount(map: Map<number, number>, name: string): void {
  const m = LAYER_INDEX_PATTERN.exec(name);
  if (!m || m[1] === undefined) return;
  const layer = parseInt(m[1], 10);
  if (!Number.isFinite(layer)) return;
  map.set(layer, (map.get(layer) ?? 0) + 1);
}

/**
 * Verify that an extracted pack map matches the expected layer
 * structure of BitNet 2B: 30 layers × 7 projections = 210 packs.
 *
 * Returns an array of per-layer issues, or empty if the structure
 * matches expectation.
 */
export function verifyBitNet2bPackStructure(
  result: ExtractResult,
  expectedNumLayers = 30,
  expectedProjsPerLayer = 7,
): string[] {
  const issues: string[] = [];
  const totalExpected = expectedNumLayers * expectedProjsPerLayer;
  if (result.packs.size !== totalExpected) {
    issues.push(
      `pack count = ${String(result.packs.size)}, expected ${String(totalExpected)} (${String(expectedNumLayers)} layers × ${String(expectedProjsPerLayer)} projections)`,
    );
  }
  for (let layer = 0; layer < expectedNumLayers; layer++) {
    const count = result.countByLayer.get(layer) ?? 0;
    if (count !== expectedProjsPerLayer) {
      issues.push(
        `layer ${String(layer)} has ${String(count)} projections, expected ${String(expectedProjsPerLayer)}`,
      );
    }
  }
  if (result.typeMismatches.length > 0) {
    issues.push(
      `${String(result.typeMismatches.length)} initializers matched name pattern but had wrong dataType (expected bfloat16): ${result.typeMismatches
        .slice(0, 3)
        .map((m) => `${m.name}(dt=${String(m.dataType)})`)
        .join(", ")}${result.typeMismatches.length > 3 ? "..." : ""}`,
    );
  }
  return issues;
}
