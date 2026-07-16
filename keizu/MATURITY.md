# 系図 (keizu) — Maturity

**ADR**: 2606066000 · **Status**: 🟡 R0 (design + offline analyzer + dry-run) · **Updated**: 2026-06-06

## Stage ladder

| Stage | Scope | Gate | State |
|---|---|---|---|
| **R0** | ontology + 4 lexicons + `:representative` global seed + analyzer (weave/concentration/social/ingest) + 5 cell scaffolds (`.solve()` raise) + tests | ADR-2606066000 (PROPOSED) | ✅ landed |
| R1 | ingest + committee_graph + money_graph build kotoba EAVT datoms over **offline** public-source batches; no live posting | Council Lv6+ ≥3 per cell | ⏳ (source registry ready — `registry/sources.seed.json`, 12 sources `unverified-seed`) |
| R2 | +relation_weave aggregate concentration on live read-path; first dry-run networkPosts reviewed | Council Lv6+ ≥4 + 30-day public comment | ⏳ |
| R3 | +social_post live publication under 1 SBT = 1 vote + member signature; live public-source ingest | Council Lv7+ + operator | ⏳ |

## R0 evidence

- **Tests**: `./run_tests.sh` green — **180 tests** across weave (57) / social (8) / ingest (17) / sources (8) / registry (7) / bridge (10) / export (6) / charter-invariants (28) / analyze (13) / lexicons (5) / consistency (6) / cells (12) / membrane-flow (3).
- **Per-jurisdiction slice** (`by_jurisdiction`): the core country-level view of a GLOBAL graph — node + committee counts and total disbursed money per jurisdiction (money attributed to the PAYER's jurisdiction). On the seed: jp 10 nodes/2 committees/¥2.3B · us 5/1/$40M · eu 2/0/€2M · oecd 1/0/0. Wired into the report + kanae payload.
- **Empty-graph report path covered**: `analyze.run` over an empty seed exercises the `"(none in seed)"` fallbacks (cross-committee / connector / revolving-door / award-and-fund), the 0-dangling integrity line, and the empty posts/kanae-flows — the degenerate branches the populated seed never reaches.
- **Deny-list on the outbound post path**: `social._enough_sources` now also runs `source_denied`, so a dry-run social post citing a commercial gov-intel terminal is refused (Rider §2(e)/N5). The deny-list now covers BOTH the inbound datoms (rel/money/statement/committee) and the public-facing post output.
- **Committee validation** (`validate_committee`): committees are now gate-checked like the other datoms — id + ≥1 public seat (G1) + ≥1 public source with no prohibited terminal (G3/Rider §2(e)) + declared sourcing (G11). Enforced in `weave()` (seed) AND `ingest.normalize_committee`. All five datom types (node/committee/rel/money/statement) now have a validator on every write path.
- **Deny-list uniform across all sourced datoms**: the commercial-gov-intel `SOURCE_DENY` is now enforced in `validate_statement` too (was only rel/money) — a statement citing a prohibited terminal is refused (Charter Rider §2(e)/N5). All three sourced datom types (rel/money/statement) now share the same source gate.
- **Money amount robustness**: `validate_money` requires a finite, non-negative amount (absent→0 allowed) — a negative / NaN / inf / non-numeric amount is refused, protecting the HHI/share math in `money_concentration`/`payer_concentration` from corruption. Enforced on every path (seed/ingest/bridge).
- **Registry-driven sourcing on ingest** (G11): if an ingest record names a registry `sourceId`, the REGISTRY's verification status wins — a caller cannot forge `:authoritative` for an unverified source (it stays `:representative` even if the record claims otherwise). With no `sourceId`, the caller's declared sourcing is honored.
- **Deny-list is now a RUNTIME invariant** (`SOURCE_DENY` + `source_denied` in `weave.validate_rel`/`validate_money`): a derived datom citing a commercial gov-intel terminal (GovWin/Bloomberg Government/FiscalNote/四季報/CapIQ/…) is refused on EVERY path — seed, ingest, bridge (Charter Rider §2(e)/N5). `registry.py` reuses the same constant and drives **G11 sourcing from verification status** (`sourcing_for`: :authoritative only when the registry marks a source verified, else :representative).
- **R1-readiness — public-source registry** (`registry/sources.seed.json` + `VERIFICATION.md`): 12 global primary sources (JP/US/EU/OECD procurement · budget · political-finance · committee rosters · statements), each with a `mapsTo` keizu datom type, all `unverified-seed` (G8 — no live ingest until Council Lv6+ + operator verifies). The **no-commercial-gov-intel deny-list** (GovWin/Bloomberg Government/FiscalNote/四季報/CapIQ/… — Charter Rider §2(e), N5) is enforced by `test_sources.py`.
- **Pipeline output**: `analyze.run` emits 3 downstream artifacts — `intel-report.md` (human), `relation-graph.kotoba.edn` (validated datoms), `kanae-render.json` (the kanae viz payload). The keizu→kanae export is now PRODUCED by the pipeline, not only unit-tested.
- **Referential integrity** (`check_integrity` / `assert_integrity`): catches DANGLING refs the per-record validators miss — a `:rel`/`:money`/`:committee`/`:statement` pointing at a non-existent entity. Correct id-space per field (a `:rel` endpoint may be a node OR committee OR statement; money/members/speaker → node). Seed verified 0 dangling; analyzer reports the count as an honesty line.
- **Ingest node validation** (`normalize_node`): the ingest path now validates NODES through `validate_node`, carrying through extra raw fields so a smuggled PII / power-score / private-scope field is REFUSED on ingest (not only on the seed via `weave()`). A bad node aborts the whole batch — G1/G4/G9 hold at the live-ingest boundary too.
- **Cell-chain integration** (`cells/test_membrane_flow.py`): threads one public-source batch through all 5 cells in sequence (ingest→committee_graph→money_graph→relation_weave→social_post), the relation_weave finding becoming the social_post subject — proving the cells COMPOSE into the documented pipeline, not just pass in isolation. Head-refusal (private node) and tail-refusal (published request) both abort the chain.
- **keizu → kanae export** (`export.py`): fiscal `:money` → kanae fundFlowEdge shape (the outbound side of `bridge.py`) + a JSON-safe `render_payload` (Sankey/treemap-ready, carries isMirror/nonAdjudicating). `:political-donation` excluded as non-fiscal (honest skip count, no silent drop). Round-trip keizu→kanae→bridge→keizu preserves kind+amount for all 4 fiscal kinds.
- **Statements (発言)**: `validate_statement` (speaker + ≥1 source G3 + sourcing G11, enforced in `weave()`) + `statement_index` (per-speaker count + per-topic speaker set) — indexed by topic, never rated true/false (ake/danjo own truth-rating). Closes the 発言 dimension of the original brief.
- **G9/G1 no-doxxing guard** (`PII_FORBIDDEN_NODE_ATTRS` + `validate_node`): a public-seat node carrying a personal-contact/sensitive field (email/phone/address/dob/mynumber/passport/face/health/…) is REFUSED in code — enforced across seed + ingest. Any such datum lives encrypted off-graph (ADR-2605181100).
- **G10 as-of time-travel** (`active_as_of`): the append-only graph is queryable at any timestamp — a query at an earlier ts sees fewer datoms; nothing is overwritten (非終末論). Monotonic, verified.
- **Cross-organ connector seats** (`connector_seats`): a seat bridging committees from >1 convening organ, derived on read from `:committee-membership` edges + each committee's organ (edge-primary, G4).
- **Cross-actor bridge** (`bridge.py`): maps danjo crossReferenceLink → keizu `:rel` + kanae fundFlowEdge → keizu `:money`, re-asserting keizu's OWN G2/G3 gates at the import boundary (a verdict category or an under-sourced record is REFUSED — a sibling cannot smuggle a charter violation into keizu). The charter-invariant suite parses all THREE homes of each structural gate (ontology `:db/allowed`/closed-vocab + lexicon `:const`/`:enum` + seed values) and asserts they agree, AND drift-locks lexicon enum ⊆/⊇ ontology closed vocab **both directions** (rel-kinds, money-kinds, sourcing-grades, post-status).
- **Analyzer** (`analyze.py` over the seed: 18 public role/organ nodes / 3 committees / 15 rels / 6 money / 3 statements): committee cross-organ concentration, 1 cross-committee co-membership seat, money HHI ≈ 0.96 **by payee + by payer (jp-meti top disburser)**, 1 revolving-door chain, 2 dry-run mirror posts.
- **Registration**: `did:web:etzhayyim.com:actor:keizu` in `tier-b-actors.gen.ts` + `actor-profile-seed.kotoba.edn`.

## Invariant coverage (structural, 3 places each)

| Gate | ontology | lexicon | python | test |
|---|---|---|---|---|
| G1 public-role-only | `:node/scope :db/allowed` | members `:enum` | `validate_node` | `test_charter_invariants`, `test_weave` |
| G2 non-adjudicating | rel/money `:db/allowed` + notice `[true]` | `:enum` no-verdict + `:const true` | `validate_rel`/`validate_money` | `test_weave`, `test_ingest` |
| G3 ≥2 sources | `:rel/:money/sources` doc | `:minLength 2` | `validate_*` | `test_weave`, `test_social` |
| G4 edge-primary | no `:node/power-score` attr | — | `validate_node` raise | `test_weave`, `test_charter_invariants` |
| G7 no-server-key | `:post/server-held-key [false]` | `:const false` | `social._post` / cell | `test_social`, `cells` |
| G8 outward-gated | `:post/status [:dry-run]` | `:const "dry-run"` | `build_live`/`ingest_live`/`.solve()` | `test_social`, `test_ingest`, `cells` |

## Known R0 limits (honest)

- The seed is bounded `:representative` (public seats/organs, rounded figures), **not** a live authoritative capture; nodes are public roles, never named private individuals.
- No live public-source ingest (官報 / 政治資金収支報告書 / 調達ポータル / Federal Register / USAspending / TED / OECD) — G8-gated.
- No live social posting — G7/G8-gated (member signature + Council Lv6+/Lv7+).
- LLM narration (G6) is not yet wired; the analyzer is deterministic.
- Cells `.solve()` raise; only the coded state machines run.

## Next maturity steps (loop targets)

- ✅ ~~lexicon enum ⊆ ontology closed vocab drift-lock (both directions)~~ (iter 2).
- ✅ ~~payer-side money concentration (HHI by payer)~~ (iter 2).
- ✅ ~~award-and-fund co-occurrence (a node that both receives public money AND donates), FACTUAL + non-adjudicating, multi-hop money composition~~ (iter 3) + report-level no-verdict-language assertion.
- ✅ ~~`bridge.py` mapping danjo crossref + kanae fiscal edges into keizu `:rel`/`:money`, with keizu gates re-asserted at the import boundary~~ (iter 4). Next: run it against REAL sibling outputs once danjo/kanae cells emit (R1).
- ✅ ~~G10 as-of time-travel (`active_as_of`) + cross-organ connector metric (`connector_seats`), edge-primary~~ (iter 5).
- Bind the relation graph onto ooyake gov-unit ids (a `:node/organ` → ooyake unit resolution check).
- Add a term-to / end-of-tenure field so as-of windows are bounded (not just open-ended term-from); recompute connectors per as-of snapshot.
- MIGRATION-NOTES for any legacy gov-relation surface keizu supersedes.
