# aburi 炙り

**Personal-tracking-exposure observatory (member-side, own-data).** Answers: *when I accept the
ToS / grant a permission on Google · Facebook · X · Apple, **which** ad networks & data brokers
collect my data, and **how much** does each one track me* — and routes that exposure to **relief**
(DSAR / opt-out / sever / on-device revoke).

The member-side, own-data inversion of the ad-tech lineage: **sukashi 透かし** maps the firm↔firm
programmatic supply chain; **aburi** maps *your* exposure and makes the asymmetric ad-watcher
visible to the watched (§2(c) v3.1 — symmetric 相互監視 affirmed). It holds only the member's OWN,
local-only data, builds no dossier of anyone, never tracks, never sells.

- **ADR**: 2606161630 · **Status**: 🟡 R0 design-only · **Tier**: B
- **Ontology**: `00-contracts/schemas/tracker-exposure-ontology.kotoba.edn`
- **Gates**: G1 own-data-only · G2 edge-primary · G3 non-adjudicating · G4 reciprocity-restoring ·
  G5 sourcing-honesty · G6 Murakumo-only · G7 outward-gated + local-only · G8 no-credential/raw-id

```bash
bb test:aburi                                  # 14 cljc (1369 assert) + 17 python (ingest/bridge) green
bb aburi:ingest --cycles 1                     # (B) ingest member exports → local commit-DAG
bb aburi:build-wasm                            # (C) build WASM component + CID (operator)
cd 20-actors/aburi && python3 methods/analyze.py   # → out/tracking-exposure-report.md
```

> **bb is the standard runner — no `.sh` scripts in this repo.** Tasks live in the root `bb.edn`
> (`test:aburi`, `aburi:ingest`, `aburi:bridge`, `aburi:build-wasm`, `aburi:publish`); impl in
> `tools/{build,publish}.clj`.

See `CLAUDE.md` for the full constitutional discipline and the relief-routing chain.
