#!/usr/bin/env node
// Headless proof of the "one Worker, many WASM actors" resolution path
// (ADR-2606014500). Mirrors exactly what the browser loader (index.html) does,
// but runs in Node so it is CI-runnable without a browser:
//
//   1. resolve the actor DID document (fixture here; live = etzhayyim.com/actor/<h>/did.json)
//   2. find the EtzhayyimWasmComponent service → ipfs://<cid>
//   3. fetch the WASM (local dist here; live = trustless IPFS gateway)
//   4. VERIFY content integrity: recompute the IPFS CIDv1 of the bytes and
//      assert it equals the CID the DID document points at (this is the trust
//      anchor — content-addressing, NO server key, ADR-2605231525)
//   5. instantiate + run compute() → read JSON from linear memory → assert output
//
// No dependencies: CIDv1 (raw, sha2-256, base32) computed with node:crypto.

import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const DIR = dirname(fileURLToPath(import.meta.url));

// RFC4648 base32 lower, no padding (multibase prefix 'b').
function base32(bytes) {
  const A = "abcdefghijklmnopqrstuvwxyz234567";
  let bits = 0, val = 0, out = "";
  for (const b of bytes) {
    val = (val << 8) | b;
    bits += 8;
    while (bits >= 5) {
      out += A[(val >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += A[(val << (5 - bits)) & 31];
  return out;
}

// CIDv1, raw codec (0x55), sha2-256 (0x12, len 0x20) — matches `ipfs add --cid-version=1` on a single raw block.
function cidV1Raw(bytes) {
  const digest = createHash("sha256").update(bytes).digest();
  const cid = Buffer.concat([Buffer.from([0x01, 0x55, 0x12, 0x20]), digest]);
  return "b" + base32(cid);
}

function pass(m) { console.log("  ✅ " + m); }
function fail(m) { console.error("  ❌ " + m); process.exitCode = 1; throw new Error(m); }

async function main() {
  console.log("resolve → integrity → execute (tsumugi WASM actor)\n");

  // 1) resolve DID document
  const did = JSON.parse(readFileSync(join(DIR, "tsumugi.did.json"), "utf8"));
  pass(`DID resolved: ${did.id}`);

  // 2) find the WASM component service
  const svc = (did.service || []).find((s) => s.type === "EtzhayyimWasmComponent");
  if (!svc) fail("no EtzhayyimWasmComponent service in DID doc");
  const cid = svc.serviceEndpoint.replace(/^ipfs:\/\//, "");
  pass(`WASM component service: ipfs://${cid}`);

  // 3) fetch the WASM (local dist == what a gateway would return for the CID)
  const wasm = readFileSync(join(DIR, "tsumugi-core.wasm"));

  // 4) content-integrity: recompute CID, assert it matches the DID doc's CID
  const got = cidV1Raw(wasm);
  if (got !== cid) fail(`CID mismatch — doc=${cid} bytes=${got}`);
  pass(`content integrity OK — recomputed CID == DID doc CID (no server key, TLS+CID trust)`);

  // 5) instantiate + run
  const { instance } = await WebAssembly.instantiate(wasm, {});
  const { compute, result_ptr, memory } = instance.exports;
  const len = compute();
  const ptr = result_ptr();
  const out = JSON.parse(Buffer.from(memory.buffer, ptr, len).toString("utf8"));
  if (out.actor !== "tsumugi") fail("unexpected actor");
  if (out.top[0].label !== "TSMC") fail(`expected TSMC top 取, got ${out.top[0].label}`);
  pass(`executed locally → top 取 = ${out.top[0].label} (${out.top[0].karma}), nodes=${out.nodes}, edges=${out.edges}`);

  console.log("\nresult:", JSON.stringify(out));
  console.log("\n✅ PASS — actor resolved + integrity-verified + executed with ZERO per-actor server.");
}

main().catch((e) => { console.error("\nFAILED:", e.message); process.exit(1); });
