/**
 * Vessel T1 Actor Manifest — Integration Tests
 * Validates manifest structure, pipeline definitions, capability whitelist,
 * and mirrors etzhayyim mitama validateActorManifest() validation.
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));

// etzhayyim mitama allowed MCP primitives (from mitama.go L176-181)
const VALID_PRIMITIVES = new Set([
  "graph.query", "graph.write", "graph.vectorSearch",
  "agent.chat", "agent.invoke", "identity.resolve",
  "browser.fetch", "signal.encrypt", "consent.check",
  "derive:social", "dmn.evaluate", "form.collect",
]);

const VALID_TRIGGER_TYPES = new Set(["cron", "subscribeRepos", "xrpc"]);

describe("Vessel Actor Manifest", () => {
  // --- Required fields (mitama.go validateActorManifest) ---

  it("has valid @context", () => {
    expect(manifest["@context"]).toBe("https://etzhayyim.com/ns/actor/v1");
  });

  it("has valid DID @id", () => {
    expect(manifest["@id"]).toMatch(/^did:/);
    expect(manifest["@id"]).toBe("did:web:vessel.etzhayyim.com");
  });

  it("has name", () => {
    expect(typeof manifest.name).toBe("string");
    expect(manifest.name).toBe("vessel");
  });

  it("has nanoid", () => {
    expect(typeof manifest.nanoid).toBe("string");
    expect(manifest.nanoid).toBe("v3ss3l01");
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

  it("has graph.query and graph.write capabilities", () => {
    expect(manifest.capabilities).toContain("graph.query");
    expect(manifest.capabilities).toContain("graph.write");
  });

  it("has derive:social for social posting", () => {
    expect(manifest.capabilities).toContain("derive:social");
  });

  it("has agent.invoke for cross-app cross-actor (vessel→port)", () => {
    expect(manifest.capabilities).toContain("agent.invoke");
  });

  // --- T1 constraint: no custom handlers ---

  it("no pipeline step uses fn:custom (T1 constraint)", () => {
    for (const pipeline of manifest.pipelines) {
      for (const step of pipeline.steps) {
        expect(step.fn).not.toBe("custom");
      }
    }
  });

  it("all pipeline step fns are valid MCP primitives", () => {
    const usedFns = new Set<string>();
    for (const pipeline of manifest.pipelines) {
      for (const step of pipeline.steps) {
        usedFns.add(step.fn);
        expect(VALID_PRIMITIVES.has(step.fn)).toBe(true);
      }
    }
    // Should use at least graph.query
    expect(usedFns.has("graph.query")).toBe(true);
  });

  // --- Pipelines ---

  it("has 14 pipelines", () => {
    expect(manifest.pipelines).toHaveLength(14);
  });

  it("every pipeline has trigger and steps", () => {
    for (const pipeline of manifest.pipelines) {
      expect(pipeline.trigger).toBeDefined();
      expect(pipeline.trigger.type).toBeDefined();
      expect(VALID_TRIGGER_TYPES.has(pipeline.trigger.type)).toBe(true);
      expect(Array.isArray(pipeline.steps)).toBe(true);
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

  describe("cron pipeline (fleet intelligence report)", () => {
    const cron = manifest.pipelines.find((p: any) => p.trigger.type === "cron");

    it("exists with valid cron expression", () => {
      expect(cron).toBeDefined();
      expect(cron.trigger.cron).toMatch(/^\d+\s/); // starts with minute field
    });

    it("has 5 steps: query→query→query→analyze→announce", () => {
      expect(cron.steps).toHaveLength(5);
      expect(cron.steps[0].fn).toBe("graph.query");
      expect(cron.steps[1].fn).toBe("graph.query");
      expect(cron.steps[2].fn).toBe("graph.query");
      expect(cron.steps[3].fn).toBe("agent.chat");
      expect(cron.steps[4].fn).toBe("derive:social");
    });

    it("agent.chat references prior step results", () => {
      const chatStep = cron.steps[3];
      expect(chatStep.args.message).toContain("$fleetStats.rows");
      expect(chatStep.args.message).toContain("$activeVoyages.rows");
    });

    it("derive:social references agent.chat output", () => {
      const socialStep = cron.steps[4];
      expect(socialStep.args.template).toContain("$analyze.text");
    });
  });

  describe("subscribeRepos pipelines", () => {
    const subPipelines = manifest.pipelines.filter((p: any) => p.trigger.type === "subscribeRepos");

    it("has 2 subscribeRepos pipelines (position + portCall)", () => {
      expect(subPipelines).toHaveLength(2);
    });

    it("position pipeline subscribes to vesselPosition collection", () => {
      const posPipeline = subPipelines.find((p: any) =>
        p.trigger.collections.includes("com.etzhayyim.apps.vessel.vesselPosition")
      );
      expect(posPipeline).toBeDefined();
    });

    it("portCall pipeline invokes port.etzhayyim.com via agent.invoke", () => {
      const callPipeline = subPipelines.find((p: any) =>
        p.trigger.collections.includes("com.etzhayyim.apps.vessel.portCall")
      );
      expect(callPipeline).toBeDefined();
      const invokeStep = callPipeline.steps.find((s: any) => s.fn === "agent.invoke");
      expect(invokeStep).toBeDefined();
      expect(invokeStep.args.did).toBe("did:web:port.etzhayyim.com");
    });
  });

  describe("xrpc pipelines", () => {
    const xrpcPipelines = manifest.pipelines.filter((p: any) => p.trigger.type === "xrpc");

    it("has 11 xrpc pipelines", () => {
      expect(xrpcPipelines).toHaveLength(11);
    });

    it("covers key vessel NSID endpoints", () => {
      const nsids = xrpcPipelines.map((p: any) => p.trigger.nsid);
      expect(nsids).toContain("com.etzhayyim.apps.vessel.registry.getShip");
      expect(nsids).toContain("com.etzhayyim.apps.vessel.registry.listShips");
      expect(nsids).toContain("com.etzhayyim.apps.vessel.registry.searchShips");
      expect(nsids).toContain("com.etzhayyim.apps.vessel.tracking.getVesselPosition");
      expect(nsids).toContain("com.etzhayyim.apps.vessel.tracking.listVesselsInArea");
      expect(nsids).toContain("com.etzhayyim.apps.vessel.voyage.getVesselChain");
      expect(nsids).toContain("com.etzhayyim.apps.vessel.getDashboard");
      expect(nsids).toContain("com.etzhayyim.apps.vessel.health");
    });

    it("getVesselChain has 5 parallel graph queries", () => {
      const chain = xrpcPipelines.find((p: any) => p.trigger.nsid === "com.etzhayyim.apps.vessel.voyage.getVesselChain");
      expect(chain.steps).toHaveLength(5);
      expect(chain.steps.every((s: any) => s.fn === "graph.query")).toBe(true);
    });

    it("all graph.query steps have sql field", () => {
      for (const pipeline of xrpcPipelines) {
        for (const step of pipeline.steps) {
          if (step.fn === "graph.query") {
            expect(typeof step.args.sql).toBe("string");
            expect(step.args.sql).toContain("MATCH");
          }
        }
      }
    });
  });

  // --- Triggers ---

  it("triggers.subscribeRepos covers vessel + cross-app collections", () => {
    const cols = manifest.triggers.subscribeRepos.collections;
    expect(cols).toContain("com.etzhayyim.apps.vessel.ship");
    expect(cols).toContain("com.etzhayyim.apps.vessel.vesselPosition");
    expect(cols).toContain("com.etzhayyim.apps.vessel.voyage");
    expect(cols).toContain("com.etzhayyim.apps.vessel.portCall");
    expect(cols).toContain("com.etzhayyim.apps.maps.port");         // cross-app
    expect(cols).toContain("com.etzhayyim.legalEntity.entity"); // cross-app
  });

  // --- Actors (Multi-DID) ---

  it("has 7 path-based actor DIDs", () => {
    expect(manifest.actors).toHaveLength(7);
  });

  it("actors cover container/tanker/bulk/lng/ais/voyage/chain segments", () => {
    const paths = manifest.actors.map((a: any) => a.path);
    expect(paths).toContain("registry:container");
    expect(paths).toContain("registry:tanker");
    expect(paths).toContain("registry:bulk");
    expect(paths).toContain("registry:lng");
    expect(paths).toContain("tracking:ais");
    expect(paths).toContain("voyage:manager");
    expect(paths).toContain("chain:intelligence");
  });

  it("every actor has path, displayName, description", () => {
    for (const actor of manifest.actors) {
      expect(typeof actor.path).toBe("string");
      expect(typeof actor.displayName).toBe("string");
      expect(typeof actor.description).toBe("string");
    }
  });

  // --- Profile ---

  it("profile has isBot:true and operator", () => {
    expect(manifest.profile.isBot).toBe(true);
    expect(manifest.profile.operator).toBe("etzhayyim.com");
  });

  // --- Governance ---

  it("governance has IMO compliance frameworks", () => {
    expect(manifest.governance.complianceFrameworks).toContain("IMO Conventions");
  });
});
