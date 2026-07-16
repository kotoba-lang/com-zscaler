/**
 * Unit tests for OrchestrationProjector (Phase 3 reference implementation).
 *
 * Exercises all three index types (text, attribute, aggregate) under
 * create/update/delete sequences, ensuring idempotency and correctness.
 */

import { describe, it, expect, beforeEach } from "vitest";
import { OrchestrationProjector } from "../src/orchestrator.js";
import { kiyoProjector } from "../src/kiyo-config.js";

describe("OrchestrationProjector — kiyo config", () => {
  let projector: OrchestrationProjector;

  beforeEach(() => {
    projector = new OrchestrationProjector(kiyoProjector);
  });

  describe("text search", () => {
    it("matches papers by title keyword", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "paper-1",
        value: {
          title: "Bonsai cultivar layer above myco-yeast",
          status: "published",
          language: "en",
          field: "computer-science",
        },
      });
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "paper-2",
        value: {
          title: "RisingWave cutover plan",
          status: "published",
          language: "en",
          field: "infrastructure",
        },
      });

      const out = await projector.queryTextSearch({
        collection: "com.etzhayyim.kiyo.paper",
        query: "bonsai myco",
      });

      expect(out.items).toHaveLength(1);
      expect(out.items[0].rkey).toBe("paper-1");
      expect(out.items[0].score).toBeGreaterThan(0);
      expect(out.items[0].fields.title).toContain("Bonsai");
    });

    it("respects limit parameter", async () => {
      for (let i = 0; i < 5; i++) {
        await projector.processCommit({
          collection: "com.etzhayyim.kiyo.paper",
          rkey: `paper-${i}`,
          value: { title: `Machine learning paper ${i}`, status: "published" },
        });
      }

      const out = await projector.queryTextSearch({
        collection: "com.etzhayyim.kiyo.paper",
        query: "machine",
        limit: 2,
      });

      expect(out.items.length).toBeLessThanOrEqual(2);
    });

    it("returns empty for nonexistent query", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "paper-1",
        value: { title: "Foo bar", status: "published" },
      });

      const out = await projector.queryTextSearch({
        collection: "com.etzhayyim.kiyo.paper",
        query: "nonexistent",
      });

      expect(out.items).toHaveLength(0);
    });
  });

  describe("attribute query", () => {
    it("filters papers by field", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "paper-1",
        value: { title: "x", status: "published", field: "cs" },
      });
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "paper-2",
        value: { title: "y", status: "published", field: "physics" },
      });

      const out = await projector.queryAttribute({
        collection: "com.etzhayyim.kiyo.paper",
        attribute: "field",
        value: "cs",
      });

      expect(out.items).toContain("paper-1");
      expect(out.items).not.toContain("paper-2");
    });

    it("filters papers by status", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "paper-1",
        value: { title: "x", status: "published", field: "cs" },
      });
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "paper-2",
        value: { title: "y", status: "draft", field: "cs" },
      });

      const out = await projector.queryAttribute({
        collection: "com.etzhayyim.kiyo.paper",
        attribute: "status",
        value: "draft",
      });

      expect(out.items).toEqual(["paper-2"]);
    });

    it("respects limit parameter", async () => {
      for (let i = 0; i < 10; i++) {
        await projector.processCommit({
          collection: "com.etzhayyim.kiyo.paper",
          rkey: `paper-${i}`,
          value: { title: `x${i}`, status: "published", field: "cs" },
        });
      }

      const out = await projector.queryAttribute({
        collection: "com.etzhayyim.kiyo.paper",
        attribute: "status",
        value: "published",
        limit: 5,
      });

      expect(out.items.length).toBeLessThanOrEqual(5);
    });

    it("returns empty for nonexistent value", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "paper-1",
        value: { title: "x", status: "published", field: "cs" },
      });

      const out = await projector.queryAttribute({
        collection: "com.etzhayyim.kiyo.paper",
        attribute: "field",
        value: "nonexistent-field",
      });

      expect(out.items).toHaveLength(0);
    });
  });

  describe("aggregate query", () => {
    it("counts papers by status", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "a", status: "published" },
      });
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p2",
        value: { title: "b", status: "published" },
      });
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p3",
        value: { title: "c", status: "submitted" },
      });

      const out = await projector.queryAggregate({
        collection: "com.etzhayyim.kiyo.paper",
        groupBy: "status",
      });

      expect(out.counts.published).toBe(2);
      expect(out.counts.submitted).toBe(1);
    });

    it("counts papers by language", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "a", language: "en" },
      });
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p2",
        value: { title: "b", language: "ja" },
      });
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p3",
        value: { title: "c", language: "en" },
      });

      const out = await projector.queryAggregate({
        collection: "com.etzhayyim.kiyo.paper",
        groupBy: "language",
      });

      expect(out.counts.en).toBe(2);
      expect(out.counts.ja).toBe(1);
    });

    it("decrements on status transition (update with previousValue)", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "a", status: "submitted" },
      });

      let counts = (
        await projector.queryAggregate({
          collection: "com.etzhayyim.kiyo.paper",
          groupBy: "status",
        })
      ).counts;
      expect(counts.submitted).toBe(1);

      // Update to published
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "a", status: "published" },
        previousValue: { title: "a", status: "submitted" },
      });

      counts = (
        await projector.queryAggregate({
          collection: "com.etzhayyim.kiyo.paper",
          groupBy: "status",
        })
      ).counts;

      expect(counts.submitted ?? 0).toBe(0);
      expect(counts.published).toBe(1);
    });

    it("handles multi-field aggregates", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "a", status: "published", language: "en", field: "cs" },
      });
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p2",
        value: { title: "b", status: "published", language: "ja", field: "physics" },
      });

      const byField = await projector.queryAggregate({
        collection: "com.etzhayyim.kiyo.paper",
        groupBy: "field",
      });

      expect(byField.counts.cs).toBe(1);
      expect(byField.counts.physics).toBe(1);
    });
  });

  describe("delete operations", () => {
    it("removes from text index on delete", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "ghost paper", status: "published" },
      });

      let out = await projector.queryTextSearch({
        collection: "com.etzhayyim.kiyo.paper",
        query: "ghost",
      });
      expect(out.items).toHaveLength(1);

      // Delete
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        previousValue: { title: "ghost paper", status: "published" },
      });

      out = await projector.queryTextSearch({
        collection: "com.etzhayyim.kiyo.paper",
        query: "ghost",
      });
      expect(out.items).toHaveLength(0);
    });

    it("removes from attribute index on delete", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "x", status: "published", field: "cs" },
      });

      let out = await projector.queryAttribute({
        collection: "com.etzhayyim.kiyo.paper",
        attribute: "status",
        value: "published",
      });
      expect(out.items).toContain("p1");

      // Delete
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        previousValue: { title: "x", status: "published", field: "cs" },
      });

      out = await projector.queryAttribute({
        collection: "com.etzhayyim.kiyo.paper",
        attribute: "status",
        value: "published",
      });
      expect(out.items).not.toContain("p1");
    });

    it("decrements aggregates on delete", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "x", status: "published" },
      });

      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        previousValue: { title: "x", status: "published" },
      });

      const counts = (
        await projector.queryAggregate({
          collection: "com.etzhayyim.kiyo.paper",
          groupBy: "status",
        })
      ).counts;

      expect(counts.published ?? 0).toBe(0);
    });
  });

  describe("idempotency", () => {
    it("duplicate creates are idempotent", async () => {
      const commit = {
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "test", status: "published" },
      };

      await projector.processCommit(commit);
      await projector.processCommit(commit);

      const counts = (
        await projector.queryAggregate({
          collection: "com.etzhayyim.kiyo.paper",
          groupBy: "status",
        })
      ).counts;

      // Duplicate upsert should only result in 1 record
      expect(counts.published).toBe(1);
    });

    it("duplicate deletes are idempotent", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "test", status: "published" },
      });

      const deleteCommit = {
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        previousValue: { title: "test", status: "published" },
      };

      await projector.processCommit(deleteCommit);
      await projector.processCommit(deleteCommit);

      const counts = (
        await projector.queryAggregate({
          collection: "com.etzhayyim.kiyo.paper",
          groupBy: "status",
        })
      ).counts;

      expect(counts.published ?? 0).toBe(0);
    });
  });

  describe("cross-collection isolation", () => {
    it("ignores events for unconfigured collections", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.unknown.collection",
        rkey: "x",
        value: { title: "foo" },
      });

      const out = await projector.queryAggregate({
        collection: "com.etzhayyim.kiyo.paper",
        groupBy: "status",
      });

      expect(Object.keys(out.counts)).toHaveLength(0);
    });

    it("handles review collection separately", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.review",
        rkey: "review-1",
        value: { conclusion: "Good paper", status: "approved" },
      });

      // Papers and reviews are separate collections
      const paperCounts = (
        await projector.queryAggregate({
          collection: "com.etzhayyim.kiyo.paper",
          groupBy: "status",
        })
      ).counts;

      const reviewCounts = (
        await projector.queryAggregate({
          collection: "com.etzhayyim.kiyo.review",
          groupBy: "status",
        })
      ).counts;

      expect(Object.keys(paperCounts)).toHaveLength(0);
      expect(reviewCounts.approved).toBe(1);
    });
  });

  describe("edge cases", () => {
    it("handles undefined/null fields gracefully", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: {
          title: "test",
          status: "published",
          field: undefined,
          language: null,
        },
      });

      const out = await projector.queryAttribute({
        collection: "com.etzhayyim.kiyo.paper",
        attribute: "field",
        value: "undefined",
      });

      // undefined should not be indexed
      expect(out.items).toHaveLength(0);
    });

    it("handles empty queries safely", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "test", status: "published" },
      });

      const out = await projector.queryTextSearch({
        collection: "com.etzhayyim.kiyo.paper",
        query: "   ",
      });

      // Empty/whitespace query should return no results
      expect(out.items).toHaveLength(0);
    });

    it("supports CJK text search", async () => {
      await projector.processCommit({
        collection: "com.etzhayyim.kiyo.paper",
        rkey: "p1",
        value: { title: "日本語のタイトル", titleLocal: "Japanese Title" },
      });

      const out = await projector.queryTextSearch({
        collection: "com.etzhayyim.kiyo.paper",
        query: "日本語",
      });

      expect(out.items).toHaveLength(1);
      expect(out.items[0].rkey).toBe("p1");
    });
  });
});
