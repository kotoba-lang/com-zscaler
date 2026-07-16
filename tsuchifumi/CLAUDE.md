# tsuchifumi 土踏み — CLAUDE (per-actor)

clj-native Tier-B actor. earthing (アーシング) under-institutionalization × ambient-EMF
→ Wellbecoming observatory + system-dynamics model + risk analysis + transparent
おせっかい (ossekai) nudge over social-proto / atproto. **ADR-2606212000** (R0).

## Invariants (do not weaken without amending the ADR)

- **G2 is the defining invariant — epistemic honesty / anti-pseudoscience.** This is a
  scientifically CONTESTED domain. exposure-load + earthing-deficit are MEASURED facts;
  the bioelectric health burden is a HYPOTHESIS reported ONLY with its resting evidence
  tier. **Never assert a :contested/:anecdotal claim (non-thermal EMF harm, earthing-
  therapy benefit) as established harm.** A practice nudge must rest on ≥:emerging
  evidence; only the honesty post may NAME a contested claim, to disclaim it. Do not add
  a path that lets a contested burden become an asserted harm or a `:relief-priority`.
- **G1 non-diagnostic / non-therapeutic.** tsuchifumi never diagnoses/treats/cures.
  `:tsuchifumi/diagnose|:treat|:cure` are unrepresentable. Care routes to mitate/iyashi/
  kokoro. Do not add a clinical-claim or treatment method.
- **G5 no-commerce.** Sells NOTHING — no earthing mat/device/product/affiliate. This
  domain is rife with product fear-marketing; that is structurally excluded
  (`:tsuchifumi/product|:buy` unrepresentable; sales tokens refused in post bodies).
- **G4 no-fear nudge + ossekai handoff.** ossekai PROPOSES no-regret low-risk practices;
  the **ossekai 御節介** actor CARRIES them consent-bound + on-chain-logged. tsuchifumi
  never fear-mongers and never publishes itself (shiori→ossekai pattern).
- **G6 distribution-only model (非終末論).** The system-dynamics model emits p10/p50/p90
  ensemble bands; a single point forecast is unrepresentable. Forecasting → mitooshi.
- **Co-scientist (特定+分析) keeps two tracks separate.** `coscientist.cljc` ranks
  `:action` (no-regret, ≥:emerging → ossekai) by expected relief and `:research`
  (contested → suimin/mitooshi, studied) by value-of-information. A contested candidate
  on the `:action` track must stay vetoed — never add a path that lets a contested claim
  be acted on or asserted. The catalog is closed (aligned mechanisms only); do not let
  the generator free-write. Keep the tournament deterministic (no randomness/wall clock).
- **G3 no person-data.** Aggregate cohort/region only.
- **G8 no-server-key + kotoba EAVT.** State is the kotoba Datom log; the heartbeat holds
  no key and does no network I/O. Live atproto carry is member-signed + done by ossekai.

## Code conventions

- clj/bb over the kotoba Datom log (repo-wide 実装 convention). Pure stdlib; methods are
  `.cljc` with `#?(:clj …)` for file I/O. No `.py`/`.sh` for first-party logic.
- Deterministic everywhere: no `Math/random`, no wall clock — the sysdyn ensemble jitter
  is sha256-seeded (hakoniwa pattern); the ledger CID is content-addressed (kafun/ugachi
  family). Tests must stay byte-deterministic.
- The persistence family (`kotoba.cljc`/`autorun.cljc`) is the kafun/ugachi/meisai
  content-addressed commit-DAG verbatim in shape — keep `verify-chain` + idempotent-by-
  content semantics.
- `viz.cljc` generates HTML from REAL method output only (no hand-copied data, no network
  resource). Keep it self-contained (the test enforces no http/https/external script).

## R1+ (operator/Council-gated)

- Replace the :synthetic seed with real environmental-EMF / public-health / greenspace-
  access data (G7).
- Live atproto carry via ossekai (member-signed, consent-bound, on-chain-logged) (G8).
- DID registration (actor-profile-seed + INFRA_ACTORS + static did.json/profile),
  fleet heartbeat cell, IPFS/IPNS publication of the viz + ledger snapshot.
- .cljc → WASM build (componentize-py / rasen pattern) for browser-local execution.
