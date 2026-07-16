import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const __dirname = dirname(fileURLToPath(import.meta.url));
const m = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));
const VP = new Set(["graph.query","graph.write","graph.vectorSearch","agent.chat","agent.invoke","identity.resolve","browser.fetch","signal.encrypt","consent.check","derive:social","dmn.evaluate","form.collect"]);

describe("Bunker Actor Manifest", () => {
  it("@context valid", () => { expect(m["@context"]).toBe("https://etzhayyim.com/ns/actor/v1"); });
  it("DID valid", () => { expect(m["@id"]).toBe("did:web:bunker.etzhayyim.com"); });
  it("runtime", () => { expect(m.runtime).toBe("k8s-langserver"); });
  it("nanoid", () => { expect(m.nanoid).toBe("bnkr0001"); });
  it("capabilities valid", () => { for (const c of m.capabilities) expect(VP.has(c)).toBe(true); });
  it("no fn:custom", () => { for (const p of m.pipelines) for (const s of p.steps) expect(s.fn).not.toBe("custom"); });
  it("8 pipelines", () => { expect(m.pipelines).toHaveLength(8); });
  it("every step has id/fn/args", () => { for (const p of m.pipelines) for (const s of p.steps) { expect(s.id).toBeDefined(); expect(s.fn).toBeDefined(); expect(s.args).toBeDefined(); } });
  it("12h cron: deliveries→failed samples→emissions→analyze→social", () => {
    const cron = m.pipelines.find((p: any) => p.trigger.type === "cron");
    expect(cron.steps).toHaveLength(5);
    expect(cron.steps[1].id).toBe("failedSamples");
    expect(cron.steps[2].id).toBe("emissions");
  });
  it("subscribes to bunker.fuelSample for compliance check", () => {
    const sub = m.pipelines.find((p: any) => p.trigger.type === "subscribeRepos");
    expect(sub.trigger.collections).toContain("com.etzhayyim.apps.bunker.fuelSample");
  });
  it("MARPOL compliance check has 2 queries (deliveries + failed samples)", () => {
    const mp = m.pipelines.find((p: any) => p.trigger?.nsid?.includes("checkMarpolCompliance"));
    expect(mp.steps).toHaveLength(2);
  });
  it("xrpc covers delivery, samples, consumption, MARPOL", () => {
    const nsids = m.pipelines.filter((p: any) => p.trigger.type === "xrpc").map((p: any) => p.trigger.nsid);
    expect(nsids).toContain("com.etzhayyim.apps.bunker.supply.getDelivery");
    expect(nsids).toContain("com.etzhayyim.apps.bunker.supply.listSamples");
    expect(nsids).toContain("com.etzhayyim.apps.bunker.supply.getConsumption");
    expect(nsids).toContain("com.etzhayyim.apps.bunker.supply.checkMarpolCompliance");
  });
  it("5 actors (VLSFO, LNG, alternative, MARPOL, emissions)", () => { expect(m.actors).toHaveLength(5); });
  it("MARPOL Annex VI compliance", () => { expect(m.governance.complianceFrameworks).toContain("MARPOL Annex VI"); });
  it("isBot true", () => { expect(m.profile.isBot).toBe(true); });
});
