# kamado (竈) — closed-loop refining + fossil-refinery decommission/transition actor

**DID**: `did:web:etzhayyim.com:actor:kamado` · **Tier**: B · **Status**: R0 · **ADR**: 2606051500

## What this is

The charter-clean answer to *「石油精製のプラントの actor・robotics は設計されているか」*. 竈 = the
sacred hearth/furnace (竈神/荒神, the kami of transforming matter by fire). The transformation
apparatus (distillation/cracking/reforming/hydrotreating) is morally neutral; the **carbon source**
and the **carbon fate** carry the multi-generational harm. kamado reclaims the furnace for
closed-loop carbon and refuses the fossil feedstock **by construction**.

Three faces over the kotoba Datom log:
- **A. observation** — the kotoba-native successor to the legacy `oil-refining` Cypher actor
  (which it **supersedes**; no RisingWave/`graph.write`). Observes public refinery/unit/outage
  assets + transition-readiness. A resilience + **transition** map, NEVER a target-list (G4);
  observation ≠ operation.
- **B. decommission/transition robotics** — §2(d)-permitted: safely wind down / remediate /
  **convert** existing fossil refineries (→ hikari solar / synthesis plant / hodoki+kanayama
  materials recovery), removing humans from H₂S/benzene/pyrophoric hot zones (G9).
- **C. closed-loop synthetic refining** — distillation/cracking/reforming on **biogenic /
  captured-CO₂ / recycled carbon ONLY**; drop-in fuels & feedstock with net atmospheric carbon
  Δ ≤ 0 (D3). tazuna-style member-signed Transparent-Force process-control (G5).

ISIC C1920 · ISCO 3134/8131/9311 · UNSPSC 15/71.

## The honest thesis (empirically demonstrated — `methods/carbon_balance.py`)

A fossil→combusted pathway is **+3.50 tCO₂e/t**. Full robotic advanced-process-control on the
*same* fossil pathway only reaches **+3.38** — a ~3% cut, all from the ~11% process slice;
origin+fate (~89%) is in the carbon, untouchable by automation. **Robotics makes fossil refining
cleaner, never harmless.** The only pathways that reach net ≤ 0 change the **feedstock** to
closed-loop carbon. So kamado forbids the fossil feedstock structurally rather than pretending
control can fix it.

## The G1 invariant lives in THREE places (mirror of nusa `:thc-class`)

1. **schema** `00-contracts/schemas/refining-ontology.kotoba.edn` — `:feedstock/class :db/allowed`
   excludes `:fossil-virgin-crude`.
2. **lexicon** `lex/feedstockProvenance.edn` / `lex/synthesisRun.edn` — `feedstockClass` enum has
   no `fossil-virgin-crude` member; `closedLoop`/`screened` `const true`.
3. **code** `methods/feedstock_guard.cljc` + `cells/feedstock_guard/state_machine.py` — `ValueError`
   on any fossil feedstock (and `screen_intervention` raises on `:expand`/`:restart-fossil`, G3).

## Cells (langgraph→WASM; Murakumo-only; `.solve()` raises at R0)

asset_observation (reuben) · **feedstock_guard** (simeon — coded; G1 closed-loop screen + G2/D3
carbon balance) · **decommission_plan** (levi — coded; G3 wind-down/convert state machine) ·
synthesis_control (judah — process-control scaffold; G5/G11) · transition_bridge (zebulun — routes
site→hikari, unit→hodoki/kanayama, policy→danjo/moushibumi; never lobbies).

## Gates (immutable)

G1 closed-loop-carbon-only · G2 net-carbon-Δ≤0 (D3) · G3 decommission/transition-only (§2(d)) ·
G4 observation-not-target-list · G5 no-server-key · G6 Murakumo-only · G7 sourcing-honesty ·
G8 outward-gated · G9 labor-liberation/displacement-dividend · G10 local-harm-min/honest-bound ·
G11 safety-honesty (not a certified safety system) · G12 no-persistence-laundering. (Full text:
`manifest.edn` / ADR.)

## Build / test

```
cd methods && python3 carbon_balance.py && python3 analyze.py
cd methods && python3 ingest.py    # legacy oil-refining graph export → kotoba EAVT + kg.ingest_batch
cd methods && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest test_kamado.py test_ingest.py  # 11+6
cd cells   && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest test_state_machines.py          # 8
```

**Legacy migration** (supersedes `oil-refining`): `methods/ingest.py` converts a legacy
RisingWave/Cypher node export (Refinery/RefineryUnit/RefineryOutage) → kotoba EAVT datoms +
a `kg.ingest_batch` body (dedup vs seed; G4 no-person/org-operator; G1 `:observed-fossil`;
G7 `:representative`). Live legacy read + KV/kotoba promotion are operator-gated (G8) — see
`20-actors/oil-refining/MIGRATION-NOTES.md`.

(Repo pytest plugin env is broken — `pydantic`/`langsmith`; the `PYTEST_DISABLE_PLUGIN_AUTOLOAD=1`
prefix runs the suites in isolation.)

## Do not

- Do not add `:fossil-virgin-crude` (or any fossil feedstock) to the `:feedstock/class` allowed set,
  the lexicon enum, or the guard — G1 / §2(d) / D3 (it cannot be added; Lv7+ would still violate §2(d)).
- Do not introduce an `:expand` / `:restart-fossil` / throughput-revamp intervention — G3 (§2(d)
  permits wind-down/convert of existing assets only; never life-extension).
- Do not present asset observation as an interdiction/target output — G4 (a transition map, like
  watari/watatsuna).
- Do not call any cell `.solve()` — R0 raises `RuntimeError`. No live process actuation / teardown
  without Council Lv6+ + operator + a certified-safety review — G8 / G11.
- Do not hold a process-control / actuation key server-side — G5 (member/operator signs).
- Do not reintroduce RisingWave / Cypher / `graph.write` — N6 (supersedes legacy `oil-refining`).
- Do not make **etzhayyim kotobase (`kotobase.net` / `authn.etzhayyim.com`) the canonical write/auth path** —
  Ownership invariant + Murakumo-only consent boundary. Canonical state = etzhayyim's OWN kotoba
  endpoint + etzhayyim DID-bound auth (`ingest.py --push` requires `KOTOBA_ENDPOINT`+`KOTOBA_AUTH`,
  no vendor default); etzhayyim is an OPTIONAL pinning mirror only (`--mirror-etzhayyim`, content-addressed copy).
- Do not supply military/naval/aviation fuel — N3 (§2(a) force-separation).
- Do not lobby for/against fossil policy — route to danjo (facts) / moushibumi (neutral comment).
