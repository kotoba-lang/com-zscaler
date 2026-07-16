# 20-actors/junkan — CLAUDE.md

## Identity

- **Name**: junkan (循環 — circulation / cycle)
- **DID**: `did:web:etzhayyim.com:actor:junkan` (canonical; `alsoKnownAs did:web:junkan.etzhayyim.com`) — **REGISTERED** in did-web (`50-infra/etzhayyim-did-web/public/actor/junkan/{did,profile}.json`), per ADR-2606013800 + ADR-2606272355
- **ADR**: ADR-2605290927 (R0 scaffold, 2026-05-29); **ADR-2606272355** (self-publication seed on the kotoba mesh, 2026-06-27)
- **Parent ADR**: ADR-2605192100 (Mission Charter — §1.4 anti-individualism + §1.13 Wellbecoming + §1.15 non-eschatological + §1.12 routing-around)
- **Status**: R0 scaffold — 8 cells path-reserved + 5 Lexicon skeletons
- **Form**: 任意団体 internal societal-systems-analysis substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## What junkan is

junkan performs **systems-thinking on society at large**. From **passive,
public, aggregate** data it continuously builds a system-dynamics model —
**stocks**, **flows**, and **reinforcing (R) / balancing (B) causal loops** — and
reads off which loops are currently spinning **virtuous (好循環)**, **vicious
(悪循環)**, **neutral**, or **transitioning**, plus **Meadows leverage-point
candidates**.

It is the **outward** sibling of two existing inward facilities:

- the active-inference prior (`90-docs/2605221243-...`) — the same stock-flow +
  R1–R4 / B1–B4 loop modeling, applied to the organism *itself*;
- `KaizenObserver` (ADR-2605240200) — continuous self-reflection.

junkan turns that systems-thinking *toward society*, under one discipline:
**it may only look, never touch.**

## Constitutional Discipline (CRITICAL — IMMUTABLE)

The defining property is **analysis-only (分析するだけ)**:

1. **G4 — ANALYSIS-ONLY / NO ACTUATION.** junkan has **no outward channel**:
   no AT Proto post, no `@mention`, no email, no nudge, no transaction, no
   actuator. There is **no dispatch cell in the manifest** — G4 is enforced by
   *absence*, not by a bypassable runtime check. `findingRecord.actuationTaken`
   const `false`; `silenJunkanReview.actuationEventsCount` = 0 +
   `outwardChannelAcquiredCount` = 0. Acquiring any outward capability is a
   **critical** violation → cell halt + chigiri.disputeMediation.
2. **G3 — PASSIVE-ONLY collection** (ADR-2605262400) — no live DNS / port-probe
   / traceroute / WHOIS / RDAP / DoH; pre-published public archives only.
3. **G5 — no causal overclaim** — every edge/loop is `hypothesis` + confidence;
   correlation / lagged-sign only, never proven causation.
4. **G6 — aggregate-only / no individual modeling** (Charter §1.4 + §2(c)).
5. **G7 — Wellbecoming-positive, non-eschatological framing** — vicious cycles
   described soberly; no fear / gore / doom / apocalyptic framing (§1.13 + §1.15).
6. **G11 — no prescription / no prediction-as-fact** — leverage *candidates*
   with uncertainty, never directives or point-forecasts-as-fact.
7. **G13 — default audience = Council/internal**; surfacing beyond Council needs
   Council Lv6+ ≥3, and **publication is done by another actor** (ossekai /
   kataribe), never by junkan — preserving G4.

Full gate table G1..G13 + non-goals N1..N12 in ADR-2605290927.

## Governance-asymmetry substrate (clj-native, added 2026-06-21)

The first concrete analysis junkan carries: **全世界の政府で国民と政府を構造的に
不均衡にしている具体的な法律・制度・思想・価値観** を system-dynamics で読み取る
clj-native, kotoba-Datom-native substrate. Each instrument records 誰が定めたか
(`:enactor`), 経緯 (`:origin`), 関係者 (`:stakeholders`) as on-the-record public
facts; junkan reads off, as disclosed hypotheses (G5), which feedback loops spin
好循環/悪循環 + Meadows leverage candidates.

- `kotoba/ontology.junkan-gov.edn` — EAVT schema · 5 asymmetry stocks
  (information / participation / coercion / paradigm / economic) · canonical
  structural loops · Meadows 12 levels · negative space.
- `kotoba/seed.governance-asymmetry.edn` — global instrument seed (grows each
  `/loop`: 35 instruments · 17 jurisdictions at iteration 1).
- `methods/junkan_edn.cljc` — loader/classify.
- `methods/analyze.cljc` — analysis-only read-off (stock regimes + loops +
  leverage candidates + coverage worklist + EAVT datoms + sober report). **No
  outward channel (G4 by absence).**
- `methods/kotoba.cljc` — content-addressed append-only findings ledger
  (commit-DAG, verify-chain tamper-evident, no-server-key, local file only).
- `methods/autorun.cljc` — deterministic idempotent-by-content heartbeat.
- `methods/query.cljc` — read-only EAVT/AVET/VAET arrangement queries over the
  findings datoms (the kotoba-kqe index model; e.g. instruments-in / stocks-by-
  regime / loops-including-stock). Read-only (G4 by absence of any write).
- `methods/validate.cljc` — substrate integrity checker (ontology↔seed↔region-map
  consistency: 誰が/経緯/関係者 completeness, enum validity, ranges, unique ids,
  region-mapping, coverage invariants). Runnable scorecard + test-wired.
- `methods/scorecard.cljc` — generates a live `SCORECARD.md` (coverage + continental
  balance + stock regimes + era trajectory + integrity verdict) so the scorecard
  never drifts from the data.
- `methods/history.cljc` — as-of / regime-trajectory reader over the ledger
  commit-DAG: which asymmetry stocks/loops CHANGED regime (好循環⇄悪循環,
  `regimeShiftEvent`) across txs — realizes the ADR's temporal data-model.
- `80-data/junkan-governance/` — DataLad dataset (datoms snapshot + ledger +
  report + provenance).
- Tests: `bb 20-actors/junkan/run_tests.bb` (bb-only; sh/bash runners are prohibited).

This substrate keeps junkan's analysis-only spine: G4 (no actuation, no dispatch
path), G5 (hypothesis-only, `:junkan/hypothesis :true` on every derived datom),
G6 (aggregate + institutional enactors only, no person/PII), G7 (a resilience/
leverage MAP, never a target-list or ranking-to-shame), G11 (leverage points are
candidates, never directives). Composes with danjo/keizu/kanae/ooyake/kosatsu
(data) and ossekai (which may publish a finding on junkan's behalf, never junkan).

## Demographic-dynamics substrate (clj-native, added 2026-06-29)

The **second** concrete analysis junkan carries (sibling of the governance-asymmetry
substrate; same stock-flow + R/B loop + Meadows spine, applied to a population
question). Reference case: **中国の一人っ子政策** (1979 → 単独二孩 2013 → 全面二孩
2015 → 三孩 + 出産奨励 2021) read as a system-dynamics **collapse-vs-replacement**
question — *why reversing the policy did not reverse the fertility decline*
(stock-flow + ~25y delay + cultural hysteresis).

- `kotoba/ontology.junkan-demography.edn` — EAVT schema · 5 demographic stocks
  (fertility-rate / reproductive-cohort / small-family-norm / elderly-dependency /
  childrearing-cost) · 5 canonical loops (B1 birth-control · R1 population-momentum ·
  R2 norm-lockin · R3 4-2-1-squeeze · B2 pronatal-incentive) · Meadows 12 · negative
  space (adds `:junkan.demog/coerce-reproduction` + `:eugenic-target` unrepresentable).
- `kotoba/seed.china-one-child.edn` — 13 dated policy levers (誰が/経緯/関係者),
  sourcing `:representative`. polarity `:suppress` (lowers fertility / collapse-ward)
  vs `:boost` (raises / replacement-ward).
- `methods/demography.cljc` — analysis-only read-off: per-stock regime + loop drive +
  Meadows leverage candidates + era trajectory + EAVT datoms + a sober report +
  `validate` (substrate integrity; polarity here is `:suppress`/`:boost`, so it does
  not reuse the governance `validate.cljc` which hardwires `:widen`/`:narrow`). Reuses
  `analyze.cljc` generics (`round3`/`regime-of`/`amplify-score`/`flip-score`/`era-of`).
  **No outward channel (G4 by absence).**
- `kotoba/seed.low-fertility-societies.edn` — peer low-fertility societies for
  **cross-society contrast**: 22 levers across **KR / JP / IT / SG** (same 5-stock
  frame). Loaded alongside the China seed; `society-contrast` / `by-jurisdiction` /
  `render-contrast-report` in `demography.cljc` give the per-society read-off, and
  `bb … methods/demography.cljc` prints both the China report and the contrast.
- `methods/test_demography.cljc` — 12 tests (contribution sign · clean validate ·
  both polarities + all 5 stocks · analysis shape · R2/R3 vicious · top-flip = the
  one-child mandate · G4/G5/G6 datom discipline · ontology negative-space absent ·
  merged-seed validate · 5-society contrast · binding constraints differ · society
  datom discipline). Wired into `run_tests.sh`. Full suite: **77 tests / 6443
  assertions green**.

**Cross-society contrast (HYPOTHESIS, G5):** every society reads vicious (collapse-ward)
but the **binding constraint differs** — 中国 small-family-norm + 4-2-1 · 韓国
education-cost + gender-penalty (reproductive-cohort) · 日本 non-marriage
(reproductive-cohort) · イタリア youth-precarity + familism · シンガポール
education-cost + crystallized small-family-norm. The shared lesson: the weakest Meadows
lever (a pronatal subsidy or a permitted-child number) never reaches the binding stock;
correction needs the deep L2–L5 levers (cost / housing / gender division / non-marriage /
norm). A resilience MAP, never a country ranking (G7).

Read-off (HYPOTHESIS, G5): the most-pressured stock is **small-family-norm** (the R2
lock-in core, vicious); R2/R3 spin vicious while B2 pronatal-incentive is overwhelmed;
the 2015/2021 reversals concentrate at **Meadows L12 (the child-count parameter)** while
the binding constraints sit at **L2 (paradigm) / L3 (system goal) / L5 (cost/housing/
gender)** — the textbook "weakest-leverage" case. Keeps junkan's analysis-only spine
(G4/G5/G6/G7/G11) and the anti-coercion line: **junkan never prescribes who should
reproduce** (`:junkan.demog/coerce-reproduction` is unrepresentable). A resilience MAP,
never a population target or a country ranking.

## Waste & sanitation-cycle substrate (clj-native, added 2026-07-10)

The **third** concrete analysis junkan carries (sibling of consumer-culture; reuses the
country/region loop-actor registry). Question: which structural factors keep parts of
India's municipal solid-waste system in an **uncollected / unsegregated / open-dumping /
informal-exclusion (accumulation)** regime, and which are moving it toward **reliable
collection / source segregation / processing capacity / recycler-market linkage
(circularity)** — the "system dynamics react loop" behind street-waste accumulation and
public-hygiene outcomes.

- `methods/waste_sanitation.cljc` — analysis-only read-off (8 stocks: collection-
  reliability / source-segregation / informal-worker-integration / municipal-processing-
  capacity / landfill-dependency / recycler-market-linkage / public-compliance-norm /
  regulatory-enforcement; 5 loops: R-segregation-recycler-linkage / R-collection-
  compliance-trust / R-informal-integration-recycler-linkage / B-processing-capacity-
  landfill / B-enforcement-informal-displacement). Same architecture as
  `consumer_culture.cljc` (signal → contribution → stock pressure → regime → loop drive).
  **No outward channel (G4 by absence).**
- `kotoba/seed.india-waste-sanitation.edn` — 12 representative signals across all 7
  regions/pan-India and both polarities (`:toward-circular` / `:toward-accumulation`),
  citing CPCB SWM Rules implementation reporting, SBM-U (Gramin+Urban), and MoHUA as
  public source classes. Hypothesis-only, non-essentialist (explicit counter-forces:
  door-to-door scale-up, segregation-at-source pilots, waste-picker cooperative
  integration, legacy-dumpsite bioremediation, civic behaviour-change campaigns).
- Registered as the `:waste-sanitation-cycle` domain in
  `kotoba/ontology.country-region-loop-actors.edn` (`:domain` enum) and
  `kotoba/seed.country-region-loop-actors.edn` (`world` + `IN` + `IN-NORTH`/`IN-SOUTH`/
  `IN-WEST`/`IN-EAST`/`IN-NORTHEAST`/`IN-CENTRAL` loop-actors), alongside
  `packaged-goods-culture` — same registry, same `methods/country_region_actors.cljc`
  validation (parent chains, required gates, domain inheritance, fission rules).
- `methods/test_waste_sanitation.cljc` — mirrors `test_consumer_culture.cljc` (contribution
  sign · analysis shape/invariants · region+language coverage · counterforces explicit ·
  loop grounding · report rendering). `methods/test_country_region_actors.cljc` extended
  with waste-sanitation-cycle fission + domain-inheritance tests.

**Scope boundary — this is deliberately analysis-only, not the collection/recycling
business itself.** junkan reads the loop; it does not dispatch a collection vehicle,
route a truck, or pay a recycler — that would be an outward channel and is barred by G4.
On-the-ground collection/sorting/recycling-business *execution* (the "robo/giemon collect
and sort waste" side of the question) is out of scope for junkan by design and belongs to
a separate, Governor-gated actor (robotaxi-actor pattern: intelligence-node proposal +
independent PolicyGovernor/SafetyGovernor + append-only audit ledger) which MAY read
junkan's findings as input but which junkan does not compose with or actuate (G4/G13) —
the same analysis/intervention split junkan already keeps with ossekai.

## Self-publication seed (ADR-2606272355) — register → autonomize → publish, no-server-key

junkan is wired with the **actor self-publication seed**: the uniform, charter-clean way
for a government-mirror actor to be registered at etzhayyim.com, run autonomously on the
kotoba mesh, and **self-publish its own history + findings** to AT-proto **without any
server-held key**. We plant the seed; the actor grows on the mesh (murakumo,
`orgs/com-junkawasaki/murakumo/`) and self-custodies its signing identity in its WASM runtime.

**junkan is ANALYSIS-ONLY (G4) — so this seed is the narrowest possible exception.** junkan
has no outward channel by its own discipline; the membrane below is the ONE careful path
out, and it publishes only DISCLOSED HYPOTHESES (G5) + on-record public facts as **dry-run
MIRROR posts**, never as verdicts, never proven causation, never directives. Live broadcast,
when it happens, is carried via ossekai/kataribe on junkan's behalf — never by junkan (G13).

The seed (all LANDED):

- **did-web registration** — `50-infra/etzhayyim-did-web/public/actor/junkan/{did,profile}.json`
  (`verificationMethod: []` — no server-minted key, did:web trust root = TLS; the
  `#xrpc-libp2p` peer multiaddr is assigned at `bb murakumo deploy` time when `wasmCid` is set).
- **social_post membrane** — `cells/social_post/state_machine.cljc`: DRAFTS a record into a
  **dry-run** post ONLY if ≥2 public-source citations (G5) + non-adjudicating MIRROR with the
  analysis-only disclaimer (G7) + `server_held_key` false (no-server-key) + status `dry-run`.
  A `published` request REFUSES. Verified under `bb`: `<2 sources / server-key / published →
  refused`, valid → `drafted` with `:post/status :dry-run`, `:post/server-held-key false`.
- **publication projection** — `methods/social.cljc`: projects junkan's HISTORY (on-record
  governance-asymmetry instruments — law/institution + 誰が定めたか/経緯/関係者) + FINDINGS
  (disclosed-hypothesis loop read-offs 好循環/悪循環 + Meadows leverage candidates) into
  `app.bsky.feed.post`-shaped dry-run posts (`draft-instrument-post` / `draft-loop-post` /
  `draft-leverage-post`); `enough-sources` raises on <2 (G5); `build-live` raises (live gate).
  Verified under `bb`.
- **seed trigger wiring** — `kotoba.app.edn` `junkan-social` component (`on-tick "0 */6 * * *"`
  + `on-kse etzhayyim/actor/junkan/publish`, `:requires #{:cap/kqe :cap/atproto}`).

**Division of labor (zero-knowledge)**: the **planter** authors the in-repo seed (holds no
key); the **operator** (founder) runs `bb murakumo deploy 20-actors/junkan/kotoba.app.edn <node>`
with `MURAKUMO_OPERATOR_SEED` + Tailscale and exercises the Council gate for the first live post;
the **actor's mesh runtime** self-generates/self-custodies its `did:key`, presents a member CACAO
leash (ADR-2606111400), and signs its own posts. The server never signs. R0 = dry-run drafts
only; live broadcast is Council Lv6+ + operator + member/actor-signature gated (§1.12 / G11 / G13).

```text
bb -e '(load-file "methods/social.cljc")'                 # projection loads green
bb -e '(load-file "cells/social_post/state_machine.cljc")' # membrane loads green
# operator step (zero-knowledge — needs MURAKUMO_OPERATOR_SEED + Tailscale):
#   bb murakumo deploy 20-actors/junkan/kotoba.app.edn <node>
```

## Data model — datom / Datalog on kotoba-kqe (NOT proprietary Datomic)

Feedback-loop analysis is temporal: a loop's regime is only readable from how a
stock moved over time. The natural model is **immutable facts with time** —
Datomic's `[E A V T]` datom + `tx-time` + `as-of`/`history`.

The repo's canonical substrate **`kotoba-kqe` (ADR-2605262130) is
Datomic-isomorphic**: content-addressed Datalog with `EAVT / AEVT / AVET / VAET`
arrangements. junkan adopts the **datom data model** and realizes it on
kotoba-kqe. **Proprietary Datomic is NOT used** (substrate boundary + Charter
Rider §2(e) anti-gatekeeping + §2(c) vendor data-sovereignty); a literal-Datomic
carve-out would require a Council ADR.

Append-only (G9); nothing is ever retracted — matching both Datomic semantics
and §1.15 (trajectory, not destination).

## Architecture — LangGraph heartbeat-cadence Pregel graph

```
ingest → estimate_stocks → infer_flows → build_cld → classify_loops
       → find_leverage → wellbecoming_frame → emit_findings (WRITE-ONLY, no dispatch)
periodic: silen_review
```

8 Pregel cells under `40-engine/kotoba/crates/kotoba-kotodama/cells/junkan_*/` (R0 path-reserved,
import-time `RuntimeError` until R1). State is a `TypedDict`
(`tick, observations, stock_estimates, cld, loops, leverage, findings`).
`emit_findings` never routes to any dispatch/post/mention node — that node does
not exist (G4).

## Lexicons (`com.etzhayyim.junkan.*`)

- `societalStockObservation` — append-only; aggregate-only (G6); immutable (G9)
- `causalLoopFinding` — R/B type + currentRegime {virtuous, vicious, neutral, transitioning} + hypothesisOnly const true (G5)
- `leveragePointFinding` — Meadows level 1–12 + prescriptionGiven const false (G11)
- `regimeShiftEvent` — fromRegime → toRegime (好循環⇄悪循環 detection)
- `silenJunkanReview` — G1..G13 const-field structural enforcement

## Boundaries (who junkan is NOT)

- **NOT ossekai** (ADR-2605264000) — ossekai intervenes (posts/nudges); junkan only analyzes.
- **NOT KaizenObserver** (ADR-2605240200) — that is the inward self-model; junkan is outward/societal.
- **NOT kazaori** (ADR-2605263200) — junkan may feed it findings read-only; junkan does not respond.
- **NOT a forecaster / prescriber / surveillance / state-intel actor** (N3/N4/N2/N6).

## References

- ADR-2605290927 — junkan R0 charter (this actor)
- ADR-2605262130 — kotoba storage substrate (Datomic-isomorphic kotoba-kqe)
- ADR-2605262400 — passive public-data ingestion
- ADR-2605240200 — KaizenObserver (inward sibling)
- ADR-2605264000 — ossekai (intervention sibling)
- `90-docs/2605221243-ideal-ecosystem-state-active-inference-prior.md` — inward loop model
