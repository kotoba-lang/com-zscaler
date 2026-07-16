# meisai 明細 — MATURITY

**R0** (2026-06-12, ADR-2606122400)

| Axis | State |
|---|---|
| Methods | kotoba.py (commit-DAG writer + EDN reader) · ingest.py (G2-guarded EAVT) · autorun.py (intake heartbeat) — pure stdlib, pywasm-ready |
| Tests | 21 checks green across 2 standalone suites (ingest shape/determinism/G2 raise; autorun dedup/commit-DAG/tamper/G3 local-only) |
| Fetch leg | computer-use-clj `sumitclub_meisai.clj` — live-verified against local Ollama gemma 4 QAT (mock-host loop) 2026-06-12; real-desktop run is a member/operator step |
| Lexicon | none yet (R1) |
| Fleet | not registered (R1) |
| Data | none committed — `data/` gitignored by construction (G3) |

**R0 honesty**: the ingestion side is real and tested; the live browser fetch against
sumitclub.jp has not yet been exercised on the real desktop (requires the member's vault item +
macOS permissions). Aggregates, kaiyaku handoff, and additional card sources are R1+.
