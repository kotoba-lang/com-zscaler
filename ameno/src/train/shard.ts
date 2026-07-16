/**
 * @etzhayyim/ameno/train/shard — 16-example microbench shard loader +
 * deterministic grader (ADR-2605242630 §4 R1 eval microbench shard).
 *
 * The microbench shard is a JSONL file pinned to IPFS. Each row is a
 * shape-agnostic example with:
 *   - `id`         — string row identifier (stable across rounds).
 *   - `category`   — one of "arithmetic" / "factual" / "instruction" /
 *                    "multilingual"; used by the eval microbench to
 *                    weight category-level loss.
 *   - `prompt`     — input string fed to the model.
 *   - `expected`   — reference answer; semantics depend on grader.
 *   - `grader`     — {kind: "exact-match"|"regex"|"token-set"}; the
 *                    deterministic grader to apply to the model output.
 *
 * `loadShard()` parses the JSONL bytes and CID-verifies the shard
 * against the expected CID (sha256 hex with the "sha256-" prefix; the
 * format used by the dataset-pin lexicon + the e7m-dataset tooling).
 * The CID must match exactly — round-replay parity (G10) requires every
 * device to train on bit-identical bytes.
 *
 * `gradeResponse()` is a pure function over (example, response). It
 * implements three deterministic graders:
 *
 *   - exact-match — trim whitespace, lowercase, compare. Used for
 *                    arithmetic + factual prompts.
 *   - regex       — `expected` is a JS regex source string; test the
 *                    response. Used for instruction-following.
 *   - token-set   — Unicode-aware tokenisation + multiset comparison.
 *                    Used for multilingual prompts where token order
 *                    isn't important.
 *
 * No clocks, no randomness, no LLM calls — pure deterministic graders.
 */

export type GraderKind = "exact-match" | "regex" | "token-set";

export interface ShardExample {
  readonly id: string;
  readonly category: string;
  readonly prompt: string;
  readonly expected: string;
  readonly grader: { readonly kind: GraderKind };
}

export interface LoadedShard {
  /** Parsed examples in file order. */
  readonly examples: readonly ShardExample[];
  /** Computed CID of the raw bytes (sha256, with "sha256-" prefix). */
  readonly cid: string;
  /** Raw byte length (the same length the dataset-pin record reports). */
  readonly sizeBytes: number;
}

export interface LoadShardOptions {
  /** Required CID (sha256 hex with "sha256-" prefix). Loader throws on mismatch. */
  expectedCid: string;
}

/**
 * Load + CID-verify a microbench shard.
 *
 * Throws when:
 *   - the bytes don't hash to `expectedCid` (G10 round-replay parity),
 *   - any row fails JSON parsing or lacks a required field,
 *   - the row count is not 16 (R1 microbench is fixed-size).
 */
export async function loadShard(
  jsonlBytes: Uint8Array,
  opts: LoadShardOptions,
): Promise<LoadedShard> {
  const cid = await computeSha256Cid(jsonlBytes);
  if (cid !== opts.expectedCid) {
    throw new Error(
      `loadShard: CID mismatch — got ${cid}, expected ${opts.expectedCid} (G10 round-replay parity)`,
    );
  }

  const text = new TextDecoder("utf-8", { fatal: true }).decode(jsonlBytes);
  const lines = text.split("\n").filter((l) => l.trim().length > 0);
  const examples: ShardExample[] = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    let parsed: unknown;
    try {
      parsed = JSON.parse(line);
    } catch (err) {
      throw new Error(
        `loadShard: row ${i} failed JSON parse: ${(err as Error).message}`,
      );
    }
    examples.push(coerceExample(parsed, i));
  }

  if (examples.length !== 16) {
    throw new Error(
      `loadShard: expected 16 rows, got ${examples.length} (R1 microbench is fixed-size, ADR-2605242630 §4)`,
    );
  }

  return {
    examples,
    cid,
    sizeBytes: jsonlBytes.byteLength,
  };
}

/**
 * Grade a single response against an example. Returns `true` iff the
 * response satisfies the example's grader.
 *
 * Pure function — no clocks, no randomness, no I/O.
 */
export function gradeResponse(
  example: ShardExample,
  response: string,
): boolean {
  switch (example.grader.kind) {
    case "exact-match":
      return normaliseExactMatch(response) === normaliseExactMatch(example.expected);
    case "regex": {
      let regex: RegExp;
      try {
        regex = new RegExp(example.expected);
      } catch (err) {
        throw new Error(
          `gradeResponse: invalid regex on example ${example.id}: ${(err as Error).message}`,
        );
      }
      return regex.test(response);
    }
    case "token-set":
      return tokenMultisetEqual(response, example.expected);
    default: {
      const _exhaustive: never = example.grader.kind;
      throw new Error(`gradeResponse: unknown grader kind ${String(_exhaustive)}`);
    }
  }
}

// ── Helpers ───────────────────────────────────────────────────────

function coerceExample(raw: unknown, idx: number): ShardExample {
  if (typeof raw !== "object" || raw === null) {
    throw new Error(`loadShard: row ${idx} is not an object`);
  }
  const r = raw as Record<string, unknown>;
  const id = r["id"];
  const category = r["category"];
  const prompt = r["prompt"];
  const expected = r["expected"];
  const grader = r["grader"];
  if (typeof id !== "string" || id.length === 0) {
    throw new Error(`loadShard: row ${idx} missing string 'id'`);
  }
  if (typeof category !== "string" || category.length === 0) {
    throw new Error(`loadShard: row ${idx} missing string 'category'`);
  }
  if (typeof prompt !== "string") {
    throw new Error(`loadShard: row ${idx} missing string 'prompt'`);
  }
  if (typeof expected !== "string") {
    throw new Error(`loadShard: row ${idx} missing string 'expected'`);
  }
  if (
    typeof grader !== "object" ||
    grader === null ||
    typeof (grader as Record<string, unknown>)["kind"] !== "string"
  ) {
    throw new Error(`loadShard: row ${idx} missing object 'grader' with 'kind'`);
  }
  const kind = (grader as Record<string, unknown>)["kind"] as string;
  if (kind !== "exact-match" && kind !== "regex" && kind !== "token-set") {
    throw new Error(`loadShard: row ${idx} unknown grader kind ${kind}`);
  }
  return {
    id,
    category,
    prompt,
    expected,
    grader: { kind: kind as GraderKind },
  };
}

function normaliseExactMatch(text: string): string {
  return text.trim().toLowerCase().replace(/\s+/g, " ");
}

function tokenMultisetEqual(a: string, b: string): boolean {
  const ta = tokenise(a);
  const tb = tokenise(b);
  if (ta.length !== tb.length) return false;
  const counts = new Map<string, number>();
  for (const t of ta) counts.set(t, (counts.get(t) ?? 0) + 1);
  for (const t of tb) {
    const c = counts.get(t);
    if (c === undefined || c <= 0) return false;
    counts.set(t, c - 1);
  }
  return true;
}

/**
 * Unicode-aware tokeniser: lowercase, split on non-letter / non-digit
 * runs (using the Unicode property escapes). Works for Latin, CJK, and
 * Devanagari scripts without language-specific tables.
 */
function tokenise(text: string): string[] {
  const lower = text.toLowerCase();
  const out: string[] = [];
  let buf = "";
  for (const ch of lower) {
    if (/[\p{L}\p{N}]/u.test(ch)) {
      buf += ch;
    } else if (buf.length > 0) {
      out.push(buf);
      buf = "";
    }
  }
  if (buf.length > 0) out.push(buf);
  return out;
}

/** Compute the "sha256-<hex>" CID for a byte buffer. */
async function computeSha256Cid(bytes: Uint8Array): Promise<string> {
  // Copy through a fresh ArrayBuffer so `subtle.digest` accepts a
  // strict `ArrayBuffer` (some TS lib configs reject ArrayBufferLike).
  const ab = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(ab).set(bytes);
  const digest = await crypto.subtle.digest("SHA-256", ab);
  const view = new Uint8Array(digest);
  let hex = "";
  for (let i = 0; i < view.length; i++) {
    hex += view[i].toString(16).padStart(2, "0");
  }
  return `sha256-${hex}`;
}
