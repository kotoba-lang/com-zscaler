/**
 * @etzhayyim/ameno/train/charter-rider — On-device Charter Rider scanner
 * mirror (ADR-2605192200 §2(a)..(h), ADR-2605242630 §6 scanner-fail).
 *
 * Mirror of the Python canonical scanner at
 * `40-engine/kotoba/crates/kotoba-kotodama/cells/feed_post/cell.py` (`_python_rego_mirror` +
 * `_JA_PROHIBITED_PATTERNS`). The TS port covers five categories:
 *
 *   - 2a  — autonomous weapons / kinetic strike vocabulary
 *   - 2b  — predatory finance (HFT, payday loans, naked short, …)
 *   - 2c  — surveillance ad-tech (DSP/SSP, data brokers, …)
 *   - advertising — solicitation language (sponsored, promo code, …)
 *   - eschatology — rapture / end-times / millennial-kingdom phrasing
 *
 * Plus a Japanese prohibited-vocabulary regex band for the
 * non-ASCII cases (`末法到来`, `黙示録の獣`, `千年王国は近い`).
 *
 * Each category supports an `allow` band of educational / regulatory /
 * historical markers — these demote the hit (the row stays in the
 * shard). Without an allow-band hit, the row is rejected.
 *
 * `scanShard(rows)` runs the full mirror against the `prompt` and
 * `expected` fields of each ShardExample and returns the totals plus a
 * truncated evidence sample. `passed === true` iff the rejection ratio
 * is ≤ 5% — the same threshold the aggregator will enforce
 * (ADR-2605242630 §6 scanner-fail).
 *
 * NOTE: the canonical Python mirror is the source of truth. CI hook
 * `charter-rider-rego-mirror` enforces drift detection between the
 * Python mirror and the Rego policy; this TS port follows whatever the
 * Python mirror reports today. Drift between Python and this TS port
 * is acceptable for R1a (device-side scanner is a self-attestation —
 * the aggregator re-runs the canonical scanner before accepting Δ;
 * ADR-2605242630 §4 lexicon notes "device's scannerPass is a
 * self-attestation, not the ground truth").
 */

import type { ShardExample } from "./shard";

export interface ScanRejection {
  /** Category code: "2a" | "2b" | "2c" | "advertising" | "eschatology" | "ja-prohibited". */
  readonly category: string;
  /** Human-readable evidence string (truncated to 200 chars in the sample). */
  readonly evidence: string;
}

export interface ScanResult {
  /** Total rows scanned. */
  readonly totalRows: number;
  /** Rows the scanner rejected. */
  readonly rejectedRows: number;
  /** True iff (rejectedRows / totalRows) <= 0.05. */
  readonly passed: boolean;
  /** Truncated rejection sample (max 8 rows, evidence ≤ 200 chars each). */
  readonly evidenceSample: ReadonlyArray<ScanRejection>;
}

/**
 * Run the Charter Rider mirror against every row in `rows` and return
 * `{totalRows, rejectedRows, passed, evidenceSample}`. Pure function —
 * no clocks, no I/O.
 */
export function scanShard(rows: readonly ShardExample[]): ScanResult {
  let rejectedRows = 0;
  const evidence: ScanRejection[] = [];

  for (const row of rows) {
    const haystack = `${row.prompt}\n${row.expected}`;
    const hits = scanText(haystack);
    if (hits.length > 0) {
      rejectedRows += 1;
      if (evidence.length < 8) {
        // Keep one representative hit per rejected row.
        const first = hits[0];
        evidence.push({
          category: first.category,
          evidence: first.evidence.slice(0, 200),
        });
      }
    }
  }

  const passed = rows.length === 0 ? true : rejectedRows / rows.length <= 0.05;
  return {
    totalRows: rows.length,
    rejectedRows,
    passed,
    evidenceSample: evidence,
  };
}

// ── Internals ─────────────────────────────────────────────────────

interface CategorySpec {
  readonly code: string;
  readonly terms: readonly string[];
  readonly allow: readonly string[];
}

const CATEGORY_SPECS: readonly CategorySpec[] = [
  {
    code: "2a",
    terms: [
      "assault rifle",
      "lethal autonomous",
      "kinetic weapon",
      "kinetic strike",
      "cyber-offensive",
      "cyber offensive",
      "munition",
      "warhead",
      "paramilitary contractor",
      "kill-chain",
      "kill chain",
    ],
    allow: [
      "historical",
      "treaty",
      "disarm",
      "ban treaty",
      "geneva",
      "red cross",
      "red crescent",
      "antiwar",
      "peace research",
      "forensic",
    ],
  },
  {
    code: "2b",
    terms: [
      "high frequency trading",
      "high-frequency trading",
      "hft strategy",
      "predatory loan",
      "predatory lending",
      "payday loan",
      "leverage spread",
      "arbitrage bot",
      "naked short",
      "pump and dump",
    ],
    allow: [
      "critique",
      "regulator",
      "prosecute",
      "fraud report",
      "consumer protection",
      "academic",
      "case study",
    ],
  },
  {
    code: "2c",
    terms: [
      "ad-tech dsp",
      "ad-tech ssp",
      "adtech dsp",
      "adtech ssp",
      "data broker",
      "behavioral targeting",
      "cross-site tracking",
      "fingerprinting sdk",
      "fingerprinter sdk",
    ],
    allow: [
      "privacy",
      "countermeasure",
      "defense",
      "defence",
      "audit",
      "eprivacy",
      "gdpr",
      "critique",
      "investigation",
    ],
  },
  {
    code: "advertising",
    terms: [
      "sponsored content",
      "promo code",
      "use my affiliate",
      "affiliate link",
      "discount code",
      "buy now",
      "limited time offer",
      "click my referral",
    ],
    allow: [],
  },
  {
    code: "eschatology",
    terms: [
      "rapture is coming",
      "millennial kingdom is at hand",
      "end times prophecy fulfilled",
    ],
    allow: [],
  },
];

const JA_PROHIBITED_PATTERNS: readonly RegExp[] = [
  /末法到来/u,
  /黙示録の獣/u,
  /千年王国は近い/u,
];

/** Return all category hits in `text`. Lowercases internally. */
function scanText(text: string): ScanRejection[] {
  const lower = text.toLowerCase();
  const out: ScanRejection[] = [];

  for (const spec of CATEGORY_SPECS) {
    for (const term of spec.terms) {
      if (lower.includes(term)) {
        if (spec.allow.some((a) => lower.includes(a))) {
          // Allow-band demotion — row is not rejected for this hit.
          continue;
        }
        out.push({
          category: spec.code,
          evidence: `matched ${spec.code} term: ${JSON.stringify(term)}`,
        });
        // One hit per category is enough; move to next category.
        break;
      }
    }
  }

  for (const pat of JA_PROHIBITED_PATTERNS) {
    if (pat.test(text)) {
      out.push({
        category: "ja-prohibited",
        evidence: "matched Japanese prohibited vocabulary",
      });
      break;
    }
  }

  return out;
}
