import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const __dirname = dirname(fileURLToPath(import.meta.url));
const m = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));
const VP = new Set(["graph.query","graph.write","graph.vectorSearch","agent.chat","agent.invoke","identity.resolve","browser.fetch","signal.encrypt","consent.check","derive:social","dmn.evaluate","form.collect"]);

describe("Oil Coverage Actor Manifest", () => {
  it("@context valid", () => { expect(m["@context"]).toBe("https://etzhayyim.com/ns/actor/v1"); });
  it("DID valid", () => { expect(m["@id"]).toBe("did:web:oil-coverage.etzhayyim.com"); });
  it("runtime", () => { expect(m.runtime).toBe("k8s-langserver"); });
  it("nanoid", () => { expect(m.nanoid).toBe("011c0v3r"); });
  it("capabilities valid", () => { for (const c of m.capabilities) expect(VP.has(c)).toBe(true); });
  it("no fn:custom", () => { for (const p of m.pipelines) for (const s of p.steps) expect(s.fn).not.toBe("custom"); });
  it("5 pipelines", () => { expect(m.pipelines).toHaveLength(5); });
  it("baseline cron seeds targets then posts digest", () => {
    const seed = m.pipelines.find((p: any) => p.trigger.type === "cron" && p.trigger.cron === "15 */6 * * *");
    expect(seed.steps).toHaveLength(2);
    expect(seed.steps[0].id).toBe("seedTargets");
    expect(seed.steps[1].fn).toBe("derive:social");
  });
  it("coverage cron computes nodes, targets, backbone, summary, snapshot", () => {
    const cron = m.pipelines.find((p: any) => p.trigger.type === "cron" && p.trigger.cron === "0 */6 * * *");
    expect(cron.steps).toHaveLength(5);
    expect(cron.steps[3].id).toBe("coverageSummary");
    expect(cron.steps[4].id).toBe("coverageSnapshot");
  });
  it("xrpc covers get, listTargets, listBackbone", () => {
    const nsids = m.pipelines.filter((p: any) => p.trigger.type === "xrpc").map((p: any) => p.trigger.nsid);
    expect(nsids).toContain("com.etzhayyim.apps.oil.coverage.get");
    expect(nsids).toContain("com.etzhayyim.apps.oil.coverage.listTargets");
    expect(nsids).toContain("com.etzhayyim.apps.oil.coverage.listBackbone");
  });
  it("six segment actors", () => { expect(m.actors).toHaveLength(6); });
  it("isBot true", () => { expect(m.profile.isBot).toBe(true); });
});
