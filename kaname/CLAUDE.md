# kaname 要 — cross-domain system-of-systems leverage (律速) synthesizer + おせっかい proposer

**ADR-2606172100** · Tier-B · clj-native (`.cljc`, babashka) · the META synthesis layer above the
power-mirror lineage.

## What it is

kaname is the **system-of-systems (SoS)** layer the roster lacked. The mirror lineage gives one
observatory per domain — tsumugi (power 取), keizu (gov), kabuto (supply), chie (AI), shiori
(Wellbecoming), abaki (monopoly), shionome/busshi/hokorobi (capital/commodity/finance),
kosatsu (designations), inochi (ecology). kaname **joins** them into ONE **multilayer (multiplex)
graph** — layers = domains (politics / religion / organization / ideology / economy / ecology /
security / wellbecoming / ai / information / **energy**) — and **mathematically** identifies the single
structural position (the 要 / 律速段階) whose release would most improve resilience **across the
maximum number of domains at once**, then **proposes** the intervention (おせっかい) to ossekai.

It is the **fan-rivet (扇の要)**: the one point on which the whole 縁-web pivots.

### Relationship to the two adjacent actors

| | junkan 循環 | **kaname 要** | ossekai 御節介 |
|---|---|---|---|
| view | system-dynamics CLD of society | **multilayer-graph centrality over the actor mirrors** | — |
| leverage | Meadows 12 qualitative levels | **mathematical: L = C·(V/D)·(1+B)·(1−open)** | — |
| acts? | analysis-only | **proposes** (advisory, no-server-key) | **carries** (consent-bound) |

kaname is **not** junkan (CLD) and **not** ossekai (actuator). It is the graph-centrality
synthesis between them.

## The math (on read; edge-primary; N1/G4)

- `C_i` cross-domain **concentration** = Σ inbound 取-load to *i* across all domains.
- `V_i` domain **versatility** = # distinct domains *i* bears load in. **The SoS discriminator** —
  a one-domain hoarder is NOT the 要; the 要 spans many.
- `B_i` **bridge-load** = Σ incident inter-layer connective load (`:couples`/`:gates`) — a bounded,
  deterministic proxy for inter-layer betweenness (full multiplex betweenness = R1).
- **律速 score** `L_i = C_i · (V_i / D) · (1 + B_i) · (1 − open_i)`. **要 = argmax L_i.**
  `L_i ≈ ΔΦ`, the drop in aggregate cross-domain fragility if *i*'s concentration is opened.

On the seed: the cross-domain Accreditation Interface is the 要 (L=11.7); the Capital
Concentrator out-concentrates the Doctrine instrument yet has **lower** leverage (V=1) — proving
**concentration alone is not the bottleneck**; the open commons scores 0 despite high C.

### `:energy` domain layer (ADR-2606212000, landed)

The 11th multiplex layer. Energy was previously implicit inside `:organization`/`:economy`;
making it explicit lets a node bearing load in BOTH an energy chokepoint AND another domain
surface with higher versatility (the SoS discriminator). Fed by **amime 網目**'s multi-site
mesh (ADR-2606212020): amime commits a kaname-form `:energy` graph (`20-actors/amime/out/
energy-sos.kotoba.edn` — flow `:concentrates` onto loads, single-path import is a `:depends-on`
SPOF), joined via the `:amime` adapter in `join.cljc`. Adding the layer took `D` 10→11, which
rescales every `L` by 10/11 uniformly — **the argmax (the 要) is invariant** (test-pinned).
Running amime = G7; joining its committed output = what kaname does. See `tests/test_energy_join.cljc`.

## Constitutional gates (enforced in code + tests — `methods/gates.cljc`)

- **G1 — leverage MAP, never a target-list.** Structural positions only; natural persons
  person-excluded (public ROLEs allowed); no coordinates. `osekkai` refuses a person/coordinate.
- **G2 — OPENING-only routing.** route enum = {open, route-around, add-redundancy, decentralize,
  insufficient-evidence}; **capture / seize / control / monopolize unrepresentable** (`route` raises).
  The whole point of finding the 要 is to **dissolve** it, never to grab it.
- **G3 — おせっかい transparent + consent-bound.** kaname PROPOSES (advisory/unsent/no-server-key);
  ossekai CARRIES (on-chain-logged, structural-first §1.4).
- **G4 — non-adjudicating + edge-primary.** Reads DISCLOSED per-domain concentration; integral on
  read; no `:kaname/score-of-entity`.
- **G5 — no thought-policing.** Ideology/religion = STRUCTURAL interfaces with an on-the-record
  `:en/basis`; belief-content scoring (`:belief/wrongness`/`:faith/rank`) unrepresentable.
- **G6 — synthetic seed; live mirror join G7/Council-gated; no-server-key.**

## Layout

```
methods/
  sos.cljc             EDN reader + multilayer load + leverage (C/V/bridge/L, on read) + report   → out/leverage-report.md
  centrality.cljc  R1  exact Brandes betweenness + eigenvector + ΔΦ percolation; L1 (real B inside) → out/centrality-r1.md
  join.cljc        R1  live mirror JOIN: lift a mirror's committed Datom log into a domain layer    → out/joined-ai-leverage.md
                       + reconcile-by-label across layers (shared entity → spans domains → 要)
                       + mirror-adapters registry + join-mirrors (6 real mirrors)                   → out/joined-sos-leverage.md
  ingest.cljc      R1  WEB→mirror: fetch public page (homepage/公開投稿) IN CLJ (babashka.http-client) → data/ingested-web.kotoba.edn
                       → Murakumo gemma-4-E4B extract DISCLOSED org relations, every edge :en/basis'd (G5), person-excl (G1)
                       ingest-live! over data/ingest-sources.edn = actor-runtime live fetch leg (G7)
  kotoba.cljc      R1  persist leverage readout → kotoba Datom-log commit-DAG (EAVT, CID-chained,    → data/persisted/ (gitignored)
                       idempotent-by-content, verify-chain tamper-evident; shared kotoba.datom)
  kotoba_bridge.cljc R1 push local commit-DAG → LIVE kotoba engine :8077 (…datomic.transact)        → remote Datom graph
                       host allowlist + graph-cid + :kaname.tx/* provenance + :bridge/* exactly-once cursor
graph.cljc         R1  langgraph-clj StateGraph ACTOR: :perceive-world(世界認識)→:leverage→:route→:osekkai→:persist
autorun.cljc       R1  autonomous heartbeat (invoke graph; cycle = log length; resume-safe; bb -main)
cell.cljc          R1  cell-runner entry `fire` (KanameHeartbeatCell, node naphtali, cron 53 * * * *, healthz 13083)
  route.cljc           route the 要 to OPENING; refuses capture (G2)                               → out/opening-route.md
  osekkai.cljc         ossekai handoff proposal (advisory/unsent); refuses person/coordinate (G1)  → out/osekkai-handoff.md
  gates.cljc           constitutional gate assertions (ex-info) — G1/G2/G5
  datom_emit.cljc      kotoba Datom log (GROUND :add + DERIVED :derived leverage integrals)        → out/sos-leverage-datoms.kotoba.edn
  coverage_report.cljc domain/mirror coverage honesty (G6)                                          → out/coverage-report.md
kotoba/schema.edn      :sos-leverage ontology
data/seed-sos.kotoba.edn        SYNTHETIC illustrative multilayer seed (13 nodes / 20 縁 / 8 of 10 domains)
data/fixture-mirror-datoms…edn  tiny synthetic mirror Datom-log (join test fixture)
tests/                 test_{sos,gates,route,osekkai,coverage,centrality,join}  (34 tests / 142 assertions)
00-contracts/lexicons/com/etzhayyim/kaname/{leveragePoint,osekkaiProposal}.json  (canonical home)
```

### R1 (landed) — real centrality + proven live join

- **centrality.cljc**: exact Brandes betweenness (40.3 ≫ 11.2 on the seed), eigenvector (power
  iteration), ΔΦ fragmentation sensitivity — all converge on the same 要. `L1 = C·(V/D)·(1+B'·)·(1−open)`
  with real betweenness replacing the R0 bridge proxy.
- **join.cljc**: PROVEN on **chie 智慧's REAL committed Datom log** — parsed 39 nodes / 39 縁, lifted
  into the `:ai` layer (34 kaname 縁; unmapped `:partners`/`:holds-role` dropped — no fabricated axis).
  Lifted `:ai` concentration reproduces chie's own opening-priority (OpenAI 5.55, Anthropic 4.60);
  kaname adds its own betweenness (OpenAI 114 / EU-AI-Act 66 / NVIDIA 64). `reconcile-by-label` merges
  a shared entity across mirrors so it spans layers → versatility grows → it becomes the 要.
  **Running a mirror to (re)produce output = G7-gated; joining a committed output = what kaname does.**
- **multi-mirror SoS join (founder-approved 06-17)**: `mirror-adapters` + `join-mirrors` joined **5
  real committed mirror outputs** — chie(:ai)·tsumugi(:organization)·inochi(:ecology)·hokorobi(:economy)·
  shiori(:wellbecoming) → ONE reconciled graph (170 nodes / 205 縁 / 5 domains; forms/datom
  auto-detect via `parse-graph`; per-mirror load-normalized). `reconcile-by-label` surfaced
  **OpenAI·NVIDIA·Microsoft·TSMC·SoftBank** as cross-domain entities (V=2, :ai+:organization);
  whole-multiplex 要 = **OpenAI** (L1 1.992); top bridges **NVIDIA(betw 523)/TSMC(456)** (compute
  chokepoints). → `out/joined-sos-leverage.md`. Adding a mirror = add an adapter entry; re-running a
  mirror stays G7.
- **real web-ingest (founder-approved 06-17)**: `ingest.cljc` fetches a PUBLIC page (homepage /
  公開投稿 / announcement) → extracts DISCLOSED org relations via **Murakumo local Ollama
  gemma-4-E4B** (ADR-2605215000) → mirror forms, **every edge `:en/basis` = source URL + stated
  phrase** (G5), person-excluded (G1), DISCLOSED-only (G4), no-server-key. Ran on real official
  sources (anthropic.com/news, nvidianews.nvidia.com) → **10 orgs / 11 basis'd edges** →
  `data/ingested-web.kotoba.edn` (`:economy`, `:authoritative`), joined as the 6th mirror. Result:
  **OpenAI → V=3** (:ai+:economy+:organization), **L1 1.992 → 3.513** — the cross-domain 要 grounded
  in cited public data. Adding a source = drop a `.txt`+`.url` in `data/ingested-pages/` and re-run
  (G7 operator step); the committed `.kotoba.edn` is the durable artifact.

### langgraph-clj actor + kotoba commit-DAG + 世界認識 (founder-approved 06-17)

kaname is now a first-class **langgraph-clj StateGraph actor** (`kaname.graph`, loads under bb):

```
:perceive-world (世界認識)  → join every committed mirror into the cross-domain WORLD MODEL
                              (+ optional live clj web-fetch when :live?, G7)
:leverage                   → sos/leverage + R1 centrality → the 要
:route                      → route the 要 to OPENING (G2)
:osekkai                    → ossekai handoff proposal (advisory/unsent, G1/G3)
:persist                    → append the readout to the kotoba Datom-log commit-DAG
```

- **web-fetch も clj**: `ingest/fetch-text` (babashka.http-client, anonymous GET, no-server-key) +
  `ingest-live!` over `data/ingest-sources.edn` — the ACTOR runtime fetches, not an operator tool.
- **datomic kotoba**: `methods/kotoba.cljc` persists to the canonical kotoba Datom-log as a
  content-addressed commit-DAG (shared `kotoba.datom`): EAVT `[:db/add e a v]`, CID-chained,
  **idempotent-by-content**, **verify-chain** tamper-evident, resume-safe, `data/persisted/` gitignored.
- **heartbeat**: `kaname.autorun` — `bb 20-actors/kaname/autorun.cljc [base] [log] [--live]`. Verified
  live: beat#0 perceived 6 mirrors (173n/216縁) → 要=OpenAI → persisted; beat#1 `:no-change`.

### LIVE-engine bridge + fleet registration (founder-approved 06-17)

- **`methods/kotoba_bridge.cljc`** (ibuki-R3 pattern): pushes each local commit-DAG tx to the LIVE
  kotoba engine (`com.etzhayyim.apps.kotoba.datomic.transact`). Host allowlist (loopback + EVO-X2 LAN);
  `graph-cid` KotobaCid parity; `:kaname.tx/*` provenance; `:bridge/*` exactly-once cursor;
  `expected_parent`; DRY-RUN default, `KANAME_KOTOBA_LIVE=1` for live. **Verified vs running :8077**:
  dry-run computed graph CID `bafyrei…`; live POST reached the endpoint, the server parsed the unsigned
  operator bearer and gated only on the operator-DID value → set `KANAME_KOTOBA_OPERATOR_DID` (the
  node's public operator DID) to land the authenticated commit (the documented G7 operator step).
- **fleet**: `KanameHeartbeatCell` in `50-infra/cluster/murakumo/cell-runner/cells.edn` (node naphtali,
  cron `53 * * * *`, healthz 13083) → `kaname.cell/fire` (one local heartbeat; live legs operator-gated).

## Run

```bash
# from repo root (bb.edn :paths includes 20-actors)
bb -e '(require (quote clojure.test) (quote kaname.tests.test-sos) (quote kaname.tests.test-gates) \
                (quote kaname.tests.test-route) (quote kaname.tests.test-osekkai) (quote kaname.tests.test-coverage)) \
       (clojure.test/run-tests (quote kaname.tests.test-sos) (quote kaname.tests.test-gates) \
         (quote kaname.tests.test-route) (quote kaname.tests.test-osekkai) (quote kaname.tests.test-coverage))'

# ie-flow / SoS score (ADR-2606212200) — needs the shared lib + kotoba.datom on the classpath:
bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/kaname/methods/ie_flow.cljc          # flow-state
bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/kaname/tests/test_ie_flow.cljc       # 4 tests / 12 assertions
```

## ie-flow / SoS score (`methods/ie_flow.cljc`, ADR-2606212200)

kaname is scored as an **information-control actor** in the SoS scoreboard (it tops it — score
**0.514**). Via the SHARED `etzhayyim.ie-flow.gate-adapter`: volume = raw cross-domain concentration
**C** (the scattered input), value = leverage **L** · route-factor (L re-weights C by versatility +
bridge, CONCENTRATING order onto the few true 要 — that re-weighting IS the rectification, order-index
0.555). Every route is OPENING-only (open/decentralize/route-around/add-redundancy; capture
unrepresentable, G2). `record-flow!` → `80-data/ie-flow/kaname/` (gitignored). Synthesis-only — kaname
proposes; the colony's score feeds the artificial organism's reward.

## Roadmap

- **R1 (landed 06-17)** — exact Brandes betweenness + eigenvector + ΔΦ percolation sensitivity
  (`centrality.cljc`); the live mirror-join machinery proven on chie's real output (`join.cljc`).
  Remaining R1: full multiplex tensor versatility (De Domenico et al.); joining MORE mirrors
  (kabuto/tsumugi/keizu/…) as their outputs are committed (the run-the-mirror leg stays G7).
- **R2** — ossekai-carried structural-first intervention loop (on-chain-logged; 1 SBT = 1 vote on
  any proposal touching a real institution).

## Non-goals

not a predictor (mitooshi/hakoniwa) · not the CLD view (junkan) · not an actuator (ossekai) · not a
per-person target engine (G1) · not a belief judge (G5) · not commercial intel/BI/SaaS (Rider §2(e)+(c)).
