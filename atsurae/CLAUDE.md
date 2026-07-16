# 20-actors/atsurae — CLAUDE.md

## What this is

**atsurae 誂え** — the **Product Line Engineering (PLE)** feature-model engine. The
product-FAMILY layer the roster lacked: **open-kyber 開** (ADR-2606037200) is the ERP,
**uchiwake 内訳** (ADR-2606081800) is the per-product BOM — but neither manages a *family* of
products derived from one model with shared commonality and bounded variability. atsurae is that
layer (誂え = bespoke, configured-to-order).

A feature model is a **COMMONS spec, never a license key** (no vendor lock). **SPEC +
DERIVATION only** — atsurae never manufactures (sanae/giemon/sarutahiko/funadaiku build, under
Council gate). Validity is **structural** constraint satisfaction, not a good/bad verdict.

`did:web:etzhayyim.com:atsurae` · `com.etzhayyim.atsurae.*` · ADR-2606212010 · clj-native R0.

## The model

- **FEATURE** `{:id :parent :kind :group}` — `:kind ∈ {:root :mandatory :optional}` (non-grouped
  child); `:group ∈ {:xor :or nil}` (`:xor` = exactly 1 child, `:or` = ≥1 child).
- **CONSTRAINT** `{:kind :from :to}` — `:requires` (A⇒B) / `:excludes` (¬(A∧B)).
- **BINDING** `{:feature :parts [{:part :qty}…]}` — feature → parts for BOM derivation.

## What it computes (`methods/feature_model.cljc`)

- `valid-config?` — structural cardinalities (mandatory / xor / or / orphan) + cross-tree
  constraints → `{:valid? :violations}`.
- `variants` — every valid complete variant (bounded structural enumeration ∩ constraints).
- `commonality` — feature → fraction of variants that include it (1.0 = common platform,
  0<·<1 = variation point, 0.0 = constraint-dead).
- `variation-points` — where the family actually varies.
- `derive-bom` — a variant's bill of materials (∪ of selected features' parts, qty summed) →
  hands off to **uchiwake** (BOM KG) + **open-kyber** (ERP).

On the synthetic OSS-robotics mobility-base seed: **15 features → 176 valid variants**; the
common platform is `{robot-base, locomotion, power}` (in every variant); `autonomy`/`tethered`/…
are variation points. `autonomy requires lidar`, `legs excludes tethered`, `autonomy excludes
tethered` are all enforced.

## Hard invariants (proven by tests)

- **G1 commons-not-license** — `:atsurae/license-lock` / `:atsurae/drm` unrepresentable.
- **G2 spec-only** — `:atsurae/manufacture` unrepresentable; the manufacturing actors build under Council gate.
- **G3 structural-not-adjudicating** — `:atsurae.product/verdict` unrepresentable.
- xor/or cardinalities + requires/excludes enforced; every enumerated variant re-validates.

## Composition

```
atsurae (feature model → valid variants + commonality)
   │  derive-bom(variant)
   ▼
uchiwake 内訳 (product BOM KG) → open-kyber 開 (ERP: cost/inventory/procurement)
   │
   ▼  (build a chosen variant)
sanae / hataori / kiyome / giemon / sarutahiko / funadaiku  — under Council gate (G2)
```

## Files

```
methods/feature_model.cljc  load + valid-config? + variants + commonality + variation-points + derive-bom + report
methods/emit.cljc           per-feature commonality + line-summary EAVT datoms (G1/G2 negative space enforced)
methods/kotoba.cljc         content-addressed append-only PRODUCT-LINE LEDGER (tamper-evident commit-DAG)
methods/autorun.cljc        deterministic, idempotent-by-content heartbeat — analyze → append ONLY on change
methods/test_*.cljc         cardinalities + constraints + commonality + BOM + G1/G2 invariants
kotoba/ontology.atsurae.edn EAVT schema + enums + negative space (license-lock/drm/manufacture unrepresentable)
kotoba/seed.edn             synthetic 15-feature OSS-robotics mobility-base line
data/ (gitignored)          generated product-line ledger — never committed/hand-edited
manifest.edn                gates G1–G7 + non-goals N1–N5
```

## Run

```bash
./20-actors/atsurae/run_tests.sh                                          # 2 suites (11 tests / 41 assert)
bb --classpath 20-actors 20-actors/atsurae/methods/feature_model.cljc     # print the product-line report
bb --classpath 20-actors 20-actors/atsurae/methods/autorun.cljc           # heartbeat → append to the ledger
```

## Pairs with

- **uchiwake 内訳** (per-product BOM, derive-bom hands off) · **open-kyber 開** (ERP) ·
  **sumitsubo 墨壺** (CAD geometry behind a feature).
- **sanae / hataori / kiyome / giemon / sarutahiko / funadaiku** (the manufacturing bodies that
  build a configured variant — under Council gate, never atsurae).

## R0 → later

- **R1**: SAT/BDD enumeration for large feature models (>16-wide or-groups); feature-model diff
  (commonality drift across product-line versions, as-of on the ledger); attribute features
  (numeric/cost constraints) feeding open-kyber cost accounting; uchiwake GTIN binding.
