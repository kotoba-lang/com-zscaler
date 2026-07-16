# junkan (循環)

**Analysis-only societal feedback-loop observer.**

junkan applies systems-thinking to society at large: from passive, public,
aggregate data it continuously builds a system-dynamics model (stocks, flows,
reinforcing/balancing causal loops) and reads off which loops are currently
spinning **virtuous (好循環)**, **vicious (悪循環)**, **neutral**, or
**transitioning**, plus Meadows leverage-point candidates.

It then stops. **junkan has no actuator** — no post, no nudge, no email, no
transaction. Its only output is append-only structured findings for the Council
and other actors to read. That is the whole point: 分析するだけ.

## DID

- `did:web:junkan.etzhayyim.com`

## ADR

- ADR-2605290927 (R0 scaffold, 2026-05-29)
- Manifest: `20-actors/junkan/manifest.jsonld`

## Position in the ecosystem

| | Inward (self) | Outward (society) |
|---|---|---|
| Loop model | active-inference prior (doc 2605221243) | **junkan** |
| Continuous observer | KaizenObserver (ADR-2605240200) | **junkan** |
| Intervention | — | ossekai (ADR-2605264000) |

junkan is the outward, analysis-only complement of the inward self-model. It is
**not** ossekai (which intervenes) and **not** KaizenObserver (which is the self).

## Tech

- **Python LangGraph** heartbeat-cadence Pregel graph (8 cells).
- **datom / Datalog** data model (immutable society-stock facts + time-travel)
  on canonical **kotoba-kqe** (ADR-2605262130, Datomic-isomorphic
  EAVT/AEVT/AVET/VAET). Proprietary Datomic is **not** used (Charter Rider
  §2(e)+§2(c)).

## Constitutional spine

Analysis-only (G4, enforced by absence of any dispatch cell) · passive-only
collection (G3) · no causal overclaim (G5) · aggregate-only / no individual
modeling (G6) · non-eschatological framing (G7) · no prescription /
prediction-as-fact (G11) · Murakumo-only inference (G10) · default
Council-internal, publication-by-others (G13). Full table in ADR-2605290927.

## Status

R0 scaffold — 8 cells path-reserved + 5 Lexicon skeletons. No runtime code
until R1 (post Bootstrap-Council ratify).

## Execution Rule

`junkan` is **bb-only**. Do not add or invoke `.sh` / bash / shell runners for
this actor. Run tests with `bb 20-actors/junkan/run_tests.bb` from the repo root.

## India Packaged-Goods Culture Addendum

`methods/consumer_culture.cljc` adds a separate aggregate-only read-off for the
question of Indian packaged goods vs loose/refill/kirana purchase. It models
regional, language, channel, and rural/urban pressures rather than treating
"Indians" as one culture. Positive net pressure means loose/refill/local-small-
quantity purchase persists; negative pressure means packaged/modern-retail pull.

Seed data lives in `kotoba/seed.india-packaged-goods.edn`. It is explicitly
representative and hypothesis-only, with counter-forces for sachets, modern
trade, ecommerce, and language-local packaged brands.

## Country/Region Loop Actors

`kotoba/ontology.country-region-loop-actors.edn` and
`kotoba/seed.country-region-loop-actors.edn` define the repeatable actor pattern:
a world domain actor, country actors, and region actors that inherit shared
stocks/loops while carrying local language, settlement, channel, and source
coverage. `methods/country_region_actors.cljc` validates parent chains, required
gates, domain inheritance, and fission rules.

The initial packaged-goods registry seeds `IN` plus `IN-NORTH`, `IN-SOUTH`,
`IN-WEST`, `IN-EAST`, `IN-NORTHEAST`, and `IN-CENTRAL`, with `JP`, `US`, and
`BR` left as designed country actors awaiting local aggregate public sources.
The `waste-sanitation-cycle` domain (below) reuses the same registry with its
own `world` + `IN` + 6-region actor set.

## India Waste & Sanitation Cycle Addendum

`methods/waste_sanitation.cljc` adds a separate aggregate-only read-off for
India's municipal solid-waste **collection, source-segregation, processing,
and recycling-market-linkage** cycle — the "system dynamics react loop" behind
uncollected street waste, open dumping/burning, and informal waste-picker
exclusion versus reliable collection, segregation compliance, processing
capacity, and recycler-market linkage. It models region, language, channel,
and rural/urban pressures, not "Indian sanitation" as one uniform condition:
India also has ODF++/5-star SBM-U certified cities, scientific-processing
capacity build-out, and waste-picker cooperative integration pilots, and those
counter-forces are represented explicitly. Positive net pressure means the
cycle is moving toward circularity (collection/segregation/processing/
recycling); negative pressure means it is moving toward accumulation
(uncollected/unsegregated/landfill/open-dumping).

Seed data lives in `kotoba/seed.india-waste-sanitation.edn`. It is explicitly
representative and hypothesis-only, with counter-forces for door-to-door
collection scale-up, segregation-at-source pilots, waste-picker cooperative
integration, legacy-dumpsite bioremediation, and civic behaviour-change
campaigns. The `waste-sanitation-cycle` domain is registered in
`kotoba/ontology.country-region-loop-actors.edn` /
`kotoba/seed.country-region-loop-actors.edn` alongside `packaged-goods-culture`,
seeding `world` + `IN` + `IN-NORTH`/`IN-SOUTH`/`IN-WEST`/`IN-EAST`/
`IN-NORTHEAST`/`IN-CENTRAL`.

**Scope boundary (G4, analysis-only):** this addendum reads which loops are
spinning toward circularity or accumulation and surfaces Meadows leverage
candidates. It has no dispatch, route-optimization, or recycler-payment
function — junkan never schedules a collection vehicle or pays a recycler.
On-the-ground collection/sorting/recycling-business *execution*, if built, is
a separate, Governor-gated actor's concern (robotaxi-actor pattern: proposal
+ independent Governor + append-only audit ledger), which MAY read junkan's
findings but which junkan does not compose with or actuate (G4/G13).

## License

Apache-2.0 WITH etzhayyim Charter Compliance Rider v2.0 (`/CHARTER-RIDER.md`).
