/**
 * LanceDB-backed text index — production replacement for InMemoryTextIndex.
 *
 * Install: pnpm add @lancedb/lancedb
 *
 * This file imports @lancedb/lancedb dynamically so the package can be
 * absent without breaking the rest of the projector. When absent, the
 * orchestrator falls back to InMemoryTextIndex.
 *
 * Per ADR-2605212000 §Phase 3c: LanceDB (production IVF vector text search).
 */

import type { EmbeddingProvider } from "../adapters.js";

export interface LanceDbTextIndexConfig {
  /** LanceDB database path. */
  dbPath: string;
  /** Table name (one per collection). */
  tableName: string;
  /** Embedding vector dimension (e.g. 384 for all-MiniLM-L6-v2). */
  vectorDim: number;
  /** Embedding provider for text → vector. */
  embedder: EmbeddingProvider;
}

export interface LanceDbSearchHit {
  rkey: string;
  score: number;
  fields: Record<string, string>;
}

export class LanceDbTextIndex {
  readonly kind = "lancedb" as const;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private db: any | null = null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private table: any | null = null;

  constructor(private config: LanceDbTextIndexConfig) {}

  async init(): Promise<void> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let mod: any;
    try {
      // @ts-ignore — @lancedb/lancedb is an optional peer dep; falls back at runtime
      mod = await import(/* @vite-ignore */ "@lancedb/lancedb");
    } catch (err) {
      throw new Error(
        `LanceDbTextIndex: @lancedb/lancedb not installed. ` +
          `Run \`pnpm add @lancedb/lancedb\` or fall back to InMemoryTextIndex. ` +
          `Underlying error: ${(err as Error).message}`,
      );
    }

    const lancedb = mod.connect ?? mod.default?.connect;
    if (!lancedb) throw new Error("@lancedb/lancedb: connect() export not found");

    this.db = await lancedb(this.config.dbPath);
    const tables: string[] = await this.db.tableNames();

    if (tables.includes(this.config.tableName)) {
      this.table = await this.db.openTable(this.config.tableName);
    } else {
      // Create empty table with schema.
      this.table = await this.db.createTable(
        this.config.tableName,
        [
          {
            rkey: "__init__",
            vector: new Array(this.config.vectorDim).fill(0),
            fields: "{}",
          },
        ],
      );
      // Delete the seed row.
      await this.table.delete("rkey = '__init__'");
    }
  }

  async upsert(
    rkey: string,
    fields: Record<string, string | undefined>,
  ): Promise<void> {
    if (!this.table) throw new Error("LanceDbTextIndex.init() not called");

    const text = Object.values(fields)
      .filter((v): v is string => !!v)
      .join(" ");
    if (!text) return;

    const vector = Array.from(
      await this.config.embedder.embed(text),
    ) as number[];
    const payload = {
      rkey,
      vector,
      fields: JSON.stringify(fields),
    };

    // Delete existing then add (LanceDB doesn't have a native upsert in all versions).
    await this.table.delete(`rkey = '${escapeSql(rkey)}'`);
    await this.table.add([payload]);
  }

  async delete(rkey: string): Promise<void> {
    if (!this.table) return;
    await this.table.delete(`rkey = '${escapeSql(rkey)}'`);
  }

  async search(
    query: string,
    limit = 20,
  ): Promise<LanceDbSearchHit[]> {
    if (!this.table) throw new Error("LanceDbTextIndex.init() not called");

    const qVector = Array.from(
      await this.config.embedder.embed(query),
    ) as number[];
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const results: any[] = await this.table
      .search(qVector)
      .limit(limit)
      .toArray();

    return results.map((r) => ({
      rkey: r.rkey,
      score: 1 - (r._distance ?? 0), // L2 distance → similarity
      fields: r.fields ? JSON.parse(r.fields as string) : {},
    }));
  }

  async close(): Promise<void> {
    this.table = null;
    this.db = null;
  }
}

function escapeSql(s: string): string {
  return s.replace(/'/g, "''");
}
