/**
 * Smoke tests for the 26-actor ProjectorConfig registry.
 *
 * Verifies every actor has a well-formed config (actorDid + at least one
 * collection + collection NSIDs match the actor slug).
 */

import { describe, expect, it } from "vitest";
import { ALL_PROJECTORS, getProjectorConfig } from "../src/configs.js";

describe("ALL_PROJECTORS", () => {
  it("registers 26 non-kiyo kotoba actors", () => {
    expect(Object.keys(ALL_PROJECTORS).length).toBe(26);
  });

  it("each actor has actorDid + at least one collection", () => {
    for (const [slug, config] of Object.entries(ALL_PROJECTORS)) {
      expect(config.actorDid, `${slug}: actorDid`).toMatch(
        /^did:web:[a-z][a-z0-9-]*\.etzhayyim\.com$/,
      );
      expect(
        Object.keys(config.collections).length,
        `${slug}: collections`,
      ).toBeGreaterThan(0);
    }
  });

  it("every collection has a non-empty NSID", () => {
    for (const [slug, config] of Object.entries(ALL_PROJECTORS)) {
      for (const [name, proj] of Object.entries(config.collections)) {
        expect(proj.collection, `${slug}/${name}: collection NSID`).toMatch(
          /^com\.etzhayyim\.\w+/,
        );
      }
    }
  });

  it("text-indexed collections name at least one field", () => {
    for (const [slug, config] of Object.entries(ALL_PROJECTORS)) {
      for (const [name, proj] of Object.entries(config.collections)) {
        if (proj.textIndex) {
          expect(
            proj.textIndex.fields.length,
            `${slug}/${name}: textIndex.fields`,
          ).toBeGreaterThan(0);
        }
      }
    }
  });

  it("getProjectorConfig returns the right entry by slug", () => {
    expect(getProjectorConfig("ipaddress")?.actorDid).toBe(
      "did:web:ipaddress.etzhayyim.com",
    );
    expect(getProjectorConfig("nonexistent")).toBeUndefined();
  });

  it("hanrei declares the three Phase E reference collections", () => {
    const cfg = ALL_PROJECTORS["hanrei"];
    const collections = Object.values(cfg.collections).map((c) => c.collection);
    expect(collections).toContain("com.etzhayyim.hanrei.case");
    expect(collections).toContain("com.etzhayyim.hanrei.law");
    expect(collections).toContain("com.etzhayyim.hanrei.gazetteEntry");
  });

  it("ipaddress declares provider + scan with countryIso3 facet", () => {
    const cfg = ALL_PROJECTORS["ipaddress"];
    const provider = Object.values(cfg.collections).find(
      (c) => c.collection === "com.etzhayyim.ipaddress.provider",
    );
    expect(provider?.attributes).toContain("countryIso3");
    expect(provider?.aggregates).toContain("countryIso3");
  });
});
