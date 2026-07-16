/**
 * Pluggable embedding provider + deterministic test embedder (Phase 3c).
 *
 * Standalone module — does NOT modify scaffold adapters.ts. Production wiring
 * (HuggingFace transformers.js) lives in src/embedders/transformers.ts.
 *
 * Per ADR-2605212000 §"DuckDB cursor for incremental aggregate updates" +
 * Phase 3c production wiring.
 */

export interface EmbeddingProvider {
  readonly dim: number;
  readonly modelId: string;
  embed(text: string): Promise<Float32Array>;
}

/**
 * Deterministic hash-based embedder for tests and local development.
 *
 * NOT semantic — but cosine between two FakeHash embeddings is stable and
 * monotonic in token overlap, enough to exercise EmbeddingTextIndex without
 * downloading 90MB+ ONNX models in CI.
 */
export class FakeHashEmbedder implements EmbeddingProvider {
  readonly modelId: string;

  constructor(readonly dim: number = 384) {
    if (dim <= 0 || !Number.isInteger(dim)) {
      throw new Error(`FakeHashEmbedder: dim must be a positive integer (got ${dim})`);
    }
    this.modelId = `fake-hash-d${dim}-v1`;
  }

  async embed(text: string): Promise<Float32Array> {
    const vec = new Float32Array(this.dim);
    const tokens = (text ?? "")
      .toLowerCase()
      .split(/[^a-z0-9぀-ゟ゠-ヿ一-鿿]+/u)
      .filter((t) => t.length >= 2);
    if (tokens.length === 0) return vec;

    for (const tok of tokens) {
      const h1 = fnv1a(tok, 0x811c9dc5);
      const h2 = fnv1a(tok, 0xcbf29ce4);
      vec[h1 % this.dim] += (h1 & 0x80000000) ? -1 : 1;
      vec[h2 % this.dim] += (h2 & 0x80000000) ? -1 : 1;
    }

    let norm = 0;
    for (let i = 0; i < this.dim; i++) norm += vec[i] * vec[i];
    if (norm > 0) {
      const inv = 1 / Math.sqrt(norm);
      for (let i = 0; i < this.dim; i++) vec[i] *= inv;
    }
    return vec;
  }
}

function fnv1a(s: string, seed: number): number {
  let h = seed >>> 0;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = (h + ((h << 1) | 0) + ((h << 4) | 0) + ((h << 7) | 0) + ((h << 8) | 0) + ((h << 24) | 0)) >>> 0;
  }
  return h;
}
