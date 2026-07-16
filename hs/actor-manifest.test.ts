import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const m = JSON.parse(readFileSync(resolve(__dirname, "actor-manifest.jsonld"), "utf-8"));
const VP = new Set(["graph.query","graph.write","graph.vectorSearch","agent.chat","agent.invoke","identity.resolve","browser.fetch","signal.encrypt","consent.check","derive:social","dmn.evaluate","form.collect"]);

describe("HS Actor Manifest", () => {
  it("@context valid", () => { expect(m["@context"]).toBe("https://etzhayyim.com/ns/actor/v1"); });
  it("DID valid", () => { expect(m["@id"]).toBe("did:web:hs.etzhayyim.com"); });
  it("runtime", () => { expect(m.runtime).toBe("k8s-langserver"); });
  it("nanoid", () => { expect(m.nanoid).toBe("hs6c0d3x"); });
  it("capabilities valid", () => { for (const c of m.capabilities) expect(VP.has(c)).toBe(true); });
  it("no fn:custom", () => { for (const p of m.pipelines) for (const s of p.steps) expect(s.fn).not.toBe("custom"); });
  it("6 pipelines", () => { expect(m.pipelines).toHaveLength(6); });
  it("cron coverage has 4 steps", () => {
    const cron = m.pipelines.find((p: any) => p.trigger.type === "cron" && p.trigger.cron === "0 */6 * * *");
    expect(cron.steps).toHaveLength(4);
    expect(cron.steps[2].id).toBe("analyzeCoverage");
  });
  it("xrpc covers node, children, concordance, policy, health", () => {
    const nsids = m.pipelines.filter((p: any) => p.trigger.type === "xrpc").map((p: any) => p.trigger.nsid);
    expect(nsids).toContain("com.etzhayyim.apps.hs.getNode");
    expect(nsids).toContain("com.etzhayyim.apps.hs.getChildren");
    expect(nsids).toContain("com.etzhayyim.apps.hs.resolveConcordance");
    expect(nsids).toContain("com.etzhayyim.apps.hs.getPolicyOverlay");
    expect(nsids).toContain("com.etzhayyim.apps.hs.health");
  });
  it("4 actor paths", () => { expect(m.actors).toHaveLength(4); });
});
