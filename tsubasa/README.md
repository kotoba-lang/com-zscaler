# tsubasa 翼 — flight-route / fare discovery commons

The **Skyscanner inversion**. An honest fare/route meta-search that takes no commission, never
tracks the searcher, and surfaces CO₂ on every option. tsubasa **plans**; its sibling
[`watari`](../watari) **tracks** live aircraft positions. Neither is an OTA — tsubasa transacts
no booking; the member self-books on the airline's own site.

## What it does

1. **Search & rank** (`py/agent.cljc`) — find fares for an O–D/date and rank by **true total cost**
   (fare + checked-bag), not the headline fare. `compare` returns the **cheapest, greenest, and
   fastest** options side by side, so a low-fare/high-CO₂ option can never hide a greener one.
2. **Affiliate-strip & self-book handoff** — every onward link is stripped of affiliate/tracking
   params and points at the **airline's own** booking page. tsubasa is never merchant-of-record:
   `commission ≡ 0`, principal = the member.
3. **Competition + fare map** (`methods/analyze.cljc`) — per O–D route it derives carrier
   **concentration** (HHI over carrier presence) and a competition reading
   (`:competitive` / `:concentrated` / `:monopoly`); thin-competition routes are flagged
   **`:opening`** (surface alternatives). This is a competition map routed to *opening* — **never a
   paid ranking and never a target-list**.
4. **Persist** (`methods/{kotoba,autorun}.cljc`) — observations append to a content-addressed,
   tamper-evident kotoba Datom **commit-DAG** via a deterministic, idempotent-by-content heartbeat
   (an unchanged beat is a no-op). No server key; appends to a local file only.

## Constitutional invariants (structural, not policy)

| gate | invariant |
|---|---|
| G1 | no affiliate / no inflow — `commission`/`affiliate`/`merchant` attributes do not exist |
| G3 | anti-dark — no `urgency`/`scarcity`/`price-will-rise` attribute exists |
| G4 | emissions-honest — `:fare/co2-kg` REQUIRED + positive; greenest is first-class |
| G5 | no person fare-tracking — no `:searcher`/`:person` attribute; search is stateless |
| G7 | kotoba-EAVT-native — no RisingWave/SQL |
| G8 | outward UNLOCKED (R3, charter-bounded) — live ingest `:public`/`:member-principal` only (paid GDS terminal refused), no network in the loop (no-server-key), G1/G3/G4/G5 enforced at ingest |

## Status

**R3** — see [`MATURITY.md`](MATURITY.md). 57 tests / 590 assertions green. The **G8 live-ingest
gate is UNLOCKED** (founder Lv7+ attested via PR review, 2026-06-21) under structural charter
bounds — `:public`/`:member-principal` sources only (no paid GDS terminal), G1/G3/G4/G5 enforced
at ingest. **no-server-key bars a custodial unilateral signing key, not automation, and exempts
read-only**: so **public sources are fetched AUTONOMOUSLY by the actor** (`methods/fetch.cljc`,
no key, no human). Only a member-principal pull (member's creds/runtime) and the compiled WASM
artifact are consent/operator steps; signing the actor's own writes uses a self-`did:key` (sealed,
present-only) + member CACAO leash. Today the bundled data is still a bounded `:representative` seed.

Per **ADR-2606072800**. Apache 2.0 + etzhayyim Charter Compliance Rider.
