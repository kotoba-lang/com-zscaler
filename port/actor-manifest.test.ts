/**
 * Port T1 Actor Manifest — Integration Tests
 * Validates manifest structure, pipeline definitions, capability whitelist.
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));

const VALID_PRIMITIVES = new Set([
  "graph.query", "graph.write", "graph.vectorSearch",
  "agent.chat", "agent.invoke", "identity.resolve",
  "browser.fetch", "signal.encrypt", "consent.check",
  "derive:social", "dmn.evaluate", "form.collect",
]);

const VALID_TRIGGER_TYPES = new Set(["cron", "subscribeRepos", "xrpc"]);

describe("Port Actor Manifest", () => {
  // --- Required fields ---

  it("has valid @context", () => {
    expect(manifest["@context"]).toBe("https://etzhayyim.com/ns/actor/v1");
  });

  it("has valid DID @id", () => {
    expect(manifest["@id"]).toBe("did:web:port.etzhayyim.com");
  });

  it("has name and nanoid", () => {
    expect(manifest.name).toBe("port");
    expect(manifest.nanoid).toBe("p0rt7890");
  });

  it("runtime is k8s-langserver", () => {
    expect(manifest.runtime).toBe("k8s-langserver");
  });

  // --- Capabilities ---

  it("capabilities are all valid MCP primitives", () => {
    for (const cap of manifest.capabilities) {
      expect(VALID_PRIMITIVES.has(cap)).toBe(true);
    }
  });

  it("has graph.query, graph.write, derive:social", () => {
    expect(manifest.capabilities).toContain("graph.query");
    expect(manifest.capabilities).toContain("graph.write");
    expect(manifest.capabilities).toContain("derive:social");
  });

  // --- T1 constraint ---

  it("no pipeline step uses fn:custom", () => {
    for (const pipeline of manifest.pipelines) {
      for (const step of pipeline.steps) {
        expect(step.fn).not.toBe("custom");
      }
    }
  });

  it("all step fns are valid MCP primitives", () => {
    for (const pipeline of manifest.pipelines) {
      for (const step of pipeline.steps) {
        expect(VALID_PRIMITIVES.has(step.fn)).toBe(true);
      }
    }
  });

  // --- Pipelines ---

  it("has 11 pipelines", () => {
    expect(manifest.pipelines).toHaveLength(11);
  });

  it("every pipeline has trigger and steps", () => {
    for (const pipeline of manifest.pipelines) {
      expect(VALID_TRIGGER_TYPES.has(pipeline.trigger.type)).toBe(true);
      expect(pipeline.steps.length).toBeGreaterThan(0);
    }
  });

  it("every step has id, fn, args", () => {
    for (const pipeline of manifest.pipelines) {
      for (const step of pipeline.steps) {
        expect(typeof step.id).toBe("string");
        expect(typeof step.fn).toBe("string");
        expect(step.args).toBeDefined();
      }
    }
  });

  describe("cron pipeline (port intelligence)", () => {
    const cron = manifest.pipelines.find((p: any) => p.trigger.type === "cron");

    it("has 4 steps: query→query→analyze→announce", () => {
      expect(cron.steps).toHaveLength(4);
      expect(cron.steps[0].fn).toBe("graph.query");
      expect(cron.steps[1].fn).toBe("graph.query");
      expect(cron.steps[2].fn).toBe("agent.chat");
      expect(cron.steps[3].fn).toBe("derive:social");
    });

    it("agent.chat references portStats and berthOccupancy", () => {
      expect(cron.steps[2].args.message).toContain("$portStats.rows");
      expect(cron.steps[2].args.message).toContain("$berthOccupancy.rows");
    });
  });

  describe("subscribeRepos pipeline (vessel portCall → event)", () => {
    const sub = manifest.pipelines.find((p: any) => p.trigger.type === "subscribeRepos");

    it("subscribes to vessel.portCall", () => {
      expect(sub.trigger.collections).toContain("com.etzhayyim.apps.vessel.portCall");
    });

    it("writes PortCallEvent via graph.write", () => {
      expect(sub.steps[0].fn).toBe("graph.write");
      expect(sub.steps[0].args.template).toContain("PortCallEvent");
    });
  });

  describe("xrpc pipelines", () => {
    const xrpcPipelines = manifest.pipelines.filter((p: any) => p.trigger.type === "xrpc");

    it("has 9 xrpc pipelines", () => {
      expect(xrpcPipelines).toHaveLength(9);
    });

    it("covers port CRUD endpoints", () => {
      const nsids = xrpcPipelines.map((p: any) => p.trigger.nsid);
      expect(nsids).toContain("com.etzhayyim.apps.port.infrastructure.getPort");
      expect(nsids).toContain("com.etzhayyim.apps.port.infrastructure.listPorts");
      expect(nsids).toContain("com.etzhayyim.apps.port.infrastructure.searchPorts");
      expect(nsids).toContain("com.etzhayyim.apps.port.infrastructure.getPortBerths");
      expect(nsids).toContain("com.etzhayyim.apps.port.infrastructure.getPortTerminals");
    });

    it("covers port call tracking endpoints", () => {
      const nsids = xrpcPipelines.map((p: any) => p.trigger.nsid);
      expect(nsids).toContain("com.etzhayyim.apps.port.portCallTracking.getVesselsAtPort");
      expect(nsids).toContain("com.etzhayyim.apps.port.portCallTracking.getPortOccupancy");
    });

    it("getPortOccupancy has 3 parallel queries (berthed/approaching/totalBerths)", () => {
      const occ = xrpcPipelines.find((p: any) => p.trigger.nsid.includes("getPortOccupancy"));
      expect(occ.steps).toHaveLength(3);
      expect(occ.steps.map((s: any) => s.id)).toEqual(["berthed", "approaching", "totalBerths"]);
    });

    it("all graph.query steps have MATCH sql", () => {
      for (const pipeline of xrpcPipelines) {
        for (const step of pipeline.steps) {
          if (step.fn === "graph.query") {
            expect(step.args.sql).toContain("MATCH");
          }
        }
      }
    });
  });

  // --- Triggers ---

  it("triggers.subscribeRepos covers port + vessel cross-app", () => {
    const cols = manifest.triggers.subscribeRepos.collections;
    expect(cols).toContain("com.etzhayyim.apps.port.port");
    expect(cols).toContain("com.etzhayyim.apps.port.berth");
    expect(cols).toContain("com.etzhayyim.apps.port.terminal");
    expect(cols).toContain("com.etzhayyim.apps.vessel.portCall"); // cross-app
  });

  // --- Actors ---

  it("has 7 regional/type actor DIDs", () => {
    expect(manifest.actors).toHaveLength(7);
  });

  it("actors cover asia/europe/americas/mideast/japan + tanker/bulk types", () => {
    const paths = manifest.actors.map((a: any) => a.path);
    expect(paths).toContain("region:asia");
    expect(paths).toContain("region:europe");
    expect(paths).toContain("region:americas");
    expect(paths).toContain("region:mideast");
    expect(paths).toContain("region:japan");
    expect(paths).toContain("type:tanker");
    expect(paths).toContain("type:bulk");
  });

  it("Japan actor lists 5 JP ports", () => {
    const jp = manifest.actors.find((a: any) => a.path === "region:japan");
    expect(jp.description).toContain("Yokohama");
    expect(jp.description).toContain("Tokyo");
    expect(jp.description).toContain("Kobe");
    expect(jp.description).toContain("Osaka");
    expect(jp.description).toContain("Nagoya");
  });

  // --- Profile ---

  it("profile has isBot:true", () => {
    expect(manifest.profile.isBot).toBe(true);
  });

  // --- Governance ---

  it("governance has ISPS Code compliance", () => {
    expect(manifest.governance.complianceFrameworks).toContain("ISPS Code");
  });
});
