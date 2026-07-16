# 委 yudane — CLAUDE (actor-local rules)

Human Intention Energy Engine — the 意味変換 leg of the **Energy Order Protocol** suite
(submits to 澪 mio). The MOST charter-sensitive actor. OBSERVATION + TRANSLATION ONLY.
ADR-2606211200.

## Invariants (do not weaken — this is the actor where privacy is the product)

- **G1 consent-bound.** Intention enters ONLY via a member-signed capability that is
  present + unexpired (ibuki revocable-leash). No capability ⇒ `:refused-no-capability`;
  expired ⇒ `:refused-expired`. Never bypass the capability check.
- **G2 content-free / k-anonymous.** Only an intention CLASS + an AGGREGATE cohort signal
  are ever read or emitted. NEVER add a `:yudane.person/*`, `:intent-content`, or per-person
  field. NEVER lower `k-anon-min` below a real anonymity floor (a cohort under it is refused —
  an individual could be identified). The degeneration series (五人組→隣組→Stasi→
  social-credit) must stay unrepresentable: no `:score`, no `:denunciation`, no `:surveil`.
  Tests `g1-g2-g4-degeneration-series-unrepresentable` + `below-k-anon-refused` guard this.
- **G3 reciprocal-transparent.** Non-reciprocal/unlogged ⇒ refused (Rider §2(c) symmetry).
- **G4 translation-only.** yudane translates; it NEVER controls. No `:yudane/dispatch`.

## Conventions

- clj/bb over the kotoba Datom log; append-only content-addressed commit-DAG ledger.
  Mirrors mio's single-kind verdict shape. Ledger machinery byte-identical to the family.
- The seed is synthetic cohorts; live intention ingest is consent/operator-gated (R1) —
  the capability is member-signed in the member's OWN runtime; yudane is the bearer.
- Tests: `./20-actors/yudane/run_tests.sh` (babashka). Keep green before commit.

## Suite

backbone = 澪 mio. yudane translates consented aggregate intention → flex offers → 撓
tawami / 澪 mio. hikari actuates under Council gate. Scaffolded last, behind its consent gate.
