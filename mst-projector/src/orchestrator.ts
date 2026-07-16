/**
 * OrchestrationProjector — simplified in-memory projector backed by pure-TS indexes.
 *
 * This is a reference implementation that demonstrates Phase 3 semantics without
 * external dependencies. It wraps the in-memory indexes (InMemoryTextIndex,
 * InMemoryAttributeIndex, InMemoryAggregateIndex) and provides a single API
 * for processing commits and querying indexed data.
 *
 * Production deployments may swap the indexes for LanceDB / DuckDB while
 * maintaining API compatibility.
 */

import type { ProjectorConfig, CollectionProjection } from "./types.js";
import {
  InMemoryTextIndex,
  InMemoryAttributeIndex,
  InMemoryAggregateIndex,
} from "./inmemory.js";

export interface CommitEvent {
  collection: string;
  rkey: string;
  /** new record value, or undefined on delete */
  value?: Record<string, unknown>;
  /** previous record value, if known (for aggregate decrement) */
  previousValue?: Record<string, unknown>;
}

/**
 * In-memory orchestrator for indexed materialized views.
 *
 * Maintains 3 index types per collection per config:
 * - TextIndex (TF-lite scoring) for searchable collections
 * - AttributeIndex (inverted) for faceted navigation
 * - AggregateIndex (counts) for pre-computed stats
 */
export class OrchestrationProjector {
  private text = new Map<string, InMemoryTextIndex>();
  private attr = new Map<string, InMemoryAttributeIndex>();
  private agg = new Map<string, InMemoryAggregateIndex>();

  constructor(public config: ProjectorConfig) {
    for (const [name, proj] of Object.entries(config.collections)) {
      if (proj.textIndex) this.text.set(proj.collection, new InMemoryTextIndex());
      if (proj.attributes?.length) {
        this.attr.set(proj.collection, new InMemoryAttributeIndex());
      }
      if (proj.aggregates?.length) {
        this.agg.set(proj.collection, new InMemoryAggregateIndex());
      }
    }
  }

  /**
   * Process one commit event (create/update/delete).
   * Idempotent for the same (rkey, value) sequence.
   */
  async processCommit(event: CommitEvent): Promise<void> {
    const projForCollection = this.projectionFor(event.collection);
    if (!projForCollection) return;

    if (event.value === undefined) {
      // delete
      this.text.get(event.collection)?.delete(event.rkey);
      this.attr.get(event.collection)?.delete(event.rkey);
      if (event.previousValue && projForCollection.aggregates) {
        this.agg
          .get(event.collection)
          ?.delete(
            event.rkey,
            event.previousValue,
            projForCollection.aggregates,
          );
      }
      return;
    }

    // upsert (create or update)
    if (projForCollection.textIndex) {
      const fields: Record<string, string | undefined> = {};
      for (const f of projForCollection.textIndex.fields) {
        const v = event.value[f];
        if (typeof v === "string") fields[f] = v;
      }
      this.text.get(event.collection)?.upsert(event.rkey, fields);
    }

    if (projForCollection.attributes?.length) {
      const attrs: Record<string, unknown> = {};
      for (const a of projForCollection.attributes) attrs[a] = event.value[a];
      this.attr.get(event.collection)?.upsert(event.rkey, attrs);
    }

    if (projForCollection.aggregates?.length) {
      this.agg
        .get(event.collection)
        ?.upsert(
          event.rkey,
          event.previousValue,
          event.value,
          projForCollection.aggregates,
        );
    }
  }

  /**
   * Query text search across a collection.
   * Returns matching rkeys + scores + extracted field values.
   */
  async queryTextSearch(params: {
    collection: string;
    query: string;
    limit?: number;
  }): Promise<{ items: Array<{ rkey: string; score: number; fields: Record<string, string> }> }> {
    const idx = this.text.get(params.collection);
    if (!idx) return { items: [] };
    return { items: idx.search(params.query, params.limit ?? 20) };
  }

  /**
   * Query attribute inverted index.
   * Returns rkeys matching (attribute=value).
   */
  async queryAttribute(params: {
    collection: string;
    attribute: string;
    value: string;
    limit?: number;
  }): Promise<{ items: string[] }> {
    const idx = this.attr.get(params.collection);
    if (!idx) return { items: [] };
    return { items: idx.query(params.attribute, params.value, params.limit ?? 50) };
  }

  /**
   * Query aggregate counts.
   * Returns counts grouped by a specified field.
   */
  async queryAggregate(params: {
    collection: string;
    groupBy: string;
  }): Promise<{ counts: Record<string, number> }> {
    const idx = this.agg.get(params.collection);
    if (!idx) return { counts: {} };
    return { counts: idx.counts_for(params.groupBy) };
  }

  /**
   * Look up the projection config for a given collection.
   */
  private projectionFor(collection: string): CollectionProjection | undefined {
    for (const p of Object.values(this.config.collections)) {
      if (p.collection === collection) return p;
    }
    return undefined;
  }
}
