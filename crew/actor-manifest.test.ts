import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const __dirname = dirname(fileURLToPath(import.meta.url));
const m = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));
const VP = new Set(["graph.query","graph.write","graph.vectorSearch","agent.chat","agent.invoke","identity.resolve","browser.fetch","signal.encrypt","consent.check","derive:social","dmn.evaluate","form.collect"]);

describe("Crew Actor Manifest", () => {
  it("@context valid", () => { expect(m["@context"]).toBe("https://etzhayyim.com/ns/actor/v1"); });
  it("DID valid", () => { expect(m["@id"]).toBe("did:web:crew.etzhayyim.com"); });
  it("runtime", () => { expect(m.runtime).toBe("k8s-langserver"); });
  it("nanoid", () => { expect(m.nanoid).toBe("cr3w0001"); });
  it("capabilities valid", () => { for (const c of m.capabilities) expect(VP.has(c)).toBe(true); });
  it("no fn:custom", () => { for (const p of m.pipelines) for (const s of p.steps) expect(s.fn).not.toBe("custom"); });
  it("9 pipelines", () => { expect(m.pipelines).toHaveLength(9); });
  it("every step has id/fn/args", () => { for (const p of m.pipelines) for (const s of p.steps) { expect(s.id).toBeDefined(); expect(s.fn).toBeDefined(); expect(s.args).toBeDefined(); } });
  it("daily cron checks expiring certs", () => {
    const cron = m.pipelines.find((p: any) => p.trigger.type === "cron");
    expect(cron.trigger.cron).toContain("6"); // 06:00 daily
    expect(cron.steps[0].args.sql).toContain("expiryDate");
  });
  it("subscribes to vessel.portCall for crew change", () => {
    const sub = m.pipelines.find((p: any) => p.trigger.type === "subscribeRepos");
    expect(sub.trigger.collections).toContain("com.etzhayyim.apps.vessel.portCall");
  });
  it("xrpc covers seafarer, certs, manifest, crew changes", () => {
    const nsids = m.pipelines.filter((p: any) => p.trigger.type === "xrpc").map((p: any) => p.trigger.nsid);
    expect(nsids).toContain("com.etzhayyim.apps.crew.management.getSeafarer");
    expect(nsids).toContain("com.etzhayyim.apps.crew.management.listCertificates");
    expect(nsids).toContain("com.etzhayyim.apps.crew.management.getCrewManifest");
    expect(nsids).toContain("com.etzhayyim.apps.crew.management.listCrewChanges");
  });
  it("5 actors (deck/engine officers, deck/engine ratings, STCW)", () => { expect(m.actors).toHaveLength(5); });
  it("STCW + MLC compliance", () => {
    expect(m.governance.complianceFrameworks).toContain("STCW Convention");
    expect(m.governance.complianceFrameworks).toContain("MLC 2006");
  });
  it("isBot true", () => { expect(m.profile.isBot).toBe(true); });
});
