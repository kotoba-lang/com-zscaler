# 系図 (keizu) — government power-relations knowledge graph

> いまの政府関係の **調達・お金・発言・人間関係** を分析・公開する actor。お金の流れを公開情報から
> すべて追い、**委員会の構成メンバー**を分析して **kotoba Datom** として保持し、**social post** する。

keizu (系図 = a genealogy / relation chart) is the global government **power-relations** knowledge
graph. It is a sibling of danjo / kanae / tsumugi / tadori / ooyake, and fills the one clear gap
none of them covers: weaving **procurement + money + statements + human/network relationships +
committee/advisory-council composition** into a single relation graph keyed on **public roles**.

It is, by construction:

- an **accountability MAP, never a target-list** (G5; watatsuna/watari/tsumugi invariant);
- **non-adjudicating** (G2; danjo invariant) — it records observed ties and disclosed money
  shares, and never asserts a crime / 不正 / guilt (legal characterization routes to chigiri +
  external counsel);
- **edge-primary** (G4) — concentration is computed on read from incident edges; there is **no
  per-node power/influence score**;
- **public-role-only** (G1) — a node is a committee **seat** / ministry / agency / party role,
  **never a private individual** (the no-doxxing invariant);
- **sourced** (G3) — ≥2 public-source citations per relation / money flow;
- **no-server-key** (G7) + **outward-gated** (G8) — social posts are member-signed; live ingest
  and live posting are Council Lv6+ + operator + member-signature gated; R0 is offline + dry-run.

## What it produces

Run over the bounded `:representative` global seed, the analyzer surfaces:

- **committee cross-organ concentration** — how diverse (or concentrated) a committee's seats are
  by convening organ;
- **cross-committee co-membership** — a public seat sitting on more than one committee;
- **per-payee money concentration (HHI)** — award/subsidy/donation share by payee;
- **revolving-door chains** — organ → committee-seat movement;
- **dry-run social posts** — each opening with the mirror / non-adjudicating disclaimer.

## Substrate

- **State**: kotoba Datom log (ADR-2605312345) — `:node`/`:committee`/`:rel`/`:money`/`:statement`/`:post`.
  No Kotoba/Datomic/SQL.
- **Schema**: `00-contracts/schemas/government-relations-ontology.kotoba.edn`.
- **Lexicons**: `lex/relationEdge.edn` · `committeeComposition.edn` · `moneyFlowObservation.edn` ·
  `networkPost.edn` (`com.etzhayyim.keizu.*`).
- **Inference**: Murakumo-only (ADR-2605215000).

## Layout

```
20-actors/keizu/
├── CLAUDE.md                       # actor invariants (read first)
├── manifest.jsonld                 # Tier-B manifest (DID, cells, gates, non-goals)
├── README.md                       # this file
├── run_tests.sh                    # all suites (141 tests; see MATURITY.md)
├── data/
│   └── seed-relation-graph.kotoba.edn   # :representative global seed (public roles/organs)
├── lex/                            # 4 lexicons (com.etzhayyim.keizu.*)
├── methods/
│   ├── weave.py                    # validate + build graph + aggregate concentration (the heart)
│   ├── social.py                   # dry-run social-post projection
│   ├── ingest.py                   # offline normalizer (──live G8-gated)
│   ├── analyze.py                  # end-to-end → methods/out/intel-report.md
│   └── test_*.py                   # weave / social / ingest / charter-invariants / analyze / lexicons / consistency
└── cells/
    ├── ingest/ committee_graph/ money_graph/ relation_weave/ social_post/   # cell.py (.solve raises) + state_machine.py
    └── test_state_machines.py
```

## Run

```bash
cd 20-actors/keizu && ./run_tests.sh
cd methods && python3 analyze.py     # → out/intel-report.md + out/relation-graph.kotoba.edn
```

## Honest R0

Design + data-model + offline analyzer + dry-run posts. The seed is bounded `:representative`
(public roles/organs, rounded figures); nodes are public seats/organs, **never named private
individuals**. Live full-universe public-source ingest and live social posting are Council Lv6+ +
operator gated (Lv7+ for live publication under 1 SBT = 1 vote). See ADR-2606066000.
