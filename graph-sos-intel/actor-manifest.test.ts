import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(
  readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"),
);

const VALID_PRIMITIVES = new Set([
  "graph.query",
  "graph.write",
  "graph.vectorSearch",
  "agent.chat",
  "agent.invoke",
  "identity.resolve",
  "browser.fetch",
  "signal.encrypt",
  "consent.check",
  "derive:social",
  "dmn.evaluate",
  "form.collect",
]);

describe("Graph SoS Intel Actor Manifest", () => {
  it("declares the canonical actor identity", () => {
    expect(manifest["@context"]).toBe("https://etzhayyim.com/ns/actor/v1");
    expect(manifest["@id"]).toBe("did:web:graph-sos-intel.etzhayyim.com");
    expect(manifest.name).toBe("graph-sos-intel");
    expect(manifest.nanoid).toBe("gs0s1nt7");
    expect(manifest.runtime).toBe("k8s-langserver");
  });

  it("uses only valid MCP primitives and no custom handler", () => {
    for (const capability of manifest.capabilities) {
      expect(VALID_PRIMITIVES.has(capability)).toBe(true);
    }
    for (const pipeline of manifest.pipelines) {
      for (const step of pipeline.steps) {
        expect(step.fn).not.toBe("custom");
        expect(VALID_PRIMITIVES.has(step.fn)).toBe(true);
      }
    }
  });

  it("exposes the graph intelligence runtime surfaces", () => {
    const cronSchedules = manifest.pipelines
      .filter((pipeline: any) => pipeline.trigger.type === "cron")
      .map((pipeline: any) => pipeline.trigger.cron);
    expect(cronSchedules).toContain("*/15 * * * *");
    expect(cronSchedules).toContain("0 */6 * * *");

    const nsids = manifest.pipelines
      .filter((pipeline: any) => pipeline.trigger.type === "xrpc")
      .map((pipeline: any) => pipeline.trigger.nsid);
    expect(nsids).toContain("com.etzhayyim.apps.graphSosIntel.health");
    expect(nsids).toContain("com.etzhayyim.apps.graphSosIntel.listRelations");
    expect(nsids).toContain("com.etzhayyim.apps.graphSosIntel.listFindings");
  });

  it("reads relation and index catalog inventories", () => {
    const sqlText = JSON.stringify(manifest.pipelines);
    expect(sqlText).toContain("information_schema.tables");
    expect(sqlText).toContain("pg_indexes");
  });

  it("persists snapshots without direct heavy DDL", () => {
    const sqlText = JSON.stringify(manifest.pipelines);
    expect(sqlText).toContain("vertex_graph_sos_intel_snapshot");
    expect(sqlText).not.toMatch(/\bCREATE\s+(INDEX|MATERIALIZED\s+VIEW|TABLE)\b/i);
    expect(sqlText).not.toMatch(/\bDROP\s+(INDEX|MATERIALIZED\s+VIEW|TABLE)\b/i);
    expect(sqlText).not.toMatch(/\bALTER\s+TABLE\b/i);
  });

  it("documents the no-heavy-DDL governance rule", () => {
    const ruleIds = manifest.governance.rules.map((rule: any) => rule.id);
    expect(ruleIds).toContain("RULE-GRAPH-SOS-NO-HEAVY-DDL");
  });
});
