import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));

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

describe("Datacenter Actor Manifest", () => {
  it("has valid @context and DID", () => {
    expect(manifest["@context"]).toBe("https://etzhayyim.com/ns/actor/v1");
    expect(manifest["@id"]).toBe("did:web:infra.etzhayyim.com:datacenter");
  });

  it("is a T1 service actor", () => {
    expect(manifest.name).toBe("datacenter");
    expect(manifest.nanoid).toBe("d7c3n7r0");
    expect(manifest.runtime).toBe("k8s-langserver");
    expect(manifest.performerType).toBe("service");
    expect(manifest.parentDid).toBe("did:web:infra.etzhayyim.com");
  });

  it("uses only valid capabilities", () => {
    for (const cap of manifest.capabilities) {
      expect(VALID_PRIMITIVES.has(cap)).toBe(true);
    }
    expect(manifest.capabilities).toContain("graph.query");
    expect(manifest.capabilities).toContain("graph.write");
    expect(manifest.capabilities).toContain("agent.invoke");
  });

  it("declares xrpc triggers for all datacenter NSIDs", () => {
    const procedures = manifest.triggers.xrpc.procedures;
    const queries = manifest.triggers.xrpc.queries;

    expect(procedures).toContain("com.etzhayyim.apps.datacenter.startOperation");
    expect(procedures).toContain("com.etzhayyim.apps.datacenter.requestAccess");
    expect(procedures).toContain("com.etzhayyim.apps.datacenter.reserveCapacity");
    expect(procedures).toContain("com.etzhayyim.apps.datacenter.purgeAccessPii");
    expect(queries).toContain("com.etzhayyim.apps.datacenter.getOperation");
    expect(queries).toContain("com.etzhayyim.apps.datacenter.getMyAccessRequest");
    expect(queries).toContain("com.etzhayyim.apps.datacenter.listAccessForFacility");
  });

  it("defines sub-actors for routing, review, stabilization, access, and capacity", () => {
    expect(manifest.actors).toHaveLength(5);
    const paths = manifest.actors.map((a: any) => a.path);
    expect(paths).toContain("actor:intake-router");
    expect(paths).toContain("actor:change-review");
    expect(paths).toContain("actor:incident-stabilizer");
    expect(paths).toContain("actor:physical-access-review");
    expect(paths).toContain("actor:capacity-planner");
  });

  it("contains governance rules for approval, health check, and audit", () => {
    const ruleIds = manifest.governance.rules.map((r: any) => r.id);
    expect(ruleIds).toContain("RULE-DATACENTER-APPROVAL");
    expect(ruleIds).toContain("RULE-DATACENTER-HEALTHCHECK");
    expect(ruleIds).toContain("RULE-DATACENTER-AUDIT");
  });
});
