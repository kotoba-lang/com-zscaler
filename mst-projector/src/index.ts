/**
 * mst-projector — Phase 3 indexed materialized views for kotoba actors.
 *
 * Subscribes to PDS firehose, maintains LanceDB (text indexes) + DuckDB (aggregates),
 * and serves O(log N) / O(1) queries instead of O(N) collection scans.
 *
 * Per ADR-2605212000.
 */

export * from "./types.js";
export * from "./inmemory.js";
export * from "./orchestrator.js";
export { kiyoProjector } from "./kiyo-config.js";

// Production adapters — export for backend selection at deploy time
export type { EmbeddingProvider, BackendAdapter } from "./adapters.js";
export { LanceDbTextIndexStub, DuckDbAggregateIndexStub } from "./adapters.js";

// Re-export LanceDB and DuckDB production impls
export { LanceDbTextIndex } from "./adapters/lancedb.js";
export type { LanceDbTextIndexConfig, LanceDbSearchHit } from "./adapters/lancedb.js";
export { DuckDbAttributeIndex, DuckDbAggregateIndex } from "./adapters/duckdb.js";
export type { DuckDbIndexConfig } from "./adapters/duckdb.js";

// Embedding providers
// NOTE: transformers.js embedder is a pending implementation gap — re-add the
// export when 20-actors/mst-projector/src/embedders/transformers.ts lands.
export { createHuggingFaceEmbedder, HUGGINGFACE_PRESETS } from "./embedders/huggingface.js";
export type { HuggingFaceEmbeddingConfig } from "./embedders/huggingface.js";

import type {
  ProjectorConfig,
  ProjectorStatus,
  TextSearchOutput,
  TextSearchQuery,
  AttributeQueryOutput,
  AttributeQuery,
  AggregateQueryOutput,
  AggregateQuery,
} from "./types.js";

/**
 * MstProjector — indexed materialized view service.
 *
 * Responsibilities:
 * 1. Subscribe to PDS firehose (`com.atproto.sync.subscribeRepos`)
 * 2. Filter by configured actor DIDs and collection NSIDs
 * 3. Maintain indexes:
 *    - LanceDB: text search (IVF embedding vectors)
 *    - DuckDB: attribute inverted index + aggregate counts
 * 4. Serve queries: textSearch, attributeQuery, aggregateQuery
 * 5. Materialize results back to PDS as `com.etzhayyim.projector.<actor>View` records
 *
 * Storage: file-based (stateless, recoverable via PDS cursor replay)
 * Update latency SLA: p50 ≤1s, p99 ≤10s
 */
export class MstProjector {
  constructor(public config: ProjectorConfig) {}

  /**
   * Start the projector instance.
   * Connects to PDS firehose, resumes from last checkpoint (if exists),
   * and begins incremental index updates.
   *
   * Phase 3 implementation pending.
   * @throws Error("Phase 3 impl pending — see ADR-2605212000")
   */
  async start(): Promise<void> {
    throw new Error("Phase 3 impl pending — see ADR-2605212000");
  }

  /**
   * Stop the projector instance.
   * Closes firehose subscription and flushes pending index writes.
   * Phase 3 implementation pending.
   */
  async stop(): Promise<void> {
    // Phase 3 impl pending
  }

  /**
   * Get current projector status snapshot.
   * Returns subscription state, last update time, record counts per collection.
   * Phase 3 implementation pending.
   */
  async getStatus(): Promise<ProjectorStatus> {
    throw new Error("Phase 3 impl pending — see ADR-2605212000");
  }

  /**
   * Query text search index.
   * Returns records sorted by embedding similarity.
   * O(log N) via IVF indexing.
   *
   * Example:
   * ```ts
   * const results = await projector.queryTextSearch({
   *   collection: "com.etzhayyim.kiyo.paper",
   *   query: "machine learning",
   *   limit: 50
   * });
   * ```
   *
   * Phase 3 implementation pending.
   * @param params Text search query parameters
   * @returns Text search results with embedding scores
   */
  async queryTextSearch<T = Record<string, unknown>>(
    params: TextSearchQuery
  ): Promise<TextSearchOutput<T>> {
    throw new Error("Phase 3 impl pending — see ADR-2605212000");
  }

  /**
   * Query attribute inverted index.
   * Returns all records matching a specific attribute value.
   * O(1) hash table lookup.
   *
   * Example:
   * ```ts
   * const results = await projector.queryAttribute({
   *   collection: "com.etzhayyim.ipaddress.provider",
   *   attribute: "countryIso3",
   *   value: "US"
   * });
   * ```
   *
   * Phase 3 implementation pending.
   * @param params Attribute query parameters
   * @returns Records matching the attribute
   */
  async queryAttribute<T = Record<string, unknown>>(
    params: AttributeQuery
  ): Promise<AttributeQueryOutput<T>> {
    throw new Error("Phase 3 impl pending — see ADR-2605212000");
  }

  /**
   * Query pre-computed aggregate counts.
   * Returns counts grouped by a specified field.
   * O(1) disk cache lookup.
   *
   * Example:
   * ```ts
   * const results = await projector.queryAggregate({
   *   collection: "com.etzhayyim.kiyo.paper",
   *   groupBy: "status"
   * });
   * // Returns { counts: { "published": 1234, "draft": 567 }, total: 1801 }
   * ```
   *
   * Phase 3 implementation pending.
   * @param params Aggregate query parameters
   * @returns Counts keyed by group value
   */
  async queryAggregate(params: AggregateQuery): Promise<AggregateQueryOutput> {
    throw new Error("Phase 3 impl pending — see ADR-2605212000");
  }
}
