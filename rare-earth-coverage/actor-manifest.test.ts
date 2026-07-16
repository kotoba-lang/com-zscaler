import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const m = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));
const VP = new Set(["graph.query","graph.write","graph.vectorSearch","agent.chat","agent.invoke","identity.resolve","browser.fetch","signal.encrypt","consent.check","derive:social","dmn.evaluate","form.collect"]);

describe("Rare Earth Coverage Actor Manifest", () => {
  it("@context valid", () => { expect(m["@context"]).toBe("https://etzhayyim.com/ns/actor/v1"); });
  it("DID valid", () => { expect(m["@id"]).toBe("did:web:rare-earth-coverage.etzhayyim.com"); });
  it("runtime", () => { expect(m.runtime).toBe("k8s-langserver"); });
  it("nanoid", () => { expect(m.nanoid).toBe("re4c0v26"); });
  it("capabilities valid", () => { for (const c of m.capabilities) expect(VP.has(c)).toBe(true); });
  it("no fn:custom", () => { for (const p of m.pipelines) for (const s of p.steps) expect(s.fn).not.toBe("custom"); });
  it("five pipelines", () => { expect(m.pipelines).toHaveLength(5); });
  it("seed cron registers actors then flows then digest", () => {
    const seed = m.pipelines.find((p: any) => p.trigger.type === "cron" && p.trigger.cron === "15 */6 * * *");
    expect(seed.steps).toHaveLength(3);
    expect(seed.steps[0].id).toBe("seedActors");
    expect(seed.steps[1].id).toBe("seedFlows");
    expect(seed.steps[2].fn).toBe("derive:social");
  });
  it("coverage cron computes nodes, flows, stages, summary, snapshot", () => {
    const cron = m.pipelines.find((p: any) => p.trigger.type === "cron" && p.trigger.cron === "0 */6 * * *");
    expect(cron.steps).toHaveLength(5);
    expect(cron.steps[2].id).toBe("coverageStages");
    expect(cron.steps[3].id).toBe("coverageSummary");
    expect(cron.steps[4].id).toBe("coverageSnapshot");
  });
  it("xrpc covers get, listActors, listFlows", () => {
    const nsids = m.pipelines.filter((p: any) => p.trigger.type === "xrpc").map((p: any) => p.trigger.nsid);
    expect(nsids).toContain("com.etzhayyim.apps.rareEarth.coverage.get");
    expect(nsids).toContain("com.etzhayyim.apps.rareEarth.coverage.listActors");
    expect(nsids).toContain("com.etzhayyim.apps.rareEarth.coverage.listFlows");
  });
  it("registers broad rare earth actor set", () => { expect(m.actors.length).toBeGreaterThanOrEqual(30); });
  it("isBot true", () => { expect(m.profile.isBot).toBe(true); });
});
