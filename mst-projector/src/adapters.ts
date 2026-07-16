/**
 * mst-projector LanceDB + DuckDB adapter STUBS.
 *
 * These are NOT runnable as-is — the LanceDB / DuckDB packages are not
 * declared as dependencies in this package. Phase 3 production deployment
 * installs:
 *   - @lancedb/lancedb (vector store for IVF text search)
 *   - duckdb-async (in-process OLAP for aggregates + inverted indexes)
 *   - @huggingface/transformers (local ONNX embeddings) or
 *     @huggingface/inference (API-based embeddings)
 *
 * Per ADR-2605212000 §"Storage": Phase 3 progression is:
 *   - Phase 3a: in-memory (Phase 3 reference, always available)
 *   - Phase 3b: DuckDB (production aggregate + attribute persistence)
 *   - Phase 3c: LanceDB (production IVF vector text search)
 */

/**
 * Embedding provider interface — unifies local + API-based embedding backends.
 * Implementations return fixed-dim float32 vectors from text input.
 *
 * Implemented by:
 *   - createTransformersEmbedder (embedders/transformers.ts) — local ONNX
 *   - createHuggingFaceEmbedder (embedders/huggingface.ts) — API-based
 *
 * LanceDB text index consumes this to embed queries + docs.
 */
export interface EmbeddingProvider {
  /** Dimension of returned vectors (e.g. 384 for all-MiniLM-L6-v2). */
  readonly dim: number;

  /** Model identifier for attestation / reproducibility. */
  readonly modelId: string;

  /**
   * Embed text string to float32 vector of length `dim`.
   * MUST return unit vectors (L2 norm = 1) for cosine similarity.
   */
  embed(text: string): Promise<Float32Array>;
}

/**
 * Marker interface for pluggable storage backend adapters.
 */
export interface BackendAdapter {
  readonly kind: "inmemory" | "lancedb" | "duckdb";
  init(): Promise<void>;
  close(): Promise<void>;
}

/**
 * LanceDB text index stub.
 */
export class LanceDbTextIndexStub implements BackendAdapter {
  readonly kind = "lancedb" as const;

  async init(): Promise<void> {
    throw new Error(
      "LanceDB adapter not installed — fall back to InMemoryTextIndex. " +
        "Production deployment: install @lancedb/lancedb and " +
        "@huggingface/transformers (local ONNX) or set HF_INFERENCE_TOKEN for API.",
    );
  }

  async close(): Promise<void> {}
}

/**
 * DuckDB aggregate + attribute index stub.
 */
export class DuckDbAggregateIndexStub implements BackendAdapter {
  readonly kind = "duckdb" as const;

  async init(): Promise<void> {
    throw new Error(
      "DuckDB adapter not installed — fall back to InMemoryAggregateIndex. " +
        "Production deployment: install duckdb-async.",
    );
  }

  async close(): Promise<void> {}
}

/**
 * Embedding provider stub.
 */
export class EmbeddingProviderStub {
  async embed(_text: string): Promise<Float32Array> {
    throw new Error(
      "Embedding provider not configured — " +
        "use createTransformersEmbedder (local) or " +
        "createHuggingFaceEmbedder (API) from embedders/",
    );
  }
}
