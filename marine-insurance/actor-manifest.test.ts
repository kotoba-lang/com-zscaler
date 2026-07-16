import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const __dirname = dirname(fileURLToPath(import.meta.url));
const m = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));
const VP = new Set(["graph.query","graph.write","graph.vectorSearch","agent.chat","agent.invoke","identity.resolve","browser.fetch","signal.encrypt","consent.check","derive:social","dmn.evaluate","form.collect"]);

describe("Marine Insurance Actor Manifest", () => {
  it("@context valid", () => { expect(m["@context"]).toBe("https://etzhayyim.com/ns/actor/v1"); });
  it("DID valid", () => { expect(m["@id"]).toBe("did:web:marine-insurance.etzhayyim.com"); });
  it("runtime", () => { expect(m.runtime).toBe("k8s-langserver"); });
  it("nanoid", () => { expect(m.nanoid).toBe("m1ns0001"); });
  it("capabilities valid", () => { for (const c of m.capabilities) expect(VP.has(c)).toBe(true); });
  it("no fn:custom", () => { for (const p of m.pipelines) for (const s of p.steps) expect(s.fn).not.toBe("custom"); });
  it("8 pipelines", () => { expect(m.pipelines).toHaveLength(8); });
  it("every step has id/fn/args", () => { for (const p of m.pipelines) for (const s of p.steps) { expect(s.id).toBeDefined(); expect(s.fn).toBeDefined(); expect(s.args).toBeDefined(); } });
  it("weekly cron (Monday 08:00): portfolio→claims→expiring→analyze→social", () => {
    const cron = m.pipelines.find((p: any) => p.trigger.type === "cron");
    expect(cron.trigger.cron).toContain("8 * * 1"); // Monday 08:00
    expect(cron.steps).toHaveLength(5);
    expect(cron.steps[2].id).toBe("expiringPolicies");
  });
  it("subscribes to vessel.ship for coverage check", () => {
    const sub = m.pipelines.find((p: any) => p.trigger.type === "subscribeRepos");
    expect(sub.trigger.collections).toContain("com.etzhayyim.apps.vessel.ship");
  });
  it("vesselCoverage has 3 queries (policies + P&I + claims)", () => {
    const vc = m.pipelines.find((p: any) => p.trigger?.nsid?.includes("getVesselCoverage"));
    expect(vc.steps).toHaveLength(3);
    expect(vc.steps.map((s: any) => s.id)).toEqual(["policies", "piEntries", "openClaims"]);
  });
  it("xrpc covers policies, claims, P&I, vessel coverage", () => {
    const nsids = m.pipelines.filter((p: any) => p.trigger.type === "xrpc").map((p: any) => p.trigger.nsid);
    expect(nsids).toContain("com.etzhayyim.apps.marineInsurance.underwriting.getPolicy");
    expect(nsids).toContain("com.etzhayyim.apps.marineInsurance.underwriting.listClaims");
    expect(nsids).toContain("com.etzhayyim.apps.marineInsurance.underwriting.listPiEntries");
    expect(nsids).toContain("com.etzhayyim.apps.marineInsurance.underwriting.getVesselCoverage");
  });
  it("6 actors (H&M, P&I, cargo, war, GA, claims)", () => { expect(m.actors).toHaveLength(6); });
  it("York-Antwerp Rules compliance", () => { expect(m.governance.complianceFrameworks).toContain("York-Antwerp Rules"); });
  it("isBot true", () => { expect(m.profile.isBot).toBe(true); });
});
