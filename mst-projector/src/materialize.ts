/**
 * mst-projector result materialization.
 *
 * Writes projector outputs back to PDS as records so clients can read
 * indexed results via the standard e.read() API without coupling to
 * LanceDB / DuckDB internals.
 *
 * Per ADR-2605212000 §"Materializes back to PDS".
 */

import type { Etzhayyim } from "@etzhayyim/sdk";

export interface MaterializedAggregateRecord {
  actorDid: string;
  collection: string;
  groupBy: string;
  counts: Record<string, number>;
  materializedAt: string;
}

export interface MaterializedTextSearchRecord {
  actorDid: string;
  collection: string;
  query: string;
  resultRkeys: string[];
  materializedAt: string;
}

/**
 * Write projector views to PDS so clients can read indexed results directly.
 */
export class Materializer {
  constructor(private e: Etzhayyim) {}

  async materializeAggregate(input: MaterializedAggregateRecord): Promise<{ uri: string }> {
    const rkey = `agg-${slug(input.collection)}-${slug(input.groupBy)}`;
    const receipt = await this.e.write({
      collection: "com.etzhayyim.projector.aggregate",
      record: input as unknown as Record<string, unknown>,
      rkey,
    });
    return { uri: receipt.uri };
  }

  async materializeTextSearch(input: MaterializedTextSearchRecord): Promise<{ uri: string }> {
    const rkey = `txt-${slug(input.collection)}-${slug(input.query).slice(0, 24)}`;
    const receipt = await this.e.write({
      collection: "com.etzhayyim.projector.textSearch",
      record: input as unknown as Record<string, unknown>,
      rkey,
    });
    return { uri: receipt.uri };
  }
}

function slug(s: string): string {
  return s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}
