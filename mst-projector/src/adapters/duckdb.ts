/**
 * DuckDB-backed aggregate + attribute index — production replacement for
 * InMemoryAggregateIndex and InMemoryAttributeIndex.
 *
 * Install: pnpm add duckdb-async
 *
 * Dynamic import so absence falls back to in-memory.
 *
 * Per ADR-2605212000 §Phase 3b: DuckDB (production aggregate + attribute persistence).
 */

export interface DuckDbIndexConfig {
  dbPath: string;
  collection: string;
}

export class DuckDbAttributeIndex {
  readonly kind = "duckdb" as const;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private conn: any | null = null;

  constructor(private config: DuckDbIndexConfig) {}

  async init(): Promise<void> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let mod: any;
    try {
      // @ts-ignore — duckdb-async is an optional peer dep; falls back at runtime
      mod = await import(/* @vite-ignore */ "duckdb-async");
    } catch (err) {
      throw new Error(
        `DuckDbAttributeIndex: duckdb-async not installed. ` +
          `Run \`pnpm add duckdb-async\` or fall back to InMemoryAttributeIndex. ` +
          `Underlying error: ${(err as Error).message}`,
      );
    }

    const Database = mod.Database ?? mod.default?.Database;
    const db = await Database.create(this.config.dbPath);
    this.conn = await db.connect();

    const tbl = `attr_${this.tableName()}`;
    await this.conn.exec(
      `CREATE TABLE IF NOT EXISTS ${tbl} (rkey VARCHAR, attribute VARCHAR, value VARCHAR, PRIMARY KEY (rkey, attribute))`,
    );
    await this.conn.exec(
      `CREATE INDEX IF NOT EXISTS idx_attr_${this.tableName()}_val ON ${tbl} (attribute, value)`,
    );
  }

  async upsert(
    rkey: string,
    attributes: Record<string, unknown>,
  ): Promise<void> {
    if (!this.conn)
      throw new Error("DuckDbAttributeIndex.init() not called");

    const tbl = `attr_${this.tableName()}`;
    await this.conn.exec(`DELETE FROM ${tbl} WHERE rkey = ?`, [rkey]);

    for (const [attr, val] of Object.entries(attributes)) {
      if (val === undefined || val === null) continue;

      const valStr = Array.isArray(val) ? val.join(",") : String(val);
      await this.conn.exec(
        `INSERT INTO ${tbl} (rkey, attribute, value) VALUES (?, ?, ?)`,
        [rkey, attr, valStr],
      );
    }
  }

  async delete(rkey: string): Promise<void> {
    if (!this.conn) return;

    const tbl = `attr_${this.tableName()}`;
    await this.conn.exec(`DELETE FROM ${tbl} WHERE rkey = ?`, [rkey]);
  }

  async query(
    attribute: string,
    value: string,
    limit = 50,
  ): Promise<string[]> {
    if (!this.conn)
      throw new Error("DuckDbAttributeIndex.init() not called");

    const tbl = `attr_${this.tableName()}`;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const rows: any[] = await this.conn.all(
      `SELECT rkey FROM ${tbl} WHERE attribute = ? AND value = ? LIMIT ?`,
      [attribute, value, limit],
    );

    return rows.map((r) => r.rkey);
  }

  async close(): Promise<void> {
    this.conn = null;
  }

  private tableName(): string {
    return this.config.collection.replace(/[^a-zA-Z0-9]/g, "_");
  }
}

export class DuckDbAggregateIndex {
  readonly kind = "duckdb" as const;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private conn: any | null = null;

  constructor(private config: DuckDbIndexConfig) {}

  async init(): Promise<void> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let mod: any;
    try {
      // @ts-ignore — duckdb-async is an optional peer dep; falls back at runtime
      mod = await import(/* @vite-ignore */ "duckdb-async");
    } catch (err) {
      throw new Error(
        `DuckDbAggregateIndex: duckdb-async not installed. ` +
          `Run \`pnpm add duckdb-async\` or fall back to InMemoryAggregateIndex. ` +
          `Underlying error: ${(err as Error).message}`,
      );
    }

    const Database = mod.Database ?? mod.default?.Database;
    const db = await Database.create(this.config.dbPath);
    this.conn = await db.connect();

    const tbl = `agg_${this.tableName()}`;
    await this.conn.exec(
      `CREATE TABLE IF NOT EXISTS ${tbl} (rkey VARCHAR PRIMARY KEY, payload JSON)`,
    );
  }

  async upsert(
    rkey: string,
    _prev: Record<string, unknown> | undefined,
    next: Record<string, unknown>,
    _groupBy: string[],
  ): Promise<void> {
    if (!this.conn)
      throw new Error("DuckDbAggregateIndex.init() not called");

    const tbl = `agg_${this.tableName()}`;
    await this.conn.exec(`DELETE FROM ${tbl} WHERE rkey = ?`, [rkey]);
    await this.conn.exec(
      `INSERT INTO ${tbl} (rkey, payload) VALUES (?, ?)`,
      [rkey, JSON.stringify(next)],
    );
  }

  async delete(
    rkey: string,
    _prev: Record<string, unknown>,
    _groupBy: string[],
  ): Promise<void> {
    if (!this.conn) return;

    const tbl = `agg_${this.tableName()}`;
    await this.conn.exec(`DELETE FROM ${tbl} WHERE rkey = ?`, [rkey]);
  }

  async counts_for(groupBy: string): Promise<Record<string, number>> {
    if (!this.conn)
      throw new Error("DuckDbAggregateIndex.init() not called");

    const tbl = `agg_${this.tableName()}`;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const rows: any[] = await this.conn.all(
      `SELECT json_extract_string(payload, '$.${groupBy}') AS val, COUNT(*) AS cnt FROM ${tbl} GROUP BY val`,
    );

    const out: Record<string, number> = {};
    for (const r of rows) {
      if (r.val) out[r.val] = Number(r.cnt);
    }
    return out;
  }

  async close(): Promise<void> {
    this.conn = null;
  }

  private tableName(): string {
    return this.config.collection.replace(/[^a-zA-Z0-9]/g, "_");
  }
}
