/**
 * HuggingFace Inference API embedder.
 *
 * Calls out to `api-inference.huggingface.co` for embedding via HTTP.
 * Useful when local transformers.js is too slow or unavailable.
 *
 * Yorishiro-aligned (ADR-2605211900).
 *   Contract : ai.etzhayyim.yorishiro.huggingface-inference.extractFeatures
 *   Lexicon  : 00-contracts/lexicons/ai/etzhayyim/yorishiro/huggingface-inference/extractFeatures.json
 *   MCP equiv: @etzhayyim/yorishiro-huggingface-inference-mcp
 *   Charter  : grant (read-only model inference; non-promotional)
 *
 * Direct fetch is retained here because this embedder is consumed from
 * `mst-projector` worker code that already has the HF token in scope —
 * routing through the MCP server would require copying the token across
 * a process boundary for marginal benefit. The on-the-wire shape (POST
 * /pipeline/feature-extraction/{model_id} with `inputs` body) matches
 * the lexicon's input schema exactly.
 *
 * Install: pnpm add @huggingface/inference
 *
 * Set `HF_INFERENCE_TOKEN` env var (from https://huggingface.co/settings/tokens).
 *
 * Per ADR-2605212000 §Phase 3c: embedding provider unification.
 */

import { createDefaultHuggingfaceInferenceHandle } from "@etzhayyim/yorishiro-huggingface-inference-mcp/handle";
import type { EmbeddingProvider } from "../adapters.js";

export interface HuggingFaceEmbeddingConfig {
  /** HuggingFace model id, e.g. "sentence-transformers/all-MiniLM-L6-v2". */
  model: string;
  /** API token (from HF_INFERENCE_TOKEN env var or explicit). */
  apiKey: string;
  /** Expected embedding dimension (must match the model). */
  vectorDim: number;
}

/**
 * Create a HuggingFace Inference API embedder.
 *
 * Throws a clear error if the API token is missing or invalid.
 * Uses fetch internally (available in Node 18+).
 */
export async function createHuggingFaceEmbedder(
  config: HuggingFaceEmbeddingConfig,
): Promise<EmbeddingProvider> {
  if (!config.apiKey) {
    throw new Error(
      "HuggingFace embedder: apiKey is required. " +
        "Set HF_INFERENCE_TOKEN env var or pass it explicitly.",
    );
  }

  const modelId = config.model;
  const dim = config.vectorDim;
  const apiKey = config.apiKey;

  // Yorishiro handle (ADR-2605211900) — same on-the-wire shape as the
  // previous direct fetch, but credentials are injected via the
  // `headers` option (the yorishiro itself never stores them).
  const handle = createDefaultHuggingfaceInferenceHandle({
    baseUrl: "https://api-inference.huggingface.co",
    headers: { Authorization: `Bearer ${apiKey}` },
  });

  const embedder: EmbeddingProvider = {
    dim,
    modelId,
    async embed(text: string): Promise<Float32Array> {
      const res = await handle.extract_features({
        model_id: modelId,
        inputs: text,
        wait_for_model: true,
      });

      if (res.error || res.httpStatus !== 200) {
        throw new Error(
          `HuggingFace Inference ${res.httpStatus}: ${res.error ?? res.body ?? "<no body>"}`,
        );
      }

      // The kami returns number[][] or number[] depending on the model.
      const payload: unknown = res.json ?? (res.body ? JSON.parse(res.body) : undefined);
      if (!Array.isArray(payload)) {
        throw new Error(`HuggingFace Inference returned non-array payload`);
      }
      const arr = Array.isArray((payload as unknown[])[0])
        ? (payload as number[][])[0]!
        : (payload as number[]);

      if (arr.length !== dim) {
        throw new Error(
          `HuggingFace Inference returned ${arr.length}-dim vector, expected ${dim}`,
        );
      }

      return Float32Array.from(arr);
    },
  };

  return embedder;
}

/**
 * Production preset configs for common HuggingFace embedding models.
 *
 * API-based embedders are slower than local transformers.js but can use
 * larger/better models if the latency budget permits.
 *
 * Pick by use-case:
 *   - sentence-transformers/all-MiniLM-L6-v2 — 384-dim, fast, general-purpose
 *   - sentence-transformers/bge-large-en-v1.5 — 1024-dim, higher quality
 */
export const HUGGINGFACE_PRESETS = {
  "all-MiniLM-L6-v2": {
    model: "sentence-transformers/all-MiniLM-L6-v2" as const,
    vectorDim: 384 as const,
  },
  "bge-large-en-v1.5": {
    model: "sentence-transformers/bge-large-en-v1.5" as const,
    vectorDim: 1024 as const,
  },
} as const;
