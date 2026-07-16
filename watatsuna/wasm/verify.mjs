#!/usr/bin/env node
// Headless verification that the watatsuna WASI component runs and produces the
// expected chokepoint ranking (top = Malacca). Per ADR-2606014600.
//
// Requires `./build.sh` to have produced `transpiled/` (the 17.6 MB component
// and its jco transpilation are gitignored — rebuild from app.py + wit/).
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const DIR = dirname(fileURLToPath(import.meta.url));
const transpiled = join(DIR, "transpiled", "watatsuna.js");

if (!existsSync(transpiled)) {
  console.error("transpiled/watatsuna.js not found — run ./build.sh first.");
  process.exit(2);
}

const meta = JSON.parse(readFileSync(join(DIR, "watatsuna-actor.meta.json"), "utf8"));
const mod = await import(transpiled);
const out = JSON.parse(mod.compute());

const ok =
  out.actor === "watatsuna" &&
  out.top?.[0]?.chokepoint === "malacca" &&
  meta.exec_tier === "T2 donated-mesh";

console.log("top chokepoints:", JSON.stringify(out.top, null, 0));
console.log(`CID ${meta.cid_v1} (${meta.cid_codec}), tier ${meta.exec_tier}`);
if (!ok) {
  console.error("❌ verification failed");
  process.exit(1);
}
console.log(`✅ watatsuna component runs → top chokepoint = ${out.top[0].chokepoint} (${out.top[0].load_tbps} Tbps)`);
