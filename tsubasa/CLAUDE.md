# 20-actors/tsubasa 翼

**Flight-route / fare discovery commons — the Skyscanner inversion. ADR-2606072800. Status: R3.**

Honest fare/route meta-search; closes the last named-app coverage gap (uber→ainori, airbnb/
hotels→shukubo, salesforce→business-manager, calendly→yotei, drive→organizer, indeed→talent,
shopify→omise, **flight-scanner→tsubasa**). Sibling of `watari` (live position) — tsubasa plans,
watari tracks; both observational, neither an OTA.

## Hard prohibitions (structurally unrepresentable, not policy)
- **No affiliate / no inflow** (G1): onward links affiliate-stripped; member self-books on the
  airline's OWN site; tsubasa is never merchant-of-record (`commissionMinor`/`titheMinor` ≡ 0,
  `principal` = member). No commission/affiliate/merchant field in any lexicon, ontology, or datom.
- **Emissions-honest** (G4): `co2Kg` / `:fare/co2-kg` is REQUIRED on every fare/result; `compare`
  exposes the greenest option as a first-class result — a high-CO₂ option cannot be ranked-away
  invisibly. Rank by true total cost (fare + baggage), not headline fare.
- **Anti-dark** (G3): no urgency / "price will rise" / scarcity field exists.
- **No person fare-tracking** (G5): search is stateless w.r.t. the searcher; no `:searcher`/`:person` field.

These are enforced *structurally* — the forbidden attributes are absent from the ontology, the
seed, and the datom emitter — and proven by `test_analyze` + `test_seed_integrity`.

## Layout
- `py/agent.cljc` / `py/agent.clj` — live query handlers: `search-fares` (true total cost +
  emissions surfaced) · `compare` (cheapest·greenest·fastest first-class) · `strip-affiliate` ·
  `self-book-handoff` (no commission, member principal). py→clj port (`py/agent.py`).
- `00-contracts/schemas/flight-fare-ontology.kotoba.edn` — canonical EAVT ontology (with the
  constitutional boundary in its header). `kotoba/schema.edn` is the legacy R0 schema (subset).
- `data/seed-fares.kotoba.edn` — `:representative` seed (13 airports / 9 regions / 13 carriers /
  11 routes / 23 fares). Committed input; `data/persisted/` is the generated ledger (gitignored).
- `methods/analyze.cljc` — per-route carrier-HHI concentration → competition reading → `:opening`
  route + cheapest/greenest/fastest + coverage gap worklist + EAVT `datoms` + markdown report.
- `methods/kotoba.cljc` — content-addressed append-only commit-DAG (`tx-cid` / `verify-chain`,
  tamper-evident, no-server-key) — the busshi/meisai/kakaku family machinery.
- `methods/autorun.cljc` — deterministic, idempotent-by-content heartbeat (analyze → append on
  change; a no-op when unchanged; resume-safe).
- `methods/ingest.cljc` — **R3** live fare ingest: a parsed fetch-leg payload → `:authoritative`
  `:fare` rows. Charter-bounded source (`:public`/`:member-principal`; `:paid-terminal` refused),
  no network in the loop (no-server-key), G1/G4/G5 enforced (poisoned / no-CO₂ rows rejected).
- `methods/fetch.cljc` — **R3** AUTONOMOUS read-only PUBLIC-source fetch → ingest. The actor
  fetches public fare sources ITSELF (no operator, no key); no-server-key EXEMPTS read-only.
  `:member-principal` is refused here (runs in the member's runtime); `:paid-terminal` refused downstream.
- `methods/digest.cljc` — **R3** Murakumo-narrated digest, loopback-only (`127.0.0.1:4000`),
  fail-open to a deterministic anti-dark template (G6).
- `wasm/` — **R3** compute-only WASM Component scaffold (`world.wit` + `build.clj` (bb)); no
  `wasi:sockets/clocks/random` (absence = G1/G5/G6); artifact build = operator step.
- `methods/identity.cljc` — **R3+** actor self-certifying `did:key`: Ed25519 keygen + did:key
  encode + present-only sign/verify; seed sealed (Keychain/1Password), never exposed.
- `methods/kotoba_bridge.cljc` — **R3+** push local commit-DAG → LIVE kotoba engine (:8077);
  host allowlist, exactly-once `:bridge` cursor, dry-run default, operator-bearer + member CACAO
  leash present-only, fail-open. Wired into `autorun --bridge`.
- `methods/openflights.cljc` — **R3+** OpenFlights ODbL public-domain airports/airlines →
  `:authoritative` `:airport`/`:carrier` coverage rows (read-only; no fares fabricated).
- `cell.cljc` — **R3+** `tsubasa.cell/fire` heartbeat; registered as `TsubasaHeartbeatCell`
  (cells.edn, node asher, cron `27 * * * *`, healthz 13090).
- `methods/test_*.cljc` — analyze / kotoba / autorun / seed-integrity / ingest / digest / fetch /
  identity / kotoba_bridge / openflights suites.
- `run_tests.clj` — bb-native runner (no shell — per the repo clj/bb rule; supersedes `run_tests.sh`).

## Run (scripts are bb — repo clj/bb rule; no shell)
```
bb 20-actors/tsubasa/run_tests.clj                                   # 74 tests / 634 assertions (cwd-independent)
bb --classpath 20-actors 20-actors/tsubasa/methods/autorun.cljc 20-actors/tsubasa/data/seed-fares.kotoba.edn data/persisted/tsubasa.observations.kotoba.edn --bridge  # heartbeat + push to LIVE engine (dry-run unless TSUBASA_KOTOBA_LIVE=1)
bb --classpath 20-actors 20-actors/tsubasa/methods/analyze.cljc      # competition + fare map + coverage
bb --classpath 20-actors 20-actors/tsubasa/methods/autorun.cljc      # one heartbeat → append to the ledger
bb --classpath 20-actors 20-actors/tsubasa/methods/digest.cljc       # Murakumo digest (fail-open template)
bb --classpath 20-actors 20-actors/tsubasa/methods/fetch.cljc "<public-fare-source-url>" "<as-of>"  # AUTONOMOUS read-only fetch → ingest
bb --classpath 20-actors 20-actors/tsubasa/wasm/build.clj <component.wasm>   # verify WASM cleanliness + CID (operator)
```

## Gating (G8 — UNLOCKED R3, charter-bounded)
Live GDS/airline fare ingest is **UNLOCKED** (R3, 2026-06-21) — founder Lv7+ attested via PR review
(Bootstrap Council attestation premise). The unlock is **structurally bounded**:
- **source** is `:public` or `:member-principal` ONLY — a `:paid-terminal` is refused (Rider §2(e)/§2(i)).
- **no-server-key ≠ no-automation.** It bars a *custodial unilateral signing key* on an etzhayyim
  automated process, and **read-only is exempt** (ADR-2605231525). So **public sources are fetched
  AUTONOMOUSLY by the actor itself** (`methods/fetch.cljc`, read-only, no key — like kaname/watari/
  tsumugi). Only `:member-principal` creds (member's runtime/consent) and the actor's OWN
  write-*signing* need a key — and signing uses a **self-generated did:key (sealed seed, present-only)
  + member CACAO leash** (ibuki/kaname pattern); appending to the LOCAL log needs no key.
- **G1/G3/G4/G5 unchanged** and enforced at ingest (poisoned / no-CO₂ rows rejected).
- tsubasa transacts no booking (member self-books).

See ADR-2606072800 §R3 for the full gate-unlock + no-server-key clarification.

## DID
`did:web:etzhayyim.com:actor:tsubasa` — registered in `50-infra/.../registry/infra-actors.ts`
(+ static `public/actor/tsubasa/{did,profile}.json`). primaryLexicon `com.etzhayyim.tsubasa.fare`,
primarySchema `flight-fare-ontology.kotoba.edn`.
