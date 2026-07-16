import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import {
  loadActor,
  resolveActorWasm,
  fetchVerifiedWasm,
  cidV1Raw,
  isRawCidV1,
  didToDocUrl,
} from "../src/inference/wasm-actor-loader.ts";

// Fixtures committed by ADR-2606014500 — the real tsumugi WASM actor + its DID doc.
const DIR = dirname(fileURLToPath(import.meta.url));
const LOADER = join(DIR, "../../tsumugi/wasm/loader");
const DID_DOC = JSON.parse(readFileSync(join(LOADER, "tsumugi.did.json"), "utf8"));
const WASM = readFileSync(join(LOADER, "tsumugi-core.wasm"));
const DID = "did:web:etzhayyim.com:actor:tsumugi";

// A gateway stub that serves the committed bytes for /ipfs/<cid>.
function gatewayStub(bytes: Uint8Array): typeof fetch {
  return (async (input: string | URL | Request) => {
    const u = String(input);
    if (u.includes("/ipfs/")) {
      return new Response(bytes as unknown as BodyInit, { status: 200 });
    }
    throw new Error(`unexpected fetch: ${u}`);
  }) as typeof fetch;
}

test("didToDocUrl maps actor DID → did.json URL", () => {
  assert.equal(didToDocUrl(DID), "https://etzhayyim.com/actor/tsumugi/did.json");
  assert.throws(() => didToDocUrl("did:web:example.com"));
});

test("cidV1Raw matches the DID doc's WASM CID", async () => {
  const svc = DID_DOC.service.find((s: any) => s.type === "EtzhayyimWasmComponent");
  const cid = svc.serviceEndpoint.replace(/^ipfs:\/\//, "");
  assert.ok(isRawCidV1(cid));
  assert.equal(await cidV1Raw(WASM), cid);
});

test("resolveActorWasm extracts the EtzhayyimWasmComponent CID", async () => {
  const ref = await resolveActorWasm(DID, { didResolver: async () => DID_DOC });
  assert.equal(ref.uri.startsWith("ipfs://"), true);
  assert.equal(ref.cid, await cidV1Raw(WASM));
});

test("loadActor: resolve → CID-verify → instantiate → run = TSMC", async () => {
  const { ref, run } = await loadActor(DID, {
    didResolver: async () => DID_DOC,
    fetchImpl: gatewayStub(WASM),
  });
  assert.equal(ref.did, DID);
  const out = run() as { actor: string; top: { label: string }[] };
  assert.equal(out.actor, "tsumugi");
  assert.equal(out.top[0].label, "TSMC");
});

test("fetchVerifiedWasm REJECTS tampered bytes (untrusted gateway)", async () => {
  const tampered = new Uint8Array(WASM);
  tampered[tampered.length - 1] ^= 0xff; // flip a byte
  const cid = await cidV1Raw(WASM);
  await assert.rejects(
    () => fetchVerifiedWasm(cid, { fetchImpl: gatewayStub(tampered) }),
    /integrity check FAILED/,
  );
});
