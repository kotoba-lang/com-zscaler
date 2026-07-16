/**
 * mst-projector materialization tests.
 *
 * Verifies that Materializer writes records with expected rkeys
 * via a mock Etzhayyim SDK instance.
 */

import { describe, it, expect } from "vitest";
import { Materializer } from "../src/materialize.js";

/**
 * Mock Etzhayyim implementation for testing.
 *
 * Stores records in memory and supports basic read/write operations.
 */
class MockEtzhayyim {
  private collections = new Map<string, Map<string, Record<string, unknown>>>();

  constructor(public config: { did: string }) {}

  async write(params: {
    collection: string;
    record: Record<string, unknown>;
    rkey: string;
  }): Promise<{ uri: string }> {
    if (!this.collections.has(params.collection)) {
      this.collections.set(params.collection, new Map());
    }
    const col = this.collections.get(params.collection)!;
    col.set(params.rkey, params.record);
    const uri = `at://${this.config.did}/${params.collection}/${params.rkey}`;
    return { uri };
  }

  /** Helper: dump all records in a collection for assertion. */
  dump(collection: string): Array<[string, Record<string, unknown>]> {
    const col = this.collections.get(collection);
    return col ? Array.from(col.entries()) : [];
  }
}

describe("Materializer", () => {
  it("aggregate write lands at expected rkey", async () => {
    const e = new MockEtzhayyim({ did: "did:web:kiyo.etzhayyim.com" });
    const m = new Materializer(e as any);

    const r = await m.materializeAggregate({
      actorDid: "did:web:kiyo.etzhayyim.com",
      collection: "com.etzhayyim.kiyo.paper",
      groupBy: "status",
      counts: { published: 3, submitted: 1 },
      materializedAt: "2026-05-21T00:00:00Z",
    });

    expect(r.uri).toContain("agg-etzhayyim-kiyo-paper-status");
    const records = e.dump("com.etzhayyim.projector.aggregate");
    expect(records.length).toBe(1);
    expect(records[0][0]).toBe("agg-etzhayyim-kiyo-paper-status");
    expect(records[0][1].groupBy).toBe("status");
    expect(records[0][1].counts).toEqual({ published: 3, submitted: 1 });
  });

  it("text-search write lands at expected rkey", async () => {
    const e = new MockEtzhayyim({ did: "did:web:kiyo.etzhayyim.com" });
    const m = new Materializer(e as any);

    const r = await m.materializeTextSearch({
      actorDid: "did:web:kiyo.etzhayyim.com",
      collection: "com.etzhayyim.kiyo.paper",
      query: "bonsai myco yeast",
      resultRkeys: ["paper-1", "paper-2"],
      materializedAt: "2026-05-21T00:00:00Z",
    });

    expect(r.uri).toContain("txt-etzhayyim-kiyo-paper-");
    const records = e.dump("com.etzhayyim.projector.textSearch");
    expect(records.length).toBe(1);
    expect(records[0][1].resultRkeys).toEqual(["paper-1", "paper-2"]);
    expect(records[0][1].query).toBe("bonsai myco yeast");
  });

  it("multiple aggregates in same collection use distinct rkeys", async () => {
    const e = new MockEtzhayyim({ did: "did:web:kiyo.etzhayyim.com" });
    const m = new Materializer(e as any);

    await m.materializeAggregate({
      actorDid: "did:web:kiyo.etzhayyim.com",
      collection: "com.etzhayyim.kiyo.paper",
      groupBy: "status",
      counts: { published: 10 },
      materializedAt: "2026-05-21T00:00:00Z",
    });

    await m.materializeAggregate({
      actorDid: "did:web:kiyo.etzhayyim.com",
      collection: "com.etzhayyim.kiyo.paper",
      groupBy: "language",
      counts: { en: 7, ja: 3 },
      materializedAt: "2026-05-21T00:00:00Z",
    });

    const records = e.dump("com.etzhayyim.projector.aggregate");
    expect(records.length).toBe(2);
    expect(records[0][0]).toBe("agg-etzhayyim-kiyo-paper-status");
    expect(records[1][0]).toBe("agg-etzhayyim-kiyo-paper-language");
  });

  it("aggregates from different collections are keyed separately", async () => {
    const e = new MockEtzhayyim({ did: "did:web:kiyo.etzhayyim.com" });
    const m = new Materializer(e as any);

    await m.materializeAggregate({
      actorDid: "did:web:kiyo.etzhayyim.com",
      collection: "com.etzhayyim.kiyo.paper",
      groupBy: "status",
      counts: { published: 10 },
      materializedAt: "2026-05-21T00:00:00Z",
    });

    await m.materializeAggregate({
      actorDid: "did:web:kiyo.etzhayyim.com",
      collection: "com.etzhayyim.kiyo.review",
      groupBy: "status",
      counts: { approved: 5 },
      materializedAt: "2026-05-21T00:00:00Z",
    });

    const records = e.dump("com.etzhayyim.projector.aggregate");
    expect(records.length).toBe(2);
    // Both have same groupBy "status", but different collection slugs
    expect(records[0][0]).toBe("agg-etzhayyim-kiyo-paper-status");
    expect(records[1][0]).toBe("agg-etzhayyim-kiyo-review-status");
  });
});
