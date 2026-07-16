import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const m = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));

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

const VALID_TRIGGER_TYPES = new Set(["cron", "subscribeRepos", "xrpc"]);

describe("Data Center Ops Actor Manifest", () => {
  it("@context valid", () => {
    expect(m["@context"]).toBe("https://etzhayyim.com/ns/actor/v1");
  });

  it("DID valid", () => {
    expect(m["@id"]).toBe("did:web:data-center-ops.etzhayyim.com");
  });

  it("name and nanoid", () => {
    expect(m.name).toBe("data-center-ops");
    expect(m.nanoid).toBe("dc0psmcp");
  });

  it("execution tier is T1", () => {
    expect(m.runtime).toBe("k8s-langserver");
  });

  it("capabilities are valid MCP primitives", () => {
    for (const cap of m.capabilities) {
      expect(VALID_PRIMITIVES.has(cap)).toBe(true);
    }
  });

  it("every step uses valid primitive and no fn:custom", () => {
    for (const pipeline of m.pipelines) {
      for (const step of pipeline.steps) {
        expect(step.fn).not.toBe("custom");
        expect(VALID_PRIMITIVES.has(step.fn)).toBe(true);
      }
    }
  });

  it("has 15 pipelines", () => {
    expect(m.pipelines).toHaveLength(15);
  });

  it("every pipeline has trigger and steps", () => {
    for (const pipeline of m.pipelines) {
      expect(VALID_TRIGGER_TYPES.has(pipeline.trigger.type)).toBe(true);
      expect(pipeline.steps.length).toBeGreaterThan(0);
    }
  });

  it("xrpc coverage endpoints are present", () => {
    const nsids = m.pipelines
      .filter((p: any) => p.trigger.type === "xrpc")
      .map((p: any) => p.trigger.nsid);

    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.infrastructure.getFacility");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.infrastructure.listFacilities");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.infrastructure.listRacks");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.infrastructure.getPowerZones");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.infrastructure.getSlaSummary");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.dependency.seedBaseline");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.dependency.collectGlobal");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.dependency.listNodes");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.dependency.listEdges");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.dependency.getReverseTopo");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.health");
    expect(nsids).toContain("com.etzhayyim.apps.dataCenterOps.coverage.get");
  });

  it("subscribeRepos pipeline tracks incident collection", () => {
    const sub = m.pipelines.find((p: any) => p.trigger.type === "subscribeRepos");
    expect(sub.trigger.collections).toContain("com.etzhayyim.apps.dataCenterOps.incident");
  });

  it("cron intelligence pipeline has report flow", () => {
    const cron = m.pipelines.find((p: any) => p.trigger.type === "cron" && p.trigger.cron === "0 */8 * * *");
    expect(cron.steps).toHaveLength(5);
    expect(cron.steps[0].id).toBe("facilityStats");
    expect(cron.steps[3].fn).toBe("agent.chat");
    expect(cron.steps[4].fn).toBe("derive:social");
  });

  it("reverse topo query exists and reads the RW materialized view", () => {
    const p = m.pipelines.find((x: any) => x.trigger.nsid === "com.etzhayyim.apps.dataCenterOps.dependency.getReverseTopo");
    expect(p).toBeTruthy();
    expect(p.steps[0].args.sql).toContain("mv_data_center_dependency_reverse_topology");
    expect(p.steps[0].args.sql).toContain("dependency_level DESC");
  });

  it("global collection query exists and reads the global RW materialized view", () => {
    const p = m.pipelines.find((x: any) => x.trigger.nsid === "com.etzhayyim.apps.dataCenterOps.dependency.collectGlobal");
    expect(p).toBeTruthy();
    expect(p.steps[0].args.sql).toContain("mv_data_center_dependency_global_actor");
    expect(p.steps[1].args.sql).toContain("country_code");
  });

  it("has 6 actor paths", () => {
    expect(m.actors).toHaveLength(6);
  });
});
