/**
 * mst-projector Phase 3 — types for indexed materialized views.
 *
 * Defines ProjectorConfig interface for declaring per-actor indexes,
 * and result types for text search, attribute lookup, and aggregate queries.
 *
 * Phase 3 implementation: LanceDB (vectors) + DuckDB (aggregates + inverted index).
 * Storage is file-based and stateless — recovery via PDS subscription cursor replay.
 */

/**
 * Per-actor configuration for indexed materialized views.
 * Declares which collections to project and which indexes to maintain.
 */
export interface ProjectorConfig {
  /** Actor DID (PDS identity, e.g., "did:web:kiyo.etzhayyim.com"). */
  actorDid: string;

  /** Collections to project, keyed by collection NSID. */
  collections: Record<string, CollectionProjection>;
}

/**
 * Per-collection projection configuration.
 * Declares text search, attribute inverted indexes, and aggregate breakdowns.
 */
export interface CollectionProjection {
  /** AT collection NSID (e.g., "com.etzhayyim.kiyo.paper"). */
  collection: string;

  /**
   * Text search index configuration.
   * If present, fields are concatenated and embedded into vector space.
   * Enables O(log N) similarity search via IVF indexing.
   * Null if collection is not searchable.
   */
  textIndex?: {
    /**
     * Record fields to concatenate for embedding.
     * Example: ["title", "abstract", "tags"] for kiyo papers.
     */
    fields: string[];

    /**
     * Embedding model identifier.
     * - "all-MiniLM-L6-v2" — 384-dim, 22M params, fast (recommended)
     * - "bge-large-en-v1.5" — 1024-dim, higher quality for domain-specific queries
     */
    model: "all-MiniLM-L6-v2" | "bge-large-en-v1.5";
  };

  /**
   * Inverted attribute indexes.
   * Each field creates an attribute → [record DID] mapping.
   * Enables O(1) lookup: "what records have status=published?"
   * Example: ["jurisdiction", "court"] for hanrei cases.
   */
  attributes?: string[];

  /**
   * Aggregate breakdown fields.
   * Each field generates pre-computed counts: group key → count.
   * Enables O(1) stats queries without scanning.
   * Example: ["status", "language", "field"] for kiyo papers.
   */
  aggregates?: string[];
}

/**
 * Text search query input.
 */
export interface TextSearchQuery {
  /** Collection NSID being searched. */
  collection: string;

  /** Query string (free text or embedding-compatible phrase). */
  query: string;

  /** Maximum results to return (default 100, max 1000). */
  limit?: number;

  /** Minimum embedding similarity score (0-1, default 0.5). */
  minScore?: number;
}

/**
 * Text search result item (includes embedding score).
 */
export interface TextSearchResult<T = Record<string, unknown>> {
  /** Record DID (at://<actor>/collection/rkey). */
  did: string;

  /** Record value (typed per collection). */
  record: T;

  /** Embedding similarity score (0-1). */
  score: number;
}

/**
 * Text search query output.
 */
export interface TextSearchOutput<T = Record<string, unknown>> {
  /** Matching records sorted by similarity (descending). */
  items: TextSearchResult<T>[];

  /** Whether result was truncated due to limit. */
  truncated: boolean;
}

/**
 * Attribute inverted index query input.
 */
export interface AttributeQuery {
  /** Collection NSID. */
  collection: string;

  /** Attribute field name (must be declared in ProjectorConfig). */
  attribute: string;

  /** Attribute value to match. */
  value: string;

  /** Maximum results (default 1000). */
  limit?: number;
}

/**
 * Attribute index query output.
 */
export interface AttributeQueryOutput<T = Record<string, unknown>> {
  /** Records matching the attribute filter. */
  items: T[];

  /** Whether result was truncated. */
  truncated: boolean;
}

/**
 * Aggregate counts query input.
 */
export interface AggregateQuery {
  /** Collection NSID. */
  collection: string;

  /** Field to group by (must be declared in aggregates[]).*/
  groupBy: string;
}

/**
 * Aggregate counts query output.
 */
export interface AggregateQueryOutput {
  /** Counts keyed by group value. */
  counts: Record<string, number>;

  /** Total records in collection. */
  total: number;
}

/**
 * Projector instance status snapshot.
 */
export interface ProjectorStatus {
  /** Actor DID. */
  actorDid: string;

  /** Last processed PDS subscription cursor. */
  lastCursor: string | null;

  /** Timestamp of last index update. */
  lastUpdateTime: number;

  /** Number of records indexed per collection. */
  recordCounts: Record<string, number>;

  /** Current subscription state (connecting, connected, error). */
  subscriptionState: "connecting" | "connected" | "error";

  /** Error message if subscriptionState === "error". */
  error?: string;
}
