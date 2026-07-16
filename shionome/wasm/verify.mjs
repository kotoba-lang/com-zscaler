#!/usr/bin/env node
// Headless verification that the shionome WASI component runs and produces the
// expected cross-asset capital-flow observation (regime risk-on, no_trade true).
// Per ADR-2606072200. Requires ./build.sh to have produced transpiled/ (gitignored).
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const DIR = dirname(fileURLToPath(import.meta.url));
const transpiled = join(DIR, "transpiled", "shionome.js");
if (!existsSync(transpiled)) {
  console.error("transpiled/shionome.js not found — run ./build.sh first.");
  process.exit(2);
}
const meta = JSON.parse(readFileSync(join(DIR, "shionome-actor.meta.json"), "utf8"));
const mod = await import(transpiled);
const out = JSON.parse(mod.compute());

const ok =
  out.actor === "shionome" &&
  out.no_trade === true &&            // G2 — トレードはしない
  out.is_mirror === true &&           // G5
  out.regime === "risk-on" &&
  out.top_inflow?.bucket === "us-equities" &&
  meta.exec_tier === "T2 donated-mesh";

console.log("regime:", out.regime, "| risk_net", out.risk_net, "safe_net", out.safe_net);
console.log("top rotation:", JSON.stringify(out.top_rotation));
console.log(`CID ${meta.cid_v1} (${meta.cid_codec}), tier ${meta.exec_tier}`);
if (!ok) { console.error("❌ verification failed"); process.exit(1); }
console.log(`✅ shionome kotoba-WASM component runs → regime=${out.regime}, no_trade=${out.no_trade} (トレードはしない)`);
