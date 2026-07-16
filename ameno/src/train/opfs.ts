/**
 * @etzhayyim/ameno/train/opfs — OPFS Adam-state offload (ADR-2605242630 §3).
 *
 * The Adam optimizer state (m, v moments per LoRA parameter) is
 * offloaded between micro-batch steps so a backgrounded PWA can resume
 * after a system kill. Each in-flight round lives under:
 *
 *   /baien-train/<actorDid-sanitised>/<roundId>/
 *     meta.json       round metadata (rank, alpha, baseModelCid, …)
 *     adam-m.f32      Float32Array of all m moments
 *     adam-v.f32      Float32Array of all v moments
 *     delta-A.f32     Current Δ for A matrices (rolling)
 *     delta-B.f32     Current Δ for B matrices (rolling)
 *     rng-state.json  Deterministic RNG seed + step counter
 *
 * On round commit (`handle.commit()`) the directory is marked with a
 * `COMMITTED` sentinel and the rolling delta files become final.
 * On round abort (`handle.abort(reason)`) the directory is marked with
 * an `ABORTED` sentinel that records the abort reason (thermal / OOM /
 * fp16-overflow / scanner-fail / timeout).
 *
 * NOTE: a full `move` of the directory to `committed/<roundId>/` (per
 * the ADR §3 sketch) requires the FileSystemAccess `move` API which is
 * not yet shipped in all WebKit / Chromium channels. The sentinel-file
 * approach keeps the contract stable: the round directory is the
 * canonical artifact; a sibling sentinel marks state. The aggregator
 * (R2) reads the sentinel before treating a directory as "ready".
 */

/** roundId = sha256(baseModelCid || prevAdapterCid || datasetShardCid || iter) truncated to 16 hex chars. */
export type RoundId = string;

export interface RoundMeta {
  readonly rank: number;
  readonly alpha: number;
  readonly baseModelCid: string;
  readonly prevAdapterCid: string;
  readonly datasetShardCid: string;
  readonly iter: number;
  readonly stepsCompleted: number;
  readonly deviceClass: "ios" | "android" | "wasm-desktop";
  readonly path: "A" | "B" | "C";
}

export interface RngState {
  readonly seed: number;
  readonly step: number;
}

/**
 * Sentinel state the round directory advertises. R2's aggregator
 * treats `IN_FLIGHT` directories as ignorable, `COMMITTED` as ready,
 * and `ABORTED` as tombstoned (with reason).
 */
export type RoundSentinel =
  | { kind: "IN_FLIGHT" }
  | { kind: "COMMITTED"; committedAt: string }
  | { kind: "ABORTED"; reason: string; abortedAt: string };

export interface RoundDirHandle {
  readonly roundId: RoundId;
  readonly actorDid: string;
  /** Read the meta.json file. Returns `null` if absent. */
  readMeta(): Promise<RoundMeta | null>;
  /** Write (or overwrite) the meta.json file. */
  writeMeta(meta: RoundMeta): Promise<void>;
  /** Read `adam-m.f32` as a Float32Array. Returns null if absent. */
  readAdamM(): Promise<Float32Array | null>;
  /** Write `adam-m.f32` (truncate + replace). */
  writeAdamM(m: Float32Array): Promise<void>;
  /** Read `adam-v.f32` as a Float32Array. Returns null if absent. */
  readAdamV(): Promise<Float32Array | null>;
  /** Write `adam-v.f32` (truncate + replace). */
  writeAdamV(v: Float32Array): Promise<void>;
  /** Read `delta-A.f32` as a Float32Array. Returns null if absent. */
  readDeltaA(): Promise<Float32Array | null>;
  /** Write `delta-A.f32` (truncate + replace). */
  writeDeltaA(d: Float32Array): Promise<void>;
  /** Read `delta-B.f32` as a Float32Array. Returns null if absent. */
  readDeltaB(): Promise<Float32Array | null>;
  /** Write `delta-B.f32` (truncate + replace). */
  writeDeltaB(d: Float32Array): Promise<void>;
  /** Read `rng-state.json`. Returns null if absent. */
  readRngState(): Promise<RngState | null>;
  /** Write `rng-state.json`. */
  writeRngState(state: RngState): Promise<void>;
  /** Read the current sentinel state (defaults to IN_FLIGHT). */
  readSentinel(): Promise<RoundSentinel>;
  /** Mark the round directory committed. Writes a `COMMITTED` sentinel file. */
  commit(): Promise<void>;
  /** Mark the round directory aborted with a reason. Writes an `ABORTED` sentinel file. */
  abort(reason: string): Promise<void>;
}

/**
 * Compute the canonical roundId for a (baseModelCid, prevAdapterCid,
 * datasetShardCid, iter) tuple. The tuple is joined with a single
 * ASCII space — a space cannot appear inside any of the inputs (CIDs
 * are bare-base32 / hex, iter is an integer), so the encoding is
 * unambiguous. Returns the first 16 hex chars of sha256.
 *
 * Deterministic — same inputs always produce the same roundId. R2's
 * aggregator independently computes this and rejects deltas whose
 * roundId doesn't match.
 */
export async function computeRoundId(
  baseModelCid: string,
  prevAdapterCid: string,
  datasetShardCid: string,
  iter: number,
): Promise<RoundId> {
  const text = `${baseModelCid} ${prevAdapterCid} ${datasetShardCid} ${iter}`;
  const bytes = new TextEncoder().encode(text);
  const ab = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(ab).set(bytes);
  const digest = await crypto.subtle.digest("SHA-256", ab);
  const view = new Uint8Array(digest);
  let hex = "";
  for (let i = 0; i < view.length && hex.length < 16; i++) {
    hex += view[i].toString(16).padStart(2, "0");
  }
  return hex.slice(0, 16);
}

/**
 * Open (or create) the OPFS directory for an in-flight round. Returns
 * a handle exposing typed read/write helpers for each f32 file +
 * meta.json + rng-state.json + the sentinel.
 *
 * Throws if OPFS is not available in this environment (no
 * `navigator.storage.getDirectory`). Mobile Safari + modern Chrome
 * support it; legacy browsers do not.
 */
export async function openRoundDir(
  actorDid: string,
  roundId: RoundId,
): Promise<RoundDirHandle> {
  const root = await getOpfsRoot();
  const baienDir = await root.getDirectoryHandle("baien-train", {
    create: true,
  });
  const actorDir = await baienDir.getDirectoryHandle(sanitiseDid(actorDid), {
    create: true,
  });
  const roundDir = await actorDir.getDirectoryHandle(roundId, { create: true });

  return makeHandle(roundDir, roundId, actorDid);
}

// ── Internals ─────────────────────────────────────────────────────

function makeHandle(
  dir: FileSystemDirectoryHandle,
  roundId: RoundId,
  actorDid: string,
): RoundDirHandle {
  return {
    roundId,
    actorDid,
    readMeta: () => readJsonFile<RoundMeta>(dir, "meta.json"),
    writeMeta: (meta) => writeJsonFile(dir, "meta.json", meta),
    readAdamM: () => readFloat32File(dir, "adam-m.f32"),
    writeAdamM: (m) => writeFloat32File(dir, "adam-m.f32", m),
    readAdamV: () => readFloat32File(dir, "adam-v.f32"),
    writeAdamV: (v) => writeFloat32File(dir, "adam-v.f32", v),
    readDeltaA: () => readFloat32File(dir, "delta-A.f32"),
    writeDeltaA: (d) => writeFloat32File(dir, "delta-A.f32", d),
    readDeltaB: () => readFloat32File(dir, "delta-B.f32"),
    writeDeltaB: (d) => writeFloat32File(dir, "delta-B.f32", d),
    readRngState: () => readJsonFile<RngState>(dir, "rng-state.json"),
    writeRngState: (state) => writeJsonFile(dir, "rng-state.json", state),
    readSentinel: async () => {
      const committed = await readJsonFile<{ committedAt: string }>(
        dir,
        "COMMITTED",
      );
      if (committed) {
        return { kind: "COMMITTED", committedAt: committed.committedAt };
      }
      const aborted = await readJsonFile<{ reason: string; abortedAt: string }>(
        dir,
        "ABORTED",
      );
      if (aborted) {
        return {
          kind: "ABORTED",
          reason: aborted.reason,
          abortedAt: aborted.abortedAt,
        };
      }
      return { kind: "IN_FLIGHT" };
    },
    commit: async () => {
      // Write the COMMITTED sentinel. The FileSystemAccess `move` API
      // would relocate the directory to `committed/<roundId>/`; until
      // that ships universally, R2's aggregator keys off the sentinel.
      await writeJsonFile(dir, "COMMITTED", {
        committedAt: new Date().toISOString(),
      });
    },
    abort: async (reason: string) => {
      await writeJsonFile(dir, "ABORTED", {
        reason,
        abortedAt: new Date().toISOString(),
      });
    },
  };
}

async function getOpfsRoot(): Promise<FileSystemDirectoryHandle> {
  if (
    typeof navigator === "undefined" ||
    typeof navigator.storage === "undefined" ||
    typeof navigator.storage.getDirectory !== "function"
  ) {
    throw new Error(
      "openRoundDir: OPFS not available (navigator.storage.getDirectory missing); R1 requires a browser with FileSystemAccess + OPFS support",
    );
  }
  return navigator.storage.getDirectory();
}

function sanitiseDid(did: string): string {
  // OPFS directory names can technically hold `:` but Safari has
  // historically been quirky about it. Replace the colons + drop other
  // non-[A-Za-z0-9._-] characters to a safe form.
  return did.replace(/[^A-Za-z0-9._-]/g, "_");
}

async function readJsonFile<T>(
  dir: FileSystemDirectoryHandle,
  name: string,
): Promise<T | null> {
  let fh: FileSystemFileHandle;
  try {
    fh = await dir.getFileHandle(name, { create: false });
  } catch {
    return null;
  }
  const file = await fh.getFile();
  const text = await file.text();
  if (text.length === 0) return null;
  return JSON.parse(text) as T;
}

async function writeJsonFile(
  dir: FileSystemDirectoryHandle,
  name: string,
  value: unknown,
): Promise<void> {
  const fh = await dir.getFileHandle(name, { create: true });
  const writable = await fh.createWritable();
  await writable.write(JSON.stringify(value));
  await writable.close();
}

async function readFloat32File(
  dir: FileSystemDirectoryHandle,
  name: string,
): Promise<Float32Array | null> {
  let fh: FileSystemFileHandle;
  try {
    fh = await dir.getFileHandle(name, { create: false });
  } catch {
    return null;
  }
  const file = await fh.getFile();
  const ab = await file.arrayBuffer();
  // Float32Array requires a length divisible by 4.
  if (ab.byteLength === 0) return new Float32Array(0);
  if (ab.byteLength % 4 !== 0) {
    throw new Error(
      `readFloat32File: ${name} byte length ${ab.byteLength} is not a multiple of 4`,
    );
  }
  return new Float32Array(ab);
}

async function writeFloat32File(
  dir: FileSystemDirectoryHandle,
  name: string,
  data: Float32Array,
): Promise<void> {
  const fh = await dir.getFileHandle(name, { create: true });
  const writable = await fh.createWritable();
  // Copy through a fresh ArrayBuffer of the exact length so the writer
  // doesn't see any padding from the underlying Float32Array's buffer.
  const ab = new ArrayBuffer(data.byteLength);
  new Float32Array(ab).set(data);
  await writable.write(ab);
  await writable.close();
}
