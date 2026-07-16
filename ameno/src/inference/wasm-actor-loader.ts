/**
 * WASM-actor loader — resolve an actor DID, fetch its content-addressed WASM
 * component through the apex trustless gateway, verify the CID client-side, and
 * instantiate it for **browser-local** execution. The runtime half of the
 * "one Worker, many WASM actors" model (ADR-2606014500 + 2606014600).
 *
 * Trust: the DID doc is fetched over TLS from `etzhayyim.com`; the WASM bytes
 * are trusted ONLY after this module re-hashes them to the CID the DID doc
 * points at (the gateway is untrusted — no server key, ADR-2605231525).
 *
 * Targets compact, single-block (raw-CID) core-wasm actors — e.g. tsumugi-core.
 * Large componentize-py components (multi-block `bafy…`) are the T2 donated-mesh
 * tier and are not loaded here.
 *
 * Runs unchanged in the browser and in Node (uses `fetch`, `WebAssembly`,
 * `crypto.subtle`, `TextDecoder` — all standard in both).
 */

export interface WasmActorRef {
  readonly did: string;
  readonly cid: string;
  readonly uri: string; // ipfs://<cid>
}

export interface WasmActorLoaderOpts {
  /** Override DID resolution (default: GET https://etzhayyim.com/actor/<h>/did.json). */
  readonly didResolver?: (did: string) => Promise<unknown>;
  /** Trustless gateway base (default: https://etzhayyim.com). Fetches `<base>/ipfs/<cid>`. */
  readonly gatewayBase?: string;
  /** Injectable fetch (tests / non-global environments). */
  readonly fetchImpl?: typeof fetch;
  /** Re-hash + compare the CID client-side. Default true; never disable in production. */
  readonly verify?: boolean;
}

const DEFAULT_GATEWAY = "https://etzhayyim.com";
const ACTOR_DID_RE = /^did:web:etzhayyim\.com:actor:([a-z0-9][a-z0-9-]*)$/;

/** did:web:etzhayyim.com:actor:<h> → https://etzhayyim.com/actor/<h>/did.json */
export function didToDocUrl(did: string): string {
  const m = ACTOR_DID_RE.exec(did);
  if (!m) throw new Error(`not an etzhayyim actor DID: ${did}`);
  return `https://etzhayyim.com/actor/${m[1]}/did.json`;
}

interface DidServiceEntry {
  type?: string;
  serviceEndpoint?: string;
}
interface DidDoc {
  service?: DidServiceEntry[];
}

/** Resolve the actor's `EtzhayyimWasmComponent` service → CID. */
export async function resolveActorWasm(
  did: string,
  opts: WasmActorLoaderOpts = {},
): Promise<WasmActorRef> {
  const doFetch = opts.fetchImpl ?? fetch;
  const doc = (
    opts.didResolver
      ? await opts.didResolver(did)
      : await doFetch(didToDocUrl(did)).then((r) => {
          if (!r.ok) throw new Error(`DID resolve ${r.status}`);
          return r.json();
        })
  ) as DidDoc;
  const svc = (doc.service ?? []).find(
    (s) => s.type === "EtzhayyimWasmComponent",
  );
  if (!svc?.serviceEndpoint) {
    throw new Error(`no EtzhayyimWasmComponent service for ${did}`);
  }
  const uri = svc.serviceEndpoint;
  const cid = uri.replace(/^ipfs:\/\//, "");
  if (!isRawCidV1(cid)) {
    throw new Error(
      `actor WASM CID ${cid} is not a raw single-block CIDv1; browser-local load supports raw CIDs only (multi-block = T2 mesh)`,
    );
  }
  return { did, cid, uri };
}

/** Fetch the WASM via the trustless gateway and verify it hashes to `cid`. */
export async function fetchVerifiedWasm(
  cid: string,
  opts: WasmActorLoaderOpts = {},
): Promise<ArrayBuffer> {
  const doFetch = opts.fetchImpl ?? fetch;
  const base = (opts.gatewayBase ?? DEFAULT_GATEWAY).replace(/\/$/, "");
  const res = await doFetch(`${base}/ipfs/${cid}`);
  if (!res.ok) throw new Error(`gateway ${res.status} for ${cid}`);
  const buf = await res.arrayBuffer();
  if (opts.verify !== false) {
    const got = await cidV1Raw(buf);
    if (got !== cid) {
      throw new Error(
        `CID integrity check FAILED — gateway returned bytes hashing to ${got}, expected ${cid}`,
      );
    }
  }
  return buf;
}

export async function instantiateActor(
  buf: BufferSource,
  imports: WebAssembly.Imports = {},
): Promise<WebAssembly.Instance> {
  const { instance } = await WebAssembly.instantiate(buf as BufferSource, imports);
  return instance;
}

/** Run the standard etzhayyim core-actor ABI: `compute() -> len`, `result_ptr()`,
 *  JSON in linear `memory`. Returns the parsed result. */
export function runCompute(instance: WebAssembly.Instance): unknown {
  const ex = instance.exports as {
    compute?: () => number;
    result_ptr?: () => number;
    memory?: WebAssembly.Memory;
  };
  if (!ex.compute || !ex.result_ptr || !ex.memory) {
    throw new Error("actor does not export the compute()/result_ptr()/memory ABI");
  }
  const len = ex.compute();
  const ptr = ex.result_ptr();
  const bytes = new Uint8Array(ex.memory.buffer, ptr, len);
  return JSON.parse(new TextDecoder().decode(bytes));
}

/** One-shot: resolve → fetch+verify → instantiate. `run()` executes the ABI. */
export async function loadActor(
  did: string,
  opts: WasmActorLoaderOpts = {},
): Promise<{
  ref: WasmActorRef;
  instance: WebAssembly.Instance;
  run: () => unknown;
}> {
  const ref = await resolveActorWasm(did, opts);
  const buf = await fetchVerifiedWasm(ref.cid, opts);
  const instance = await instantiateActor(buf);
  return { ref, instance, run: () => runCompute(instance) };
}

// ── content-address (inline; client verifies independently of the gateway) ──
const B32 = "abcdefghijklmnopqrstuvwxyz234567";
function base32(bytes: Uint8Array): string {
  let bits = 0,
    val = 0,
    out = "";
  for (const b of bytes) {
    val = (val << 8) | b;
    bits += 8;
    while (bits >= 5) {
      out += B32[(val >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += B32[(val << (5 - bits)) & 31];
  return out;
}

export function isRawCidV1(cid: string): boolean {
  return /^bafkrei[a-z2-7]{52}$/.test(cid);
}

/** CIDv1 (raw, sha2-256) of `bytes` — matches `ipfs add --cid-version=1`. */
export async function cidV1Raw(bytes: BufferSource): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  const cid = new Uint8Array(4 + digest.length);
  cid.set([0x01, 0x55, 0x12, 0x20], 0);
  cid.set(digest, 4);
  return "b" + base32(cid);
}
